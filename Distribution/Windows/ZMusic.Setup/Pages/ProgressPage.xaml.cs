using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace ZMusic.Setup.Pages;

public partial class ProgressPage : UserControl
{
    private Storyboard? _loop;

    public ProgressPage()
    {
        InitializeComponent();
        Loaded += (_, _) => StartLoop();
        Unloaded += (_, _) => StopLoop();
        SizeChanged += (_, _) => RestartSweepIfNeeded();
    }

    public void SetStatus(string text) => StatusText.Text = text;

    public void SetIndeterminate(bool value)
    {
        if (value)
        {
            StartLoop();
        }
        else
        {
            StopLoop();
        }
    }

    private void RestartSweepIfNeeded()
    {
        if (_loop is not null)
        {
            StartLoop();
        }
    }

    private void StartLoop()
    {
        StopLoop();

        var spin = new DoubleAnimation(0, 360, TimeSpan.FromSeconds(1.35))
        {
            RepeatBehavior = RepeatBehavior.Forever,
        };

        var width = ActualWidth > 0 ? Math.Min(520, ActualWidth) : 520;
        var travel = Math.Max(200, width);
        var sweep = new DoubleAnimation(-140, travel, TimeSpan.FromSeconds(1.6))
        {
            RepeatBehavior = RepeatBehavior.Forever,
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseInOut },
        };

        _loop = new Storyboard();
        Storyboard.SetTarget(spin, SpinTransform);
        Storyboard.SetTargetProperty(spin, new PropertyPath(RotateTransform.AngleProperty));
        Storyboard.SetTarget(sweep, SweepTransform);
        Storyboard.SetTargetProperty(sweep, new PropertyPath(TranslateTransform.XProperty));
        _loop.Children.Add(spin);
        _loop.Children.Add(sweep);
        _loop.Begin();
    }

    private void StopLoop()
    {
        _loop?.Stop();
        _loop = null;
    }
}
