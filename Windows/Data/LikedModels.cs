namespace ZMusic.Data;

public sealed class LikedPlaylistHeader
{
    public long PlaylistId { get; init; }
    public string Name { get; init; } = "我喜欢的音乐";
    public string? Description { get; init; }
    public string? CoverUrl { get; init; }
    public int TrackCount { get; init; }
}

public sealed class LikedTrackRow
{
    public long Id { get; init; }
    public string Name { get; init; } = "";
    public string Artists { get; init; } = "—";
    public string? CoverUrl { get; init; }
    public long DurationMs { get; init; }

    public string DurationText
    {
        get
        {
            if (DurationMs <= 0)
            {
                return "--:--";
            }

            var totalSeconds = DurationMs / 1000;
            var minutes = totalSeconds / 60;
            var seconds = totalSeconds % 60;
            return $"{minutes}:{seconds:00}";
        }
    }
}
