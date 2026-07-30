using System.Diagnostics;
using System.Text;

namespace ZMusic.Distribution;

public enum InstallScope
{
    PerUser,
    PerMachine,
}

public sealed record SilentInstallRequest(
    string MsiPath,
    string InstallDirectory,
    InstallScope Scope,
    bool DesktopShortcut,
    bool StartMenuShortcut);

public sealed record MsiExecResult(int ExitCode, string? LogPath);

/// <summary>
/// Thin msiexec wrapper. Logs only exit codes and paths — never session/cookie content.
/// </summary>
public static class MsiExec
{
    public static async Task<MsiExecResult> InstallSilentAsync(
        SilentInstallRequest request,
        CancellationToken cancellationToken = default)
    {
        if (!File.Exists(request.MsiPath))
        {
            throw new FileNotFoundException("MSI payload missing.", request.MsiPath);
        }

        // Never end a quoted MSI property with a single '\' — it escapes the closing quote (classic 1603 cause).
        var installDir = NormalizeInstallDir(request.InstallDirectory);
        var logPath = Path.Combine(
            Path.GetTempPath(),
            $"ZMusic-Setup-{Guid.NewGuid():N}.log");

        var args = new List<string>
        {
            "/i",
            request.MsiPath,
            "/qn",
            "/norestart",
            $"/l*v",
            logPath,
            $"{InstallInventory.MsiPropertyInstallDir}={installDir}",
            $"{InstallInventory.MsiPropertyDesktopShortcut}={(request.DesktopShortcut ? "1" : "0")}",
            $"{InstallInventory.MsiPropertyStartMenuShortcut}={(request.StartMenuShortcut ? "1" : "0")}",
        };

        if (request.Scope == InstallScope.PerMachine)
        {
            args.Add("ALLUSERS=1");
            args.Add("MSIINSTALLPERUSER=");
        }
        else
        {
            // Dual-purpose package: force per-user context.
            args.Add("ALLUSERS=");
            args.Add("MSIINSTALLPERUSER=1");
        }

        var exit = await RunAsync(args, elevate: request.Scope == InstallScope.PerMachine, cancellationToken);
        if (exit is not (0 or 3010) && !File.Exists(logPath))
        {
            logPath = null;
        }

        return new MsiExecResult(exit, logPath);
    }

    public static async Task<int> UninstallSilentAsync(
        bool elevate = false,
        CancellationToken cancellationToken = default)
    {
        var args = new List<string>
        {
            "/x",
            ProductIdentity.ProductCode,
            "/qn",
            "/norestart",
        };
        return await RunAsync(args, elevate, cancellationToken);
    }

    private static async Task<int> RunAsync(
        IReadOnlyList<string> args,
        bool elevate,
        CancellationToken cancellationToken)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "msiexec.exe",
            UseShellExecute = elevate,
            CreateNoWindow = !elevate,
        };

        if (elevate)
        {
            // UseShellExecute=true does not support ArgumentList — build a safe Arguments string.
            psi.Verb = "runas";
            psi.Arguments = JoinArguments(args);
        }
        else
        {
            foreach (var a in args)
            {
                psi.ArgumentList.Add(a);
            }

            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
        }

        using var process = new Process { StartInfo = psi };
        try
        {
            if (!process.Start())
            {
                return -1;
            }
        }
        catch (System.ComponentModel.Win32Exception)
        {
            // UAC cancelled or msiexec missing.
            return 1602;
        }

        await process.WaitForExitAsync(cancellationToken);
        return process.ExitCode;
    }

    private static string NormalizeInstallDir(string path)
    {
        var full = Path.GetFullPath(path.Trim());
        return full.TrimEnd('\\', '/');
    }

    /// <summary>Shell-safe join for elevated msiexec (UseShellExecute path).</summary>
    private static string JoinArguments(IReadOnlyList<string> args)
    {
        var sb = new StringBuilder();
        foreach (var raw in args)
        {
            if (sb.Length > 0)
            {
                sb.Append(' ');
            }

            if (raw.Length == 0)
            {
                sb.Append("\"\"");
                continue;
            }

            var needsQuotes = raw.Contains(' ', StringComparison.Ordinal)
                              || raw.Contains('\t', StringComparison.Ordinal)
                              || raw.Contains('"', StringComparison.Ordinal);
            if (!needsQuotes)
            {
                sb.Append(raw);
                continue;
            }

            sb.Append('"');
            // Escape embedded quotes; avoid ending with a single backslash before the closing quote.
            var escaped = raw.Replace("\"", "\\\"", StringComparison.Ordinal);
            if (escaped.EndsWith('\\'))
            {
                escaped += '\\';
            }

            sb.Append(escaped);
            sb.Append('"');
        }

        return sb.ToString();
    }
}
