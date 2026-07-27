namespace ZMusic.Playback;

/// <summary>
/// Process-wide playback façade (Windows port of Android PlaybackBridge).
/// </summary>
public sealed class PlaybackBridge : IDisposable
{
    private readonly PlaylistCoordinator _coordinator;

    public PlaybackBridge(PlaylistCoordinator coordinator)
    {
        _coordinator = coordinator;
    }

    public PlaylistCoordinator Coordinator => _coordinator;

    public void PlayQueue(
        IReadOnlyList<QueueTrack> tracks,
        int startIndex,
        long? sourcePlaylistId = null,
        string? sourcePlaylistTitle = null) =>
        _coordinator.PlayQueue(tracks, startIndex, sourcePlaylistId, sourcePlaylistTitle);

    public void TogglePlayPause() => _coordinator.TogglePlayPause();

    public void CyclePlaybackMode() => _coordinator.CyclePlaybackMode();

    public void SeekTo(long ms) => _coordinator.SeekTo(ms);

    public void SkipNext() => _coordinator.SkipNext();

    public void Dispose() => _coordinator.Dispose();
}
