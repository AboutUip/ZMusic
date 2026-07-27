using System.Windows.Media.Imaging;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using ZMusic.Data;

namespace ZMusic.ViewModels;

public enum LoginMode
{
    Qr,
    Phone,
}

public partial class LoginViewModel : ObservableObject
{
    private const int CaptchaResendIntervalSec = 60;

    private readonly SessionStore _sessions;
    private readonly NcmAuthClient _api;
    private CancellationTokenSource? _qrPollCts;
    private CancellationTokenSource? _captchaCooldownCts;
    private string? _qrUnikey;

    public LoginViewModel(SessionStore sessions, NcmAuthClient api)
    {
        _sessions = sessions;
        _api = api;
    }

    public event Action? LoggedIn;

    [ObservableProperty]
    private LoginMode _mode = LoginMode.Qr;

    [ObservableProperty]
    private bool _busy;

    [ObservableProperty]
    private string? _bannerError;

    [ObservableProperty]
    private string _qrHint = "正在准备二维码…";

    [ObservableProperty]
    private BitmapImage? _qrImage;

    [ObservableProperty]
    private bool _qrExpired;

    [ObservableProperty]
    private string _phone = string.Empty;

    [ObservableProperty]
    private string _captcha = string.Empty;

    [ObservableProperty]
    private bool _captchaSending;

    [ObservableProperty]
    private int _captchaCooldownSec;

    [ObservableProperty]
    private string _smsHint = string.Empty;

    public bool CanSendCaptcha =>
        !CaptchaSending && CaptchaCooldownSec <= 0 && !string.IsNullOrWhiteSpace(Phone);

    public string SendCaptchaLabel =>
        CaptchaCooldownSec > 0 ? $"{CaptchaCooldownSec}s 后重发" : "获取验证码";

    partial void OnPhoneChanged(string value)
    {
        _captchaCooldownCts?.Cancel();
        CaptchaCooldownSec = 0;
        SmsHint = string.Empty;
        OnPropertyChanged(nameof(CanSendCaptcha));
        OnPropertyChanged(nameof(SendCaptchaLabel));
    }

    partial void OnCaptchaSendingChanged(bool value)
    {
        OnPropertyChanged(nameof(CanSendCaptcha));
        OnPropertyChanged(nameof(SendCaptchaLabel));
    }

    partial void OnCaptchaCooldownSecChanged(int value)
    {
        OnPropertyChanged(nameof(CanSendCaptcha));
        OnPropertyChanged(nameof(SendCaptchaLabel));
    }

    partial void OnModeChanged(LoginMode value)
    {
        BannerError = null;
        if (value == LoginMode.Qr)
        {
            _ = ResumeOrStartQrAsync();
        }
        else
        {
            // Keep current QR; only pause polling while on phone tab.
            StopQrPolling();
        }
    }

    public Task InitializeAsync() => StartQrFlowAsync(forceRefresh: false);

    public void DisposeRuntime()
    {
        StopQrPolling();
        _captchaCooldownCts?.Cancel();
        _captchaCooldownCts?.Dispose();
    }

    [RelayCommand]
    private void SelectQr() => Mode = LoginMode.Qr;

    [RelayCommand]
    private void SelectPhone() => Mode = LoginMode.Phone;

    [RelayCommand]
    private async Task RefreshQrAsync() => await StartQrFlowAsync(forceRefresh: true);

    private Task ResumeOrStartQrAsync()
    {
        // Still valid: resume polling without regenerating the image.
        if (!QrExpired
            && QrImage is not null
            && !string.IsNullOrWhiteSpace(_qrUnikey))
        {
            if (_qrPollCts is null)
            {
                QrHint = string.IsNullOrWhiteSpace(QrHint) || QrHint.Contains("过期", StringComparison.Ordinal)
                    ? "使用网易云音乐 App 扫一扫"
                    : QrHint;
                _ = PollQrAsync(_qrUnikey);
            }

            return Task.CompletedTask;
        }

        return StartQrFlowAsync(forceRefresh: true);
    }

    [RelayCommand]
    private async Task SendCaptchaAsync()
    {
        if (!CanSendCaptcha)
        {
            return;
        }

        if (string.IsNullOrWhiteSpace(Phone))
        {
            BannerError = "请输入手机号";
            return;
        }

        CaptchaSending = true;
        BannerError = null;
        var sentOk = false;
        try
        {
            var json = await _api.CaptchaSentAsync(Phone.Trim()).ConfigureAwait(true);
            if (NcmJson.ApiCode(json) != 200)
            {
                BannerError = NcmJson.Message(json) ?? "验证码发送失败";
            }
            else
            {
                SmsHint = "验证码已发送，请查收短信";
                sentOk = true;
            }
        }
        catch (Exception ex)
        {
            BannerError = ex.Message;
        }
        finally
        {
            CaptchaSending = false;
        }

        if (sentOk)
        {
            await BeginCaptchaCooldownAsync().ConfigureAwait(true);
        }
    }

    [RelayCommand]
    private async Task LoginWithSmsAsync()
    {
        if (Busy)
        {
            return;
        }

        if (string.IsNullOrWhiteSpace(Phone) || string.IsNullOrWhiteSpace(Captcha))
        {
            BannerError = "请输入手机号与验证码";
            return;
        }

        Busy = true;
        BannerError = null;
        try
        {
            var json = await _api.LoginCellphoneAsync(Phone.Trim(), Captcha.Trim())
                .ConfigureAwait(true);
            if (ConsumeLoginSuccess(json))
            {
                LoggedIn?.Invoke();
            }
        }
        catch (Exception ex)
        {
            BannerError = ex.Message;
        }
        finally
        {
            Busy = false;
        }
    }

