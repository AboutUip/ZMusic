using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace ZMusic.Converters;

/// <summary>
/// values[0] == values[1] (as Int64) → Visible; optional values[2]==false forces Collapsed.
/// </summary>
public sealed class LongEqualsToVisibilityConverter : IMultiValueConverter
{
    public object Convert(object?[] values, Type targetType, object? parameter, CultureInfo culture)
    {
        if (values.Length < 2)
        {
            return Visibility.Collapsed;
        }

        if (!TryLong(values[0], out var a) || !TryLong(values[1], out var b) || a == 0 || a != b)
        {
            return Visibility.Collapsed;
        }

        return Visibility.Visible;
    }

    public object[] ConvertBack(object? value, Type[] targetTypes, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();

    private static bool TryLong(object? value, out long result)
    {
        switch (value)
        {
            case long l:
                result = l;
                return true;
            case int i:
                result = i;
                return true;
            case string s when long.TryParse(s, out var parsed):
                result = parsed;
                return true;
            default:
                result = 0;
                return false;
        }
    }
}

public sealed class LongEqualsToBoolConverter : IMultiValueConverter
{
    public object Convert(object?[] values, Type targetType, object? parameter, CultureInfo culture)
    {
        if (values.Length < 2)
        {
            return false;
        }

        return TryLong(values[0], out var a) && TryLong(values[1], out var b) && a != 0 && a == b;
    }

    public object[] ConvertBack(object? value, Type[] targetTypes, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();

    private static bool TryLong(object? value, out long result)
    {
        switch (value)
        {
            case long l:
                result = l;
                return true;
            case int i:
                result = i;
                return true;
            default:
                result = 0;
                return false;
        }
    }
}
