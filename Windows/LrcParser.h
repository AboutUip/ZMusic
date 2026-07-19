#pragma once

#include "pch.h"

#include <string>
#include <vector>

namespace winrt::ZMusic
{
    struct LrcLine
    {
        int64_t timeMs{};
        std::wstring text;
    };

    /** 与 Android LrcParser 对齐：[mm:ss.xx] / [mm:ss] 行。 */
    struct LrcParser
    {
        static std::vector<LrcLine> Parse(hstring const& raw);
    };
}
