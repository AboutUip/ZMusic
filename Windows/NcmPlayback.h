#pragma once

#include "pch.h"

namespace winrt::ZMusic
{
    /** 与 Android NcmPlaybackParse 对齐：解析 `/song/url`、`/lyric`。 */
    struct NcmPlayback
    {
        static std::optional<hstring> SongUrlForTrackId(Windows::Data::Json::JsonObject const& songUrlJson, int64_t trackId);
        static std::optional<hstring> LyricRawLrcText(Windows::Data::Json::JsonObject const& lyricJson);
    };
}
