using System.IO;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;
using ZMusic.Distribution;

namespace ZMusic.Setup.Pages;

public partial class LocationPage : UserControl
{
    private readonly WizardState _state;

    public LocationPage(WizardState state)
    {
        _state = state;
        InitializeComponent();
        PathBox.Text = state.InstallDirectory;
        HintText.Text = state.Scope == InstallScope.PerMachine
            ? "整机安装默认位于 Program Files；安装时可能请求管理员权限。"
            : "当前用户安装默认位于 LocalAppData\\Programs。";
    }

    public void Commit() => _state.InstallDirectory = PathBox.Text.Trim();

    private void Browse_Click(object sender, RoutedEventArgs e)
    {
        // OpenFolderDialog is available on modern WPF / Windows.
        var dialog = new OpenFolderDialog
        {
            Title = "选择安装文件夹",
            Multiselect = false,
        };

        if (!string.IsNullOrWhiteSpace(PathBox.Text) && Directory.Exists(PathBox.Text))
        {
            dialog.InitialDirectory = PathBox.Text;
        }

        if (dialog.ShowDialog() == true)
        {
            PathBox.Text = dialog.FolderName;
        }
    }
}
