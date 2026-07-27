using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;
using ZMusic.Data;
using ZMusic.ViewModels;

namespace ZMusic.Views.Pages;

public partial class LikedPage : UserControl
{
    private readonly LikedViewModel _vm = new();

    public LikedPage()
    {
        InitializeComponent();
        DataContext = _vm;
        Unloaded += (_, _) => _vm.Detach();
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        Keyboard.ClearFocus();
        _vm.Attach();
        PlayContentEnter();
    }

    private void OnTrackClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: LikedTrackRow track })
        {
            _vm.PlayTrackCommand.Execute(track);
        }
    }

    private void PlayContentEnter()
    {
        ContentRoot.Opacity = 0;
        var fade = new DoubleAnimation
        {
            From = 0,
            To = 1,
            Duration = TimeSpan.FromMilliseconds(280),
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        ContentRoot.BeginAnimation(OpacityProperty, fade);
    }
}
