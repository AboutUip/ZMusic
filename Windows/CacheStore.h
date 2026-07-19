#pragma once

#include "pch.h"

namespace winrt::ZMusic
{
    /** 会话级磁盘缓存：位于 TemporaryFolder，应用启动与挂起/关闭时清空。 */
    struct CacheStore
    {
        static Windows::Foundation::IAsyncAction ClearAllAsync();

        /** 未命中时返回空字符串。 */
        static Windows::Foundation::IAsyncOperation<hstring> TryReadHttpJsonAsync(hstring const& fileBaseName);
        static Windows::Foundation::IAsyncAction WriteHttpJsonAsync(hstring const& fileBaseName, hstring const& jsonText);

        /** 若已缓存则返回文件；否则下载并写入 img 子目录后返回。 */
        static Windows::Foundation::IAsyncOperation<Windows::Storage::StorageFile> EnsureImageFileForUrlAsync(hstring const& url);
    };
}
