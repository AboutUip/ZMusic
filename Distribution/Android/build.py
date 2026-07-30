#!/usr/bin/env python3
"""Build Android release APK/AAB into repo artifacts/android/.

Examples:
  python build.py
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


DIST = Path(__file__).resolve().parent
ROOT = DIST.parents[1]
ANDROID_ROOT = ROOT / "Android"
ARTIFACTS = ROOT / "artifacts" / "android"


def main() -> int:
    print("==> Android release → artifacts/android")
    if not ANDROID_ROOT.is_dir():
        raise SystemExit(f"Android project not found: {ANDROID_ROOT}")

    if sys.platform == "win32":
        gradlew = ANDROID_ROOT / "gradlew.bat"
    else:
        gradlew = ANDROID_ROOT / "gradlew"

    if not gradlew.is_file():
        raise SystemExit(f"Gradle wrapper not found: {gradlew}")

    cmd = [str(gradlew), ":app:publishReleaseToArtifacts", "--no-daemon"]
    print("+", " ".join(cmd))
    completed = subprocess.run(cmd, cwd=ANDROID_ROOT)
    if completed.returncode != 0:
        raise SystemExit(
            f"Gradle publishReleaseToArtifacts failed with exit code {completed.returncode}"
        )

    print()
    print("Artifacts:")
    if ARTIFACTS.is_dir():
        for path in sorted(ARTIFACTS.iterdir()):
            print(f"  {path}")
    else:
        print("  (directory missing — check Gradle output)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
