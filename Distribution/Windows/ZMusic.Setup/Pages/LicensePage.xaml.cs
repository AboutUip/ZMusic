using System.Windows;
using System.Windows.Controls;

namespace ZMusic.Setup.Pages;

public partial class LicensePage : UserControl
{
    private readonly WizardState _state;

    public LicensePage(WizardState state)
    {
        _state = state;
        InitializeComponent();
        LicenseBox.Text = EmbeddedResources.ReadText("ZMusic.Setup.LICENSE");
        var notice = EmbeddedResources.ReadText("ZMusic.Setup.NOTICE");
        var third = EmbeddedResources.ReadText("ZMusic.Setup.ThirdPartyNotices");
        NoticeBox.Text = notice.TrimEnd() + "\n\n" + third;
        AcceptCheck.IsChecked = _state.LicenseAccepted;
    }

    private void Accept_Changed(object sender, RoutedEventArgs e)
    {
        _state.LicenseAccepted = AcceptCheck.IsChecked == true;
        if (Window.GetWindow(this) is MainWindow main)
        {
            main.SyncLicenseNextEnabled();
        }
    }
}
