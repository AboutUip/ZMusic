#pragma once

#include "pch.h"

namespace winrt::ZMusic
{
    /** 0 = 跟随系统, 1 = 浅色, 2 = 深色 */
    enum class ThemePreference : int32_t
    {
        System = 0,
        Light = 1,
        Dark = 2,
    };

    struct AppSettings
    {
        static hstring ApiBaseUrl();
        static void ApiBaseUrl(hstring const& value);

        static ThemePreference Theme();
        static void Theme(ThemePreference value);

    private:
        static Windows::Storage::ApplicationDataContainer Local();
    };
}
