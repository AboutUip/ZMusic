using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace ZMusic.Setup;

internal static class PageMotion
{
    public static void PlayEnter(UIElement element)
    {
        if (element is null)
        {
            return;
        }

        if (element.RenderTransform is not TranslateTransform)
        {
            element.RenderTransform = new TranslateTransform();
        }

        element.Opacity = 0;
        ((TranslateTransform)element.RenderTransform).Y = 18;

        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(420))
        {
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        var slide = new DoubleAnimation(18, 0, TimeSpan.FromMilliseconds(480))
        {
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };

        element.BeginAnimation(UIElement.OpacityProperty, fade);
        element.RenderTransform.BeginAnimation(TranslateTransform.YProperty, slide);
    }
}
