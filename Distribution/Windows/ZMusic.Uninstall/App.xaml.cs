using System.IO;
using System.Windows;
using ZMusic.Distribution;

namespace ZMusic.Uninstall;

public partial class App : Application
{
    private async void Application_Startup(object sender, StartupEventArgs e)
    {
        var silent = e.Args.Any(a =>
            string.Equals(a, "/silent", StringComparison.OrdinalIgnoreCase) ||
            string.Equals(a, "/S", StringComparison.OrdinalIgnoreCase) ||
            string.Equals(a, "-silent", StringComparison.OrdinalIgnoreCase));

        var clearData = !e.Args.Any(a =>
            string.Equals(a, "/keepdata", StringComparison.OrdinalIgnoreCase));

        if (silent)
        {
            ShutdownMode = ShutdownMode.OnExplicitShutdown;
            var code = await UninstallService.RunAsync(clearUserData: clearData, elevateIfNeeded: true);
            Shutdown(code);
            return;
        }

        var window = new MainWindow();
        MainWindow = window;
        window.Show();
    }
}

internal static class UninstallService
{
    public static async Task<int> RunAsync(bool clearUserData, bool elevateIfNeeded)
    {
        InstallInventory.CleanupOrphanedSetupTempDirectories();

        var underProgramFiles = IsUnderProgramFiles(AppContext.BaseDirectory);
        var exitCode = await MsiExec.UninstallSilentAsync(elevate: elevateIfNeeded && underProgramFiles);

        if (exitCode is not (0 or 3010) && elevateIfNeeded && !underProgramFiles)
        {
            // Retry elevated in case of machine-wide install registered for this user.
            exitCode = await MsiExec.UninstallSilentAsync(elevate: true);
        }

        if (exitCode is 0 or 3010)
        {
            if (clearUserData)
            {
                InstallInventory.ClearUserData();
            }

            InstallInventory.CleanupOrphanedSetupTempDirectories();
        }

        return exitCode is 3010 ? 0 : exitCode;
    }

    private static bool IsUnderProgramFiles(string path)
    {
        try
        {
            var full = Path.GetFullPath(path);
            var pf = Path.GetFullPath(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles));
            var pf86 = Path.GetFullPath(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86));
            return full.StartsWith(pf, StringComparison.OrdinalIgnoreCase)
                   || full.StartsWith(pf86, StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return false;
        }
    }
}
