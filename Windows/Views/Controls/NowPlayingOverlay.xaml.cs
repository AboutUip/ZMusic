using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using ZMusic.Playback;
using ZMusic.ViewModels;

using WpfScrollViewer = System.Windows.Controls.ScrollViewer;

namespace ZMusic.Views.Controls;

public partial class NowPlayingOverlay : UserControl
{
    private const int LyricMotionMs = 420;
    private const int TrackSwitchMs = 380;

    private PlaybackViewModel? _vm;
    private bool _isOpen;
    private bool _seeking;
    private bool _userBrowsingLyrics;
    private DateTime _resumeFollowAt = DateTime.MinValue;
    private int _lastScrolledLyric = -1;
    private long _lastTrackId = -1;
    private bool _trackSwitchPlaying;

    public event EventHandler? DismissRequested;

    public bool IsOpen => _isOpen;

    public NowPlayingOverlay()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
        IsHitTestVisible = false;
        Loaded += OnLoaded;
        SizeChanged += OnSizeChanged;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        ApplyModeIcons();
        SeekBar.AddHandler(Thumb.DragStartedEvent, new DragStartedEventHandler(OnSeekDragStarted), true);
        SeekBar.AddHandler(Thumb.DragCompletedEvent, new DragCompletedEventHandler(OnSeekDragCompleted), true);
        SeekBar.AddHandler(UIElement.PreviewMouseLeftButtonDownEvent, new MouseButtonEventHandler(OnSeekMouseDown), true);
        SeekBar.AddHandler(UIElement.PreviewMouseLeftButtonUpEvent, new MouseButtonEventHandler(OnSeekMouseUp), true);
        SeekBar.LostMouseCapture += OnSeekLostCapture;
        UpdateLyricPadding();
        UpdateSeekFill();
    }

    private void OnSizeChanged(object sender, SizeChangedEventArgs e)
    {
        UpdateSeekFill();
        UpdateLyricPadding();
        UpdateResponsiveChrome();
        if (_isOpen)
        {
            ScrollActiveLyric(force: true);
        }
    }

    private void UpdateResponsiveChrome()
    {
        var h = ActualHeight;
        var w = ActualWidth;
        if (h <= 0 || w <= 0)
        {
            return;
        }

        var padLeft = w < 980 ? 24 : 40;
        var padRight = w < 980 ? 20 : 32;
        var padTop = h < 640 ? 2 : 4;
        BodyGrid.Margin = new Thickness(padLeft, padTop, padRight, 0);
    }

    public void Open(bool animate = true)
    {
        if (_isOpen)
        {
            return;
        }

        _isOpen = true;
        Visibility = Visibility.Visible;
        IsHitTestVisible = true;
        Focus();
        _userBrowsingLyrics = false;
        _lastScrolledLyric = -1;
        _lastTrackId = _vm?.CurrentTrackId ?? -1;
        UpdateLyricPadding();

        if (!animate)
        {
            Opacity = 1;
            SetMotionRest(open: true);
            ScrollActiveLyric(force: true);
            return;
        }

        PlayEnterMotion();
        Dispatcher.BeginInvoke(
            () => ScrollActiveLyric(force: true),
            System.Windows.Threading.DispatcherPriority.Loaded);
    }

    public void Close(bool animate = true, Action? completed = null)
    {
        if (!_isOpen)
        {
            completed?.Invoke();
            return;
        }

        _isOpen = false;
        IsHitTestVisible = false;

        if (!animate)
        {
            Opacity = 0;
            SetMotionRest(open: false);
            Visibility = Visibility.Collapsed;
            completed?.Invoke();
            return;
        }

        PlayExitMotion(completed);
    }

    private void PlayEnterMotion()
    {
        var easeOut = new CubicEase { EasingMode = EasingMode.EaseOut };
        var soft = new QuadraticEase { EasingMode = EasingMode.EaseOut };

        BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(380))
        {
            EasingFunction = soft,
        });

        AnimateDouble(ChromeHost, OpacityProperty, 0, 1, 280, 40, easeOut);
        AnimateTranslate(ChromeSlide, TranslateTransform.YProperty, -16, 0, 340, 40, easeOut);

        AnimateDouble(StageHost, OpacityProperty, 0, 1, 420, 60, easeOut);
        AnimateDouble(StageScale, ScaleTransform.ScaleXProperty, 0.92, 1, 480, 60, easeOut);
        AnimateDouble(StageScale, ScaleTransform.ScaleYProperty, 0.92, 1, 480, 60, easeOut);
        AnimateTranslate(StageSlide, TranslateTransform.YProperty, 36, 0, 480, 60, easeOut);

        AnimateDouble(LyricHost, OpacityProperty, 0, 1, 420, 140, easeOut);
        AnimateTranslate(LyricSlide, TranslateTransform.XProperty, 48, 0, 480, 140, easeOut);
    }

    private void PlayExitMotion(Action? completed)
    {
        var easeIn = new CubicEase { EasingMode = EasingMode.EaseIn };

        AnimateDouble(ChromeHost, OpacityProperty, 1, 0, 180, 0, easeIn);
        AnimateTranslate(ChromeSlide, TranslateTransform.YProperty, 0, -10, 200, 0, easeIn);

        AnimateDouble(LyricHost, OpacityProperty, 1, 0, 200, 0, easeIn);
        AnimateTranslate(LyricSlide, TranslateTransform.XProperty, 0, 28, 220, 0, easeIn);

        AnimateDouble(StageHost, OpacityProperty, 1, 0, 240, 40, easeIn);
        AnimateDouble(StageScale, ScaleTransform.ScaleXProperty, 1, 0.94, 260, 40, easeIn);
        AnimateDouble(StageScale, ScaleTransform.ScaleYProperty, 1, 0.94, 260, 40, easeIn);
        AnimateTranslate(StageSlide, TranslateTransform.YProperty, 0, 24, 260, 40, easeIn);

        var fade = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(300))
        {
            BeginTime = TimeSpan.FromMilliseconds(40),
            EasingFunction = easeIn,
        };
        fade.Completed += (_, _) =>
        {
            Visibility = Visibility.Collapsed;
            SetMotionRest(open: false);
            completed?.Invoke();
        };
        BeginAnimation(OpacityProperty, fade);
    }

    private void PlayTrackSwitchMotion()
    {
        if (!_isOpen || _trackSwitchPlaying)
        {
            return;
        }

        _trackSwitchPlaying = true;
        var easeOut = new CubicEase { EasingMode = EasingMode.EaseOut };
        var easeIn = new CubicEase { EasingMode = EasingMode.EaseIn };
        var half = TrackSwitchMs / 2;

        AnimateAnimatable(CoverScale, ScaleTransform.ScaleXProperty, 1, 0.92, half, 0, easeIn);
        AnimateAnimatable(CoverScale, ScaleTransform.ScaleYProperty, 1, 0.92, half, 0, easeIn);
        AnimateAnimatable(CoverSlide, TranslateTransform.YProperty, 0, 12, half, 0, easeIn);
        AnimateElement(CoverHost, OpacityProperty, CoverHost.Opacity, 0.35, half, easeIn);

        AnimateAnimatable(MetaSlide, TranslateTransform.YProperty, 0, 10, half, 0, easeIn);
        AnimateElement(MetaHost, OpacityProperty, MetaHost.Opacity, 0.2, half, easeIn);

        AnimateElement(LyricHost, OpacityProperty, LyricHost.Opacity, 0, half - 20, easeIn);
        AnimateAnimatable(LyricSlide, TranslateTransform.XProperty, 0, 28, half, 0, easeIn);

        AnimateAnimatable(BackdropScale, ScaleTransform.ScaleXProperty, 1.04, 1.1, TrackSwitchMs, 0, easeOut);
        AnimateAnimatable(BackdropScale, ScaleTransform.ScaleYProperty, 1.04, 1.1, TrackSwitchMs, 0, easeOut);

        Dispatcher.BeginInvoke(async () =>
        {
            await Task.Delay(half);
            if (!_isOpen)
            {
                _trackSwitchPlaying = false;
                return;
            }

            _lastScrolledLyric = -1;
            LyricScroller.ScrollToVerticalOffset(0);

            AnimateAnimatable(CoverScale, ScaleTransform.ScaleXProperty, 0.92, 1, TrackSwitchMs, 0, easeOut);
            AnimateAnimatable(CoverScale, ScaleTransform.ScaleYProperty, 0.92, 1, TrackSwitchMs, 0, easeOut);
            AnimateAnimatable(CoverSlide, TranslateTransform.YProperty, 12, 0, TrackSwitchMs, 0, easeOut);
            AnimateElement(CoverHost, OpacityProperty, 0.35, 1, TrackSwitchMs, easeOut);

            AnimateAnimatable(MetaSlide, TranslateTransform.YProperty, 14, 0, TrackSwitchMs, 0, easeOut);
            AnimateElement(MetaHost, OpacityProperty, 0.2, 1, TrackSwitchMs, easeOut);

            AnimateAnimatable(LyricSlide, TranslateTransform.XProperty, 36, 0, TrackSwitchMs, 0, easeOut);
            AnimateElement(LyricHost, OpacityProperty, 0, 1, TrackSwitchMs, easeOut);

            AnimateAnimatable(BackdropScale, ScaleTransform.ScaleXProperty, 1.1, 1.04, TrackSwitchMs + 80, 0, easeOut);
            AnimateAnimatable(BackdropScale, ScaleTransform.ScaleYProperty, 1.1, 1.04, TrackSwitchMs + 80, 0, easeOut);

            await Task.Delay(TrackSwitchMs + 40);
            _trackSwitchPlaying = false;
            ScrollActiveLyric(force: true);
        }, System.Windows.Threading.DispatcherPriority.Background);
    }

    private void SetMotionRest(bool open)
    {
        ChromeHost.Opacity = open ? 1 : 0;
        ChromeSlide.Y = open ? 0 : -12;
        StageHost.Opacity = open ? 1 : 0;
        StageScale.ScaleX = open ? 1 : 0.94;
        StageScale.ScaleY = open ? 1 : 0.94;
        StageSlide.Y = open ? 0 : 28;
        LyricHost.Opacity = open ? 1 : 0;
        LyricSlide.X = open ? 0 : 36;
        CoverHost.Opacity = 1;
        CoverScale.ScaleX = 1;
        CoverScale.ScaleY = 1;
        CoverSlide.Y = 0;
        MetaHost.Opacity = 1;
        MetaSlide.Y = 0;
    }

    private static void AnimateDouble(
        UIElement target,
        DependencyProperty property,
        double from,
        double to,
        int durationMs,
        int delayMs,
        IEasingFunction ease)
    {
        target.BeginAnimation(property, new DoubleAnimation(from, to, TimeSpan.FromMilliseconds(durationMs))
        {
            BeginTime = TimeSpan.FromMilliseconds(delayMs),
            EasingFunction = ease,
        });
    }

    private static void AnimateDouble(
        Animatable target,
        DependencyProperty property,
        double from,
        double to,
        int durationMs,
        int delayMs,
        IEasingFunction ease)
    {
        target.BeginAnimation(property, new DoubleAnimation(from, to, TimeSpan.FromMilliseconds(durationMs))
        {
            BeginTime = TimeSpan.FromMilliseconds(delayMs),
            EasingFunction = ease,
        });
    }

    private static void AnimateTranslate(
        TranslateTransform target,
        DependencyProperty property,
        double from,
        double to,
        int durationMs,
        int delayMs,
        IEasingFunction ease) =>
        AnimateDouble(target, property, from, to, durationMs, delayMs, ease);

    private static void AnimateAnimatable(
        Animatable target,
        DependencyProperty property,
        double from,
        double to,
        int durationMs,
        int delayMs,
        IEasingFunction ease) =>
        AnimateDouble(target, property, from, to, durationMs, delayMs, ease);

    private void OnDataContextChanged(object sender, DependencyPropertyChangedEventArgs e)
    {
        if (_vm is not null)
        {
            _vm.PropertyChanged -= OnVmPropertyChanged;
        }

        _vm = e.NewValue as PlaybackViewModel;
        if (_vm is not null)
        {
            _vm.PropertyChanged += OnVmPropertyChanged;
            _lastTrackId = _vm.CurrentTrackId;
            ApplyModeIcons();
            ScrollActiveLyric(force: true);
        }
    }

    private void OnVmPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(PlaybackViewModel.PlaybackMode))
        {
            ApplyModeIcons();
        }
        else if (e.PropertyName is nameof(PlaybackViewModel.Progress) && !_seeking)
        {
            UpdateSeekFill();
        }
        else if (e.PropertyName is nameof(PlaybackViewModel.CurrentTrackId))
        {
            var id = _vm?.CurrentTrackId ?? -1;
            if (_isOpen && id != _lastTrackId && id > 0)
            {
                _lastTrackId = id;
                PlayTrackSwitchMotion();
            }
            else
            {
                _lastTrackId = id;
            }
        }
        else if (e.PropertyName is nameof(PlaybackViewModel.ActiveLyricIndex) or nameof(PlaybackViewModel.HasLyrics))
        {
            if (!_trackSwitchPlaying)
            {
                ScrollActiveLyric(force: false);
            }
        }
    }

    private void OnDismissClick(object sender, RoutedEventArgs e) =>
        DismissRequested?.Invoke(this, EventArgs.Empty);

    private void OnSeekDragStarted(object sender, DragStartedEventArgs e) => BeginSeek();

    private void OnSeekDragCompleted(object sender, DragCompletedEventArgs e) => EndSeek();

    private void OnSeekMouseDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton == MouseButton.Left)
        {
            BeginSeek();
        }
    }

    private void OnSeekMouseUp(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton == MouseButton.Left)
        {
            EndSeek();
        }
    }

    private void OnSeekLostCapture(object sender, MouseEventArgs e) => EndSeek();

    private void OnSeekValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        UpdateSeekFill();
        if (_seeking && _vm is not null && Mouse.LeftButton == MouseButtonState.Pressed)
        {
            _vm.PreviewProgress(SeekBar.Value);
        }
    }

    private void BeginSeek()
    {
        if (_seeking)
        {
            return;
        }

        _seeking = true;
        if (_vm is not null)
        {
            _vm.IsSeekDragging = true;
        }
    }

    private void EndSeek()
    {
        if (!_seeking || _vm is null)
        {
            _seeking = false;
            return;
        }

        _vm.SeekFromProgress(SeekBar.Value);
        _vm.IsSeekDragging = false;
        _seeking = false;
        UpdateSeekFill();
        _userBrowsingLyrics = false;
        ScrollActiveLyric(force: true);
    }

    private void UpdateSeekFill()
    {
        if (SeekBar.Template?.FindName("TrackFill", SeekBar) is not Border fill)
        {
            return;
        }

        var trackWidth = SeekBar.ActualWidth;
        if (trackWidth <= 0)
        {
            return;
        }

        fill.Width = Math.Clamp(SeekBar.Value, 0, 1) * trackWidth;
    }

    private void ApplyModeIcons()
    {
        var mode = _vm?.PlaybackMode ?? PlaybackMode.Order;
        ModeOrderIcon.Visibility = mode == PlaybackMode.Order ? Visibility.Visible : Visibility.Collapsed;
        ModeOneIcon.Visibility = mode == PlaybackMode.RepeatOne ? Visibility.Visible : Visibility.Collapsed;
        ModeShuffleIcon.Visibility = mode == PlaybackMode.Shuffle ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnLyricMouseWheel(object sender, MouseWheelEventArgs e)
    {
        _userBrowsingLyrics = true;
        _resumeFollowAt = DateTime.UtcNow.AddSeconds(3.5);
    }

    private void OnLyricLineLoaded(object sender, RoutedEventArgs e)
    {
        if (sender is Button button)
        {
            EnsureMutableLyricTransform(button);
            ApplyLyricLineVisual(button, animate: false);
        }
    }

    private static void EnsureMutableLyricTransform(Button button)
    {
        if (button.RenderTransform is TransformGroup existing &&
            !existing.IsFrozen &&
            existing.Children.Count >= 2 &&
            existing.Children[0] is ScaleTransform &&
            existing.Children[1] is TranslateTransform)
        {
            return;
        }

        double sx = 1, sy = 1, ty = 0;
        if (button.RenderTransform is TransformGroup frozen && frozen.Children.Count >= 2)
        {
            if (frozen.Children[0] is ScaleTransform s)
            {
                sx = s.ScaleX;
                sy = s.ScaleY;
            }

            if (frozen.Children[1] is TranslateTransform t)
            {
                ty = t.Y;
            }
        }

        button.RenderTransform = new TransformGroup
        {
            Children =
            {
                new ScaleTransform(sx, sy),
                new TranslateTransform(0, ty),
            },
        };
    }

    private void UpdateLyricPadding()
    {
        var viewport = LyricScroller.ViewportHeight > 0
            ? LyricScroller.ViewportHeight
            : LyricScroller.ActualHeight;
        if (viewport <= 0)
        {
            return;
        }

        var pad = viewport * 0.5;
        if (Math.Abs(LyricList.Padding.Top - pad) > 0.5)
        {
            LyricList.Padding = new Thickness(0, pad, 0, pad);
        }
    }

    private void ScrollActiveLyric(bool force)
    {
        if (!_isOpen || _vm is null || !_vm.HasLyrics)
        {
            return;
        }

        if (!force && _userBrowsingLyrics)
        {
            if (DateTime.UtcNow < _resumeFollowAt)
            {
                AnimateAllLyricLineVisuals(force: false);
                return;
            }

            _userBrowsingLyrics = false;
        }

        var idx = _vm.ActiveLyricIndex;
        if (idx < 0)
        {
            idx = 0;
        }

        if (idx >= LyricList.Items.Count)
        {
            AnimateAllLyricLineVisuals(force);
            return;
        }

        var indexChanged = idx != _lastScrolledLyric;
        if (!force && !indexChanged)
        {
            return;
        }

        _lastScrolledLyric = idx;
        UpdateLyricPadding();

        Dispatcher.BeginInvoke(() =>
        {
            LyricScroller.UpdateLayout();
            LyricList.UpdateLayout();

            FrameworkElement? item =
                LyricList.ItemContainerGenerator.ContainerFromIndex(idx) as FrameworkElement;
            if (item is null)
            {
                LyricList.UpdateLayout();
                item = LyricList.ItemContainerGenerator.ContainerFromIndex(idx) as FrameworkElement;
                if (item is null)
                {
                    return;
                }
            }

            var lineRoot = FindLyricButton(item) ?? item;
            lineRoot.UpdateLayout();

            var viewport = LyricScroller.ViewportHeight;
            if (viewport <= 0)
            {
                return;
            }

            Point topLeft;
            try
            {
                topLeft = lineRoot.TransformToVisual(LyricScroller).Transform(new Point(0, 0));
            }
            catch
            {
                return;
            }

            var lineCenterInView = topLeft.Y + lineRoot.ActualHeight * 0.5;
            var delta = lineCenterInView - viewport * 0.5;
            var desired = LyricScroller.VerticalOffset + delta;
            var maxOffset = Math.Max(0, LyricScroller.ExtentHeight - viewport);
            desired = Math.Clamp(desired, 0, maxOffset);

            var from = LyricScroller.VerticalOffset;
            if (Math.Abs(from - desired) < 0.5 && !indexChanged && force)
            {
                AnimateAllLyricLineVisuals(force: true);
                return;
            }

            var duration = force && !indexChanged ? 1 : LyricMotionMs;
            ScrollViewerBehavior.SetVerticalOffset(LyricScroller, from);
            var anim = new DoubleAnimation(from, desired, TimeSpan.FromMilliseconds(duration))
            {
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseInOut },
                FillBehavior = FillBehavior.Stop,
            };
            anim.Completed += (_, _) =>
            {
                LyricScroller.BeginAnimation(ScrollViewerBehavior.VerticalOffsetProperty, null);
                LyricScroller.ScrollToVerticalOffset(desired);
                ScrollViewerBehavior.SetVerticalOffset(LyricScroller, desired);
            };
            LyricScroller.BeginAnimation(ScrollViewerBehavior.VerticalOffsetProperty, anim);

            AnimateAllLyricLineVisuals(force: force && !indexChanged);
        }, System.Windows.Threading.DispatcherPriority.Loaded);
    }

    private void AnimateAllLyricLineVisuals(bool force)
    {
        var count = LyricList.Items.Count;
        for (var i = 0; i < count; i++)
        {
            if (LyricList.ItemContainerGenerator.ContainerFromIndex(i) is not FrameworkElement container)
            {
                continue;
            }

            if (FindLyricButton(container) is Button button)
            {
                ApplyLyricLineVisual(button, animate: !force);
            }
        }
    }

    private void ApplyLyricLineVisual(Button button, bool animate)
    {
        if (button.DataContext is not LyricLineItem line || _vm is null)
        {
            return;
        }

        EnsureMutableLyricTransform(button);

        var idx = _vm.LyricLines.IndexOf(line);
        var active = _vm.ActiveLyricIndex;
        double opacity;
        double scale;
        double offsetY;
        double fontSize;

        if (idx == active)
        {
            opacity = 1.0;
            scale = 1.08;
            offsetY = 0;
            fontSize = 26;
        }
        else if (idx >= 0 && idx < active)
        {
            opacity = 0.40;
            scale = 1.0;
            offsetY = -2;
            fontSize = 20;
        }
        else
        {
            opacity = 0.28;
            scale = 0.98;
            offsetY = 4;
            fontSize = 20;
        }

        if (button.Content is not TextBlock text)
        {
            return;
        }

        if (button.RenderTransform is not TransformGroup group ||
            group.Children.Count < 2 ||
            group.Children[0] is not ScaleTransform scaleT ||
            group.Children[1] is not TranslateTransform slideT)
        {
            return;
        }

        var ease = new CubicEase { EasingMode = EasingMode.EaseInOut };
        var ms = animate ? LyricMotionMs : 1;

        AnimateElement(button, UIElement.OpacityProperty, button.Opacity, opacity, ms, ease);
        AnimateAnimatable(scaleT, ScaleTransform.ScaleXProperty, scaleT.ScaleX, scale, ms, 0, ease);
        AnimateAnimatable(scaleT, ScaleTransform.ScaleYProperty, scaleT.ScaleY, scale, ms, 0, ease);
        AnimateAnimatable(slideT, TranslateTransform.YProperty, slideT.Y, offsetY, ms, 0, ease);
        text.BeginAnimation(TextBlock.FontSizeProperty, new DoubleAnimation(text.FontSize, fontSize, TimeSpan.FromMilliseconds(ms))
        {
            EasingFunction = ease,
            FillBehavior = FillBehavior.HoldEnd,
        });
        text.FontWeight = idx == active ? FontWeights.SemiBold : FontWeights.Normal;
        text.LineHeight = fontSize + 10;
    }

    private static Button? FindLyricButton(DependencyObject root)
    {
        if (root is Button b)
        {
            return b;
        }

        var count = VisualTreeHelper.GetChildrenCount(root);
        for (var i = 0; i < count; i++)
        {
            var child = VisualTreeHelper.GetChild(root, i);
            var found = FindLyricButton(child);
            if (found is not null)
            {
                return found;
            }
        }

        return null;
    }

    private static void AnimateElement(
        UIElement target,
        DependencyProperty property,
        double from,
        double to,
        int durationMs,
        IEasingFunction ease)
    {
        target.BeginAnimation(property, new DoubleAnimation(from, to, TimeSpan.FromMilliseconds(durationMs))
        {
            EasingFunction = ease,
            FillBehavior = FillBehavior.HoldEnd,
        });
    }
}

internal static class ScrollViewerBehavior
{
    public static readonly DependencyProperty VerticalOffsetProperty =
        DependencyProperty.RegisterAttached(
            "VerticalOffset",
            typeof(double),
            typeof(ScrollViewerBehavior),
            new PropertyMetadata(0.0, OnVerticalOffsetChanged));

    public static double GetVerticalOffset(DependencyObject obj) =>
        (double)obj.GetValue(VerticalOffsetProperty);

    public static void SetVerticalOffset(DependencyObject obj, double value) =>
        obj.SetValue(VerticalOffsetProperty, value);

    private static void OnVerticalOffsetChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is WpfScrollViewer sv)
        {
            sv.ScrollToVerticalOffset((double)e.NewValue);
        }
    }
}
