#include "pch.h"
#include "CryptoMd5.h"

#include <cwctype>

using namespace winrt;
using namespace winrt::Windows::Security::Cryptography;
using namespace winrt::Windows::Security::Cryptography::Core;
using namespace winrt::Windows::Storage::Streams;

namespace winrt::ZMusic
{
    hstring Md5HexUtf8String(hstring const& text)
    {
        IBuffer utf8 = CryptographicBuffer::ConvertStringToBinary(text, BinaryStringEncoding::Utf8);
        HashAlgorithmProvider alg = HashAlgorithmProvider::OpenAlgorithm(HashAlgorithmNames::Md5());
        IBuffer hash = alg.HashData(utf8);
        hstring hex = CryptographicBuffer::EncodeToHexString(hash);
        std::wstring w{ hex.c_str() };
        for (wchar_t& ch : w)
        {
            ch = static_cast<wchar_t>(std::towlower(ch));
        }
        return hstring{ w };
    }
}
