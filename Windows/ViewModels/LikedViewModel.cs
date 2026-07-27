using System.Collections.ObjectModel;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using ZMusic.Data;
using ZMusic.Playback;

namespace ZMusic.ViewModels;

public partial class LikedViewModel : ObservableObject
{
    private readonly LikedPlaylistCache _cache;
    private readonly PlaybackBridge _playback;

    public LikedViewModel(LikedPlaylistCache? cache = null, PlaybackBridge? playback = null)
    {
        _cache = cache ?? AppServices.Current.Liked;
        _playback = playback ?? AppServices.Current.Playback;
        ApplyFromCache();
        _cache.Changed += OnCacheChanged;
        _playback.Coordinator.Changed += OnPlaybackChanged;
        SyncPlayingState();
    }

    [ObservableProperty]
    private bool _isLoading;

    [ObservableProperty]
    private bool _showSkeleton;

    [ObservableProperty]
    private string? _errorMessage;

    [ObservableProperty]
    private string _playlistName = "我喜欢的音乐";

    [ObservableProperty]
    private string? _playlistDescription = "喜欢的音乐会收集在这里";

    [ObservableProperty]
    private string? _coverUrl;

    [ObservableProperty]
    private int _trackCount;

    [ObservableProperty]
    private bool _hasCover;

    [ObservableProperty]
    private long _playlistId;

    [ObservableProperty]
    private long _playingTrackId;

    [ObservableProperty]
    private bool _isPlaybackActive;

    public ObservableCollection<LikedTrackRow> Tracks { get; } = new();

    public void Attach()
    {
        _cache.Changed -= OnCacheChanged;
        _cache.Changed += OnCacheChanged;
        _playback.Coordinator.Changed -= OnPlaybackChanged;
        _playback.Coordinator.Changed += OnPlaybackChanged;
        ApplyFromCache();
        SyncPlayingState();
        if (_cache.State is LikedCacheState.Idle or LikedCacheState.Failed || !_cache.HasData)
        {
            _cache.Prefetch();
        }
    }

    public void Detach()
    {
        _cache.Changed -= OnCacheChanged;
        _playback.Coordinator.Changed -= OnPlaybackChanged;
    }

    [RelayCommand]
    private void PlayTrack(LikedTrackRow? track)
    {
        if (track is null || Tracks.Count == 0)
        {
            return;
        }

        var index = -1;
        for (var i = 0; i < Tracks.Count; i++)
        {
            if (ReferenceEquals(Tracks[i], track) || Tracks[i].Id == track.Id)
            {
                index = i;
                break;
            }
        }

        if (index < 0)
        {
            return;
        }

        var queue = Tracks.Select(QueueTrack.From).ToList();
        _playback.PlayQueue(
            queue,
            index,
            PlaylistId > 0 ? PlaylistId : null,
            PlaylistName);
    }

    private void OnCacheChanged()
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null)
        {
            ApplyFromCache();
            return;
        }

        if (dispatcher.CheckAccess())
        {
            ApplyFromCache();
        }
        else
        {
            dispatcher.Invoke(ApplyFromCache);
        }
    }

    private void OnPlaybackChanged()
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null)
        {
            SyncPlayingState();
            return;
        }

        if (dispatcher.CheckAccess())
        {
            SyncPlayingState();
        }
        else
        {
            dispatcher.Invoke(SyncPlayingState);
        }
    }

    private void SyncPlayingState()
    {
        var track = _playback.Coordinator.CurrentTrack;
        PlayingTrackId = track?.Id ?? 0;
        IsPlaybackActive = _playback.Coordinator.HasQueue && _playback.Coordinator.IsPlaying;
    }

    private void ApplyFromCache()
    {
        var snap = _cache.Snapshot;
        var state = _cache.State;

        ErrorMessage = state == LikedCacheState.Failed ? _cache.ErrorMessage : null;
        IsLoading = state == LikedCacheState.Loading;

        if (snap is not null)
        {
            PlaylistId = snap.Header.PlaylistId;
            PlaylistName = snap.Header.Name;
            PlaylistDescription = string.IsNullOrWhiteSpace(snap.Header.Description)
                ? "喜欢的音乐会收集在这里"
                : snap.Header.Description;
            CoverUrl = snap.Header.CoverUrl;
            HasCover = !string.IsNullOrWhiteSpace(CoverUrl);
            TrackCount = snap.Header.TrackCount > 0 ? snap.Header.TrackCount : snap.Tracks.Count;

            if (snap.Tracks.Count > 0 || state == LikedCacheState.Ready)
            {
                SyncTracks(snap.Tracks);
            }
        }

        ShowSkeleton = state == LikedCacheState.Loading && snap is null;
    }

    private void SyncTracks(IReadOnlyList<LikedTrackRow> rows)
    {
        if (Tracks.Count == rows.Count)
        {
            var same = true;
            for (var i = 0; i < rows.Count; i++)
            {
                if (Tracks[i].Id != rows[i].Id)
                {
                    same = false;
                    break;
                }
            }

            if (same)
            {
                return;
            }
        }

        Tracks.Clear();
        foreach (var row in rows)
        {
            Tracks.Add(row);
        }
    }
}
