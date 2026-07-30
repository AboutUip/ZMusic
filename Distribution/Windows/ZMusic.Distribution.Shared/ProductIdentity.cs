namespace ZMusic.Distribution;

/// <summary>
/// Stable product identity shared by Setup, Uninstall, and MSI packaging.
/// Change <see cref="ProductCode"/> when shipping a major upgrade that must replace an existing install.
/// </summary>
public static class ProductIdentity
{
    public const string ProductName = "ZMusic";
    public const string Manufacturer = "ZMusic Contributors";
    public const string Version = "0.1.0";

    /// <summary>Never change once released; identifies the upgrade family.</summary>
    public const string UpgradeCode = "{B7E4C2A1-8F3D-4E91-9C06-2A5B8D7E1F40}";

    /// <summary>MSI ProductCode for this release line; used by msiexec /x.</summary>
    public const string ProductCode = "{C8F5D3B2-9E4A-5F02-AD17-3B6C9E8F2051}";

    /// <summary>Custom ARP registry key (not the MSI-managed ProductCode key).</summary>
    public const string ArpRegistryName = "ZMusic";

    public const string MainExecutableName = "ZMusic.exe";
    public const string UninstallExecutableName = "Uninstall.exe";
    public const string LicenseFileName = "LICENSE";
    public const string NoticesFileName = "ThirdPartyNotices.txt";
    public const string SourceOfferUrl = "https://github.com/AboutUip/ZMusic";
}
