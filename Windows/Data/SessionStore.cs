using System.IO;
using System.Security.Cryptography;
using System.Text.Json;

namespace ZMusic.Data;

/// <summary>
/// Persists the NCM cookie with Windows DPAPI (CurrentUser).
/// </summary>
public sealed class SessionStore
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = false,
    };

    private readonly string _filePath;
    private readonly object _gate = new();

    public SessionStore(string? filePath = null)
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ZMusic");
        Directory.CreateDirectory(dir);
        _filePath = filePath ?? Path.Combine(dir, "session.dat");
    }

    public StoredSession? Current { get; private set; }

    public StoredSession? Load()
    {
        lock (_gate)
        {
            if (!File.Exists(_filePath))
            {
                Current = null;
                return null;
            }

            try
            {
                var protectedBytes = File.ReadAllBytes(_filePath);
                var jsonBytes = ProtectedData.Unprotect(
                    protectedBytes,
                    optionalEntropy: null,
                    scope: DataProtectionScope.CurrentUser);
                var session = JsonSerializer.Deserialize<StoredSession>(jsonBytes, JsonOptions);
                if (session is null || string.IsNullOrWhiteSpace(session.Cookie))
                {
                    Current = null;
                    return null;
                }

                Current = session;
                return session;
            }
            catch
            {
                Current = null;
                return null;
            }
        }
    }

    public void Save(string cookie, string? label)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(cookie);

        var session = new StoredSession
        {
            Cookie = cookie.Trim(),
            Label = string.IsNullOrWhiteSpace(label) ? null : label.Trim(),
            SavedAt = DateTimeOffset.UtcNow,
        };

        var jsonBytes = JsonSerializer.SerializeToUtf8Bytes(session, JsonOptions);
        var protectedBytes = ProtectedData.Protect(
            jsonBytes,
            optionalEntropy: null,
            scope: DataProtectionScope.CurrentUser);

        lock (_gate)
        {
            File.WriteAllBytes(_filePath, protectedBytes);
            Current = session;
        }
    }

    public void Clear()
    {
        lock (_gate)
        {
            Current = null;
            if (File.Exists(_filePath))
            {
                File.Delete(_filePath);
            }
        }
    }
}
