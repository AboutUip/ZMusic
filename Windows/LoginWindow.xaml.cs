using System.ComponentModel;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Wpf.Ui.Controls;
using ZMusic.ViewModels;

namespace ZMusic;

public partial class LoginWindow : FluentWindow
{
    private readonly LoginViewModel _viewModel;
    private Storyboard? _qrBreath;

    public LoginWindow(LoginViewModel viewModel)
    {
        _viewModel = viewModel;
        DataContext = _viewModel;
        InitializeComponent();

        Loaded += OnLoaded;
        Closed += OnClosed;
        _viewModel.PropertyChanged += OnViewModelPropertyChanged;
        _viewModel.LoggedIn += OnLoggedIn;
    }

    public event Action? LoginSucceeded;

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        WidenResizeBorder();
        PlayEnterAnimation();
        StartQrBreath();
        UpdateModeChips();
        await _viewModel.InitializeAsync();
    }

    private static void WidenResizeBorder(Window window)
    {
        var chrome = System.Windows.Shell.WindowChrome.GetWindowChrome(window);
        if (chrome is null)
        {
            return;
        }

        chrome.ResizeBorderThickness = new Thickness(10);
    }

    private void WidenResizeBorder() => WidenResizeBorder(this);

    private void OnClosed(object? sender, EventArgs e)
    {
        _viewModel.PropertyChanged -= OnViewModelPropertyChanged;
        _viewModel.LoggedIn -= OnLoggedIn;
        _viewModel.DisposeRuntime();
        _qrBreath?.Stop();
    }

    private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(LoginViewModel.Mode))
        {
            UpdateModeChips();
            PlayPanelSwap();
        }
    }

    private void OnLoggedIn()
    {
        Dispatcher.InvokeAsync(() =>
        {
            LoginSucceeded?.Invoke();
            Close();
        });
    }

    private void UpdateModeChips()
    {
        if (_viewModel.Mode == LoginMode.Qr)
        {
            QrModeButton.Style = (Style)FindResource("ModeChipActive");
            PhoneModeButton.Style = (Style)FindResource("ModeChip");
            StartQrBreath();
        }
        else
        {
            QrModeButton.Style = (Style)FindResource("ModeChip");
            PhoneModeButton.Style = (Style)FindResource("ModeChipActive");
            _qrBreath?.Stop();
            QrGlow.Opacity = 1;
        }
    }

    private void PlayEnterAnimation()
    {
        var fade = new DoubleAnimation
        {
            From = 0,
            To = 1,
            Duration = TimeSpan.FromMilliseconds(520),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        var slide = new DoubleAnimation
        {
            From = 16,
            To = 0,
            Duration = TimeSpan.FromMilliseconds(560),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        RootContent.BeginAnimation(OpacityProperty, fade);
        RootContent.RenderTransform.BeginAnimation(TranslateTransform.YProperty, slide);
    }

    private void PlayPanelSwap()
    {
        var panel = _viewModel.Mode == LoginMode.Qr ? QrPanel : PhonePanel;
        panel.Opacity = 0;
        var fade = new DoubleAnimation
        {
            From = 0,
            To = 1,
            Duration = TimeSpan.FromMilliseconds(280),
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        panel.BeginAnimation(OpacityProperty, fade);
    }

    private void StartQrBreath()
    {
        _qrBreath?.Stop();
        var anim = new DoubleAnimation
        {
            From = 0.88,
            To = 1,
            Duration = TimeSpan.FromSeconds(1.6),
            AutoReverse = true,
            RepeatBehavior = RepeatBehavior.Forever,
            EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
        };
        _qrBreath = new Storyboard();
        Storyboard.SetTarget(anim, QrGlow);
        Storyboard.SetTargetProperty(anim, new PropertyPath(OpacityProperty));
        _qrBreath.Children.Add(anim);
        _qrBreath.Begin();
    }
}
