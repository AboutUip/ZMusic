using System.Text.Json;

namespace ZMusic.Data;

internal static class NcmJson
{
    public static int ApiCode(JsonElement json)
    {
        if (TryGetInt(json, "code", out var code))
        {
            return code;
        }

        if (json.TryGetProperty("data", out var data)
            && data.ValueKind == JsonValueKind.Object
            && TryGetInt(data, "code", out code))
        {
            return code;
        }

        if (TryGetInt(json, "status", out var status))
        {
            return status;
        }

        return -1;
    }

    public static string? ExtractCookie(JsonElement json)
    {
        if (TryGetString(json, "cookie", out var cookie) && !string.IsNullOrWhiteSpace(cookie))
        {
            return cookie.Trim();
        }

        if (json.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Object
            && TryGetString(data, "cookie", out cookie) && !string.IsNullOrWhiteSpace(cookie))
        {
            return cookie.Trim();
        }

        return null;
    }

    public static bool IsLoggedInStatus(JsonElement json)
    {
        if (ApiCode(json) != 200)
        {
            return false;
        }

        if (!json.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Object)
        {
            return false;
        }

        if (!data.TryGetProperty("account", out var account))
        {
            return false;
        }

        return account.ValueKind is not JsonValueKind.Null and not JsonValueKind.Undefined;
    }

    public static string? DisplayLabelFromLogin(JsonElement json)
    {
        if (json.TryGetProperty("profile", out var profile)
            && TryGetString(profile, "nickname", out var nick)
            && !string.IsNullOrWhiteSpace(nick))
        {
            return nick.Trim();
        }

        if (json.TryGetProperty("account", out var account)
            && TryGetString(account, "userName", out var name)
            && !string.IsNullOrWhiteSpace(name))
        {
            return name.Trim();
        }

        if (json.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Object
            && data.TryGetProperty("profile", out var p2)
            && TryGetString(p2, "nickname", out var n2)
            && !string.IsNullOrWhiteSpace(n2))
        {
            return n2.Trim();
        }

        return null;
    }

    public static string? QrImgBase64(JsonElement json)
    {
        if (!json.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        if (!TryGetString(data, "qrimg", out var raw) || string.IsNullOrWhiteSpace(raw))
        {
            return null;
        }

        raw = raw.Trim();
        var idx = raw.IndexOf(',');
        return idx >= 0 ? raw[(idx + 1)..] : raw;
    }

    public static string? QrKey(JsonElement json)
    {
        if (!json.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        if (TryGetString(data, "unikey", out var key) && !string.IsNullOrWhiteSpace(key))
        {
            return key.Trim();
        }

        if (TryGetString(data, "key", out key) && !string.IsNullOrWhiteSpace(key))
        {
            return key.Trim();
        }

        return null;
    }

    public static int QrCheckCode(JsonElement json) =>
        TryGetInt(json, "code", out var code) ? code : 0;

    public static string? Message(JsonElement json)
    {
        if (TryGetString(json, "msg", out var msg) && !string.IsNullOrWhiteSpace(msg))
        {
            return msg.Trim();
        }

        if (TryGetString(json, "message", out msg) && !string.IsNullOrWhiteSpace(msg))
        {
            return msg.Trim();
        }

        return null;
    }

    public static string? NicknameFromStatus(JsonElement json)
    {
        var profile = GetStatusProfile(json);
        if (profile is null)
        {
            return DisplayLabelFromLogin(json);
        }

        if (TryGetString(profile.Value, "nickname", out var nick) && !string.IsNullOrWhiteSpace(nick))
        {
            return nick.Trim();
        }

        return DisplayLabelFromLogin(json);
    }

    public static string? AvatarUrlFromStatus(JsonElement json)
    {
        var profile = GetStatusProfile(json);
        if (profile is null)
        {
            return null;
        }

        if (TryGetString(profile.Value, "avatarUrl", out var url) && !string.IsNullOrWhiteSpace(url))
        {
            return url.Trim();
        }

        return null;
    }

    private static JsonElement? GetStatusProfile(JsonElement json)
    {
        if (json.TryGetProperty("profile", out var rootProfile)
            && rootProfile.ValueKind == JsonValueKind.Object)
        {
            return rootProfile;
        }

        if (!json.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        if (data.TryGetProperty("profile", out var profile) && profile.ValueKind == JsonValueKind.Object)
        {
            return profile;
        }

        if (data.TryGetProperty("data", out var nested) && nested.ValueKind == JsonValueKind.Object
            && nested.TryGetProperty("profile", out var nestedProfile)
            && nestedProfile.ValueKind == JsonValueKind.Object)
        {
            return nestedProfile;
        }

        return null;
    }

    private static bool TryGetString(JsonElement element, string name, out string value)
    {
        value = string.Empty;
        if (!element.TryGetProperty(name, out var prop))
        {
            return false;
        }

        if (prop.ValueKind == JsonValueKind.String)
        {
            value = prop.GetString() ?? string.Empty;
            return true;
        }

        return false;
    }

    private static bool TryGetInt(JsonElement element, string name, out int value)
    {
        value = 0;
        if (!element.TryGetProperty(name, out var prop))
        {
            return false;
        }

        if (prop.ValueKind == JsonValueKind.Number && prop.TryGetInt32(out value))
        {
            return true;
        }

        if (prop.ValueKind == JsonValueKind.String
            && int.TryParse(prop.GetString(), out value))
        {
            return true;
        }

        return false;
    }
}
