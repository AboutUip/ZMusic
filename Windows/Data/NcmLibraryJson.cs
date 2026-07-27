using System.Text.Json;

namespace ZMusic.Data;

internal static class NcmLibraryJson
{
    public static long? UserIdFromStatus(JsonElement json)
    {
        var profile = GetProfile(json);
        if (profile is not null && TryGetLong(profile.Value, "userId", out var profileId) && profileId > 0)
        {
            return profileId;
        }

        if (!json.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        if (data.TryGetProperty("account", out var account) && account.ValueKind == JsonValueKind.Object)
        {
            if (TryGetLong(account, "id", out var accountId) && accountId > 0)
            {
                return accountId;
            }
        }

        if (data.TryGetProperty("profile", out var nestedProfile)
            && nestedProfile.ValueKind == JsonValueKind.Object
            && TryGetLong(nestedProfile, "userId", out var nestedId)
            && nestedId > 0)
        {
            return nestedId;
        }

        return null;
    }

    /// <summary>
    /// Own heart playlist: specialType==5 or name==我喜欢的音乐, creator must be self.
    /// </summary>
    public static long? FindLikedPlaylistId(JsonElement userPlaylistJson, long selfUserId)
    {
        if (!userPlaylistJson.TryGetProperty("playlist", out var list)
            || list.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var item in list.EnumerateArray())
        {
            if (item.ValueKind != JsonValueKind.Object)
            {
                continue;
            }

            var name = GetString(item, "name") ?? "";
            var specialType = TryGetInt(item, "specialType", out var st) ? st : 0;
            long creatorId = 0;
            if (item.TryGetProperty("creator", out var creator)
                && creator.ValueKind == JsonValueKind.Object)
            {
                TryGetLong(creator, "userId", out creatorId);
            }

            var owned = creatorId == selfUserId;
            var isHeart = owned && (specialType == 5 || name == "我喜欢的音乐");
            if (!isHeart)
            {
                continue;
            }

            if (TryGetLong(item, "id", out var id) && id > 0)
            {
                return id;
            }
        }

        return null;
    }

    public static LikedPlaylistHeader? HeaderFromPlaylistDetail(JsonElement detailJson)
    {
        if (!detailJson.TryGetProperty("playlist", out var pl) || pl.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        if (!TryGetLong(pl, "id", out var id) || id <= 0)
        {
            return null;
        }

        var name = GetString(pl, "name");
        if (string.IsNullOrWhiteSpace(name))
        {
            name = "我喜欢的音乐";
        }

        var description = GetString(pl, "description");
        if (string.IsNullOrWhiteSpace(description))
        {
            description = null;
        }

        var cover = GetString(pl, "coverImgUrl") ?? GetString(pl, "coverUrl");
        var trackCount = TryGetInt(pl, "trackCount", out var count) ? count : 0;

        return new LikedPlaylistHeader
        {
            PlaylistId = id,
            Name = name!,
            Description = description,
            CoverUrl = string.IsNullOrWhiteSpace(cover) ? null : cover,
            TrackCount = trackCount,
        };
    }

    public static IReadOnlyList<LikedTrackRow> TracksFromTrackAll(JsonElement trackAllJson)
    {
        JsonElement songs;
        if (trackAllJson.TryGetProperty("songs", out var s) && s.ValueKind == JsonValueKind.Array)
        {
            songs = s;
        }
        else if (trackAllJson.TryGetProperty("playlist", out var pl)
                 && pl.ValueKind == JsonValueKind.Object
                 && pl.TryGetProperty("tracks", out var tracks)
                 && tracks.ValueKind == JsonValueKind.Array)
        {
            songs = tracks;
        }
        else
        {
            return Array.Empty<LikedTrackRow>();
        }

        var list = new List<LikedTrackRow>();
        foreach (var t in songs.EnumerateArray())
        {
            var row = ParseTrack(t);
            if (row is not null)
            {
                list.Add(row);
            }
        }

        return list;
    }

    private static LikedTrackRow? ParseTrack(JsonElement t)
    {
        if (!TryGetLong(t, "id", out var id) || id <= 0)
        {
            return null;
        }

        var name = GetString(t, "name");
        if (string.IsNullOrWhiteSpace(name))
        {
            return null;
        }

        var artists = "—";
        if (t.TryGetProperty("ar", out var ar) && ar.ValueKind == JsonValueKind.Array)
        {
            var names = new List<string>();
            foreach (var a in ar.EnumerateArray())
            {
                var n = GetString(a, "name");
                if (!string.IsNullOrWhiteSpace(n))
                {
                    names.Add(n!);
                }
            }

            if (names.Count > 0)
            {
                artists = string.Join(" / ", names);
            }
        }

        string? cover = null;
        if (t.TryGetProperty("al", out var al) && al.ValueKind == JsonValueKind.Object)
        {
            cover = GetString(al, "picUrl");
        }

        TryGetLong(t, "dt", out var durationMs);

        return new LikedTrackRow
        {
            Id = id,
            Name = name!,
            Artists = artists,
            CoverUrl = string.IsNullOrWhiteSpace(cover) ? null : cover,
            DurationMs = durationMs,
        };
    }

    private static JsonElement? GetProfile(JsonElement json)
    {
        if (json.TryGetProperty("profile", out var root) && root.ValueKind == JsonValueKind.Object)
        {
            return root;
        }

        if (json.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Object)
        {
            if (data.TryGetProperty("profile", out var p) && p.ValueKind == JsonValueKind.Object)
            {
                return p;
            }

            if (data.TryGetProperty("data", out var nested) && nested.ValueKind == JsonValueKind.Object
                && nested.TryGetProperty("profile", out var np) && np.ValueKind == JsonValueKind.Object)
            {
                return np;
            }
        }

        return null;
    }

    private static string? GetString(JsonElement el, string name)
    {
        if (!el.TryGetProperty(name, out var p) || p.ValueKind != JsonValueKind.String)
        {
            return null;
        }

        return p.GetString();
    }

    private static bool TryGetLong(JsonElement el, string name, out long value)
    {
        value = 0;
        if (!el.TryGetProperty(name, out var p))
        {
            return false;
        }

        if (p.ValueKind == JsonValueKind.Number && p.TryGetInt64(out value))
        {
            return true;
        }

        if (p.ValueKind == JsonValueKind.String && long.TryParse(p.GetString(), out value))
        {
            return true;
        }

        return false;
    }

    private static bool TryGetInt(JsonElement el, string name, out int value)
    {
        value = 0;
        if (!TryGetLong(el, name, out var longValue))
        {
            return false;
        }

        value = (int)longValue;
        return true;
    }
}
