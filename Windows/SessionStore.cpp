#include "pch.h"
#include "SessionStore.h"

using namespace winrt;
using namespace winrt::Windows::Storage;
using namespace winrt::Windows::Foundation;

namespace winrt::ZMusic
{
    static hstring const kFileName{ L"ncm_session.json" };

    static hstring JsonStr(Windows::Data::Json::JsonObject const& o, hstring const& key)
    {
        return o.HasKey(key) ? o.GetNamedString(key) : hstring{ L"" };
    }

    static bool JsonBool(Windows::Data::Json::JsonObject const& o, hstring const& key, bool def)
    {
        return o.HasKey(key) ? o.GetNamedBoolean(key) : def;
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

    SessionData SessionStore::ParseFromJson(hstring const& text)
    {
        SessionData empty{};
        if (text.empty())
        {
            return empty;
        }
        try
        {
            auto json = Windows::Data::Json::JsonObject::Parse(text);
            auto cookie = Trim(JsonStr(json, L"cookie"));
            if (cookie.empty())
            {
                return empty;
            }
            SessionData d;
            d.Cookie = cookie;
            d.DisplayLabel = JsonStr(json, L"label");
            d.IsGuest = JsonBool(json, L"guest", false);
            return d;
        }
        catch (...)
        {
            return empty;
        }
    }

    IAsyncOperation<hstring> SessionStore::LoadRawAsync()
    {
        auto folder = ApplicationData::Current().LocalFolder();
        auto item = co_await folder.TryGetItemAsync(kFileName);
        if (!item)
        {
            co_return hstring{};
        }
        auto file = item.as<StorageFile>();
        co_return co_await FileIO::ReadTextAsync(file);
    }

    IAsyncAction SessionStore::SaveAsync(SessionData const& data)
    {
        Windows::Data::Json::JsonObject json;
        json.Insert(L"cookie", Windows::Data::Json::JsonValue::CreateStringValue(data.Cookie));
        json.Insert(L"label", Windows::Data::Json::JsonValue::CreateStringValue(data.DisplayLabel));
        json.Insert(L"guest", Windows::Data::Json::JsonValue::CreateBooleanValue(data.IsGuest));
        hstring text = json.Stringify();

        auto folder = ApplicationData::Current().LocalFolder();
        auto file = co_await folder.CreateFileAsync(kFileName, CreationCollisionOption::ReplaceExisting);
        co_await FileIO::WriteTextAsync(file, text);
    }

    IAsyncAction SessionStore::ClearAsync()
    {
        auto folder = ApplicationData::Current().LocalFolder();
        auto item = co_await folder.TryGetItemAsync(kFileName);
        if (item)
        {
            co_await item.DeleteAsync();
        }
    }
}
