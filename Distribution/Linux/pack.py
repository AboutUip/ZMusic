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
import urllib.request
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
OPTIONAL_JLINK_MODULES = ("jdk.unsupported.desktop", "jdk.accessibility")

INNER_LAUNCHER = """\
#!/bin/sh
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
JAVA="$ROOT/runtime/bin/java"
if [ ! -x "$JAVA" ]; then JAVA="${JAVA_HOME:+$JAVA_HOME/bin/java}"; fi
if [ ! -x "$JAVA" ]; then JAVA="$(command -v java 2>/dev/null || true)"; fi
if [ "$1" = "--version" ]; then echo 0.1.0; exit 0; fi
if [ -z "$JAVA" ] || [ ! -x "$JAVA" ]; then
  echo "zmusic: no java runtime (expected $ROOT/runtime/bin/java)" >&2
  exit 1
fi
export LD_LIBRARY_PATH="$ROOT/lib/native${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
if [ "$1" = "--smoke" ]; then
  exec "$JAVA" -Djava.awt.headless=true -Djava.library.path="$ROOT/lib/native" -jar "$ROOT/zmusic.jar" --smoke
fi
exec "$JAVA" -Djava.library.path="$ROOT/lib/native" -jar "$ROOT/zmusic.jar" "$@"
"""


def write_unix(path: Path, text: str, mode: int = 0o644) -> None:
    data = text.replace("\r\n", "\n").replace("\r", "\n")
    if not data.endswith("\n"):
        data += "\n"
    path.write_bytes(data.encode("utf-8"))
    os.chmod(path, mode)


def copy_unix_text(src: Path, dest: Path, mode: int = 0o644) -> None:
    write_unix(dest, src.read_text(encoding="utf-8"), mode)


def run(cmd: list[str], cwd: Path | None = None) -> None:
    subprocess.check_call(cmd, cwd=str(cwd) if cwd else None)


XAIOP_JAR = LINUX / "libs" / "xaiop-0.15.1.jar"
XAIOP_URL = "https://github.com/AboutUip/XAIOP/releases/download/v0.15.1/xaiop-0.15.1.jar"
XAIOP_M2 = Path.home() / ".m2" / "repository" / "io" / "github" / "aboutuip" / "xaiop" / "0.15.1"


def ensure_xaiop_jar() -> None:
    if XAIOP_JAR.is_file() and XAIOP_JAR.stat().st_size > 100_000:
        seed_xaiop_maven_local()
        return
    XAIOP_JAR.parent.mkdir(parents=True, exist_ok=True)
    print("xaiop is not on Maven Central; fetching official JAR")
    req = urllib.request.Request(XAIOP_URL, headers={"User-Agent": "ZMusic-pack"})
    with urllib.request.urlopen(req) as resp:
        XAIOP_JAR.write_bytes(resp.read())
    if not XAIOP_JAR.is_file() or XAIOP_JAR.stat().st_size <= 100_000:
        raise SystemExit(f"xaiop download failed ({XAIOP_URL})")
    seed_xaiop_maven_local()


def seed_xaiop_maven_local() -> None:
    """Old Linux/build.gradle.kts asked Maven for this coordinate; Central 404s."""
    XAIOP_M2.mkdir(parents=True, exist_ok=True)
    dest = XAIOP_M2 / "xaiop-0.15.1.jar"
    if not dest.is_file() or dest.stat().st_size != XAIOP_JAR.stat().st_size:
        shutil.copy2(XAIOP_JAR, dest)
    pom = XAIOP_M2 / "xaiop-0.15.1.pom"
    if not pom.is_file():
        pom.write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.aboutuip</groupId>
  <artifactId>xaiop</artifactId>
  <version>0.15.1</version>
</project>
""",
            encoding="utf-8",
        )
    meta = XAIOP_M2.parent / "maven-metadata-local.xml"
    if not meta.is_file():
        meta.write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.github.aboutuip</groupId>
  <artifactId>xaiop</artifactId>
  <versioning>
    <release>0.15.1</release>
    <versions><version>0.15.1</version></versions>
  </versioning>
</metadata>
""",
            encoding="utf-8",
        )


def find_app_jar() -> Path | None:
    jars = list((LINUX / "build" / "compose" / "jars").glob("*.jar"))
    if jars:
        return jars[0]
    jars = list((LINUX / "build" / "libs").glob("*zmusic*.jar"))
    return jars[0] if jars else None


def gradle_jar() -> Path:
    ensure_xaiop_jar()
    gradlew = LINUX / "gradlew"
    cmd = [str(gradlew) if os.name != "nt" else str(LINUX / "gradlew.bat")]
    run(cmd + ["--no-daemon", "packageUberJarForCurrentOS"], cwd=LINUX)
    jar = find_app_jar()
    if jar is None:
        raise SystemExit("no application jar")
    return jar


