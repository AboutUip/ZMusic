#!/usr/bin/env python3
"""ZMusic 插件打包：init / pack / inspect。契约见 docs/plugin-engine/TOOLKIT.md。"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import zipfile
from pathlib import Path

ZPP = 1
ENGINE_MIN_DEFAULT = 100
ID_SEGMENT = re.compile(r"^[a-z][a-z0-9_]*$")
ALLOWED_EXTENSIONS = {
    "js", "json",
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg",
    "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav",
    "mp4", "webm", "mkv", "mov",
    "txt", "md", "csv", "tsv", "lrc", "srt", "vtt",
    "yml", "yaml", "toml", "ini", "cue", "m3u", "m3u8",
}
SKIP_DIR_NAMES = {".git", "node_modules", "__pycache__", ".idea", ".vscode"}
SKIP_FILE_NAMES = {".ds_store", "thumbs.db"}
ZIP_EPOCH = (1980, 1, 1, 0, 0, 0)

INIT_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="96" fill="#EC4141"/>
  <circle cx="256" cy="256" r="120" fill="#fff"/>
</svg>
"""

INIT_INDEX = """Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);
"""


class PackError(Exception):
    pass


def is_valid_id(plugin_id: str) -> bool:
    if plugin_id != plugin_id.lower():
        return False
    parts = plugin_id.split(".")
    if len(parts) < 2:
        return False
    return all(ID_SEGMENT.match(p) for p in parts)


def normalize_rel(path: str) -> str | None:
    p = path.strip().replace("\\", "/")
    while p.startswith("./"):
        p = p[2:]
    if not p or p.startswith("/") or "\\" in p or "\x00" in p:
        return None
    if p.endswith("/"):
        return None
    parts = p.split("/")
    if any(part == ".." or part == "" for part in parts):
        return None
    return p


def extension_of(path: str) -> str | None:
    name = path.rsplit("/", 1)[-1]
    dot = name.rfind(".")
    if dot <= 0 or dot == len(name) - 1:
        return None
    return name[dot + 1 :]


def entry_path_ok(entry: str) -> bool:
    rel = normalize_rel(entry)
    return rel is not None and rel.endswith(".js") and extension_of(rel) == "js"


def should_skip(path: Path, root: Path) -> bool:
    try:
        rel = path.relative_to(root)
    except ValueError:
        return True
    for part in rel.parts:
        if part in SKIP_DIR_NAMES:
            return True
        if part.startswith(".") and part not in {".", ".."} and path.is_dir():
            return True
    if path.is_file() and path.name.lower() in SKIP_FILE_NAMES:
        return True
    if path.is_file() and path.suffix.lower() == ".zpp":
        return True
    return False


def collect_files(src: Path) -> list[Path]:
    files: list[Path] = []
    for path in sorted(src.rglob("*"), key=lambda p: p.as_posix().replace("\\", "/")):
        if should_skip(path, src):
            continue
        if path.is_dir():
            continue
        files.append(path)
    return files


