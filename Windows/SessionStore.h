#pragma once

#include "pch.h"

namespace winrt::ZMusic
{
    struct SessionData
    {
        hstring Cookie;
        hstring DisplayLabel;
        bool IsGuest{};

        bool IsValid() const noexcept
        {
            return !Cookie.empty();
        }
    };

    struct SessionStore
    {
        static SessionData ParseFromJson(hstring const& jsonText);
        static Windows::Foundation::IAsyncOperation<hstring> LoadRawAsync();
        static Windows::Foundation::IAsyncAction SaveAsync(SessionData const& data);
        static Windows::Foundation::IAsyncAction ClearAsync();
    };
}
