using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;
using System.Windows.Shell;
using Wpf.Ui.Controls;
using ZMusic.Navigation;
using ZMusic.ViewModels;
using ZMusic.Views.Pages;

namespace ZMusic;

public partial class MainWindow : FluentWindow
{
    private const double PlayerDockBottom = 96;

    private readonly ShellViewModel _shell;
    private readonly PlaybackViewModel _playback;
    private readonly Dictionary<AppPage, UserControl> _pages = new();
    private bool _playerDockOpen;
    private bool _nowPlayingOpen;

    public MainWindow()
    {
        _shell = new ShellViewModel();
        _playback = new PlaybackViewModel();
        DataContext = _shell;
        InitializeComponent();

        MiniPlayer.DataContext = _playback;
        NowPlaying.DataContext = _playback;
        MiniPlayer.ExpandRequested += (_, _) => OpenNowPlaying();
        NowPlaying.DismissRequested += (_, _) => CloseNowPlaying();
        PreviewKeyDown += OnPreviewKeyDown;
        Loaded += OnLoaded;
        Closed += (_, _) => _playback.Dispose();
        _shell.PropertyChanged += OnShellPropertyChanged;
        _playback.PropertyChanged += OnPlaybackPropertyChanged;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        WidenResizeBorder();
        UpdateNavStyles();
        ShowPage(_shell.CurrentPage, animate: false);
        SetPlayerDock(_playback.HasQueue, animate: false);
        AppServices.Current.Liked.Prefetch();
        await _shell.LoadUserAsync();
    }

    private void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape && _nowPlayingOpen)
        {
            CloseNowPlaying();
            e.Handled = true;
        }
    }

    private void OpenNowPlaying()
    {
        if (!_playback.HasQueue || _nowPlayingOpen)
        {
            return;
        }

        _nowPlayingOpen = true;
        MiniPlayer.SetVisible(false, animate: true);
        NowPlaying.Open(animate: true);
    }

    private void CloseNowPlaying()
    {
        if (!_nowPlayingOpen)
        {
            return;
        }

        _nowPlayingOpen = false;
        NowPlaying.Close(animate: true, completed: () =>
        {
            if (_playback.HasQueue && !_nowPlayingOpen)
            {
                MiniPlayer.SetVisible(true, animate: true);
            }
        });
    }

    private void WidenResizeBorder()
    {
        var chrome = WindowChrome.GetWindowChrome(this);
        if (chrome is null)
        {
            return;
        }

        chrome.ResizeBorderThickness = new Thickness(10);
    }

    private void OnShellPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(ShellViewModel.CurrentPage))
        {
            UpdateNavStyles();
            ShowPage(_shell.CurrentPage, animate: true);
        }
    }

    private void OnPlaybackPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(PlaybackViewModel.HasQueue))
        {
            SetPlayerDock(_playback.HasQueue, animate: true);
            if (!_playback.HasQueue && _nowPlayingOpen)
            {
                CloseNowPlaying();
            }
            else if (_playback.HasQueue && !_nowPlayingOpen)
            {
                MiniPlayer.SetVisible(true, animate: true);
            }
        }
    }

    private void SetPlayerDock(bool open, bool animate)
    {
        if (_playerDockOpen == open && animate)
        {
            return;
        }

        _playerDockOpen = open;
        var targetBottom = open ? PlayerDockBottom : 8;

        if (!animate)
        {
            PageHost.Margin = new Thickness(12, 8, 12, targetBottom);
            return;
        }

        var from = PageHost.Margin;
        var anim = new ThicknessAnimation
        {
            From = from,
            To = new Thickness(12, 8, 12, targetBottom),
            Duration = TimeSpan.FromMilliseconds(open ? 420 : 260),
            EasingFunction = new CubicEase
            {
                EasingMode = open ? EasingMode.EaseOut : EasingMode.EaseIn,
            },
        };
        PageHost.BeginAnimation(MarginProperty, anim);
    }

    private void UpdateNavStyles()
    {
        RecommendNavButton.Style = StyleFor(_shell.IsRecommend);
        PlaylistsNavButton.Style = StyleFor(_shell.IsPlaylists);
        LikedNavButton.Style = StyleFor(_shell.IsLiked);
        SettingsNavButton.Style = StyleFor(_shell.IsSettings);
    }

    private Style StyleFor(bool active) =>
        (Style)FindResource(active ? "NavButtonActive" : "NavButton");

    private void ShowPage(AppPage page, bool animate)
    {
        if (!_pages.TryGetValue(page, out var view))
        {
            view = CreatePage(page);
            _pages[page] = view;
        }

        if (!animate)
        {
            PageHost.Content = view;
            PageHost.Opacity = 1;
            return;
        }

        var fadeOut = new DoubleAnimation
        {
            To = 0,
            Duration = TimeSpan.FromMilliseconds(120),
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseIn },
        };

        fadeOut.Completed += (_, _) =>
        {
            PageHost.Content = view;
            var fadeIn = new DoubleAnimation
            {
                From = 0,
                To = 1,
                Duration = TimeSpan.FromMilliseconds(180),
                EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
            };
            PageHost.BeginAnimation(OpacityProperty, fadeIn);
        };

        PageHost.BeginAnimation(OpacityProperty, fadeOut);
    }

    private static UserControl CreatePage(AppPage page) => page switch
    {
        AppPage.Recommend => new RecommendPage(),
        AppPage.Playlists => new PlaylistsPage(),
        AppPage.Liked => new LikedPage(),
        AppPage.Settings => new SettingsPage(),
        _ => new RecommendPage(),
    };
}
