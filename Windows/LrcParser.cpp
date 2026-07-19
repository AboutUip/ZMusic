#include "pch.h"
#include "LrcParser.h"

#include <algorithm>
#include <cwctype>
#include <regex>

using namespace winrt;

namespace winrt::ZMusic
{
    namespace
    {
        int64_t ParseFractionMs(std::wstring const& frac)
        {
            if (frac.empty())
            {
                return 0;
            }
            if (frac.size() == 1)
            {
                int64_t const v = frac[0] - L'0';
                return (v >= 0 && v <= 9) ? v * 100 : 0;
            }
            if (frac.size() == 2)
            {
                int64_t v = (frac[0] - L'0') * 10 + (frac[1] - L'0');
                if (v < 0 || v > 99)
                {
                    v = 0;
                }
                return v * 10;
            }
            int64_t v = 0;
            for (size_t i = 0; i < frac.size() && i < 3; ++i)
            {
                wchar_t const ch = frac[i];
                if (ch < L'0' || ch > L'9')
                {
                    break;
                }
                v = v * 10 + (ch - L'0');
            }
            return (std::min)(v, int64_t{ 999 });
        }
    } // namespace

    std::vector<LrcLine> LrcParser::Parse(hstring const& raw)
    {
        static std::wregex const lineRegex(
            LR"(^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)$)",
            std::regex_constants::ECMAScript);

        std::vector<LrcLine> out;
        std::wstring_view const w{ raw.c_str() };
        size_t pos = 0;
        while (pos < w.size())
        {
            size_t nl = pos;
            while (nl < w.size() && w[nl] != L'\n' && w[nl] != L'\r')
            {
                ++nl;
            }
            std::wstring line{ w.substr(pos, nl - pos) };
            while (!line.empty() && iswspace(static_cast<wint_t>(line.front())))
            {
                line.erase(line.begin());
            }
            while (!line.empty() && iswspace(static_cast<wint_t>(line.back())))
            {
                line.pop_back();
            }
            if (!line.empty())
            {
                std::wsmatch m;
                if (std::regex_match(line, m, lineRegex) && m.size() >= 5)
                {
                    int64_t const mm = std::wcstoll(m[1].str().c_str(), nullptr, 10);
                    int64_t const ss = std::wcstoll(m[2].str().c_str(), nullptr, 10);
                    int64_t const sub = ParseFractionMs(m[3].str());
                    std::wstring const text = m[4].str();
                    std::wstring textTrim = text;
                    while (!textTrim.empty() && iswspace(static_cast<wint_t>(textTrim.front())))
                    {
                        textTrim.erase(textTrim.begin());
                    }
                    while (!textTrim.empty() && iswspace(static_cast<wint_t>(textTrim.back())))
                    {
                        textTrim.pop_back();
                    }
                    if (!textTrim.empty())
                    {
                        int64_t const base = (mm * 60 + ss) * 1000 + sub;
                        LrcLine row{};
                        row.timeMs = base;
                        row.text = std::move(textTrim);
                        out.push_back(std::move(row));
                    }
                }
            }
            pos = nl;
            if (pos < w.size() && w[pos] == L'\r')
            {
                ++pos;
            }
            if (pos < w.size() && w[pos] == L'\n')
            {
                ++pos;
            }
        }
        std::sort(out.begin(), out.end(), [](LrcLine const& a, LrcLine const& b) { return a.timeMs < b.timeMs; });
        return out;
    }
}
