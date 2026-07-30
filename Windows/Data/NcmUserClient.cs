using System.Net.Http;
using System.Text.Json;
using ZMusic.Config;

namespace ZMusic.Data;

public sealed class NcmUserClient : IDisposable
{
    private readonly HttpClient _http;

    public NcmUserClient(HttpClient? httpClient = null)
    {
        _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(60) };
    }

    public Task<JsonElement> UserPlaylistAsync(
        long uid,
        string cookie,
        int limit = 100,
        int offset = 0,
        CancellationToken ct = default) =>
        GetAsync("/user/playlist", new Dictionary<string, string>
        {
            ["uid"] = uid.ToString(),
            ["limit"] = limit.ToString(),
            ["offset"] = offset.ToString(),
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> PlaylistDetailAsync(
        long playlistId,
        string cookie,
        CancellationToken ct = default) =>
        GetAsync("/playlist/detail", new Dictionary<string, string>
        {
            ["id"] = playlistId.ToString(),
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> PlaylistTrackAllAsync(
        long playlistId,
        string cookie,
        int limit = 1000,
        int offset = 0,
        CancellationToken ct = default) =>
        GetAsync("/playlist/track/all", new Dictionary<string, string>
        {
            ["id"] = playlistId.ToString(),
            ["limit"] = limit.ToString(),
            ["offset"] = offset.ToString(),
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> SongUrlAsync(
        long id,
        string cookie,
        int br = 320_000,
        CancellationToken ct = default) =>
        GetAsync("/song/url", new Dictionary<string, string>
        {
            ["id"] = id.ToString(),
            ["br"] = br.ToString(),
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> SongUrlV1Async(
        long id,
        string cookie,
        string level = "exhigh",
        CancellationToken ct = default) =>
        GetAsync("/song/url/v1", new Dictionary<string, string>
        {
            ["id"] = id.ToString(),
            ["level"] = level,
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> LyricAsync(
        long id,
        string cookie,
        CancellationToken ct = default) =>
        GetAsync("/lyric", new Dictionary<string, string>
        {
            ["id"] = id.ToString(),
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    private async Task<JsonElement> GetAsync(
        string path,
        IReadOnlyDictionary<string, string> query,
        CancellationToken ct)
    {
        var url = BuildUrl(path, query);
        using var response = await _http.GetAsync(url, ct).ConfigureAwait(false);
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException(
                $"HTTP {(int)response.StatusCode} @ {url} · {text[..Math.Min(text.Length, 200)]}");
        }

        if (string.IsNullOrWhiteSpace(text))
        {
            throw new HttpRequestException($"空响应 @ {url}");
        }

        using var doc = JsonDocument.Parse(text);
        return doc.RootElement.Clone();
    }

    private static string BuildUrl(string path, IReadOnlyDictionary<string, string> query)
    {
        var baseUrl = NcmApiConfig.BaseUrl.TrimEnd('/');
        var normalized = path.StartsWith('/') ? path : "/" + path;
        var builder = new UriBuilder(baseUrl + normalized);
        builder.Query = string.Join('&', query.Select(kv =>
            $"{Uri.EscapeDataString(kv.Key)}={Uri.EscapeDataString(kv.Value)}"));
        return builder.Uri.ToString();
    }

    private static string Ts() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();

    public void Dispose() => _http.Dispose();
}
