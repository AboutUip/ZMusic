using ZMusic.Distribution;

namespace ZMusic.Setup;

public enum WizardPage
{
    Welcome,
    License,
    Options,
    Location,
    Progress,
    Finished,
}

public sealed class WizardState
{
    public InstallScope Scope { get; set; } = InstallScope.PerUser;
    public bool DesktopShortcut { get; set; } = true;
    public bool StartMenuShortcut { get; set; } = true;
    public bool LicenseAccepted { get; set; }
    public string InstallDirectory { get; set; } = InstallInventory.DefaultPerUserInstallDirectory;
    public bool LaunchWhenFinished { get; set; } = true;
    public bool InstallSucceeded { get; set; }
    public int LastExitCode { get; set; }
    public string? MsiLogPath { get; set; }
    public string StatusMessage { get; set; } = string.Empty;

    public void ApplyScopeDefaults()
    {
        InstallDirectory = Scope == InstallScope.PerMachine
            ? InstallInventory.DefaultPerMachineInstallDirectory
            : InstallInventory.DefaultPerUserInstallDirectory;
    }
}
