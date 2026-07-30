using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace ZMusic.Converters;

/// <summary>Maps parent width to a capped stage column width (cover-aligned controls).</summary>
public sealed class StageWidthConverter : IValueConverter
{
    public static StageWidthConverter Instance { get; } = new();

    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is not double w || double.IsNaN(w) || w <= 0)
        {
            return 360.0;
        }

        // StageHost already excludes its own margin; cap the aligned column width.
        return Math.Clamp(w, 260.0, 420.0);
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        Binding.DoNothing;
}
