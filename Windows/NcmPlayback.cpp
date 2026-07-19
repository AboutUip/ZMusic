#include "pch.h"
#include "NcmPlayback.h"
#include "NcmJson.h"

using namespace winrt;
using namespace winrt::Windows::Data::Json;

namespace winrt::ZMusic
{
    namespace
    {
        static std::optional<int64_t> JsonLong(IJsonValue const& v)
        {
            switch (v.ValueType())
            {
            case JsonValueType::Number:
                return static_cast<int64_t>(v.GetNumber());
            case JsonValueType::String:
                return std::wcstoll(v.GetString().c_str(), nullptr, 10);
            default:
                return std::nullopt;
            }
        }

        static std::optional<hstring> UrlFromSongDataObject(JsonObject const& o)
        {
            if (!o.HasKey(L"url"))
            {
                return std::nullopt;
            }
            hstring const u = o.GetNamedString(L"url");
            if (u.empty())
            {
                return std::nullopt;
            }
            return u;
        }
    } // namespace

    std::optional<hstring> NcmPlayback::SongUrlForTrackId(JsonObject const& songUrlJson, int64_t const trackId)
    {
        if (NcmJson::ApiCode(songUrlJson) != 200)
        {
            return std::nullopt;
        }
        if (!songUrlJson.HasKey(L"data"))
        {
            return std::nullopt;
        }
        IJsonValue const v = songUrlJson.Lookup(L"data");
        if (v.ValueType() != JsonValueType::Array)
        {
            return std::nullopt;
        }
        JsonArray const arr = v.GetArray();
        std::optional<hstring> loneUrl{};
        if (arr.Size() == 1 && arr.GetAt(0).ValueType() == JsonValueType::Object)
        {
            loneUrl = UrlFromSongDataObject(arr.GetAt(0).GetObject());
        }
        for (uint32_t i = 0; i < arr.Size(); ++i)
        {
            if (arr.GetAt(i).ValueType() != JsonValueType::Object)
            {
                continue;
            }
            JsonObject const o = arr.GetAt(i).GetObject();
            if (!o.HasKey(L"id"))
            {
                continue;
            }
            std::optional<int64_t> const id = JsonLong(o.Lookup(L"id"));
            if (!id || *id != trackId)
            {
                continue;
            }
            if (auto u = UrlFromSongDataObject(o))
            {
                return u;
            }
        }
        return loneUrl;
    }

    std::optional<hstring> NcmPlayback::LyricRawLrcText(JsonObject const& lyricJson)
    {
        if (NcmJson::ApiCode(lyricJson) != 200)
        {
            return std::nullopt;
        }
        if (!lyricJson.HasKey(L"lrc"))
        {
            return std::nullopt;
        }
        IJsonValue const lv = lyricJson.Lookup(L"lrc");
        if (lv.ValueType() != JsonValueType::Object)
        {
            return std::nullopt;
        }
        JsonObject const lrc = lv.GetObject();
        if (!lrc.HasKey(L"lyric"))
        {
            return std::nullopt;
        }
        hstring const text = lrc.GetNamedString(L"lyric");
        if (text.empty())
        {
            return std::nullopt;
        }
        return text;
    }
}
