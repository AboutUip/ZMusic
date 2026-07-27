using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using ZMusic.Playback;

namespace ZMusic.ViewModels;

public partial class PlaybackViewModel : ObservableObject, IDisposable
{
    private readonly PlaybackBridge _bridge;
    private readonly PlaylistCoordinator _coord;
    private bool _disposed;

    public PlaybackViewModel(PlaybackBridge? bridge = null)
    {
        _bridge = bridge ?? AppServices.Current.Playback;
        _coord = _bridge.Coordinator;
        _coord.Changed += OnCoordinatorChanged;
        SyncFromCoordinator();
    }

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

    /// <summary>True while the user is dragging the seek slider.</summary>
    public bool IsSeekDragging { get; set; }

    [RelayCommand]
    private void TogglePlayPause() => _bridge.TogglePlayPause();

    [RelayCommand]
    private void CycleMode() => _bridge.CyclePlaybackMode();

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

        if (!IsSeekDragging)
        {
            PositionMsLocal(_coord.PositionMs);
            DurationText = FormatMs(_coord.DurationMs);
            Progress = _coord.DurationMs > 0
                ? Math.Clamp(_coord.PositionMs / (double)_coord.DurationMs, 0, 1)
                : 0;
        }
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
