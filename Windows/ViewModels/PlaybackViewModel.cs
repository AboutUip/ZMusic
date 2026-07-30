using System.Collections.ObjectModel;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using ZMusic.Data;
using ZMusic.Playback;

namespace ZMusic.ViewModels;

public partial class LyricLineItem : ObservableObject
{
    public LyricLineItem(long timeMs, string text)
    {
        TimeMs = timeMs;
        Text = text;
    }

    public long TimeMs { get; }

    public string Text { get; }

    [ObservableProperty]
    private bool _isActive;

    [ObservableProperty]
    private bool _isPast;
}

public partial class PlaybackViewModel : ObservableObject, IDisposable
{
    private readonly PlaybackBridge _bridge;
    private readonly PlaylistCoordinator _coord;
    private bool _disposed;
    private long _lyricTrackId = -1;

    public PlaybackViewModel(PlaybackBridge? bridge = null)
    {
        _bridge = bridge ?? AppServices.Current.Playback;
        _coord = _bridge.Coordinator;
        _coord.Changed += OnCoordinatorChanged;
        SyncFromCoordinator();
    }

    public ObservableCollection<LyricLineItem> LyricLines { get; } = new();

    [ObservableProperty]
    private bool _hasQueue;

    [ObservableProperty]
    private bool _isPlaying;

    [ObservableProperty]
    private bool _loadPending;

    [ObservableProperty]
    private string _title = "";

    [ObservableProperty]
    private string _artists = "";

    [ObservableProperty]
    private string? _coverUrl;

    [ObservableProperty]
    private bool _hasCover;

    [ObservableProperty]
    private string _positionText = "0:00";

    [ObservableProperty]
    private string _durationText = "0:00";

    [ObservableProperty]
    private double _progress;

    [ObservableProperty]
    private PlaybackMode _playbackMode = PlaybackMode.Order;

    [ObservableProperty]
    private string _modeLabel = "列表循环";

    [ObservableProperty]
    private string? _notice;

    [ObservableProperty]
    private bool _hasLyrics;

    [ObservableProperty]
    private int _activeLyricIndex = -1;

    [ObservableProperty]
    private string _lyricEmptyHint = "暂无歌词";

    [ObservableProperty]
    private long _currentTrackId = -1;

    /// <summary>True while the user is dragging the seek slider.</summary>
    public bool IsSeekDragging { get; set; }

    [RelayCommand]
    private void TogglePlayPause() => _bridge.TogglePlayPause();

    [RelayCommand]
    private void CycleMode() => _bridge.CyclePlaybackMode();

    [RelayCommand]
    private void SkipNext() => _bridge.SkipNext();

    [RelayCommand]
    private void SkipPrevious() => _bridge.SkipPrevious();

    [RelayCommand]
    private void SeekToLyric(LyricLineItem? line)
    {
        if (line is null)
        {
            return;
        }

        _bridge.SeekTo(line.TimeMs);
        PositionText = FormatMs(line.TimeMs);
        if (_coord.DurationMs > 0)
        {
            Progress = Math.Clamp(line.TimeMs / (double)_coord.DurationMs, 0, 1);
        }

        UpdateActiveLyric(line.TimeMs);
    }

    public void SeekFromProgress(double progress01)
    {
        if (!_coord.HasQueue || _coord.DurationMs <= 0)
        {
            return;
        }

        var ms = (long)(Math.Clamp(progress01, 0, 1) * _coord.DurationMs);
        _bridge.SeekTo(ms);
        PositionText = FormatMs(ms);
        Progress = Math.Clamp(progress01, 0, 1);
        UpdateActiveLyric(ms);
    }

