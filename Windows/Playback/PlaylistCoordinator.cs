using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using ZMusic.Data;

namespace ZMusic.Playback;

/// <summary>
/// Queue + MediaPlayer coordinator (Windows port of Android PlaylistCoordinator, simplified).
/// </summary>
public sealed class PlaylistCoordinator : IDisposable
{
    private readonly SessionStore _sessions;
    private readonly NcmUserClient _user;
    private readonly LyricRepository _lyrics;
    private readonly MediaPlayer _player = new();
    private readonly DispatcherTimer _ticker;
    private readonly Dispatcher _dispatcher;
    private readonly List<int> _shuffleHistory = new();
    private readonly Random _random = new();

    private CancellationTokenSource? _loadCts;
    private List<QueueTrack> _queue = new();
    private int _index = -1;
    private PlaybackMode _mode = PlaybackMode.Order;
    private int? _preparedShuffleNext;
    private bool _suppressEnded;
    private bool _disposed;
    private IReadOnlyList<LrcLine> _lyricLines = Array.Empty<LrcLine>();

    public event Action? Changed;

    public PlaylistCoordinator(SessionStore sessions, NcmUserClient user, LyricRepository lyrics)
    {
        _sessions = sessions;
        _user = user;
        _lyrics = lyrics;
        _dispatcher = Application.Current?.Dispatcher ?? Dispatcher.CurrentDispatcher;

        _player.MediaOpened += OnMediaOpened;
        _player.MediaEnded += OnMediaEnded;
        _player.MediaFailed += OnMediaFailed;

        _ticker = new DispatcherTimer(DispatcherPriority.Background, _dispatcher)
        {
            Interval = TimeSpan.FromMilliseconds(250),
        };
        _ticker.Tick += (_, _) => PublishProgress();
    }

    public bool HasQueue { get; private set; }
    public bool IsPlaying { get; private set; }
    public bool LoadPending { get; private set; }
    public string? Notice { get; private set; }
    public PlaybackMode Mode => _mode;
    public QueueTrack? CurrentTrack =>
        _index >= 0 && _index < _queue.Count ? _queue[_index] : null;
    public long PositionMs { get; private set; }
    public long DurationMs { get; private set; }
    public long? SourcePlaylistId { get; private set; }
    public string? SourcePlaylistTitle { get; private set; }
    public IReadOnlyList<LrcLine> LyricLines => _lyricLines;
    public bool HasLyrics => _lyricLines.Count > 0;

    public void PlayQueue(
        IReadOnlyList<QueueTrack> tracks,
        int startIndex,
        long? sourcePlaylistId = null,
        string? sourcePlaylistTitle = null)
    {
        if (tracks.Count == 0)
        {
            return;
        }

        RunOnUi(() =>
        {
            CancelLoad();
            _shuffleHistory.Clear();
            _preparedShuffleNext = null;
            _queue = tracks.ToList();
            _index = Math.Clamp(startIndex, 0, _queue.Count - 1);
            SourcePlaylistId = sourcePlaylistId;
            SourcePlaylistTitle = sourcePlaylistTitle;
            HasQueue = true;
            Notice = null;
            LoadPending = true;
            PositionMs = 0;
            DurationMs = _queue[_index].DurationMs;
            _lyricLines = Array.Empty<LrcLine>();
            RaiseChanged();
            _ = LoadAndPlayAsync(_index, recordShuffleHistory: false);
        });
    }

    public void TogglePlayPause()
    {
        RunOnUi(() =>
        {
            if (!HasQueue || _index < 0)
            {
                return;
            }

            if (IsPlaying)
            {
                _player.Pause();
                IsPlaying = false;
                _ticker.Stop();
                RaiseChanged();
                return;
            }

            if (_player.Source is null)
            {
                _ = LoadAndPlayAsync(_index, recordShuffleHistory: false, resumeAtMs: PositionMs);
                return;
            }

            _player.Play();
            IsPlaying = true;
            _ticker.Start();
            RaiseChanged();
        });
    }

    public void CyclePlaybackMode()
    {
        RunOnUi(() =>
        {
            _mode = _mode switch
            {
                PlaybackMode.Order => PlaybackMode.RepeatOne,
                PlaybackMode.RepeatOne => PlaybackMode.Shuffle,
                _ => PlaybackMode.Order,
            };
            _preparedShuffleNext = null;
            if (_mode != PlaybackMode.Shuffle)
            {
                _shuffleHistory.Clear();
            }

            RaiseChanged();
        });
    }

    public void SeekTo(long ms)
    {
        RunOnUi(() =>
        {
            if (_player.Source is null || !_player.NaturalDuration.HasTimeSpan)
            {
                return;
            }

            var clamped = TimeSpan.FromMilliseconds(
                Math.Clamp(ms, 0, _player.NaturalDuration.TimeSpan.TotalMilliseconds));
            _player.Position = clamped;
            PositionMs = (long)clamped.TotalMilliseconds;
            RaiseChanged();
        });
    }

