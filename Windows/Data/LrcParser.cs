using System.Text.RegularExpressions;

namespace ZMusic.Data;

/// <summary>
/// Netease-style [mm:ss.xx] / [mm:ss] LRC parser (Windows port of Android LrcParser).
/// </summary>
public static partial class LrcParser
{
    [GeneratedRegex(@"^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)$", RegexOptions.Compiled)]
    private static partial Regex LineRegex();

    public static IReadOnlyList<LrcLine> Parse(string raw)
    {
        var outList = new List<LrcLine>();
        foreach (var line in raw.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries))
        {
            var t = line.Trim();
            if (t.Length == 0)
            {
                continue;
            }

            var m = LineRegex().Match(t);
            if (!m.Success)
            {
                continue;
            }

            if (!long.TryParse(m.Groups[1].Value, out var mm) ||
                !long.TryParse(m.Groups[2].Value, out var ss))
            {
                continue;
            }

            var text = SanitizeLyricText(m.Groups[4].Value);
            if (text is null)
            {
                continue;
            }

            var subMs = ParseFractionMs(m.Groups[3].Value);
            outList.Add(new LrcLine((mm * 60L + ss) * 1000L + subMs, text));
        }

        outList.Sort((a, b) => a.TimeMs.CompareTo(b.TimeMs));
        return outList;
    }

    public static string? SanitizeLyricText(string raw)
    {
        var t = raw.Trim()
            .Replace('\u00A0', ' ');
        t = WhitespaceRegex().Replace(t, " ").Trim();
        if (t.Length == 0)
        {
            return null;
        }

        if (t.All(ch => char.IsWhiteSpace(ch) || "·.•…-_—~/|".Contains(ch)))
        {
            return null;
        }

        return t;
    }

    private static long ParseFractionMs(string frac)
    {
        if (string.IsNullOrEmpty(frac))
        {
            return 0;
        }

        return frac.Length switch
        {
            1 => long.TryParse(frac, out var a) ? a * 100L : 0L,
            2 => long.TryParse(frac, out var b) ? b * 10L : 0L,
            _ => long.TryParse(frac.Length > 3 ? frac[..3] : frac, out var c)
                ? Math.Min(c, 999L)
                : 0L,
        };
    }

    [GeneratedRegex(@"\s+", RegexOptions.Compiled)]
    private static partial Regex WhitespaceRegex();
}
