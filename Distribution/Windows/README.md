# Windows distribution

Product packaging for ZMusic Windows. Not part of the main client business logic.

## Quick build

Requires Python 3 and the .NET 9 SDK.

```bash
python build.py
```

Produces **three** packages under `../../artifacts/windows/`:

| File | Audience |
|------|----------|
| `ZMusic-Setup.exe` | Custom immersive UI (embeds silent MSI) |
| `ZMusic-Silent.msi` | Silent / IT (`msiexec /qn`) |
| `ZMusic-UI.msi` | Standard Windows Installer wizard |

Full documentation: [`docs/WINDOWS-DISTRIBUTION.md`](../../docs/WINDOWS-DISTRIBUTION.md)
