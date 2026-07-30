using System.IO;
using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;

namespace ZMusic.Views.Controls;

public class UrlImage : Image
{
    private static readonly HttpClient Http = CreateHttp();
    private CancellationTokenSource? _cts;

    public static readonly DependencyProperty UrlProperty = DependencyProperty.Register(
        nameof(Url),
        typeof(string),
        typeof(UrlImage),
        new PropertyMetadata(null, OnUrlChanged));

    public static readonly DependencyProperty DecodePixelWidthProperty = DependencyProperty.Register(
        nameof(DecodePixelWidth),
        typeof(int),
        typeof(UrlImage),
        new PropertyMetadata(0, OnUrlChanged));

    public string? Url
    {
        get => (string?)GetValue(UrlProperty);
        set => SetValue(UrlProperty, value);
    }

    /// <summary>
    /// Preferred decode width in pixels. When &lt;= 0, falls back to layout-based sizing.
    /// </summary>
    public int DecodePixelWidth
    {
        get => (int)GetValue(DecodePixelWidthProperty);
        set => SetValue(DecodePixelWidthProperty, value);
    }

    private static void OnUrlChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is UrlImage image)
        {
            _ = image.LoadAsync(image.Url);
        }
    }

    private async Task LoadAsync(string? url)
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = new CancellationTokenSource();
        var ct = _cts.Token;

        if (string.IsNullOrWhiteSpace(url))
        {
            Source = null;
            return;
        }

        var normalized = url.StartsWith("http://", StringComparison.OrdinalIgnoreCase)
            ? "https://" + url["http://".Length..]
            : url;

        try
        {
            using var response = await Http.GetAsync(normalized, ct).ConfigureAwait(true);
            response.EnsureSuccessStatusCode();
            var bytes = await response.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(true);
            if (ct.IsCancellationRequested)
            {
                return;
            }

            var decodeWidth = DecodePixelWidth > 0
                ? DecodePixelWidth
                : (int)Math.Max(ActualWidth > 0 ? ActualWidth * 2 : 120, 64);

            using var stream = new MemoryStream(bytes);
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.CreateOptions = BitmapCreateOptions.IgnoreColorProfile;
            image.DecodePixelWidth = decodeWidth;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            Source = image;
        }
        catch (OperationCanceledException)
        {
        }
        catch
        {
            Source = null;
        }
    }

    private static HttpClient CreateHttp()
    {
        var http = new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
        http.DefaultRequestHeaders.TryAddWithoutValidation(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        http.DefaultRequestHeaders.TryAddWithoutValidation("Referer", "https://music.163.com/");
        return http;
    }
}
