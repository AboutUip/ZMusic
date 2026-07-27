using ZMusic.Data;

namespace ZMusic.Playback;

public sealed class QueueTrack
{
    public long Id { get; init; }
    public string Name { get; init; } = "";
    public string Artists { get; init; } = "—";
    public string? CoverUrl { get; init; }
    public long DurationMs { get; init; }

    public static QueueTrack From(LikedTrackRow row) => new()
    {
        Id = row.Id,
        Name = row.Name,
        Artists = row.Artists,
        CoverUrl = row.CoverUrl,
        DurationMs = row.DurationMs,
    };
}
