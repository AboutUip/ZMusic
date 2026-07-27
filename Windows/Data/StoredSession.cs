namespace ZMusic.Data;

public sealed class StoredSession
{
    public required string Cookie { get; init; }

    public string? Label { get; init; }

    public DateTimeOffset SavedAt { get; init; } = DateTimeOffset.UtcNow;
}
