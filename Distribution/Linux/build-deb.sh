#!/usr/bin/env bash
# Build artifacts/linux/ZMusic-Linux-0.1.deb from a git checkout.
# Intended hosts: Ubuntu 22.04/24.04, Debian, Kali (amd64).
#
#   git clone <repo> && cd ZMusic
#   bash Distribution/Linux/build-deb.sh --install-deps
#   sudo apt install ./artifacts/linux/ZMusic-Linux-0.1.deb
#
# Lives next to pack.py / debian/ / zmusic.desktop. Do not put a copy at repo root.
#
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/../.." && pwd)"
LINUX="${ROOT}/Linux"
PACK="${SCRIPT_DIR}/pack.py"
OUT_DEB="${ROOT}/artifacts/linux/ZMusic-Linux-0.1.deb"
TOOLS_JDK="${ROOT}/.tools/jdk-21"
ADOPTIUM_JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
XAIOP_JAR="${LINUX}/libs/xaiop-0.15.1.jar"
XAIOP_URL="https://github.com/AboutUip/XAIOP/releases/download/v0.15.1/xaiop-0.15.1.jar"

INSTALL_DEPS=0
SKIP_TESTS=1
SKIP_GRADLE=0
ALLOW_FOREIGN=0
DOWNLOAD_JDK=1

usage() {
  cat <<'EOF'
Usage: bash Distribution/Linux/build-deb.sh [options]

  --install-deps   apt-get the build packages (python3, libmpv, curl, …).
                   Also tries openjdk-21 if no JDK 21 is found.
  --test           Run Linux/gradlew test (uses xvfb-run when DISPLAY is empty).
  --skip-tests     Do not run unit tests (default).
  --skip-gradle    Reuse Linux/build/compose/jars (skip compile).
  --no-download-jdk
                   Never fetch Temurin 21; fail if no local JDK 21 exists.
  --allow-foreign  Continue on non-amd64 (deb is still Architecture: amd64).
  -h, --help       Show this help.

Requires: bash, python3, JDK 21 (jlink), network for first Gradle fetch.
Does not need a distro JRE in the .deb: jlink ships a Linux runtime.
EOF
}

log() { printf -- '[zmusic-deb] %s\n' "$*"; }
die() { printf -- '[zmusic-deb] error: %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --install-deps) INSTALL_DEPS=1 ;;
    --test) SKIP_TESTS=0 ;;
    --skip-tests) SKIP_TESTS=1 ;;
    --skip-gradle) SKIP_GRADLE=1 ;;
    --no-download-jdk) DOWNLOAD_JDK=0 ;;
    --allow-foreign) ALLOW_FOREIGN=1 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1 (see --help)" ;;
  esac
  shift
done

need_file() { [ -f "$1" ] || die "missing $1 (run from a full git clone)"; }
need_cmd() { command -v "$1" >/dev/null 2>&1 || die "need command: $1"; }

java_major() {
  local bin="$1" ver
  [ -x "$bin" ] || return 1
  ver="$("$bin" -version 2>&1 || true)"
  printf '%s\n' "$ver" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | sed -n '1p'
}

looks_like_jdk21() {
  local home="$1"
  [ -n "$home" ] && [ -x "${home}/bin/java" ] && [ -x "${home}/bin/jlink" ] || return 1
  [ "$(java_major "${home}/bin/java")" = "21" ]
}