def discover_optional_modules(java_bin: Path) -> str:
    try:
        out = subprocess.check_output(
            [str(java_bin), "--list-modules"],
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (OSError, subprocess.CalledProcessError):
        return ""
    have = {line.split("@", 1)[0].strip() for line in out.splitlines() if line.strip()}
    return ",".join(name for name in OPTIONAL_JLINK_MODULES if name in have)


def jlink_runtime(dest: Path) -> None:
    # Windows / macOS jlink 会打出本机 JRE，装进 Linux deb 无法执行。
    if sys.platform != "linux":
        print("skip jlink: not a Linux builder (refusing to embed a non-Linux JRE)")
        return
    java_home = os.environ.get("JAVA_HOME") or str(Path(shutil.which("java") or "").resolve().parents[1])
    java_bin = Path(java_home) / "bin" / "java"
    jlink = Path(java_home) / "bin" / "jlink"
    if not jlink.exists():
        which = shutil.which("jlink") or ""
        jlink = Path(which)
        if which:
            java_bin = jlink.parent / "java"
    if not jlink.exists():
        print("jlink not found; deb will need a system JRE fallback")
        return
    extra = discover_optional_modules(java_bin)
    module_sets = [JLINK_MODULES]
    if extra:
        module_sets.insert(0, f"{JLINK_MODULES},{extra}")
    last: subprocess.CompletedProcess[str] | None = None
    for modules in module_sets:
        for compress in ("--compress=zip", "--compress=2"):
            if dest.exists():
                shutil.rmtree(dest)
            cmd = [
                str(jlink),
                "--add-modules",
                modules,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                compress,
                "--output",
                str(dest),
            ]
            last = subprocess.run(cmd, capture_output=True, text=True)
            if last.returncode == 0:
                for line in (last.stderr or "").splitlines():
                    low = line.lower()
                    if "compress" in low and (
                        "过时" in line or "outdated" in low or "deprecated" in low
                    ):
                        continue
                    if line.strip():
                        print(line, file=sys.stderr)
                return
    detail = (last.stderr or last.stdout or "unknown") if last else "unknown"
    raise SystemExit(f"jlink failed: {detail.strip()}")


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
    write_unix(dest, "\n".join(lines) + "\n")


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
    copy_unix_text(DIST / "zmusic.desktop", stage / "usr" / "share" / "applications" / "zmusic.desktop")
    copy_unix_text(DIST / "debian" / "copyright", stage / "usr" / "share" / "doc" / PACKAGE / "copyright")
    copy_unix_text(DIST / "debian" / "menu", stage / "usr" / "share" / "menu" / PACKAGE)
    write_unix(
        stage / "usr" / "bin" / "zmusic",
        "#!/bin/sh\nexec /opt/zmusic/bin/zmusic \"$@\"\n",
        0o755,
    )
    bin_dir = opt / "bin"
    bin_dir.mkdir()
    runtime = opt / "runtime"
    if jar:
        shutil.copy2(jar, opt / "zmusic.jar")
        jlink_runtime(runtime)
        copy_libmpv(opt / "lib" / "native")
    wrapper = bin_dir / "zmusic"
    if jar:
        write_unix(wrapper, INNER_LAUNCHER, 0o755)
    else:
        write_unix(
            wrapper,
            "#!/bin/sh\n"
            "if [ \"$1\" = \"--version\" ]; then echo 0.1.0; exit 0; fi\n"
            "if [ \"$1\" = \"--smoke\" ]; then echo ok; exit 0; fi\n"
            "echo \"ZMusic runtime not bundled in this dry tree\" >&2\n"
            "exit 0\n",
            0o755,
        )


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
                tf.add(child, arcname=child.name, filter=_exec_filter)
        debian_bin = tdp / "debian-binary"
        debian_bin.write_bytes(b"2.0\n")
        ar_deb(out, debian_bin, control_tar, data_tar)


def _exec_filter(info: tarfile.TarInfo) -> tarfile.TarInfo:
    posix = info.name.replace("\\", "/")
    base = posix.rsplit("/", 1)[-1]
    if info.isfile() and base in {"zmusic", "java", "keytool", "jspawnhelper"}:
        info.mode = (info.mode or 0o755) | 0o111
    return info


def self_test() -> None:
    if "-jar /opt/zmusic/zmusic.jar" in INNER_LAUNCHER:
        raise SystemExit("inner launcher must not hardcode /opt jar path")
    if "$ROOT/zmusic.jar" not in INNER_LAUNCHER:
        raise SystemExit("inner launcher must resolve jar from ROOT")
    src = Path(__file__).read_text(encoding="utf-8")
    if "--compress=zip" not in src:
        raise SystemExit("jlink must prefer --compress=zip")
    with tempfile.TemporaryDirectory() as td:
        script = Path(td) / "zmusic"
        write_unix(script, INNER_LAUNCHER, 0o755)
        blob = script.read_bytes()
        if b"\r" in blob:
            raise SystemExit("launcher must be LF")
        stage = Path(td) / "stage"
        stage.mkdir()
        pack_layout(stage, None)
        usr = (stage / "usr" / "bin" / "zmusic").read_bytes()
        if not usr.startswith(b"#!/bin/sh\n"):
            raise SystemExit("usr/bin/zmusic shebang")
        if b"\r" in usr:
            raise SystemExit("usr/bin/zmusic CRLF")
    print("pack.py self-test ok")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--skip-gradle", action="store_true")
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args()
    if args.self_test:
        self_test()
        return
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    jar = None
    if args.skip_gradle:
        jar = find_app_jar()
        if jar is None:
            raise SystemExit("no application jar; run without --skip-gradle")
    else:
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
