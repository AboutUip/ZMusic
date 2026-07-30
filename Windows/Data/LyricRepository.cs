using System.IO;
using System.Text;

namespace ZMusic.Data;

/// <summary>
/// Memory + disk lyric cache (simplified Windows port of Android LyricRepository).
/// </summary>
public sealed class LyricRepository
{
    private const int MemoryCap = 48;
    private const int DiskCap = 80;

    private readonly object _gate = new();
    private readonly Dictionary<long, IReadOnlyList<LrcLine>> _memory = new();
    private readonly LinkedList<long> _lru = new();
    private readonly string _dir;
    private readonly NcmUserClient _user;

    public LyricRepository(NcmUserClient user, string? cacheDir = null)
    {
        _user = user;
        _dir = cacheDir ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ZMusic",
            "lyrics");
        Directory.CreateDirectory(_dir);
    }

    public IReadOnlyList<LrcLine>? PeekMemory(long songId)
    {
        lock (_gate)
        {
            if (!_memory.TryGetValue(songId, out var lines))
            {
                return null;
            }

            TouchLru(songId);
            return lines;
        }
    }

    public async Task<IReadOnlyList<LrcLine>> LoadBestEffortAsync(
        long songId,
        string cookie,
        CancellationToken ct = default)
    {
        var mem = PeekMemory(songId);
        if (mem is { Count: > 0 })
        {
            return mem;
        }

        var disk = TryReadDisk(songId);
        if (disk is { Count: > 0 })
        {
            PutMemory(songId, disk);
            return disk;
        }

        try
        {
            var json = await _user.LyricAsync(songId, cookie, ct).ConfigureAwait(false);
            var raw = NcmPlaybackParse.LrcText(json);
            if (string.IsNullOrWhiteSpace(raw))
            {
                PutMemory(songId, Array.Empty<LrcLine>());
                return Array.Empty<LrcLine>();
            }

            var lines = LrcParser.Parse(raw);
            PutMemory(songId, lines);
            if (lines.Count > 0)
            {
                TryWriteDisk(songId, raw);
            }

            return lines;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            PutMemory(songId, Array.Empty<LrcLine>());
            return Array.Empty<LrcLine>();
        }
    }

    public void Prefetch(long songId, string cookie)
    {
        if (PeekMemory(songId) is not null)
        {
            return;
        }

        _ = Task.Run(async () =>
        {
            try
            {
                await LoadBestEffortAsync(songId, cookie).ConfigureAwait(false);
            }
            catch
            {
                // best-effort
            }
        });
    }

    private void PutMemory(long songId, IReadOnlyList<LrcLine> lines)
    {
        lock (_gate)
        {
            _memory[songId] = lines;
            TouchLru(songId);
            while (_lru.Count > MemoryCap)
            {
                var oldest = _lru.Last!.Value;
                _lru.RemoveLast();
                _memory.Remove(oldest);
            }
        }
    }

    private void TouchLru(long songId)
    {
        _lru.Remove(songId);
        _lru.AddFirst(songId);
    }

    private IReadOnlyList<LrcLine>? TryReadDisk(long songId)
    {
        var path = Path.Combine(_dir, $"{songId}.lrc");
        try
        {
            if (!File.Exists(path))
            {
                return null;
            }

            var raw = File.ReadAllText(path, Encoding.UTF8);
            return string.IsNullOrWhiteSpace(raw) ? null : LrcParser.Parse(raw);
        }
        catch
        {
            return null;
        }
    }

    private void TryWriteDisk(long songId, string raw)
    {
        try
        {
            var path = Path.Combine(_dir, $"{songId}.lrc");
            File.WriteAllText(path, raw, Encoding.UTF8);
            TrimDisk();
        }
        catch
        {
            // ignore disk errors
        }
    }

    private void TrimDisk()
    {
        try
        {
            var files = new DirectoryInfo(_dir).GetFiles("*.lrc")
                .OrderByDescending(f => f.LastWriteTimeUtc)
                .ToList();
            for (var i = DiskCap; i < files.Count; i++)
            {
                try
                {
                    files[i].Delete();
                }
                catch
                {
                    // ignore
                }
            }
        }
        catch
        {
            // ignore
        }
    }
}
