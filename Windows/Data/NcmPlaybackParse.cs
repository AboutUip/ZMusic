using System.Text.Json;

namespace ZMusic.Data;

internal static class NcmPlaybackParse
{
    public static string? SongUrlForId(JsonElement json, long id)
    {
        if (NcmJson.ApiCode(json) != 200)
        {
            return null;
        }

        if (!json.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var item in data.EnumerateArray())
        {
            if (item.ValueKind != JsonValueKind.Object)
            {
                continue;
            }

            if (!item.TryGetProperty("id", out var idEl) || !idEl.TryGetInt64(out var itemId) || itemId != id)
            {
                continue;
            }

            if (!item.TryGetProperty("url", out var urlEl) || urlEl.ValueKind != JsonValueKind.String)
            {
                return null;
            }

            var url = urlEl.GetString();
            return string.IsNullOrWhiteSpace(url) ? null : url;
        }

        return null;
    }
}
