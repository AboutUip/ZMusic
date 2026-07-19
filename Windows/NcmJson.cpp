#include "pch.h"
#include "NcmJson.h"

#include <cwchar>
#include <optional>

using namespace winrt;
using namespace winrt::Windows::Data::Json;

namespace winrt::ZMusic
{
    static hstring JsonStr(JsonObject const& o, hstring const& key)
    {
        return o.HasKey(key) ? o.GetNamedString(key) : hstring{ L"" };
    }

    static int32_t JsonCodeValue(IJsonValue const& val)
    {
        switch (val.ValueType())
        {
        case JsonValueType::Number:
            return static_cast<int32_t>(val.GetNumber());
        case JsonValueType::String:
            return static_cast<int32_t>(wcstol(val.GetString().c_str(), nullptr, 10));
        default:
            return -1;
        }
    }

    static hstring Trim(hstring const& s)
    {
        std::wstring_view w{ s };
        while (!w.empty() && iswspace(w.front()))
        {
            w.remove_prefix(1);
        }
        while (!w.empty() && iswspace(w.back()))
        {
            w.remove_suffix(1);
        }
        return hstring{ w };
    }

    static std::optional<int64_t> LongFromJsonValue(IJsonValue const& v)
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

    static std::optional<JsonObject> EffectiveLoginStatusPayload(JsonObject const& json)
    {
        if (!json.HasKey(L"data"))
        {
            return std::nullopt;
        }
        JsonObject const d1 = json.GetNamedObject(L"data");
        std::optional<JsonObject> d2;
        if (d1.HasKey(L"data") && d1.Lookup(L"data").ValueType() == JsonValueType::Object)
        {
            d2 = d1.GetNamedObject(L"data");
        }
        if (d2)
        {
            if (d2->HasKey(L"profile") || d2->HasKey(L"account"))
            {
                return *d2;
            }
        }
        if (d1.HasKey(L"profile") || d1.HasKey(L"account"))
        {
            return d1;
        }
        if (d2)
        {
            return *d2;
        }
        return d1;
    }

