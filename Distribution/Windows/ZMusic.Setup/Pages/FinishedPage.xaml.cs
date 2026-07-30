using System.Windows;
using System.Windows.Controls;

namespace ZMusic.Setup.Pages;

public partial class FinishedPage : UserControl
{
    private readonly WizardState _state;

    public FinishedPage(WizardState state)
    {
        _state = state;
        InitializeComponent();
        TitleText.Text = state.InstallSucceeded ? "安装结束" : "安装未完成";
        MessageText.Text = state.StatusMessage;
        Glyph.Text = state.InstallSucceeded ? "✓" : "!";
        Glyph.Opacity = state.InstallSucceeded ? 1 : 0.7;
        LaunchCheck.IsChecked = state.LaunchWhenFinished;
        LaunchCheck.Visibility = state.InstallSucceeded ? Visibility.Visible : Visibility.Collapsed;
    }

    private void Launch_Changed(object sender, RoutedEventArgs e)
    {
        _state.LaunchWhenFinished = LaunchCheck.IsChecked == true;
    }
}