def rel_posix(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def require_int(value, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise PackError(f"{field} 必须是整数")
    return value


def validate_manifest(raw: str) -> dict:
    try:
        obj = json.loads(raw)
    except json.JSONDecodeError as e:
        raise PackError(f"plugin.json 不是 JSON: {e}") from e
    if not isinstance(obj, dict):
        raise PackError("plugin.json 必须是对象")
    zpp = require_int(obj.get("zpp"), "zpp")
    if zpp != ZPP:
        raise PackError("zpp 必须为 1")
    plugin_id = obj.get("id")
    if not isinstance(plugin_id, str) or not is_valid_id(plugin_id):
        raise PackError("id 不是合法反向域名")
    name = obj.get("name")
    if not isinstance(name, str) or name == "":
        raise PackError("缺少 name")
    version = require_int(obj.get("version"), "version")
    if version < 0:
        raise PackError("version 不能为负")
    entry = obj.get("entry")
    if not isinstance(entry, str) or not entry_path_ok(entry):
        raise PackError("entry 非法")
    engine = obj.get("engine")
    if not isinstance(engine, dict):
        raise PackError("缺少 engine")
    engine_min = require_int(engine.get("min"), "engine.min")
    if "max" in engine and engine["max"] is not None:
        engine_max = require_int(engine.get("max"), "engine.max")
        if engine_max < engine_min:
            raise PackError("engine.max 小于 engine.min")
    capabilities = obj.get("capabilities", [])
    if capabilities is None:
        capabilities = []
    if not isinstance(capabilities, list):
        raise PackError("capabilities 必须是数组")
    if any(not isinstance(item, str) for item in capabilities):
        raise PackError("capabilities 含非字符串项")
    homepage = obj.get("homepage")
    if homepage is not None:
        if not isinstance(homepage, str) or (
            not homepage.startswith("http://") and not homepage.startswith("https://")
        ):
            raise PackError("homepage 必须是 http(s) URL")
    signatures = obj.get("signatures")
    if signatures is not None and not isinstance(signatures, list):
        raise PackError("signatures 必须是数组")
    return obj


def validate_tree(src: Path, manifest: dict) -> list[tuple[str, Path]]:
    files = collect_files(src)
    if not files:
        raise PackError("源目录为空")
    names = {rel_posix(path, src) for path in files}
    if "plugin.json" not in names:
        raise PackError("缺少 plugin.json")
    if "README.md" not in names:
        raise PackError("缺少 README.md")
    if "plugin.png" not in names and "plugin.svg" not in names:
        raise PackError("既无 plugin.png 也无 plugin.svg")
    entry = normalize_rel(str(manifest["entry"]))
    if entry is None or entry not in names:
        raise PackError("缺少入口文件")
    packed: list[tuple[str, Path]] = []
    for path in files:
        rel = rel_posix(path, src)
        if normalize_rel(rel) is None:
            raise PackError(f"非法路径: {rel}")
        ext = extension_of(rel)
        if ext is None or ext not in ALLOWED_EXTENSIONS:
            raise PackError(f"扩展名不在白名单: {rel}")
        packed.append((rel, path))
    packed.sort(key=lambda item: item[0])
    return packed


def write_zip(entries: list[tuple[str, Path]], dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name, path in entries:
            info = zipfile.ZipInfo(filename=name, date_time=ZIP_EPOCH)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            zf.writestr(info, path.read_bytes())
    dest.write_bytes(buf.getvalue())


def cmd_init(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    if (directory / "plugin.json").exists():
        raise PackError(f"已存在 plugin.json: {directory}")
    slug = directory.name.lower()
    slug = re.sub(r"[^a-z0-9_]+", "_", slug).strip("_") or "plugin"
    if slug[0].isdigit():
        slug = f"p_{slug}"
    plugin_id = f"com.example.{slug}"
    if not is_valid_id(plugin_id):
        plugin_id = "com.example.plugin"
    name = directory.name or "插件"
    manifest = {
        "zpp": ZPP,
        "id": plugin_id,
        "name": name,
        "version": 1,
        "entry": "index.js",
        "engine": {"min": ENGINE_MIN_DEFAULT},
        "description": "",
        "author": "",
        "capabilities": [],
        "signatures": [],
    }
    (directory / "plugin.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (directory / "README.md").write_text(f"# {name}\n\n在此介绍插件。\n", encoding="utf-8")
    (directory / "plugin.svg").write_text(INIT_SVG, encoding="utf-8")
    (directory / "index.js").write_text(INIT_INDEX, encoding="utf-8")
    print(f"已初始化 {directory}")
    print(f"id: {plugin_id}")


def cmd_pack(src: Path, out: Path | None) -> None:
    src = src.resolve()
    if not src.is_dir():
        raise PackError(f"不是目录: {src}")
    json_file = src / "plugin.json"
    if not json_file.is_file():
        raise PackError("缺少 plugin.json")
    manifest = validate_manifest(json_file.read_text(encoding="utf-8"))
    entries = validate_tree(src, manifest)
    dest = out.resolve() if out is not None else (src.parent / f"{manifest['id']}.zpp")
    if dest.exists() and dest.is_dir():
        raise PackError(f"输出路径是目录: {dest}")
    write_zip(entries, dest)
    print(dest)


def zip_entries(zpp: Path) -> list[zipfile.ZipInfo]:
    try:
        with zipfile.ZipFile(zpp, "r") as zf:
            if zf.pwd is not None:
                raise PackError("禁止加密包")
            return [info for info in zf.infolist() if not info.filename.startswith("__MACOSX/")]
    except zipfile.BadZipFile as e:
        raise PackError("无法打开归档") from e


def wrapping_prefix(names: list[str]) -> str | None:
    top: list[str] = []
    seen: set[str] = set()
    for name in names:
        trimmed = name.lstrip("/")
        if not trimmed:
            continue
        first = trimmed.split("/", 1)[0]
        if not first:
            return None
        if first not in seen:
            seen.add(first)
            top.append(first)
        if len(seen) > 1:
            return None
    if len(top) != 1:
        return None
    folder = top[0]
    only_folder = all(
        (n.lstrip("/") == folder or n.lstrip("/") == f"{folder}/" or n.lstrip("/").startswith(f"{folder}/"))
        for n in names
        if n.lstrip("/")
    )
    if not only_folder:
        return None
    has_nested = any(
        n.lstrip("/").startswith(f"{folder}/") and len(n.lstrip("/")) > len(folder) + 1
        for n in names
    )
    return f"{folder}/" if has_nested else None


def cmd_inspect(zpp: Path) -> None:
    zpp = zpp.resolve()
    if not zpp.is_file():
        raise PackError(f"不是文件: {zpp}")
    infos = zip_entries(zpp)
    names = [info.filename.replace("\\", "/") for info in infos]
    prefix = wrapping_prefix(names)
    mapped: list[str] = []
    with zipfile.ZipFile(zpp, "r") as zf:
        manifest_text = None
        for info in infos:
            rel = info.filename.replace("\\", "/")
            if prefix:
                if rel == prefix.rstrip("/"):
                    continue
                if not rel.startswith(prefix):
                    raise PackError("包根不一致")
                rel = rel[len(prefix) :]
            if not rel:
                continue
            directory = info.is_dir() or rel.endswith("/")
            if directory:
                mapped.append(rel if rel.endswith("/") else f"{rel}/")
                continue
            if normalize_rel(rel) is None:
                raise PackError(f"非法路径: {rel}")
            ext = extension_of(rel)
            if ext is None or ext not in ALLOWED_EXTENSIONS:
                raise PackError(f"扩展名不在白名单: {rel}")
            mapped.append(rel)
            if rel == "plugin.json":
                manifest_text = zf.read(info).decode("utf-8")
        if manifest_text is None:
            raise PackError("缺少 plugin.json")
        manifest = validate_manifest(manifest_text)
        files = [name for name in mapped if not name.endswith("/")]
        if "README.md" not in files:
            raise PackError("缺少 README.md")
        if "plugin.png" not in files and "plugin.svg" not in files:
            raise PackError("缺少图标")
        entry = normalize_rel(str(manifest["entry"]))
        if entry not in files:
            raise PackError("缺少入口文件")
        print(f"id: {manifest['id']}")
        print(f"name: {manifest['name']}")
        print(f"version: {manifest['version']}")
        print(f"entry: {manifest['entry']}")
        print(f"engine.min: {manifest['engine']['min']}")
        if manifest["engine"].get("max") is not None:
            print(f"engine.max: {manifest['engine']['max']}")
        caps = manifest.get("capabilities") or []
        print("capabilities: " + (", ".join(caps) if caps else "(空)"))
        print(f"files: {len(files)}")
        for name in sorted(files):
            print(f"  {name}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="ZMusic 插件打包工具")
    sub = parser.add_subparsers(dest="cmd", required=True)
    p_init = sub.add_parser("init", help="生成插件源目录")
    p_init.add_argument("directory", nargs="?", default=".", type=Path)
    p_pack = sub.add_parser("pack", help="校验并写出 .zpp")
    p_pack.add_argument("src", type=Path)
    p_pack.add_argument("-o", "--out", type=Path)
    p_inspect = sub.add_parser("inspect", help="打印清单与文件列表")
    p_inspect.add_argument("zpp", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.cmd == "init":
            cmd_init(args.directory)
        elif args.cmd == "pack":
            cmd_pack(args.src, args.out)
        elif args.cmd == "inspect":
            cmd_inspect(args.zpp)
        else:
            raise PackError("未知命令")
    except PackError as e:
        print(e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
