#!/usr/bin/env python3
"""Build ZMusic Windows distribution (3 artifacts).

Outputs under artifacts/windows/:
  1. ZMusic-Setup.exe   — immersive custom UI (embeds Silent MSI)
  2. ZMusic-Silent.msi  — no MSI UI; msiexec /qn or IT
  3. ZMusic-UI.msi      — standard Windows Installer wizard UI

Examples:
  python build.py
  python build.py --skip-sign
  python build.py --configuration Release --runtime win-x64
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path


DIST = Path(__file__).resolve().parent
ROOT = DIST.parents[1]
ARTIFACTS = ROOT / "artifacts" / "windows"
STAGE = DIST / "obj" / "stage"
APP_PUBLISH = STAGE / "app"
PAYLOAD_DIR = DIST / "ZMusic.Setup" / "Payload"
SIGNING_PROPS = DIST / "signing.properties"
MSI_DIR = DIST / "ZMusic.Msi"


def step(message: str) -> None:
    print(f"\n==> {message}")


def run(cmd: list[str], *, cwd: Path | None = None) -> None:
    print("+", " ".join(cmd))
    completed = subprocess.run(cmd, cwd=cwd)
    if completed.returncode != 0:
        raise SystemExit(f"Command failed ({completed.returncode}): {' '.join(cmd)}")


def read_signing_property(name: str) -> str | None:
    if not SIGNING_PROPS.is_file():
        return None
    prefix = f"{name}="
    for raw in SIGNING_PROPS.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith(prefix):
            return line[len(prefix) :].strip() or None
    return None


def which(name: str) -> str | None:
    return shutil.which(name)


def sign(path: Path, *, skip_sign: bool) -> None:
    if skip_sign:
        return
    pfx = read_signing_property("pfxFile")
    password = read_signing_property("pfxPassword")
    timestamp = read_signing_property("timestampUrl") or "http://timestamp.digicert.com"
    if not pfx:
        print("Signing skipped (no pfxFile in signing.properties).")
        return
    pfx_path = Path(pfx)
    if not pfx_path.is_file():
        pfx_path = (DIST / pfx).resolve()
    if not pfx_path.is_file():
        print(f"Signing skipped (pfx not found: {pfx}).")
        return
    signtool = which("signtool") or which("signtool.exe")
    if not signtool:
        print("signtool not found on PATH; signing skipped.")
        return
    cmd = [
        signtool,
        "sign",
        "/fd",
        "SHA256",
        "/td",
        "SHA256",
        "/tr",
        timestamp,
        "/f",
        str(pfx_path),
    ]
    if password:
        cmd.extend(["/p", password])
    cmd.append(str(path))
    run(cmd)


def rm_tree(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)


def dir_arg(path: Path) -> str:
    return str(path).rstrip("\\/") + ("\\" if sys.platform == "win32" else "/")


def build_msi(wixproj: Path, out_dir: Path, *, configuration: str, app_publish_dir: str) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    run(
        [
            "dotnet",
            "build",
            str(wixproj),
            "-c",
            configuration,
            f"-p:AppPublishDir={app_publish_dir}",
            f"-p:OutputPath={dir_arg(out_dir)}",
        ]
    )
    msi_files = sorted(out_dir.rglob("*.msi"))
    if not msi_files:
        raise SystemExit(f"MSI output not found under {out_dir}")
    return msi_files[0]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build ZMusic Windows Setup.exe + Silent MSI + UI MSI"
    )
    parser.add_argument(
        "--configuration",
        "-c",
        choices=("Debug", "Release"),
        default="Release",
    )
    parser.add_argument("--runtime", "-r", default="win-x64")
    parser.add_argument("--skip-sign", action="store_true")
    parser.add_argument(
        "--skip-setup",
        action="store_true",
        help="Build MSI packages only (skip Setup.exe)",
    )
    args = parser.parse_args()

    if which("dotnet") is None:
        raise SystemExit("dotnet not found on PATH")

    step("Clean stage / artifacts")
    rm_tree(STAGE)
    rm_tree(ARTIFACTS)
    APP_PUBLISH.mkdir(parents=True, exist_ok=True)
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    PAYLOAD_DIR.mkdir(parents=True, exist_ok=True)

    shutil.copy2(ROOT / "LICENSE", DIST / "assets" / "LICENSE")

    step(f"Publish ZMusic client (self-contained {args.runtime})")
    run(
        [
            "dotnet",
            "publish",
            str(ROOT / "Windows" / "ZMusic.csproj"),
            "-c",
            args.configuration,
            "-r",
            args.runtime,
            "--self-contained",
            "true",
            "-p:PublishSingleFile=false",
            "-p:DebugType=None",
            "-p:DebugSymbols=false",
            "-o",
            str(APP_PUBLISH),
        ]
    )

    step("Publish Uninstall.exe (single-file)")
    uninstall_out = STAGE / "uninstall"
    run(
        [
            "dotnet",
            "publish",
            str(DIST / "ZMusic.Uninstall" / "ZMusic.Uninstall.csproj"),
            "-c",
            args.configuration,
            "-r",
            args.runtime,
            "--self-contained",
            "true",
            "-p:PublishSingleFile=true",
            "-p:IncludeNativeLibrariesForSelfExtract=true",
            "-p:DebugType=None",
            "-p:DebugSymbols=false",
            "-o",
            str(uninstall_out),
        ]
    )
    shutil.copy2(uninstall_out / "Uninstall.exe", APP_PUBLISH / "Uninstall.exe")
    sign(APP_PUBLISH / "ZMusic.exe", skip_sign=args.skip_sign)
    sign(APP_PUBLISH / "Uninstall.exe", skip_sign=args.skip_sign)

    app_publish_dir = dir_arg(APP_PUBLISH)

    step("Build Silent MSI (no UI)")
    silent_msi = build_msi(
        MSI_DIR / "ZMusic.Msi.Silent.wixproj",
        STAGE / "msi-silent",
        configuration=args.configuration,
        app_publish_dir=app_publish_dir,
    )
    shutil.copy2(silent_msi, ARTIFACTS / "ZMusic-Silent.msi")
    # Setup embeds the silent package and drives UI itself.
    shutil.copy2(silent_msi, PAYLOAD_DIR / "ZMusic.msi")
    sign(ARTIFACTS / "ZMusic-Silent.msi", skip_sign=args.skip_sign)

    step("Build UI MSI (standard Windows Installer wizard)")
    ui_msi = build_msi(
        MSI_DIR / "ZMusic.Msi.UI.wixproj",
        STAGE / "msi-ui",
        configuration=args.configuration,
        app_publish_dir=app_publish_dir,
    )
    shutil.copy2(ui_msi, ARTIFACTS / "ZMusic-UI.msi")
    sign(ARTIFACTS / "ZMusic-UI.msi", skip_sign=args.skip_sign)

    if not args.skip_setup:
        step("Build Setup.exe (embeds Silent MSI)")
        setup_out = STAGE / "setup"
        run(
            [
                "dotnet",
                "publish",
                str(DIST / "ZMusic.Setup" / "ZMusic.Setup.csproj"),
                "-c",
                args.configuration,
                "-r",
                args.runtime,
                "--self-contained",
                "true",
                "-p:PublishSingleFile=true",
                "-p:IncludeNativeLibrariesForSelfExtract=true",
                "-p:DebugType=None",
                "-p:DebugSymbols=false",
                "-o",
                str(setup_out),
            ]
        )
        setup_exe = setup_out / "ZMusic-Setup.exe"
        if not setup_exe.is_file():
            raise SystemExit(f"Setup output missing: {setup_exe}")
        shutil.copy2(setup_exe, ARTIFACTS / "ZMusic-Setup.exe")
        sign(ARTIFACTS / "ZMusic-Setup.exe", skip_sign=args.skip_sign)

    step("Done — 3 distribution packages")
    print("Artifacts:")
    for path in sorted(ARTIFACTS.iterdir()):
        size_mb = path.stat().st_size / (1024 * 1024)
        print(f"  {path.name:22} {size_mb:8.1f} MB  →  {path}")
    print()
    print("1) ZMusic-Setup.exe   immersive custom UI (recommended for most users)")
    print("2) ZMusic-Silent.msi  silent / IT  →  msiexec /i ZMusic-Silent.msi /qn")
    print("3) ZMusic-UI.msi      standard MSI wizard UI")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