    public void SkipNext()
    {
        RunOnUi(() =>
        {
            if (!HasQueue || _index < 0)
            {
                return;
            }

            var next = NextIndex(_index);
            if (next is null)
            {
                return;
            }

            _ = LoadAndPlayAsync(next.Value, recordShuffleHistory: true);
        });
    }

    public void SkipPrevious()
    {
        RunOnUi(() =>
        {
            if (!HasQueue || _index < 0)
            {
                return;
            }

            // Restart current track when past the first few seconds.
            if (PositionMs > 3000)
            {
                SeekTo(0);
                if (!IsPlaying && _player.Source is not null)
                {
                    _player.Play();
                    IsPlaying = true;
                    _ticker.Start();
                    RaiseChanged();
                }

                return;
            }

            if (_mode == PlaybackMode.RepeatOne)
            {
                SeekTo(0);
                return;
            }

            if (_mode == PlaybackMode.Shuffle)
            {
                if (_shuffleHistory.Count > 0)
                {
                    var prev = _shuffleHistory[^1];
                    _shuffleHistory.RemoveAt(_shuffleHistory.Count - 1);
                    _ = LoadAndPlayAsync(prev, recordShuffleHistory: false);
                    return;
                }

                // No history yet — pick another track like shuffle next.
                var other = PickShuffle(_index);
                _ = LoadAndPlayAsync(other, recordShuffleHistory: false);
                return;
            }

            var prevIndex = _index > 0 ? _index - 1 : _queue.Count - 1;
            _ = LoadAndPlayAsync(prevIndex, recordShuffleHistory: false);
        });
    }

    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }

        _disposed = true;
        CancelLoad();
        _ticker.Stop();
        _player.MediaOpened -= OnMediaOpened;
        _player.MediaEnded -= OnMediaEnded;
        _player.MediaFailed -= OnMediaFailed;
        _player.Stop();
        _player.Close();
    }

    private async Task LoadAndPlayAsync(int index, bool recordShuffleHistory, long? resumeAtMs = null)
    {
        if (index < 0 || index >= _queue.Count)
        {
            return;
        }

        if (recordShuffleHistory && _index >= 0 && _index != index)
        {
            _shuffleHistory.Add(_index);
            if (_shuffleHistory.Count > 64)
            {
                _shuffleHistory.RemoveAt(0);
            }
        }

        CancelLoad();
        var cts = new CancellationTokenSource();
        _loadCts = cts;

        _index = index;
        var track = _queue[index];
        LoadPending = true;
        IsPlaying = false;
        PositionMs = resumeAtMs ?? 0;
        DurationMs = track.DurationMs > 0 ? track.DurationMs : DurationMs;
        Notice = null;

        var peeked = _lyrics.PeekMemory(track.Id);
        _lyricLines = peeked ?? Array.Empty<LrcLine>();
        RaiseChanged();

        var cookie = _sessions.Current?.Cookie ?? _sessions.Load()?.Cookie;
        if (string.IsNullOrWhiteSpace(cookie))
        {
            LoadPending = false;
            Notice = "未登录，无法播放";
            RaiseChanged();
            return;
        }

        _ = LoadLyricsAsync(track.Id, cookie, cts.Token);
        PrefetchNeighborLyrics(cookie);

        string? url;
        try
        {
            url = await ResolvePlayUrlAsync(track.Id, cookie, cts.Token).ConfigureAwait(true);
        }
        catch (OperationCanceledException)
        {
            return;
        }
        catch (Exception ex)
        {
            if (cts.IsCancellationRequested)
            {
                return;
            }

            LoadPending = false;
            Notice = $"无法获取播放地址：{ex.Message}";
            RaiseChanged();
            return;
        }

        if (cts.IsCancellationRequested)
        {
            return;
        }

        if (string.IsNullOrWhiteSpace(url))
        {
            LoadPending = false;
            Notice = "该歌曲暂无版权或无法播放";
            RaiseChanged();
            // Try next automatically for list modes.
            if (_mode != PlaybackMode.RepeatOne)
            {
                var next = NextIndex(index);
                if (next is not null && next != index)
                {
                    _ = LoadAndPlayAsync(next.Value, recordShuffleHistory: true);
                }
            }

            return;
        }

        try
        {
            await _dispatcher.InvokeAsync(() =>
            {
                if (cts.IsCancellationRequested)
                {
                    return;
                }

                _suppressEnded = true;
                _player.Stop();
                _player.Open(new Uri(url, UriKind.Absolute));
                if (resumeAtMs is > 0)
                {
                    void SeekOnce(object? s, EventArgs e)
                    {
                        _player.MediaOpened -= SeekOnce;
                        _player.Position = TimeSpan.FromMilliseconds(resumeAtMs.Value);
                    }

                    _player.MediaOpened += SeekOnce;
                }

                _player.Play();
                IsPlaying = true;
                LoadPending = false;
                _ticker.Start();
                RaiseChanged();
                _ = _dispatcher.BeginInvoke(
                    () => _suppressEnded = false,
                    DispatcherPriority.Background);
            });
        }
        catch (Exception ex)
        {
            LoadPending = false;
            IsPlaying = false;
            Notice = $"播放失败：{ex.Message}";
            RaiseChanged();
        }
    }

    private async Task LoadLyricsAsync(long trackId, string cookie, CancellationToken ct)
    {
        try
        {
            var lines = await _lyrics.LoadBestEffortAsync(trackId, cookie, ct).ConfigureAwait(true);
            if (ct.IsCancellationRequested)
            {
                return;
            }

            // Only apply if still on the same track.
            if (CurrentTrack?.Id != trackId)
            {
                return;
            }

            _lyricLines = lines;
            RaiseChanged();
        }
        catch (OperationCanceledException)
        {
        }
        catch
        {
            if (!ct.IsCancellationRequested && CurrentTrack?.Id == trackId)
            {
                _lyricLines = Array.Empty<LrcLine>();
                RaiseChanged();
            }
        }
    }

    private void PrefetchNeighborLyrics(string cookie)
    {
        if (_queue.Count == 0 || _index < 0)
        {
            return;
        }

        var prev = _index > 0 ? _index - 1 : _queue.Count - 1;
        var next = _index + 1 < _queue.Count ? _index + 1 : 0;
        _lyrics.Prefetch(_queue[prev].Id, cookie);
        _lyrics.Prefetch(_queue[next].Id, cookie);
    }

    private async Task<string?> ResolvePlayUrlAsync(long trackId, string cookie, CancellationToken ct)
    {
        var primary = await _user.SongUrlAsync(trackId, cookie, ct: ct).ConfigureAwait(false);
        var url = NcmPlaybackParse.SongUrlForId(primary, trackId);
        if (!string.IsNullOrWhiteSpace(url))
        {
            return url;
        }

        var v1 = await _user.SongUrlV1Async(trackId, cookie, ct: ct).ConfigureAwait(false);
        return NcmPlaybackParse.SongUrlForId(v1, trackId);
    }

    private int? NextIndex(int from)
    {
        if (_queue.Count == 0)
        {
            return null;
        }

        return _mode switch
        {
            PlaybackMode.RepeatOne => from,
            PlaybackMode.Shuffle => PickShuffle(from),
            _ => from + 1 < _queue.Count ? from + 1 : 0,
        };
    }

    private int PickShuffle(int current)
    {
        if (_queue.Count <= 1)
        {
            return current;
        }

        if (_preparedShuffleNext is int prepared && prepared != current && prepared >= 0 && prepared < _queue.Count)
        {
            _preparedShuffleNext = null;
            return prepared;
        }

        int next;
        do
        {
            next = _random.Next(0, _queue.Count);
        }
        while (next == current);

        return next;
    }

    private void OnMediaOpened(object? sender, EventArgs e)
    {
        if (_player.NaturalDuration.HasTimeSpan)
        {
            DurationMs = (long)_player.NaturalDuration.TimeSpan.TotalMilliseconds;
        }

        RaiseChanged();
    }

    private void OnMediaEnded(object? sender, EventArgs e)
    {
        if (_suppressEnded || !HasQueue || _index < 0)
        {
            return;
        }

        if (_mode == PlaybackMode.RepeatOne)
        {
            _player.Position = TimeSpan.Zero;
            _player.Play();
            IsPlaying = true;
            PositionMs = 0;
            RaiseChanged();
            return;
        }

        var next = NextIndex(_index);
        if (next is null)
        {
            IsPlaying = false;
            _ticker.Stop();
            RaiseChanged();
            return;
        }

        // For Order mode at end: NextIndex wraps to 0 (list loop).
        _ = LoadAndPlayAsync(next.Value, recordShuffleHistory: true);
    }

    private void OnMediaFailed(object? sender, ExceptionEventArgs e)
    {
        LoadPending = false;
        IsPlaying = false;
        _ticker.Stop();
        Notice = "媒体加载失败";
        RaiseChanged();
    }

    private void PublishProgress()
    {
        if (_player.Source is null)
        {
            return;
        }

        PositionMs = (long)_player.Position.TotalMilliseconds;
        if (_player.NaturalDuration.HasTimeSpan)
        {
            DurationMs = (long)_player.NaturalDuration.TimeSpan.TotalMilliseconds;
        }

        RaiseChanged();
    }

    private void CancelLoad()
    {
        _loadCts?.Cancel();
        _loadCts?.Dispose();
        _loadCts = null;
    }

    private void RaiseChanged() => Changed?.Invoke();

    private void RunOnUi(Action action)
    {
        if (_dispatcher.CheckAccess())
        {
            action();
        }
        else
        {
            _dispatcher.Invoke(action);
        }
    }
}
