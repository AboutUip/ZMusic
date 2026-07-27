using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using ZMusic.Data;
using ZMusic.Navigation;

namespace ZMusic.ViewModels;

public partial class ShellViewModel : ObservableObject
{
    private readonly SessionStore _sessions;
    private readonly NcmAuthClient _auth;

    public ShellViewModel(SessionStore? sessions = null, NcmAuthClient? auth = null)
    {
        _sessions = sessions ?? AppServices.Current.Sessions;
        _auth = auth ?? AppServices.Current.Auth;
    }

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsRecommend))]
    [NotifyPropertyChangedFor(nameof(IsPlaylists))]
    [NotifyPropertyChangedFor(nameof(IsLiked))]
    [NotifyPropertyChangedFor(nameof(IsSettings))]
    [NotifyPropertyChangedFor(nameof(PageTitle))]
    [NotifyPropertyChangedFor(nameof(ShowPageChrome))]
    private AppPage _currentPage = AppPage.Recommend;

    [ObservableProperty]
    private string _userDisplayName = "用户";

    [ObservableProperty]
    private string? _userAvatarUrl;

    public string PageTitle => CurrentPage switch
    {
        AppPage.Recommend => "推荐",
        AppPage.Playlists => "歌单",
        AppPage.Liked => "喜欢",
        AppPage.Settings => "设置",
        _ => "ZMusic",
    };

    /// <summary>
    /// Liked (and similar detail pages) own their header; hide the shell title chrome.
    /// </summary>
    public bool ShowPageChrome => CurrentPage is not AppPage.Liked;

    public bool IsRecommend => CurrentPage == AppPage.Recommend;
    public bool IsPlaylists => CurrentPage == AppPage.Playlists;
    public bool IsLiked => CurrentPage == AppPage.Liked;
    public bool IsSettings => CurrentPage == AppPage.Settings;

    [RelayCommand]
    private void Navigate(AppPage page) => CurrentPage = page;

    public async Task LoadUserAsync(CancellationToken cancellationToken = default)
    {
        var session = _sessions.Current ?? _sessions.Load();
        if (session is null || string.IsNullOrWhiteSpace(session.Cookie))
        {
            UserDisplayName = "未登录";
            UserAvatarUrl = null;
            return;
        }

        if (!string.IsNullOrWhiteSpace(session.Label))
        {
            UserDisplayName = session.Label!;
        }

        try
        {
            var status = await _auth.LoginStatusAsync(session.Cookie, cancellationToken)
                .ConfigureAwait(true);
            var nickname = NcmJson.NicknameFromStatus(status);
            if (!string.IsNullOrWhiteSpace(nickname))
            {
                UserDisplayName = nickname;
            }

            UserAvatarUrl = NcmJson.AvatarUrlFromStatus(status);
        }
        catch
        {
            // Keep local label if network fails.
            if (string.IsNullOrWhiteSpace(UserDisplayName))
            {
                UserDisplayName = "用户";
            }
        }
    }
}
