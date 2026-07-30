using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using ZMusic.Distribution;

namespace ZMusic.Uninstall;

public partial class MainWindow : Window
{
    private Storyboard? _sweep;

    public MainWindow()
    {
        InitializeComponent();
        Loaded += (_, _) =>
        {
            AnimateGlow();
            PlayEnter();
        };
        ClearDataCheck.Checked += (_, _) => KeepDataHint.Visibility = Visibility.Collapsed;
        ClearDataCheck.Unchecked += (_, _) => KeepDataHint.Visibility = Visibility.Visible;
    }

    private void AnimateGlow()
    {
        var anim = new DoubleAnimation(0.10, 0.22, TimeSpan.FromSeconds(5.5))
        {
            AutoReverse = true,
            RepeatBehavior = RepeatBehavior.Forever,
            EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
        };
        GlowA.BeginAnimation(OpacityProperty, anim);
    }

    private void PlayEnter()
    {
        ContentRoot.Opacity = 0;
        ContentRoot.RenderTransform = new TranslateTransform(0, 16);
        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(420))
        {
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        var slide = new DoubleAnimation(16, 0, TimeSpan.FromMilliseconds(460))
        {
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        ContentRoot.BeginAnimation(OpacityProperty, fade);
        ContentRoot.RenderTransform.BeginAnimation(TranslateTransform.YProperty, slide);
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton == MouseButton.Left)
        {
            DragMove();
        }
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void Cancel_Click(object sender, RoutedEventArgs e) => Close();

    private async void Uninstall_Click(object sender, RoutedEventArgs e)
    {
        UninstallButton.IsEnabled = false;
        CancelButton.IsEnabled = false;
        ClearDataCheck.IsEnabled = false;
        ProgressTrack.Visibility = Visibility.Visible;
        StartSweep();
        StatusText.Text = "正在卸载…";

        var clearData = ClearDataCheck.IsChecked == true;
        var code = await UninstallService.RunAsync(clearUserData: clearData, elevateIfNeeded: true);

        StopSweep();
        ProgressTrack.Visibility = Visibility.Collapsed;

        if (code == 0)
        {
            StatusText.Text = clearData
                ? "已卸载，并已清除本地登录数据。"
                : "已卸载。本地登录数据已按你的选择保留。";
            UninstallButton.Content = "完成";
            UninstallButton.IsEnabled = true;
            UninstallButton.Click -= Uninstall_Click;
            UninstallButton.Click += (_, _) => Close();
            CancelButton.Visibility = Visibility.Collapsed;
        }
        else
        {
            StatusText.Text = $"卸载未完成（退出码 {code}）。未写入任何账号内容到日志。";
            UninstallButton.IsEnabled = true;
            CancelButton.IsEnabled = true;
            ClearDataCheck.IsEnabled = true;
        }
    }

    private void StartSweep()
    {
        StopSweep();
        var anim = new DoubleAnimation(-120, 560, TimeSpan.FromSeconds(1.5))
        {
            RepeatBehavior = RepeatBehavior.Forever,
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseInOut },
        };
        _sweep = new Storyboard();
        Storyboard.SetTarget(anim, SweepTransform);
        Storyboard.SetTargetProperty(anim, new PropertyPath(TranslateTransform.XProperty));
        _sweep.Children.Add(anim);
        _sweep.Begin();
    }

    private void StopSweep()
    {
        _sweep?.Stop();
        _sweep = null;
    }
}
