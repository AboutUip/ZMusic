using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Animation;
using ZMusic.Distribution;

namespace ZMusic.Setup.Pages;

public partial class WelcomePage : UserControl
{
    public WelcomePage()
    {
        InitializeComponent();
        VersionText.Text = $"版本 {ProductIdentity.Version}";
        Loaded += (_, _) =>
        {
            var pulse = new DoubleAnimation(0.14, 0.32, TimeSpan.FromSeconds(2.8))
            {
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
            };
            IconHalo.BeginAnimation(OpacityProperty, pulse);
        };
    }
}
