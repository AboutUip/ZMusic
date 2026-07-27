using System.IO;
using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;

namespace ZMusic.Views.Controls;

public partial class UserAccountCard : UserControl
{
    private static readonly HttpClient Http = CreateHttp();
    private CancellationTokenSource? _avatarLoadCts;

    public static readonly DependencyProperty DisplayNameProperty =
        DependencyProperty.Register(
            nameof(DisplayName),
            typeof(string),
            typeof(UserAccountCard),
            new PropertyMetadata("未登录", OnDisplayNameChanged));

    public static readonly DependencyProperty AvatarUrlProperty =
        DependencyProperty.Register(
            nameof(AvatarUrl),
            typeof(string),
            typeof(UserAccountCard),
            new PropertyMetadata(null, OnAvatarUrlChanged));

    public UserAccountCard()
    {
        InitializeComponent();
        UpdateInitial();
    }

    public string DisplayName
    {
        get => (string)GetValue(DisplayNameProperty);
        set => SetValue(DisplayNameProperty, value);
    }

    public string? AvatarUrl
    {
        get => (string?)GetValue(AvatarUrlProperty);
        set => SetValue(AvatarUrlProperty, value);
    }

    private static void OnDisplayNameChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is UserAccountCard card)
        {
            card.UpdateInitial();
        }
    }

    private static void OnAvatarUrlChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is UserAccountCard card)
        {
            _ = card.LoadAvatarAsync(e.NewValue as string);
        }
    }

    private void UpdateInitial()
    {
        var name = string.IsNullOrWhiteSpace(DisplayName) ? "用户" : DisplayName.Trim();
        NameText.Text = name;
        InitialText.Text = name[..1].ToUpperInvariant();
    }

    private async Task LoadAvatarAsync(string? url)
    {
        _avatarLoadCts?.Cancel();
        _avatarLoadCts?.Dispose();
        _avatarLoadCts = new CancellationTokenSource();
        var ct = _avatarLoadCts.Token;

        if (string.IsNullOrWhiteSpace(url))
        {
            ShowFallback();
            return;
        }

        // NetEase CDN often serves http; prefer https when possible.
        var normalized = url.StartsWith("http://", StringComparison.OrdinalIgnoreCase)
            ? "https://" + url["http://".Length..]
            : url;

        try
        {
            using var response = await Http.GetAsync(normalized, ct).ConfigureAwait(true);
            response.EnsureSuccessStatusCode();
            var bytes = await response.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(true);
            if (ct.IsCancellationRequested)
            {
                return;
            }

            using var stream = new MemoryStream(bytes);
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.CreateOptions = BitmapCreateOptions.IgnoreColorProfile;
            image.DecodePixelWidth = 96;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();

            AvatarBrush.ImageSource = image;
            AvatarEllipse.Visibility = Visibility.Visible;
            InitialText.Visibility = Visibility.Collapsed;
        }
        catch (OperationCanceledException)
        {
            // newer request won
        }
        catch
        {
            ShowFallback();
        }
    }

    private void ShowFallback()
    {
        AvatarBrush.ImageSource = null;
        AvatarEllipse.Visibility = Visibility.Collapsed;
        InitialText.Visibility = Visibility.Visible;
    }

    private static HttpClient CreateHttp()
    {
        var http = new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
        http.DefaultRequestHeaders.TryAddWithoutValidation(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        http.DefaultRequestHeaders.TryAddWithoutValidation("Referer", "https://music.163.com/");
        return http;
    }
}
