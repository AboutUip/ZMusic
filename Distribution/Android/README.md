# Android distribution helpers

Release binaries are copied into the shared repo folder:

```text
artifacts/
  android/   ← APK / AAB (this script)
  windows/   ← ZMusic-Setup.exe / MSI
```

`artifacts/` is gitignored. Source offer: https://github.com/AboutUip/ZMusic

## Build

Requires Python 3 and a normal Android SDK / JDK setup.

```bash
python build.py
```

Or from `Android/`:

```bash
./gradlew :app:publishReleaseToArtifacts
```

Optional release keystore: see root README.
