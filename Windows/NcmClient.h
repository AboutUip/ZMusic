#pragma once

#include "pch.h"

#include <map>
#include <string>

namespace winrt::ZMusic
{
    struct NcmClient
    {
        explicit NcmClient(hstring baseUrl);

        Windows::Foundation::IAsyncOperation<Windows::Data::Json::JsonObject> GetAsync(
            hstring const& path,
            std::map<std::wstring, std::wstring> query,
            bool useSessionCache = false);

        Windows::Foundation::IAsyncOperation<Windows::Data::Json::JsonObject> PostFormAsync(
            hstring const& path,
            std::map<std::wstring, std::wstring> const& formFields);

    private:
        hstring m_base;
        Windows::Web::Http::HttpClient m_http{ nullptr };

        static hstring AppendTimestampQuery(hstring const& urlWithQuery);
        static Windows::Foundation::IAsyncOperation<Windows::Data::Json::JsonObject> ReadJsonAsync(
            Windows::Web::Http::HttpResponseMessage response,
            hstring* rawTextOut);
    };
}