    static bool PayloadShowsLoggedInUser(JsonObject const& o)
    {
        if (o.HasKey(L"account"))
        {
            auto const v = o.Lookup(L"account");
            if (v.ValueType() != JsonValueType::Null && v.ValueType() == JsonValueType::Object)
            {
                return true;
            }
        }
        if (o.HasKey(L"profile"))
        {
            auto const v = o.Lookup(L"profile");
            if (v.ValueType() == JsonValueType::Object)
            {
                JsonObject const p = v.GetObject();
                if (p.HasKey(L"userId") && p.Lookup(L"userId").ValueType() != JsonValueType::Null)
                {
                    if (auto n = LongFromJsonValue(p.Lookup(L"userId")))
                    {
                        if (*n > 0)
                        {
                            return true;
                        }
                    }
                }
                if (p.HasKey(L"userid") && p.Lookup(L"userid").ValueType() != JsonValueType::Null)
                {
                    if (auto n = LongFromJsonValue(p.Lookup(L"userid")))
                    {
                        if (*n > 0)
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    int32_t NcmJson::ApiCode(JsonObject const& json)
    {
        if (json.HasKey(L"code"))
        {
            return JsonCodeValue(json.Lookup(L"code"));
        }
        if (json.HasKey(L"status"))
        {
            return JsonCodeValue(json.Lookup(L"status"));
        }
        return -1;
    }

    std::optional<hstring> NcmJson::ExtractCookie(JsonObject const& json)
    {
        hstring c = Trim(JsonStr(json, L"cookie"));
        if (!c.empty())
        {
            return c;
        }
        if (!json.HasKey(L"data"))
        {
            return std::nullopt;
        }
        auto data = json.GetNamedObject(L"data");
        c = Trim(JsonStr(data, L"cookie"));
        if (!c.empty())
        {
            return c;
        }
        return std::nullopt;
    }

    bool NcmJson::IsLoggedInStatus(JsonObject const& json, bool isGuestSession)
    {
        if (ApiCode(json) != 200)
        {
            return false;
        }
        if (isGuestSession)
        {
            return true;
        }
        if (PayloadShowsLoggedInUser(json))
        {
            return true;
        }
        if (auto const payload = EffectiveLoginStatusPayload(json))
        {
            return PayloadShowsLoggedInUser(*payload);
        }
        return false;
    }

    std::optional<hstring> NcmJson::DisplayLabelFromLogin(JsonObject const& json)
    {
        if (json.HasKey(L"profile"))
        {
            auto profile = json.GetNamedObject(L"profile");
            auto nick = Trim(JsonStr(profile, L"nickname"));
            if (!nick.empty())
            {
                return nick;
            }
        }
        if (json.HasKey(L"account"))
        {
            auto account = json.GetNamedObject(L"account");
            auto name = Trim(JsonStr(account, L"userName"));
            if (!name.empty())
            {
                return name;
            }
        }
        if (json.HasKey(L"data"))
        {
            auto data = json.GetNamedObject(L"data");
            if (data.HasKey(L"profile"))
            {
                auto p2 = data.GetNamedObject(L"profile");
                auto n2 = Trim(JsonStr(p2, L"nickname"));
                if (!n2.empty())
                {
                    return n2;
                }
            }
        }
        return std::nullopt;
    }

    std::optional<hstring> NcmJson::QrImgBase64(JsonObject const& json)
    {
        if (!json.HasKey(L"data"))
        {
            return std::nullopt;
        }
        auto data = json.GetNamedObject(L"data");
        auto raw = Trim(JsonStr(data, L"qrimg"));
        if (raw.empty())
        {
            return std::nullopt;
        }
        std::wstring_view w{ raw };
        auto comma = w.find(L',');
        if (comma != std::wstring_view::npos)
        {
            w.remove_prefix(comma + 1);
        }
        return hstring{ w };
    }

    std::optional<hstring> NcmJson::QrKey(JsonObject const& json)
    {
        if (!json.HasKey(L"data"))
        {
            return std::nullopt;
        }
        auto data = json.GetNamedObject(L"data");
        auto k = Trim(JsonStr(data, L"unikey"));
        if (k.empty())
        {
            k = Trim(JsonStr(data, L"key"));
        }
        if (k.empty())
        {
            return std::nullopt;
        }
        return k;
    }

    int32_t NcmJson::QrCheckCode(JsonObject const& json)
    {
        int32_t rootCode = 0;
        if (json.HasKey(L"code"))
        {
            rootCode = JsonCodeValue(json.Lookup(L"code"));
        }

        auto tryNestedQrState = [](JsonObject const& data) -> std::optional<int32_t>
        {
            auto pick = [](JsonObject const& d, hstring const& key) -> std::optional<int32_t>
            {
                if (!d.HasKey(key))
                {
                    return std::nullopt;
                }
                int32_t const v = JsonCodeValue(d.Lookup(key));
                if (v == 502 || (v >= 800 && v <= 803))
                {
                    return v;
                }
                return std::nullopt;
            };
            if (auto c = pick(data, L"code"))
            {
                return c;
            }
            return pick(data, L"status");
        };

        bool const rootIsQrState = (rootCode == 502 || (rootCode >= 800 && rootCode <= 803));
        if (rootIsQrState)
        {
            return rootCode;
        }

        if (json.HasKey(L"data") && json.Lookup(L"data").ValueType() == JsonValueType::Object)
        {
            if (auto nested = tryNestedQrState(json.GetNamedObject(L"data")))
            {
                if (rootCode == 0 || rootCode == 200)
                {
                    return *nested;
                }
            }
        }

        return rootCode;
    }

    static std::optional<int64_t> LongFromObject(JsonObject const& o, hstring const& key)
    {
        if (!o.HasKey(key) || o.Lookup(key).ValueType() == JsonValueType::Null)
        {
            return std::nullopt;
        }
        return LongFromJsonValue(o.Lookup(key));
    }

    std::optional<int64_t> NcmJson::UserIdFromLoginStatus(JsonObject const& json)
    {
        int32_t const code = ApiCode(json);
        if (code != 200 && code != 301)
        {
            if (!json.HasKey(L"data") && !json.HasKey(L"profile") && !json.HasKey(L"account"))
            {
                return std::nullopt;
            }
        }
        if (json.HasKey(L"data"))
        {
            auto const data = json.GetNamedObject(L"data");
            if (data.HasKey(L"data"))
            {
                auto const inner = data.Lookup(L"data");
                if (inner.ValueType() == JsonValueType::Object)
                {
                    auto const d2 = inner.GetObject();
                if (auto id = LongFromObject(d2, L"userId"))
                {
                    return id;
                }
                if (auto id = LongFromObject(d2, L"userid"))
                {
                    return id;
                }
                if (d2.HasKey(L"profile"))
                {
                    auto const p = d2.GetNamedObject(L"profile");
                    if (auto id = LongFromObject(p, L"userId"))
                    {
                        return id;
                    }
                    if (auto id = LongFromObject(p, L"userid"))
                    {
                        return id;
                    }
                }
                if (d2.HasKey(L"account"))
                {
                    auto const a = d2.GetNamedObject(L"account");
                    if (auto id = LongFromObject(a, L"id"))
                    {
                        return id;
                    }
                }
                }
            }
            if (auto id = LongFromObject(data, L"userId"))
            {
                return id;
            }
            if (data.HasKey(L"profile"))
            {
                auto const p = data.GetNamedObject(L"profile");
                if (auto id = LongFromObject(p, L"userId"))
                {
                    return id;
                }
                if (auto id = LongFromObject(p, L"userid"))
                {
                    return id;
                }
            }
            if (data.HasKey(L"account"))
            {
                auto const a = data.GetNamedObject(L"account");
                if (auto id = LongFromObject(a, L"id"))
                {
                    return id;
                }
            }
        }
        if (json.HasKey(L"profile"))
        {
            auto const p = json.GetNamedObject(L"profile");
            if (auto id = LongFromObject(p, L"userId"))
            {
                return id;
            }
        }
        if (json.HasKey(L"account"))
        {
            auto const a = json.GetNamedObject(L"account");
            if (auto id = LongFromObject(a, L"id"))
            {
                return id;
            }
        }
        return std::nullopt;
    }
}
