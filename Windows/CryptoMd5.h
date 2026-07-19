#pragma once

#include "pch.h"

namespace winrt::ZMusic
{
    /** 与 Android 侧一致：对 UTF-8 字节序列做 MD5，输出小写十六进制。 */
    hstring Md5HexUtf8String(hstring const& text);
}
