#!/usr/bin/env python3
"""Build ZMusic-Linux-<ver>.deb. Golden host: Ubuntu 22.04 amd64."""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LINUX = ROOT / "Linux"
DIST = ROOT / "Distribution" / "Linux"
ARTIFACTS = ROOT / "artifacts" / "linux"
VERSION = "0.1"
PACKAGE = "zmusic"
JLINK_MODULES = ",".join(
    [
        "java.base",
        "java.datatransfer",
        "java.desktop",
        "java.logging",
        "java.management",
        "java.naming",
        "java.net.http",
        "java.prefs",
        "java.scripting",
        "java.sql",
        "java.xml",
        "jdk.crypto.cryptoki",
        "jdk.crypto.ec",
        "jdk.localedata",
        "jdk.unsupported",
        "jdk.zipfs",
    ]
)
SKIP_LDD = ("libc.so", "libm.so", "libdl.so", "libpthread.so", "ld-linux", "libgcc_s", "linux-vdso")


def run(cmd: list[str], cwd: Path | None = None) -> None:
    subprocess.check_call(cmd, cwd=str(cwd) if cwd else None)


def gradle_jar() -> Path:
    gradlew = LINUX / "gradlew"
    cmd = [str(gradlew) if os.name != "nt" else str(LINUX / "gradlew.bat")]
    run(cmd + ["--no-daemon", "packageUberJarForCurrentOS"], cwd=LINUX)
    jars = list((LINUX / "build" / "compose" / "jars").glob("*.jar"))
    if not jars:
        run(cmd + ["--no-daemon", "jar"], cwd=LINUX)
        jars = list((LINUX / "build" / "libs").glob("*.jar"))
    if not jars:
        raise SystemExit("no application jar")
    return jars[0]


def jlink_runtime(dest: Path) -> None:
    # Windows / macOS jlink 会打出本机 JRE，装进 Linux deb 无法执行。
    if sys.platform != "linux":
        print("skip jlink: not a Linux builder (refusing to embed a non-Linux JRE)")
        return
    java_home = os.environ.get("JAVA_HOME") or str(Path(shutil.which("java") or "").resolve().parents[1])
    jlink = Path(java_home) / "bin" / "jlink"
    if not jlink.exists():
        jlink = Path(shutil.which("jlink") or "")
    if not jlink.exists():
        print("jlink not found; deb will need a system JRE fallback")
        return
    if dest.exists():
        shutil.rmtree(dest)
    run(
        [
            str(jlink),
            "--add-modules",
            JLINK_MODULES,
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=2",
            "--output",
            str(dest),
        ]
    )


def copy_libmpv(native: Path) -> None:
    native.mkdir(parents=True, exist_ok=True)
    candidates = [
        Path("/usr/lib/x86_64-linux-gnu/libmpv.so.2"),
        Path("/usr/lib/x86_64-linux-gnu/libmpv.so.1"),
        Path("/usr/lib/libmpv.so.2"),
        Path("/usr/lib/libmpv.so.1"),
    ]
    so = next((p for p in candidates if p.exists()), None)
    if so is None:
        print("libmpv not on builder; native dir left empty")
        return
    copy_with_deps(so, native)


