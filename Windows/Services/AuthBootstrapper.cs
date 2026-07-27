using ZMusic.Data;

namespace ZMusic.Services;

public enum AuthBootstrapState
{
    Anonymous,
    Authenticated,
}

public sealed record AuthBootstrapResult(
    AuthBootstrapState State,
    StoredSession? Session,
    string? Detail);

/// <summary>
/// Restores persisted login during splash: validate status, then refresh cookie when possible.
/// </summary>
public sealed class AuthBootstrapper
{
    private readonly SessionStore _sessions;
    private readonly NcmAuthClient _api;

    public AuthBootstrapper(SessionStore sessions, NcmAuthClient api)
    {
        _sessions = sessions;
        _api = api;
    }

    public async Task<AuthBootstrapResult> RunAsync(CancellationToken cancellationToken = default)
    {
        var session = _sessions.Load();
        if (session is null || string.IsNullOrWhiteSpace(session.Cookie))
        {
            return new AuthBootstrapResult(AuthBootstrapState.Anonymous, null, "无本地会话");
        }

        try
        {
            var status = await _api.LoginStatusAsync(session.Cookie, cancellationToken)
                .ConfigureAwait(false);

            if (NcmJson.IsLoggedInStatus(status))
            {
                var refreshed = await TryRefreshAsync(session, cancellationToken).ConfigureAwait(false);
                return new AuthBootstrapResult(
                    AuthBootstrapState.Authenticated,
                    refreshed ?? session,
                    refreshed is null ? "会话有效" : "会话已刷新");
            }

            // Explicit not-logged-in: clear. Network/other codes keep optimistic path below.
            var code = NcmJson.ApiCode(status);
            if (code is 200 or 301)
            {
                _sessions.Clear();
                return new AuthBootstrapResult(AuthBootstrapState.Anonymous, null, "登录已失效");
            }

            // Ambiguous API response — keep local cookie for this launch.
            return new AuthBootstrapResult(AuthBootstrapState.Authenticated, session, "状态未知，沿用本地会话");
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            // Network blip during splash: keep cookie so UX doesn't bounce to login wrongly.
            return new AuthBootstrapResult(AuthBootstrapState.Authenticated, session, "网络异常，沿用本地会话");
        }
    }

    private async Task<StoredSession?> TryRefreshAsync(
        StoredSession session,
        CancellationToken cancellationToken)
    {
        try
        {
            var refreshed = await _api.LoginRefreshAsync(session.Cookie, cancellationToken)
                .ConfigureAwait(false);
            var cookie = NcmJson.ExtractCookie(refreshed);
            if (string.IsNullOrWhiteSpace(cookie))
            {
                return null;
            }

            // QR-login cookies often cannot refresh; only persist when a new cookie arrives.
            if (string.Equals(cookie, session.Cookie, StringComparison.Ordinal))
            {
                return session;
            }

            var label = NcmJson.DisplayLabelFromLogin(refreshed) ?? session.Label;
            _sessions.Save(cookie, label);
            return _sessions.Current;
        }
        catch
        {
            // Refresh unsupported (e.g. QR cookie) — keep existing valid session.
            return null;
        }
    }
}