    private async Task StartQrFlowAsync(bool forceRefresh)
    {
        if (!forceRefresh
            && !QrExpired
            && QrImage is not null
            && !string.IsNullOrWhiteSpace(_qrUnikey))
        {
            await ResumeOrStartQrAsync().ConfigureAwait(true);
            return;
        }

        StopQrPolling();
        Busy = true;
        BannerError = null;
        QrExpired = false;
        QrImage = null;
        _qrUnikey = null;
        QrHint = "正在准备二维码…";

        try
        {
            var keyJson = await _api.LoginQrKeyAsync().ConfigureAwait(true);
            if (NcmJson.ApiCode(keyJson) != 200)
            {
                BannerError = NcmJson.Message(keyJson) ?? "无法获取二维码";
                QrHint = "二维码准备失败";
                return;
            }

            var key = NcmJson.QrKey(keyJson);
            if (string.IsNullOrWhiteSpace(key))
            {
                BannerError = "二维码 key 解析失败";
                QrHint = "二维码准备失败";
                return;
            }

            _qrUnikey = key;
            var create = await _api.LoginQrCreateAsync(key).ConfigureAwait(true);
            if (NcmJson.ApiCode(create) != 200)
            {
                BannerError = NcmJson.Message(create) ?? "二维码生成失败";
                QrHint = "二维码准备失败";
                return;
            }

            var img = NcmJson.QrImgBase64(create);
            if (string.IsNullOrWhiteSpace(img))
            {
                BannerError = "二维码数据为空";
                QrHint = "二维码准备失败";
                return;
            }

            QrImage = DecodeBase64Image(img);
            QrHint = "使用网易云音乐 App 扫一扫";

            if (Mode == LoginMode.Qr)
            {
                _ = PollQrAsync(key);
            }
        }
        catch (Exception ex)
        {
            BannerError = ex.Message;
            QrHint = "网络异常，请刷新重试";
        }
        finally
        {
            Busy = false;
        }
    }

    private async Task PollQrAsync(string key)
    {
        _qrPollCts = new CancellationTokenSource();
        var ct = _qrPollCts.Token;

        try
        {
            while (!ct.IsCancellationRequested)
            {
                await Task.Delay(2000, ct).ConfigureAwait(true);

                var json = await _api.LoginQrCheckAsync(key, noCookie: false, ct)
                    .ConfigureAwait(true);
                var code = NcmJson.QrCheckCode(json);
                if (code == 502)
                {
                    json = await _api.LoginQrCheckAsync(key, noCookie: true, ct)
                        .ConfigureAwait(true);
                    code = NcmJson.QrCheckCode(json);
                }

                switch (code)
                {
                    case 800:
                        QrHint = "二维码已过期";
                        QrExpired = true;
                        return;
                    case 801:
                        QrHint = "等待扫描…";
                        break;
                    case 802:
                        QrHint = "已扫描，请在手机上确认";
                        break;
                    case 803:
                        var cookie = NcmJson.ExtractCookie(json);
                        if (string.IsNullOrWhiteSpace(cookie))
                        {
                            BannerError = "登录成功但未返回 cookie";
                            return;
                        }

                        _sessions.Save(cookie, NcmJson.DisplayLabelFromLogin(json));
                        QrHint = "登录成功";
                        LoggedIn?.Invoke();
                        return;
                }
            }
        }
        catch (OperationCanceledException)
        {
            // switched mode / closed
        }
        catch (Exception ex)
        {
            BannerError = ex.Message;
            QrHint = "轮询中断，可刷新二维码";
        }
    }

    private async Task BeginCaptchaCooldownAsync()
    {
        _captchaCooldownCts?.Cancel();
        _captchaCooldownCts = new CancellationTokenSource();
        var ct = _captchaCooldownCts.Token;
        CaptchaCooldownSec = CaptchaResendIntervalSec;

        try
        {
            while (!ct.IsCancellationRequested && CaptchaCooldownSec > 0)
            {
                await Task.Delay(1000, ct).ConfigureAwait(true);
                CaptchaCooldownSec--;
            }
        }
        catch (OperationCanceledException)
        {
            // phone changed / disposed
        }
    }

    private bool ConsumeLoginSuccess(System.Text.Json.JsonElement json)
    {
        if (NcmJson.ApiCode(json) != 200)
        {
            BannerError = NcmJson.Message(json) ?? "登录失败";
            return false;
        }

        var cookie = NcmJson.ExtractCookie(json);
        if (string.IsNullOrWhiteSpace(cookie))
        {
            BannerError = "登录成功但未返回 cookie，请改用扫码登录";
            return false;
        }

        _sessions.Save(cookie, NcmJson.DisplayLabelFromLogin(json));
        return true;
    }

    private void StopQrPolling()
    {
        _qrPollCts?.Cancel();
        _qrPollCts?.Dispose();
        _qrPollCts = null;
    }

    private static BitmapImage DecodeBase64Image(string base64)
    {
        var bytes = Convert.FromBase64String(base64);
        var image = new BitmapImage();
        using var stream = new System.IO.MemoryStream(bytes);
        image.BeginInit();
        image.CacheOption = BitmapCacheOption.OnLoad;
        image.StreamSource = stream;
        image.EndInit();
        image.Freeze();
        return image;
    }
}
