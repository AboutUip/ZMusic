namespace ZMusic.Config;

/// <summary>
/// NeteaseCloudMusicApi base URL. Default matches Android; override via env ZMUSIC_NCM_API_BASE_URL.
/// </summary>
public static class NcmApiConfig
{
    public const string DefaultBaseUrl = "http://120.27.244.170:3000";

    private static string? _runtimeBaseUrl;

    public static string BaseUrl
    {
        get
        {
            if (!string.IsNullOrWhiteSpace(_runtimeBaseUrl))
            {
                return _runtimeBaseUrl.TrimEnd('/');
            }

            var fromEnv = Environment.GetEnvironmentVariable("ZMUSIC_NCM_API_BASE_URL");
            if (!string.IsNullOrWhiteSpace(fromEnv))
            {
                return fromEnv.Trim().TrimEnd('/');
            }

            return DefaultBaseUrl;
        }
    }

    public static void SetRuntimeBaseUrl(string? url)
    {
        _runtimeBaseUrl = string.IsNullOrWhiteSpace(url)
            ? null
            : url.Trim().TrimEnd('/');
    }
}
