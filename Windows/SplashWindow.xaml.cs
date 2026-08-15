using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using Wpf.Ui.Controls;

namespace ZMusic;

public partial class SplashWindow : FluentWindow
{
    private const string LogoText = "ZMusic";
    private const double LogoFontSize = 72;
    private const double AnimationSeconds = 3.2;

    private readonly TaskCompletionSource _animationCompleted = new(
        TaskCreationOptions.RunContinuationsAsynchronously);

    public SplashWindow()
    {
        InitializeComponent();
        Loaded += (_, _) =>
        {
            if (!_animationCompleted.Task.IsCompleted)
            {
                StartLogoAnimation();
            }
        };

        // Safety net: never block startup forever if animation callbacks are skipped.
        _ = CompleteAfterTimeoutAsync();
    }

    private async Task CompleteAfterTimeoutAsync()
    {
        try
        {
            await Task.Delay(TimeSpan.FromSeconds(AnimationSeconds + 2.5)).ConfigureAwait(true);
            _animationCompleted.TrySetResult();
        }
        catch
        {
            _animationCompleted.TrySetResult();
        }
    }

    public Task AnimationCompleted => _animationCompleted.Task;

    public void StartLogoAnimation()
    {
        try
        {
            BuildAndAnimateLogo();
        }
        catch (Exception ex)
        {
            _animationCompleted.TrySetException(ex);
        }
    }

    private void BuildAndAnimateLogo()
    {
        var dpi = VisualTreeHelper.GetDpi(this).PixelsPerDip;
        var typeface = ResolveTypeface();
        var formatted = new FormattedText(
            LogoText,
            CultureInfo.InvariantCulture,
            FlowDirection.LeftToRight,
            typeface,
            LogoFontSize,
            Brushes.White,
            dpi);

        var geometry = formatted.BuildGeometry(new Point(0, 0));
        var bounds = geometry.Bounds;
        var offsetX = (LogoCanvas.Width - bounds.Width) / 2.0 - bounds.Left;
        var offsetY = (LogoCanvas.Height - bounds.Height) / 2.0 - bounds.Top;
        geometry.Transform = new TranslateTransform(offsetX, offsetY);

        var length = MeasureGeometryLength(geometry);
        if (length <= 0)
        {
            length = 1;
        }

        var strokeBrush = CreateBrandStrokeBrush();
        var fillBrush = CreateBrandFillBrush();

        var strokePath = new Path
        {
            Data = geometry,
            Stroke = strokeBrush,
            StrokeThickness = 2.2,
            StrokeStartLineCap = PenLineCap.Round,
            StrokeEndLineCap = PenLineCap.Round,
            StrokeLineJoin = PenLineJoin.Round,
            Fill = Brushes.Transparent,
            StrokeDashArray = new DoubleCollection { length, length },
            StrokeDashOffset = length,
            Opacity = 1,
        };

        var fillPath = new Path
        {
            Data = geometry,
            Fill = fillBrush,
            Stroke = null,
            Opacity = 0,
        };

        LogoCanvas.Children.Clear();
        LogoCanvas.Children.Add(fillPath);
        LogoCanvas.Children.Add(strokePath);

        var drawAnimation = new DoubleAnimation
        {
            From = length,
            To = 0,
            Duration = TimeSpan.FromSeconds(AnimationSeconds),
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseInOut },
        };

        drawAnimation.Completed += (_, _) =>
        {
            var fillFade = new DoubleAnimation
            {
                From = 0,
                To = 1,
                Duration = TimeSpan.FromMilliseconds(550),
                EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
            };

            fillFade.Completed += (_, _) => ShowTaglineThenFinish();
            fillPath.BeginAnimation(UIElement.OpacityProperty, fillFade);
        };

        strokePath.BeginAnimation(Shape.StrokeDashOffsetProperty, drawAnimation);
    }

    private void ShowTaglineThenFinish()
    {
        if (Tagline is null)
        {
            _animationCompleted.TrySetResult();
            return;
        }

        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(720))
        {
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        var rise = new DoubleAnimation(8, 0, TimeSpan.FromMilliseconds(720))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        fade.Completed += (_, _) => _animationCompleted.TrySetResult();
        Tagline.BeginAnimation(UIElement.OpacityProperty, fade);
        TaglineTranslate.BeginAnimation(TranslateTransform.YProperty, rise);
    }

    private static Typeface ResolveTypeface()
    {
        string[] preferred =
        [
            "Segoe Script",
            "Ink Free",
            "Lucida Calligraphy",
            "Segoe UI",
        ];

        foreach (var familyName in preferred)
        {
            var family = new FontFamily(familyName);
            var typeface = new Typeface(
                family,
                FontStyles.Italic,
                FontWeights.SemiBold,
                FontStretches.Normal);

            if (typeface.TryGetGlyphTypeface(out _))
            {
                return typeface;
            }
        }

        return new Typeface(
            new FontFamily("Segoe UI"),
            FontStyles.Italic,
            FontWeights.Bold,
            FontStretches.Normal);
    }

    private static Brush CreateBrandStrokeBrush()
    {
        var brush = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1),
        };
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xB8, 0xFF, 0x4A), 0));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xFF, 0xF0, 0x66), 0.45));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0x5C, 0xE1, 0xFF), 1));
        brush.Freeze();
        return brush;
    }

    private static Brush CreateBrandFillBrush()
    {
        var brush = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1),
        };
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xA8, 0xF5, 0x3D), 0));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xF5, 0xE8, 0x5A), 0.5));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0x4D, 0xD4, 0xF5), 1));
        brush.Freeze();
        return brush;
    }

    private static double MeasureGeometryLength(Geometry geometry)
    {
        var flattened = geometry.GetFlattenedPathGeometry(0.25, ToleranceType.Absolute);
        double length = 0;

        foreach (var figure in flattened.Figures)
        {
            var current = figure.StartPoint;
            foreach (var segment in figure.Segments)
            {
                switch (segment)
                {
                    case LineSegment line:
                        length += (line.Point - current).Length;
                        current = line.Point;
                        break;
                    case PolyLineSegment poly:
                        foreach (var point in poly.Points)
                        {
                            length += (point - current).Length;
                            current = point;
                        }
                        break;
                }
            }
        }

        return length;
    }
}
