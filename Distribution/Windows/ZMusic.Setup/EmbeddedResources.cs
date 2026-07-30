using System.IO;
using System.Reflection;

namespace ZMusic.Setup;

internal static class EmbeddedResources
{
    public static string ReadText(string logicalName)
    {
        var asm = Assembly.GetExecutingAssembly();
        using var stream = asm.GetManifestResourceStream(logicalName)
            ?? throw new InvalidOperationException($"Missing embedded resource: {logicalName}");
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }

    public static bool TryOpen(string logicalName, out Stream? stream)
    {
        stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(logicalName);
        return stream is not null;
    }

    public static void ExtractToFile(string logicalName, string destinationPath)
    {
        if (!TryOpen(logicalName, out var stream) || stream is null)
        {
            throw new InvalidOperationException(
                "Setup was built without an embedded MSI. Run Distribution/Windows/build.py.");
        }

        using (stream)
        using (var file = File.Create(destinationPath))
        {
            stream.CopyTo(file);
        }
    }
}
