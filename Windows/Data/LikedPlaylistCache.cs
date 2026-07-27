namespace ZMusic.Data;

/// <summary>
/// Process-wide cache for the user's heart playlist ("我喜欢的音乐").
/// Prefetched during splash / main shell so Liked page can open warm.
/// </summary>
public sealed class LikedPlaylistCache
{
    private readonly SessionStore _sessions;
    private readonly NcmAuthClient _auth;
    private readonly NcmUserClient _user;
    private readonly SemaphoreSlim _gate = new(1, 1);

    private CancellationTokenSource? _cts;
    private Task? _inflight;

    public LikedCacheState State { get; private set; } = LikedCacheState.Idle;

    public LikedPlaylistSnapshot? Snapshot { get; private set; }

    public string? ErrorMessage { get; private set; }

    public event Action? Changed;

    public LikedPlaylistCache(SessionStore sessions, NcmAuthClient auth, NcmUserClient user)
    {
        _sessions = sessions;
        _auth = auth;
        _user = user;
    }

    public bool HasData => Snapshot is { Tracks.Count: > 0 } || Snapshot?.Header.PlaylistId > 0;

    /// <summary>Fire-and-forget warm load; safe to call repeatedly.</summary>
    public void Prefetch()
    {
        _ = EnsureLoadedAsync();
    }

    public Task EnsureLoadedAsync(bool force = false, CancellationToken cancellationToken = default)
    {
        if (!force && State == LikedCacheState.Ready && Snapshot is not null)
        {
            return Task.CompletedTask;
        }

        if (!force && _inflight is { IsCompleted: false })
        {
            return _inflight;
        }

        _cts?.Cancel();
        _cts?.Dispose();
        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        var token = _cts.Token;
        _inflight = LoadCoreAsync(token);
        return _inflight;
    }

    private async Task LoadCoreAsync(CancellationToken cancellationToken)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            State = LikedCacheState.Loading;
            ErrorMessage = null;
            RaiseChanged();

            var session = _sessions.Current ?? _sessions.Load();
            if (session is null || string.IsNullOrWhiteSpace(session.Cookie))
            {
                State = LikedCacheState.Failed;
                ErrorMessage = "未登录，无法获取喜欢的音乐";
                Snapshot = null;
                RaiseChanged();
                return;
            }

            var status = await _auth.LoginStatusAsync(session.Cookie, cancellationToken)
                .ConfigureAwait(false);
            var uid = NcmLibraryJson.UserIdFromStatus(status);
            if (uid is null or <= 0)
            {
                State = LikedCacheState.Failed;
                ErrorMessage = "无法解析用户 ID";
                Snapshot = null;
                RaiseChanged();
                return;
            }

            var playlists = await _user.UserPlaylistAsync(uid.Value, session.Cookie, ct: cancellationToken)
                .ConfigureAwait(false);
            var likedId = NcmLibraryJson.FindLikedPlaylistId(playlists, uid.Value);
            if (likedId is null or <= 0)
            {
                State = LikedCacheState.Failed;
                ErrorMessage = "未找到「我喜欢的音乐」歌单";
                Snapshot = null;
                RaiseChanged();
                return;
            }

            var detail = await _user.PlaylistDetailAsync(likedId.Value, session.Cookie, cancellationToken)
                .ConfigureAwait(false);
            var header = NcmLibraryJson.HeaderFromPlaylistDetail(detail);
            if (header is null)
            {
                State = LikedCacheState.Failed;
                ErrorMessage = "歌单详情解析失败";
                Snapshot = null;
                RaiseChanged();
                return;
            }

            // Publish header early so UI can paint cover/title while tracks load.
            Snapshot = new LikedPlaylistSnapshot
            {
                Header = header,
                Tracks = Array.Empty<LikedTrackRow>(),
            };
            RaiseChanged();

            var trackAll = await _user.PlaylistTrackAllAsync(
                    likedId.Value,
                    session.Cookie,
                    limit: Math.Max(header.TrackCount, 50),
                    ct: cancellationToken)
                .ConfigureAwait(false);

            var rows = NcmLibraryJson.TracksFromTrackAll(trackAll);
            var finalHeader = header;
            if (rows.Count > 0 && header.TrackCount != rows.Count)
            {
                finalHeader = new LikedPlaylistHeader
                {
                    PlaylistId = header.PlaylistId,
                    Name = header.Name,
                    Description = header.Description,
                    CoverUrl = header.CoverUrl,
                    TrackCount = rows.Count,
                };
            }

            Snapshot = new LikedPlaylistSnapshot
            {
                Header = finalHeader,
                Tracks = rows,
            };
            State = LikedCacheState.Ready;
            ErrorMessage = null;
            RaiseChanged();
        }
        catch (OperationCanceledException)
        {
            // superseded / shutdown
        }
        catch (Exception ex)
        {
            State = LikedCacheState.Failed;
            ErrorMessage = ex.Message;
            RaiseChanged();
        }
        finally
        {
            _gate.Release();
        }
    }

    private void RaiseChanged() => Changed?.Invoke();
}