    public void PreviewProgress(double progress01)
    {
        if (_coord.DurationMs <= 0)
        {
            return;
        }

        var ms = (long)(Math.Clamp(progress01, 0, 1) * _coord.DurationMs);
        PositionText = FormatMs(ms);
        Progress = Math.Clamp(progress01, 0, 1);
        UpdateActiveLyric(ms);
    }

    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }

        _disposed = true;
        _coord.Changed -= OnCoordinatorChanged;
    }

    private void OnCoordinatorChanged()
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null)
        {
            SyncFromCoordinator();
            return;
        }

        if (dispatcher.CheckAccess())
        {
            SyncFromCoordinator();
        }
        else
        {
            dispatcher.Invoke(SyncFromCoordinator);
        }
    }

    private void SyncFromCoordinator()
    {
        HasQueue = _coord.HasQueue;
        IsPlaying = _coord.IsPlaying;
        LoadPending = _coord.LoadPending;
        Notice = _coord.Notice;
        PlaybackMode = _coord.Mode;
        ModeLabel = _coord.Mode switch
        {
            PlaybackMode.RepeatOne => "单曲循环",
            PlaybackMode.Shuffle => "随机播放",
            _ => "列表循环",
        };

        var track = _coord.CurrentTrack;
        Title = track?.Name ?? "";
        Artists = track?.Artists ?? "";
        CoverUrl = track?.CoverUrl;
        HasCover = !string.IsNullOrWhiteSpace(CoverUrl);
        CurrentTrackId = track?.Id ?? -1;

        SyncLyricLines(track?.Id ?? -1, _coord.LyricLines);

        if (!IsSeekDragging)
        {
            PositionMsLocal(_coord.PositionMs);
            DurationText = FormatMs(_coord.DurationMs);
            Progress = _coord.DurationMs > 0
                ? Math.Clamp(_coord.PositionMs / (double)_coord.DurationMs, 0, 1)
                : 0;
            UpdateActiveLyric(_coord.PositionMs);
        }
    }

    private void SyncLyricLines(long trackId, IReadOnlyList<LrcLine> lines)
    {
        if (trackId == _lyricTrackId &&
            LyricLines.Count == lines.Count &&
            (lines.Count == 0 || (LyricLines.Count > 0 && LyricLines[0].TimeMs == lines[0].TimeMs)))
        {
            HasLyrics = LyricLines.Count > 0;
            LyricEmptyHint = LoadPending ? "歌词加载中…" : "暂无歌词";
            return;
        }

        _lyricTrackId = trackId;
        LyricLines.Clear();
        foreach (var line in lines)
        {
            LyricLines.Add(new LyricLineItem(line.TimeMs, line.Text));
        }

        HasLyrics = LyricLines.Count > 0;
        ActiveLyricIndex = -1;
        LyricEmptyHint = LoadPending && !HasLyrics ? "歌词加载中…" : "暂无歌词";
    }

    private void UpdateActiveLyric(long positionMs)
    {
        if (LyricLines.Count == 0)
        {
            ActiveLyricIndex = -1;
            return;
        }

        var idx = FindActiveIndex(positionMs);
        if (idx == ActiveLyricIndex)
        {
            return;
        }

        if (ActiveLyricIndex >= 0 && ActiveLyricIndex < LyricLines.Count)
        {
            LyricLines[ActiveLyricIndex].IsActive = false;
        }

        for (var i = 0; i < LyricLines.Count; i++)
        {
            LyricLines[i].IsPast = i < idx;
            LyricLines[i].IsActive = i == idx;
        }

        ActiveLyricIndex = idx;
    }

    private int FindActiveIndex(long positionMs)
    {
        var lo = 0;
        var hi = LyricLines.Count - 1;
        var best = -1;
        while (lo <= hi)
        {
            var mid = (lo + hi) / 2;
            if (LyricLines[mid].TimeMs <= positionMs)
            {
                best = mid;
                lo = mid + 1;
            }
            else
            {
                hi = mid - 1;
            }
        }

        return best;
    }

    private void PositionMsLocal(long ms) => PositionText = FormatMs(ms);

    private static string FormatMs(long ms)
    {
        if (ms <= 0)
        {
            return "0:00";
        }

        var totalSeconds = ms / 1000;
        var minutes = totalSeconds / 60;
        var seconds = totalSeconds % 60;
        return $"{minutes}:{seconds:00}";
    }
}
