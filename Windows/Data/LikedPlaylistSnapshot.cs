using System.Collections.Immutable;

namespace ZMusic.Data;

public sealed class LikedPlaylistSnapshot
{
    public required LikedPlaylistHeader Header { get; init; }

    public required IReadOnlyList<LikedTrackRow> Tracks { get; init; }

    public DateTimeOffset LoadedAt { get; init; } = DateTimeOffset.UtcNow;

    public static LikedPlaylistSnapshot Empty { get; } = new()
    {
        Header = new LikedPlaylistHeader(),
        Tracks = ImmutableArray<LikedTrackRow>.Empty,
    };
}

public enum LikedCacheState
{
    Idle,
    Loading,
    Ready,
    Failed,
}
