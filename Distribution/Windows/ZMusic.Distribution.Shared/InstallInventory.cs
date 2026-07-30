namespace ZMusic.Distribution;

/// <summary>
/// Install / uninstall inventory contract.
/// Any new runtime path that may hold secrets must be registered here before Uninstall may delete it.
/// </summary>
public static class InstallInventory
{
    public const string AppDirectoryName = "ZMusic";
    public const string SetupTempDirectoryPrefix = "ZMusic-Setup-";

    public const string MsiPropertyInstallDir = "INSTALLDIR";
    public const string MsiPropertyDesktopShortcut = "INSTALLDESKTOPSHORTCUT";
    public const string MsiPropertyStartMenuShortcut = "INSTALLSTARTMENUSHORTCUT";

    /// <summary>User-scoped application data root created at runtime (not by MSI).</summary>
    public static string UserDataDirectory =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            AppDirectoryName);

    /// <summary>DPAPI-protected session / cookie store.</summary>
    public static string SessionFilePath =>
        Path.Combine(UserDataDirectory, "session.dat");

    public static string DefaultPerMachineInstallDirectory =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            AppDirectoryName);

    public static string DefaultPerUserInstallDirectory =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs",
            AppDirectoryName);

    /// <summary>
    /// Paths Uninstall may remove when the user opts to clear local data (default: yes).
    /// </summary>
    public static IReadOnlyList<string> UserDataPathsToClearOnUninstall { get; } =
    [
        UserDataDirectory,
    ];

    public static string CreateSetupTempDirectory()
    {
        var path = Path.Combine(
            Path.GetTempPath(),
            SetupTempDirectoryPrefix + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(path);
        return path;
    }

    public static void TryDeleteDirectory(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !Directory.Exists(path))
        {
            return;
        }

        try
        {
            Directory.Delete(path, recursive: true);
        }
        catch
        {
            // Best-effort cleanup only; never log directory contents (may include payloads).
        }
    }

    public static void ClearUserData()
    {
        foreach (var root in UserDataPathsToClearOnUninstall)
        {
            TryDeleteDirectory(root);
        }
    }

    public static void CleanupOrphanedSetupTempDirectories()
    {
        try
        {
            var tempRoot = Path.GetTempPath();
            foreach (var dir in Directory.EnumerateDirectories(tempRoot, SetupTempDirectoryPrefix + "*"))
            {
                TryDeleteDirectory(dir);
            }
        }
        catch
        {
            // ignore
        }
    }
}
