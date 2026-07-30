using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using ZMusic.Playback;
using ZMusic.ViewModels;

namespace ZMusic.Views.Controls;

public partial class MiniPlayerBar : UserControl
{
    private PlaybackViewModel? _vm;
    private bool _visible;

    public event EventHandler? ExpandRequested;

    public MiniPlayerBar()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
        Loaded += (_, _) =>
        {
            Opacity = 0;
            IsHitTestVisible = false;
            ApplyModeIcons();
        };
    }

    private void OnCardClick(object sender, MouseButtonEventArgs e)
    {
        // Ignore clicks on transport buttons (and other interactive controls).
        if (IsInsideInteractiveControl(e.OriginalSource as DependencyObject))
        {
            return;
        }

        ExpandRequested?.Invoke(this, EventArgs.Empty);
        e.Handled = true;
    }

    private static bool IsInsideInteractiveControl(DependencyObject? source)
    {
        while (source is not null)
        {
            if (source is ButtonBase)
            {
                return true;
            }

            source = VisualTreeHelper.GetParent(source);
        }

        return false;
    }

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
            SetVisible(_vm.HasQueue, animate: false);
            ApplyModeIcons();
        }
    }

    private void OnVmPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(PlaybackViewModel.HasQueue))
        {
            SetVisible(_vm?.HasQueue == true, animate: true);
        }
        else if (e.PropertyName is nameof(PlaybackViewModel.PlaybackMode))
        {
            ApplyModeIcons();
        }
    }

    public void SetVisible(bool visible, bool animate)
    {
        if (_visible == visible && animate)
        {
            return;
        }

        _visible = visible;
        IsHitTestVisible = visible;

        if (!animate)
        {
            Opacity = visible ? 1 : 0;
            Card.Opacity = visible ? 1 : 0;
            SlideTransform.Y = visible ? 0 : 28;
            return;
        }

        var easeOut = new CubicEase { EasingMode = EasingMode.EaseOut };
        var easeIn = new CubicEase { EasingMode = EasingMode.EaseIn };

        if (visible)
        {
            var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(280)) { EasingFunction = easeOut };
            var slide = new DoubleAnimation(14, 0, TimeSpan.FromMilliseconds(320)) { EasingFunction = easeOut };
            var cardFade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(260))
            {
                BeginTime = TimeSpan.FromMilliseconds(20),
                EasingFunction = easeOut,
            };
            BeginAnimation(OpacityProperty, fade);
            SlideTransform.BeginAnimation(TranslateTransform.YProperty, slide);
            Card.BeginAnimation(OpacityProperty, cardFade);
        }
        else
        {
            var fade = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(180)) { EasingFunction = easeIn };
            var slide = new DoubleAnimation(0, 10, TimeSpan.FromMilliseconds(200)) { EasingFunction = easeIn };
            BeginAnimation(OpacityProperty, fade);
            SlideTransform.BeginAnimation(TranslateTransform.YProperty, slide);
            Card.BeginAnimation(OpacityProperty, fade);
        }
    }

    private void ApplyModeIcons()
    {
        var mode = _vm?.PlaybackMode ?? PlaybackMode.Order;
        ModeOrderIcon.Visibility = mode == PlaybackMode.Order ? Visibility.Visible : Visibility.Collapsed;
        ModeOneIcon.Visibility = mode == PlaybackMode.RepeatOne ? Visibility.Visible : Visibility.Collapsed;
        ModeShuffleIcon.Visibility = mode == PlaybackMode.Shuffle ? Visibility.Visible : Visibility.Collapsed;
    }
}
