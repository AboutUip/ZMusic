#include "pch.h"
#include "CacheStore.h"
#include "CryptoMd5.h"

using namespace winrt;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage;
using namespace winrt::Windows::Storage::Streams;
using namespace winrt::Windows::Web::Http;

namespace winrt::ZMusic
{
    static constexpr wchar_t kRootName[] = L"ZMusicSessionCache";
    static constexpr wchar_t kHttpDir[] = L"http";
    static constexpr wchar_t kImgDir[] = L"img";

    static IAsyncOperation<StorageFolder> SessionRootAsync()
    {
        StorageFolder const temp = ApplicationData::Current().TemporaryFolder();
        co_return co_await temp.CreateFolderAsync(kRootName, CreationCollisionOption::OpenIfExists);
    }

    static IAsyncOperation<StorageFolder> HttpFolderAsync()
    {
        StorageFolder const root = co_await SessionRootAsync();
        co_return co_await root.CreateFolderAsync(kHttpDir, CreationCollisionOption::OpenIfExists);
    }

    static IAsyncOperation<StorageFolder> ImgFolderAsync()
    {
        StorageFolder const root = co_await SessionRootAsync();
        co_return co_await root.CreateFolderAsync(kImgDir, CreationCollisionOption::OpenIfExists);
    }

    IAsyncAction CacheStore::ClearAllAsync()
    {
        StorageFolder const temp = ApplicationData::Current().TemporaryFolder();
        IStorageItem const item = co_await temp.TryGetItemAsync(kRootName);
        if (item)
        {
            co_await item.DeleteAsync(StorageDeleteOption::PermanentDelete);
        }
    }

    IAsyncOperation<hstring> CacheStore::TryReadHttpJsonAsync(hstring const& fileBaseName)
    {
        StorageFolder const folder = co_await HttpFolderAsync();
        IStorageItem const it = co_await folder.TryGetItemAsync(fileBaseName);
        if (!it)
        {
            co_return hstring{};
        }
        StorageFile const file = it.as<StorageFile>();
        hstring const text = co_await FileIO::ReadTextAsync(file);
        co_return text;
    }

    IAsyncAction CacheStore::WriteHttpJsonAsync(hstring const& fileBaseName, hstring const& jsonText)
    {
        StorageFolder const folder = co_await HttpFolderAsync();
        StorageFile const file =
            co_await folder.CreateFileAsync(fileBaseName, CreationCollisionOption::ReplaceExisting);
        co_await FileIO::WriteTextAsync(file, jsonText);
    }

    IAsyncOperation<StorageFile> CacheStore::EnsureImageFileForUrlAsync(hstring const& url)
    {
        if (url.empty())
        {
            throw hresult_invalid_argument();
        }
        StorageFolder const folder = co_await ImgFolderAsync();
        hstring const name = Md5HexUtf8String(url) + L".bin";
        IStorageItem const existing = co_await folder.TryGetItemAsync(name);
        if (existing)
        {
            co_return existing.as<StorageFile>();
        }

        HttpClient http{};
        HttpResponseMessage resp = co_await http.GetAsync(Uri{ url }, HttpCompletionOption::ResponseContentRead);
        resp.EnsureSuccessStatusCode();
        IBuffer const buf = co_await resp.Content().ReadAsBufferAsync();
        if (buf.Length() > 6 * 1024 * 1024U)
        {
            throw hresult_invalid_argument();
        }
        StorageFile const file = co_await folder.CreateFileAsync(name, CreationCollisionOption::ReplaceExisting);
        co_await FileIO::WriteBufferAsync(file, buf);
        co_return file;
    }
}
