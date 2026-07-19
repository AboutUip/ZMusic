#include "pch.h"
#include "NcmClient.h"
#include "CacheStore.h"
#include "CryptoMd5.h"
#include "NcmJson.h"

#include <chrono>

using namespace winrt;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Data::Json;
using namespace winrt::Windows::Web::Http;
using namespace winrt::Windows::Foundation::Collections;

namespace winrt::ZMusic
{
    static std::wstring NormalizeBase(hstring const& base)
    {
        std::wstring b{ base.c_str() };
        while (!b.empty() && b.back() == L'/')
        {
            b.pop_back();
        }
        return b;
    }

    static std::wstring BuildUrl(std::wstring const& base, hstring const& path, std::map<std::wstring, std::wstring> const& query)
    {
        std::wstring p{ path.c_str() };
        if (p.empty() || p[0] != L'/')
        {
            p = L"/" + p;
        }
        std::wstring url = base + p;
        bool first = true;
        for (std::map<std::wstring, std::wstring>::value_type const& kv : query)
        {
            url += first ? L"?" : L"&";
            first = false;
            url += kv.first + L"=" + std::wstring{ Uri::EscapeComponent(hstring{ kv.second }).c_str() };
        }
        return url;
    }

    static hstring HttpJsonCacheBaseName(
        hstring const& apiBase,
        hstring const& path,
        std::map<std::wstring, std::wstring> const& query)
    {
        std::wstring key;
        key += apiBase.c_str();
        key.push_back(L'\n');
        key += path.c_str();
        for (auto const& kv : query)
        {
            key.push_back(L'\n');
            key += kv.first;
            key.push_back(L'=');
            key += kv.second;
        }
        return Md5HexUtf8String(hstring{ key }) + L".json";
    }

    NcmClient::NcmClient(hstring baseUrl) : m_base{ hstring{ NormalizeBase(baseUrl) } }
    {
        m_http = HttpClient();
    }

    hstring NcmClient::AppendTimestampQuery(hstring const& urlWithQuery)
    {
        std::wstring u{ urlWithQuery.c_str() };
        auto const ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::system_clock::now().time_since_epoch())
                            .count();
        u += (u.find(L'?') == std::wstring::npos ? L"?" : L"&");
        u += L"timestamp=";
        u += std::to_wstring(ms);
        return hstring{ u };
    }

    IAsyncOperation<JsonObject> NcmClient::ReadJsonAsync(HttpResponseMessage response, hstring* rawTextOut)
    {
        hstring text = co_await response.Content().ReadAsStringAsync();
        if (rawTextOut)
        {
            *rawTextOut = text;
        }
        co_return JsonObject::Parse(text);
    }

    IAsyncOperation<JsonObject> NcmClient::GetAsync(
        hstring const& path,
        std::map<std::wstring, std::wstring> query,
        bool const useSessionCache)
    {
        hstring cacheName{};
        if (useSessionCache)
        {
            cacheName = HttpJsonCacheBaseName(m_base, path, query);
            hstring const cached = co_await CacheStore::TryReadHttpJsonAsync(cacheName);
            if (!cached.empty())
            {
                try
                {
                    co_return JsonObject::Parse(cached);
                }
                catch (...)
                {
                }
            }
        }

        std::wstring url = BuildUrl(NormalizeBase(m_base), path, query);
        hstring finalUrl = AppendTimestampQuery(hstring{ url });
        Uri uri{ finalUrl };
        HttpRequestMessage msg{ HttpMethod::Get(), uri };
        HttpResponseMessage resp = co_await m_http.SendRequestAsync(msg);
        if (!resp.IsSuccessStatusCode())
        {
            hstring const errBody = co_await resp.Content().ReadAsStringAsync();
            try
            {
                JsonObject const ej = JsonObject::Parse(errBody);
                co_return ej;
            }
            catch (...)
            {
            }
            int32_t const code = static_cast<int32_t>(resp.StatusCode());
            throw hresult_error{ E_FAIL, hstring{ L"HTTP " + std::to_wstring(code) + L" " + errBody } };
        }
        hstring raw{};
        JsonObject const j = co_await ReadJsonAsync(resp, useSessionCache ? &raw : nullptr);
        if (useSessionCache && !raw.empty() && NcmJson::ApiCode(j) == 200)
        {
            co_await CacheStore::WriteHttpJsonAsync(cacheName, raw);
        }
        co_return j;
    }

    IAsyncOperation<JsonObject> NcmClient::PostFormAsync(hstring const& path, std::map<std::wstring, std::wstring> const& formFields)
    {
        std::wstring url = BuildUrl(NormalizeBase(m_base), path, {});
        hstring finalUrl = AppendTimestampQuery(hstring{ url });
        Uri uri{ finalUrl };

        StringMap sm;
        for (std::map<std::wstring, std::wstring>::value_type const& kv : formFields)
        {
            sm.Insert(hstring{ kv.first }, hstring{ kv.second });
        }
        HttpFormUrlEncodedContent content{ sm };
        HttpRequestMessage msg{ HttpMethod::Post(), uri };
        msg.Content(content);
        HttpResponseMessage resp = co_await m_http.SendRequestAsync(msg);
        co_return co_await ReadJsonAsync(resp, nullptr);
    }
}
