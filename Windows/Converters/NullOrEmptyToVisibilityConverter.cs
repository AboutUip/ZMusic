using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace ZMusic.Converters;

public sealed class NullOrEmptyToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var empty = value is null || (value is string s && string.IsNullOrWhiteSpace(s));
        var invert = parameter is string p && p.Equals("Invert", StringComparison.OrdinalIgnoreCase);
        var visible = invert ? empty : !empty;
        return visible ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        Binding.DoNothing;
}
