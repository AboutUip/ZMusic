using System.Windows;
using System.Windows.Threading;
using Wpf.Ui.Appearance;
using ZMusic.Services;
using ZMusic.ViewModels;

namespace ZMusic;

public partial class App : Application
{
    private bool _enteringMain;

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Dark Acrylic needs immersive dark mode from the theme manager.
        ApplicationThemeManager.Apply(ApplicationTheme.Dark);

        // Splash closes before the next window is shown; prevent auto-exit.
        ShutdownMode = ShutdownMode.OnExplicitShutdown;

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnUnhandledException;
        TaskScheduler.UnobservedTaskException += OnUnobservedTaskException;

        var services = AppServices.CreateDefault();

        var splash = new SplashWindow();
        MainWindow = splash;
        splash.Show();
        // Animation starts on SplashWindow.Loaded for reliable first paint.

        try
        {
            await Task.WhenAll(
                AppInitializer.RunAsync(),
                splash.AnimationCompleted);
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                $"启动失败：{ex.Message}",
                "ZMusic",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            Shutdown(1);
            return;
        }

        var auth = AppInitializer.AuthResult;
        if (auth?.State == AuthBootstrapState.Authenticated)
        {
            ShowMainWindow();
        }
        else
        {
            ShowLoginWindow(services);
        }

        splash.Close();
        ShutdownMode = ShutdownMode.OnMainWindowClose;
    }

    private void ShowLoginWindow(AppServices services)
    {
        var loginVm = new LoginViewModel(services.Sessions, services.Auth);
        var loginWindow = new LoginWindow(loginVm);
        MainWindow = loginWindow;

        loginWindow.LoginSucceeded += () =>
        {
            _enteringMain = true;
            ShowMainWindow();
        };

        loginWindow.Closed += (_, _) =>
        {
            if (!_enteringMain && services.Sessions.Current is null)
            {
                Shutdown();
            }
        };

        loginWindow.Show();
        loginWindow.Activate();
    }

    private void ShowMainWindow()
    {
        AppServices.Current.Liked.Prefetch();

        var mainWindow = new MainWindow();
        MainWindow = mainWindow;
        mainWindow.Show();
        mainWindow.Activate();

        var fade = new System.Windows.Media.Animation.DoubleAnimation
        {
            From = 0,
            To = 1,
            Duration = TimeSpan.FromMilliseconds(420),
            EasingFunction = new System.Windows.Media.Animation.CubicEase
            {
                EasingMode = System.Windows.Media.Animation.EasingMode.EaseOut,
            },
        };
        mainWindow.Opacity = 0;
        mainWindow.BeginAnimation(UIElement.OpacityProperty, fade);
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        MessageBox.Show(
            $"未处理异常：{e.Exception.Message}\n\n{e.Exception}",
            "ZMusic",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        e.Handled = true;
    }

    private static void OnUnhandledException(object sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex)
        {
            MessageBox.Show(
                $"严重错误：{ex.Message}",
                "ZMusic",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    private static void OnUnobservedTaskException(object? sender, UnobservedTaskExceptionEventArgs e)
    {
        e.SetObserved();
    }
}
