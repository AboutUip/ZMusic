using ZMusic.Data;
using ZMusic.Services;

namespace ZMusic;

/// <summary>
/// Startup work that runs while the splash animation plays.
/// </summary>
internal static class AppInitializer
{
    public static AuthBootstrapResult? AuthResult { get; private set; }

    public static async Task RunAsync(CancellationToken cancellationToken = default)
    {
        var services = AppServices.Current;
        var bootstrapper = new AuthBootstrapper(services.Sessions, services.Auth);
        AuthResult = await bootstrapper.RunAsync(cancellationToken).ConfigureAwait(false);

        // Warm liked playlist in background after session restore (does not block splash exit).
        if (AuthResult.State == AuthBootstrapState.Authenticated)
        {
            services.Liked.Prefetch();
        }
    }
}
