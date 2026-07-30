using ZMusic.Data;
using ZMusic.Playback;

namespace ZMusic;

/// <summary>
/// Process-wide services created at startup.
/// </summary>
public sealed class AppServices
{
    public required SessionStore Sessions { get; init; }

    public required NcmAuthClient Auth { get; init; }

    public required NcmUserClient User { get; init; }

    public required LikedPlaylistCache Liked { get; init; }

    public required PlaybackBridge Playback { get; init; }

    public static AppServices Current { get; private set; } = null!;

    public static AppServices CreateDefault()
    {
        var sessions = new SessionStore();
        var auth = new NcmAuthClient();
        var user = new NcmUserClient();
        var lyrics = new LyricRepository(user);
        var coordinator = new PlaylistCoordinator(sessions, user, lyrics);
        var services = new AppServices
        {
            Sessions = sessions,
            Auth = auth,
            User = user,
            Liked = new LikedPlaylistCache(sessions, auth, user),
            Playback = new PlaybackBridge(coordinator),
        };
        Current = services;
        return services;
    }
}