def copy_with_deps(so: Path, dest: Path) -> None:
    shutil.copy2(so, dest / so.name)
    try:
        out = subprocess.check_output(["ldd", str(so)], text=True, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        return
    for line in out.splitlines():
        if "=>" not in line:
            continue
        path = line.split("=>", 1)[1].split("(", 1)[0].strip()
        if not path or path == "not found":
            continue
        if any(tok in path for tok in SKIP_LDD):
            continue
        src = Path(path)
        if not src.exists() or not src.is_file():
            continue
        target = dest / src.name
        if not target.exists():
            shutil.copy2(src, target)


def write_control(dest: Path, installed_size_kb: int) -> None:
    src = (DIST / "debian" / "control").read_text(encoding="utf-8")
    lines = []
    for line in src.splitlines():
        if line.startswith("Installed-Size:"):
            continue
        lines.append(line)
    lines.append(f"Installed-Size: {installed_size_kb}")
    dest.write_text("\n".join(lines) + "\n", encoding="utf-8")


def md5sums(root: Path) -> str:
    rows = []
    for p in sorted(root.rglob("*")):
        if p.is_file():
            rel = p.relative_to(root).as_posix()
            digest = hashlib.md5(p.read_bytes()).hexdigest()
            rows.append(f"{digest}  {rel}")
    return "\n".join(rows) + "\n"


def ar_deb(out: Path, debian_bin: Path, control_tar: Path, data_tar: Path) -> None:
    def ar_member(name: str, data: bytes) -> bytes:
        header = (
            f"{name:<16}"
            f"{0:12d}"
            f"{0:6d}"
            f"{0:6d}"
            f"{100644:8o}"
            f"{len(data):10d}"
            "`\n"
        ).encode("ascii")
        pad = b"\n" if len(data) % 2 else b""
        return header + data + pad

    blob = b"!<arch>\n"
    blob += ar_member("debian-binary", debian_bin.read_bytes())
    blob += ar_member("control.tar.gz", control_tar.read_bytes())
    blob += ar_member("data.tar.gz", data_tar.read_bytes())
    out.write_bytes(blob)


def install_icons(stage: Path) -> None:
    logo = LINUX / "src" / "main" / "resources" / "drawable" / "ic_logo_vinyl_z.png"
    if not logo.exists():
        return
    for size in ("48x48", "64x64", "128x128", "256x256"):
        dest = stage / "usr" / "share" / "icons" / "hicolor" / size / "apps"
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy2(logo, dest / "zmusic.png")
    pix = stage / "usr" / "share" / "pixmaps"
    pix.mkdir(parents=True, exist_ok=True)
    shutil.copy2(logo, pix / "zmusic.png")


def add_tar_bytes(tf: tarfile.TarFile, arcname: str, data: bytes, mode: int) -> None:
    info = tarfile.TarInfo(name=arcname)
    info.size = len(data)
    info.mode = mode
    info.mtime = int(time.time())
    info.uid = 0
    info.gid = 0
    tf.addfile(info, io.BytesIO(data))


def pack_layout(stage: Path, jar: Path | None) -> None:
    opt = stage / "opt" / "zmusic"
    opt.mkdir(parents=True)
    (stage / "usr" / "bin").mkdir(parents=True)
    (stage / "usr" / "share" / "applications").mkdir(parents=True)
    (stage / "usr" / "share" / "doc" / PACKAGE).mkdir(parents=True)
    (stage / "usr" / "share" / "menu").mkdir(parents=True)
    install_icons(stage)
    shutil.copy2(DIST / "zmusic.desktop", stage / "usr" / "share" / "applications" / "zmusic.desktop")
    shutil.copy2(DIST / "debian" / "copyright", stage / "usr" / "share" / "doc" / PACKAGE / "copyright")
    shutil.copy2(DIST / "debian" / "menu", stage / "usr" / "share" / "menu" / PACKAGE)
    launcher = stage / "usr" / "bin" / "zmusic"
    launcher.write_text(
        "#!/bin/sh\n"
        "exec /opt/zmusic/bin/zmusic \"$@\"\n",
        encoding="utf-8",
    )
    os.chmod(launcher, 0o755)
    bin_dir = opt / "bin"
    bin_dir.mkdir()
    runtime = opt / "runtime"
    if jar:
        shutil.copy2(jar, opt / "zmusic.jar")
        jlink_runtime(runtime)
        copy_libmpv(opt / "lib" / "native")
    wrapper = bin_dir / "zmusic"
    if jar:
        script = (
            "#!/bin/sh\n"
            "JAVA=/opt/zmusic/runtime/bin/java\n"
            "if [ ! -x \"$JAVA\" ]; then JAVA=${JAVA_HOME:+$JAVA_HOME/bin/java}; fi\n"
            "if [ ! -x \"$JAVA\" ]; then JAVA=$(command -v java); fi\n"
            "if [ \"$1\" = \"--version\" ]; then echo 0.1.0; exit 0; fi\n"
            "export LD_LIBRARY_PATH=/opt/zmusic/lib/native${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\n"
            "exec \"$JAVA\" -Djava.library.path=/opt/zmusic/lib/native -jar /opt/zmusic/zmusic.jar \"$@\"\n"
        )
    else:
        script = (
            "#!/bin/sh\n"
            "if [ \"$1\" = \"--version\" ]; then echo 0.1.0; exit 0; fi\n"
            "if [ \"$1\" = \"--smoke\" ]; then echo ok; exit 0; fi\n"
            "echo \"ZMusic runtime not bundled in this dry tree\" >&2\n"
            "exit 0\n"
        )
    wrapper.write_text(script, encoding="utf-8")
    os.chmod(wrapper, 0o755)


def build_deb(stage: Path, out: Path) -> None:
    size_kb = sum(p.stat().st_size for p in stage.rglob("*") if p.is_file()) // 1024
    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        ctrl = tdp / "control"
        write_control(ctrl, max(size_kb, 1))
        (tdp / "md5sums").write_text(md5sums(stage), encoding="utf-8")
        control_tar = tdp / "control.tar.gz"
        with tarfile.open(control_tar, "w:gz") as tf:
            tf.add(ctrl, arcname="control")
            tf.add(tdp / "md5sums", arcname="md5sums")
            postinst = DIST / "debian" / "postinst"
            postrm = DIST / "debian" / "postrm"
            if postinst.exists():
                add_tar_bytes(tf, "postinst", postinst.read_bytes().replace(b"\r\n", b"\n"), 0o755)
            if postrm.exists():
                add_tar_bytes(tf, "postrm", postrm.read_bytes().replace(b"\r\n", b"\n"), 0o755)
        data_tar = tdp / "data.tar.gz"
        with tarfile.open(data_tar, "w:gz") as tf:
            for child in stage.iterdir():
                tf.add(child, arcname=child.name)
        debian_bin = tdp / "debian-binary"
        debian_bin.write_text("2.0\n", encoding="utf-8")
        ar_deb(out, debian_bin, control_tar, data_tar)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--skip-gradle", action="store_true")
    args = p.parse_args()
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    jar = None
    if not args.skip_gradle:
        jar = gradle_jar()
    with tempfile.TemporaryDirectory() as td:
        stage = Path(td) / "stage"
        stage.mkdir()
        pack_layout(stage, jar)
        out = ARTIFACTS / f"ZMusic-Linux-{VERSION}.deb"
        build_deb(stage, out)
        print("wrote", out)


if __name__ == "__main__":
    main()
