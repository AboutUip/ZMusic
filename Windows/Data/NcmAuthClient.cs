using System.Net.Http;
using System.Text.Json;
using ZMusic.Config;

namespace ZMusic.Data;

public sealed class NcmAuthClient : IDisposable
{
    private readonly HttpClient _http;

    public NcmAuthClient(HttpClient? httpClient = null)
    {
        _http = httpClient ?? new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(60),
        };
    }

    public Task<JsonElement> LoginStatusAsync(string cookie, CancellationToken ct = default) =>
        GetAsync("/login/status", new Dictionary<string, string>
        {
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> LoginRefreshAsync(string cookie, CancellationToken ct = default) =>
        GetAsync("/login/refresh", new Dictionary<string, string>
        {
            ["cookie"] = cookie,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> LoginQrKeyAsync(CancellationToken ct = default) =>
        GetAsync("/login/qr/key", new Dictionary<string, string>
        {
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> LoginQrCreateAsync(string key, CancellationToken ct = default) =>
        GetAsync("/login/qr/create", new Dictionary<string, string>
        {
            ["key"] = key,
            ["qrimg"] = "true",
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> LoginQrCheckAsync(string key, bool noCookie, CancellationToken ct = default)
    {
        var query = new Dictionary<string, string>
        {
            ["key"] = key,
            ["timestamp"] = Ts(),
        };
        if (noCookie)
        {
            query["noCookie"] = "true";
        }

        return GetAsync("/login/qr/check", query, ct);
    }

    public Task<JsonElement> CaptchaSentAsync(string phone, string ctcode = "86", CancellationToken ct = default) =>
        GetAsync("/captcha/sent", new Dictionary<string, string>
        {
            ["phone"] = phone,
            ["ctcode"] = ctcode,
            ["timestamp"] = Ts(),
        }, ct);

    public Task<JsonElement> LoginCellphoneAsync(
        string phone,
        string? captcha = null,
        string countrycode = "86",
        CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(captcha))
        {
            throw new ArgumentException("captcha is required for phone login.", nameof(captcha));
        }

        var form = new Dictionary<string, string>
        {
            ["phone"] = phone,
            ["countrycode"] = countrycode,
            ["captcha"] = captcha,
        };
        return PostFormAsync("/login/cellphone", form, ct);
    }

    private async Task<JsonElement> GetAsync(
        string path,
        IReadOnlyDictionary<string, string> query,
        CancellationToken ct)
    {
        var url = BuildUrl(path, query);
        using var response = await _http.GetAsync(url, ct).ConfigureAwait(false);
        return await ReadJsonAsync(response, url, ct).ConfigureAwait(false);
    }

    private async Task<JsonElement> PostFormAsync(
        string path,
        IReadOnlyDictionary<string, string> fields,
        CancellationToken ct)
    {
        var url = BuildUrl(path, new Dictionary<string, string> { ["timestamp"] = Ts() });
        using var content = new FormUrlEncodedContent(fields);
        using var response = await _http.PostAsync(url, content, ct).ConfigureAwait(false);
        return await ReadJsonAsync(response, url, ct).ConfigureAwait(false);
    }

    private static async Task<JsonElement> ReadJsonAsync(
        HttpResponseMessage response,
        string url,
        CancellationToken ct)
    {
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException(
                $"HTTP {(int)response.StatusCode} {response.ReasonPhrase} @ {url} · {Trim(text, 200)}");
        }

        if (string.IsNullOrWhiteSpace(text))
        {
            throw new HttpRequestException($"空响应 @ {url}");
        }

        try
        {
            using var doc = JsonDocument.Parse(text);
            return doc.RootElement.Clone();
        }
        catch (JsonException ex)
        {
            throw new HttpRequestException($"非 JSON 响应 @ {url}: {Trim(text, 300)}", ex);
        }
    }

    private static string BuildUrl(string path, IReadOnlyDictionary<string, string> query)
    {
        var baseUrl = NcmApiConfig.BaseUrl.TrimEnd('/');
        var normalized = path.StartsWith('/') ? path : "/" + path;
        var builder = new UriBuilder(baseUrl + normalized);
        var pairs = query.Select(kv =>
            $"{Uri.EscapeDataString(kv.Key)}={Uri.EscapeDataString(kv.Value)}");
        builder.Query = string.Join('&', pairs);
        return builder.Uri.ToString();
    }

    private static string Ts() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();

    private static string Trim(string text, int max) =>
        text.Length <= max ? text : text[..max];

    public void Dispose() => _http.Dispose();
}
