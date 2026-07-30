using System.Windows;
using System.Windows.Controls;
using ZMusic.Distribution;

namespace ZMusic.Setup.Pages;

public partial class OptionsPage : UserControl
{
    private readonly WizardState _state;
    private bool _loading = true;

    public OptionsPage(WizardState state)
    {
        _state = state;
        InitializeComponent();
        PerUserRadio.IsChecked = state.Scope == InstallScope.PerUser;
        PerMachineRadio.IsChecked = state.Scope == InstallScope.PerMachine;
        DesktopCheck.IsChecked = state.DesktopShortcut;
        StartMenuCheck.IsChecked = state.StartMenuShortcut;
        _loading = false;
    }

    private void Scope_Changed(object sender, RoutedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        _state.Scope = PerMachineRadio.IsChecked == true
            ? InstallScope.PerMachine
            : InstallScope.PerUser;
        _state.ApplyScopeDefaults();
    }

    private void Shortcuts_Changed(object sender, RoutedEventArgs e)
    {
        if (_loading)
        {
            return;
        }

        _state.DesktopShortcut = DesktopCheck.IsChecked == true;
        _state.StartMenuShortcut = StartMenuCheck.IsChecked == true;
    }
}