pick_jvm_dir() {
  local d
  if [ -n "${JAVA_HOME:-}" ] && looks_like_jdk21 "$JAVA_HOME"; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi
  for d in \
    "$TOOLS_JDK" \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/temurin-21-jdk-amd64 \
    /usr/lib/jvm/temurin-21-jdk \
    /usr/lib/jvm/jdk-21 \
    /opt/java/openjdk
  do
    [ -n "$d" ] || continue
    if looks_like_jdk21 "$d"; then
      printf '%s\n' "$d"
      return 0
    fi
  done
  # glob last: any jvm tree whose java reports 21 and has jlink
  if [ -d /usr/lib/jvm ]; then
    for d in /usr/lib/jvm/*; do
      if looks_like_jdk21 "$d"; then
        printf '%s\n' "$d"
        return 0
      fi
    done
  fi
  if [ -d "${HOME}/.sdkman/candidates/java" ]; then
    for d in "${HOME}/.sdkman/candidates/java"/21*; do
      if looks_like_jdk21 "$d"; then
        printf '%s\n' "$d"
        return 0
      fi
    done
  fi
  return 1
}

have_apt() { command -v apt-get >/dev/null 2>&1; }

pkg_showable() {
  command -v apt-cache >/dev/null 2>&1 || return 1
  apt-cache show "$1" >/dev/null 2>&1
}

first_pkg() {
  local p
  for p in "$@"; do
    if pkg_showable "$p"; then
      printf '%s\n' "$p"
      return 0
    fi
  done
  return 1
}

apt_runner() {
  if [ "$(id -u)" -eq 0 ]; then
    printf '%s\n' apt-get
    return
  fi
  command -v sudo >/dev/null 2>&1 || die "need root or sudo for --install-deps"
  printf '%s\n' sudo
}

install_apt_packages() {
  have_apt || die "--install-deps needs apt-get (Debian / Ubuntu / Kali)"
  local sudo_cmd pkgs mpv xvfb jdk
  sudo_cmd="$(apt_runner)"
  log "apt update"
  DEBIAN_FRONTEND=noninteractive "$sudo_cmd" apt-get update -y
  pkgs=(python3 ca-certificates tar gzip unzip)
  if pkg_showable curl; then pkgs+=(curl); elif pkg_showable wget; then pkgs+=(wget); fi
  pkgs+=(desktop-file-utils)
  if mpv="$(first_pkg libmpv2 libmpv1)"; then
    pkgs+=("$mpv")
  else
    log "warning: no libmpv2/libmpv1 in apt cache; playback in the deb will use the fake engine"
  fi
  if xvfb="$(first_pkg xvfb)"; then pkgs+=("$xvfb"); fi
  if ! pick_jvm_dir >/dev/null; then
    if jdk="$(first_pkg openjdk-21-jdk-headless openjdk-21-jdk temurin-21-jdk)"; then
      pkgs+=("$jdk")
    fi
  fi
  log "apt install: ${pkgs[*]}"
  DEBIAN_FRONTEND=noninteractive "$sudo_cmd" apt-get install -y --no-install-recommends "${pkgs[@]}"
}

fetch_url() {
  local url="$1" dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 2 -o "$dest" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$dest" "$url"
  else
    die "need curl or wget to download $url"
  fi
}

seed_xaiop_maven_local() {
  local dest="${HOME}/.m2/repository/io/github/aboutuip/xaiop/0.15.1"
  mkdir -p "$dest"
  cp -f "$XAIOP_JAR" "${dest}/xaiop-0.15.1.jar"
  if [ ! -f "${dest}/xaiop-0.15.1.pom" ]; then
    cat > "${dest}/xaiop-0.15.1.pom" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.aboutuip</groupId>
  <artifactId>xaiop</artifactId>
  <version>0.15.1</version>
</project>
EOF
  fi
}

ensure_xaiop_jar() {
  local bytes=0
  if [ -f "$XAIOP_JAR" ]; then
    bytes="$(wc -c < "$XAIOP_JAR" | tr -d ' ')"
  fi
  if [ "${bytes:-0}" -le 100000 ]; then
    mkdir -p "$(dirname "$XAIOP_JAR")"
    log "xaiop is not on Maven Central; fetching official JAR"
    fetch_url "$XAIOP_URL" "$XAIOP_JAR"
    bytes="$(wc -c < "$XAIOP_JAR" | tr -d ' ')"
    [ "${bytes:-0}" -gt 100000 ] || die "xaiop download failed ($XAIOP_URL)"
  fi
  seed_xaiop_maven_local
}

install_temurin_local() {
  [ "$DOWNLOAD_JDK" = 1 ] || die "no JDK 21 (jlink) found; install openjdk-21-jdk or drop --no-download-jdk"
  command -v tar >/dev/null 2>&1 || die "need tar to unpack Temurin 21"
  local tmp tarball extracted
  tmp="$(mktemp -d)"
  tarball="${tmp}/jdk21.tar.gz"
  log "downloading Temurin 21 (linux x64) into .tools/jdk-21"
  fetch_url "$ADOPTIUM_JDK_URL" "$tarball"
  tar -xzf "$tarball" -C "$tmp"
  extracted="$(find "$tmp" -maxdepth 1 -type d -name 'jdk-21*' | head -n 1 || true)"
  [ -n "$extracted" ] || die "Temurin archive layout unexpected"
  rm -rf "$TOOLS_JDK"
  mkdir -p "$(dirname "$TOOLS_JDK")"
  mv "$extracted" "$TOOLS_JDK"
  rm -rf "$tmp"
  looks_like_jdk21 "$TOOLS_JDK" || die "downloaded JDK is not 21 with jlink"
}

host_check() {
  local os arch
  os="$(uname -s 2>/dev/null || echo unknown)"
  arch="$(uname -m 2>/dev/null || echo unknown)"
  [ "$os" = Linux ] || die "this script builds a Linux amd64 .deb; run it on Linux (got ${os})"
  case "$arch" in
    x86_64|amd64) ;;
    *)
      if [ "$ALLOW_FOREIGN" = 1 ]; then
        log "warning: host is ${arch}; the package is still Architecture: amd64"
      else
        die "need amd64/x86_64 (host is ${arch}). Rebuild on an amd64 machine, or pass --allow-foreign"
      fi
      ;;
  esac
}

verify_deb() {
  [ -f "$OUT_DEB" ] || die "pack.py did not write $OUT_DEB"
  local bytes
  bytes="$(wc -c < "$OUT_DEB" | tr -d ' ')"
  [ "${bytes:-0}" -gt 1000000 ] || die "deb is too small (${bytes} bytes); jar/runtime likely missing"
  if command -v dpkg-deb >/dev/null 2>&1; then
    dpkg-deb -I "$OUT_DEB" | grep -q 'Package: zmusic' || die "deb control missing Package: zmusic"
    dpkg-deb -I "$OUT_DEB" | grep -q 'Architecture: amd64' || die "deb is not amd64"
    local extract
    extract="$(mktemp -d)"
    dpkg-deb -x "$OUT_DEB" "$extract"
    [ -x "${extract}/usr/bin/zmusic" ] || die "deb missing /usr/bin/zmusic"
    [ -f "${extract}/opt/zmusic/zmusic.jar" ] || die "deb missing application jar"
    [ -f "${extract}/usr/share/applications/zmusic.desktop" ] || die "deb missing desktop launcher"
    if [ -x "${extract}/opt/zmusic/runtime/bin/java" ]; then
      if command -v file >/dev/null 2>&1; then
        file "${extract}/opt/zmusic/runtime/bin/java" | grep -qi 'ELF' || die "bundled java is not ELF (wrong builder OS?)"
      fi
      "${extract}/opt/zmusic/bin/zmusic" --version | grep -q '0.1' || die "zmusic --version failed"
      grep -F '$ROOT/zmusic.jar' "${extract}/opt/zmusic/bin/zmusic" >/dev/null \
        || die "launcher does not resolve jar from ROOT"
      if grep -F -- '-jar /opt/zmusic/zmusic.jar' "${extract}/opt/zmusic/bin/zmusic" >/dev/null; then
        die "launcher still hardcodes /opt/zmusic/zmusic.jar"
      fi
      smoke_out="$("${extract}/opt/zmusic/bin/zmusic" --smoke)" || die "zmusic --smoke failed"
      [ "$smoke_out" = "ok" ] || die "zmusic --smoke expected ok, got: ${smoke_out}"
    else
      log "warning: no jlink runtime in the deb; Kali must provide Java 21 to launch"
    fi
    rm -rf "$extract"
  else
    log "warning: dpkg-deb not found; skipped install-gate checks"
  fi
  if command -v desktop-file-validate >/dev/null 2>&1; then
    desktop-file-validate "${SCRIPT_DIR}/zmusic.desktop"
  fi
}

# --- main ---
need_file "$PACK"
need_file "${LINUX}/gradlew"
need_file "${LINUX}/build.gradle.kts"
host_check

if [ "$INSTALL_DEPS" = 1 ]; then
  install_apt_packages
fi

need_cmd python3
need_cmd uname

JAVA_HOME_RESOLVED="$(pick_jvm_dir || true)"
if [ -z "${JAVA_HOME_RESOLVED}" ]; then
  install_temurin_local
  JAVA_HOME_RESOLVED="$TOOLS_JDK"
fi
looks_like_jdk21 "$JAVA_HOME_RESOLVED" || die "JDK 21 + jlink required (got ${JAVA_HOME_RESOLVED:-none})"

export JAVA_HOME="$JAVA_HOME_RESOLVED"
export PATH="${JAVA_HOME}/bin:${PATH}"
# ASCII gradle home: avoids ClassNotFound when the OS user path has non-ASCII characters.
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${ROOT}/.gradle-linux}"
mkdir -p "$GRADLE_USER_HOME"

log "JAVA_HOME=${JAVA_HOME}"
_java_ver="$("${JAVA_HOME}/bin/java" -version 2>&1 || true)"
log "java $(java_major "${JAVA_HOME}/bin/java") ($(printf '%s\n' "$_java_ver" | sed -n '1p'))"
log "repo ${ROOT}"

chmod +x "${LINUX}/gradlew" || true
ensure_xaiop_jar
log "pack.py --self-test"
python3 "$PACK" --self-test

if [ "$SKIP_TESTS" != 1 ]; then
  log "gradle test"
  if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    (CDPATH= cd "$LINUX" && xvfb-run -a ./gradlew --no-daemon test)
  else
    (CDPATH= cd "$LINUX" && ./gradlew --no-daemon test)
  fi
fi

log "pack.py"
if [ "$SKIP_GRADLE" = 1 ]; then
  python3 "$PACK" --skip-gradle
else
  python3 "$PACK"
fi

verify_deb
log "ok ${OUT_DEB}"
log "install on Kali/Ubuntu:  sudo apt install '${OUT_DEB}'"
log "then open the Applications menu (Sound / Multimedia) or run: zmusic"
