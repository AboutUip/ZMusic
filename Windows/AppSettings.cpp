#include "pch.h"
#include "AppSettings.h"

using namespace winrt;
using namespace winrt::Windows::Storage;

namespace winrt::ZMusic
{
    static wchar_t const* kKeyApi = L"ncm_api_base_url";
    static wchar_t const* kKeyTheme = L"ui_theme";

    ApplicationDataContainer AppSettings::Local()
    {
        return ApplicationData::Current().LocalSettings();
    }

    hstring AppSettings::ApiBaseUrl()
    {
        auto v = Local().Values().Lookup(hstring{ kKeyApi });
        if (!v)
        {
            return L"http://47.110.72.65:3000";
        }
        return unbox_value<hstring>(v);
    }

    void AppSettings::ApiBaseUrl(hstring const& value)
    {
        Local().Values().Insert(hstring{ kKeyApi }, box_value(value));
    }

    ThemePreference AppSettings::Theme()
    {
        auto v = Local().Values().Lookup(hstring{ kKeyTheme });
        if (!v)
        {
            return ThemePreference::System;
        }
        return static_cast<ThemePreference>(unbox_value<int32_t>(v));
    }

    void AppSettings::Theme(ThemePreference value)
    {
        Local().Values().Insert(hstring{ kKeyTheme }, box_value(static_cast<int32_t>(value)));
    }
}
