# -*- coding: utf-8 -*-
"""Wrap user-facing CJK Kotlin string literals with t()."""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "Android", "app", "src", "main", "java")
)
IMPORT = "import com.kite.zmusic.i18n.t"
CJK = re.compile(r"[\u4e00-\u9fff]")
IDENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")

SKIP_FILES = {
    "I18n.kt",
    "AppLanguage.kt",
}
SKIP_NAME_PARTS = (
    "NcmJson.kt",
)
SKIP_DIR_PARTS = (
    f"{os.sep}test{os.sep}",
    f"{os.sep}androidTest{os.sep}",
    f"{os.sep}testShared{os.sep}",
)

COMPARE_PREFIXES = (
    "contains(",
    "startsWith(",
    "endsWith(",
    "equals(",
    "indexOf(",
)
SKIP_CALLEES = (
    "userFacingMessage(",
    "userFacingThrowable(",
    "SimpleDateFormat(",
    "PluginLog.d(",
    "PluginLog.w(",
    "PluginLog.e(",
    "Log.d(",
    "Log.w(",
    "Log.e(",
    "Log.i(",
    "Log.v(",
)


def is_skipped_path(path: str) -> bool:
    name = os.path.basename(path)
    if name in SKIP_FILES:
        return True
    if any(s in name or s in path for s in SKIP_NAME_PARTS):
        return True
    norm = path.replace("/", os.sep)
    return any(p in norm for p in SKIP_DIR_PARTS)


def skip_ws_comments(text: str, i: int) -> int:
    n = len(text)
    while i < n:
        if text[i] in " \t\r\n":
            i += 1
            continue
        if text.startswith("//", i):
            nl = text.find("\n", i)
            i = n if nl < 0 else nl + 1
            continue
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        break
    return i


def scan_line_comment(text: str, i: int) -> int:
    nl = text.find("\n", i)
    return len(text) if nl < 0 else nl


def scan_block_comment(text: str, i: int) -> int:
    end = text.find("*/", i + 2)
    return len(text) if end < 0 else end + 2


def scan_string(text: str, i: int) -> tuple[int, str, bool]:
    """i points at opening quote. Returns (end_index exclusive, inner source, is_triple)."""
    if text.startswith('"""', i):
        end = text.find('"""', i + 3)
        if end < 0:
            return len(text), text[i + 3 :], True
        return end + 3, text[i + 3 : end], True
    j = i + 1
    n = len(text)
    while j < n:
        ch = text[j]
        if ch == "\\":
            j += 2
            continue
        if ch == '"':
            return j + 1, text[i + 1 : j], False
        if ch == "\n":
            return j, text[i + 1 : j], False
        j += 1
    return n, text[i + 1 :], False


def rstrip_code(text: str, end: int) -> str:
    return text[:end].rstrip()


def preceded_by_t(text: str, idx: int) -> bool:
    i = idx
    while i > 0 and text[i - 1] in " \t\n":
        i -= 1
    if i < 2:
        return False
    if text[i - 1] != "(" or text[i - 2] != "t":
        return False
    if i >= 3 and (text[i - 3].isalnum() or text[i - 3] == "_"):
        return False
    return True


def line_start(text: str, idx: int) -> str:
    s = text.rfind("\n", 0, idx) + 1
    return text[s:idx]


def enum_ranges(text: str) -> list[tuple[int, int]]:
    ranges: list[tuple[int, int]] = []
    for m in re.finditer(r"\benum\s+class\b", text):
        brace = text.find("{", m.end())
        if brace < 0:
            continue
        depth = 0
        j = brace
        n = len(text)
        while j < n:
            ch = text[j]
            if text.startswith('"""', j):
                end, _, _ = scan_string(text, j)
                j = end
                continue
            if ch == '"':
                end, _, _ = scan_string(text, j)
                j = end
                continue
            if text.startswith("//", j):
                j = scan_line_comment(text, j)
                continue
            if text.startswith("/*", j):
                j = scan_block_comment(text, j)
                continue
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    ranges.append((brace, j))
                    break
            j += 1
    return ranges


def in_enum_const_args(text: str, idx: int, ranges: list[tuple[int, int]]) -> bool:
    if not any(a <= idx <= b for a, b in ranges):
        return False
    line = line_start(text, idx)
    stripped = line.lstrip()
    return bool(re.match(r"[A-Z][A-Za-z0-9_]*\s*\(", stripped)) or "(" in stripped


def followed_by_when_arrow(text: str, end: int) -> bool:
    j = skip_ws_comments(text, end)
    return text.startswith("->", j)


def preceded_by_compare(text: str, idx: int) -> bool:
    before = rstrip_code(text, idx)
    if re.search(r"(==|!=)\s*$", before):
        return True
    if any(before.endswith(p) for p in COMPARE_PREFIXES):
        return True
    return False


def inside_skip_callee(text: str, idx: int) -> bool:
    window = text[max(0, idx - 220) : idx]
    semi = window.rfind(";")
    if semi >= 0:
        window = window[semi + 1 :]
    for callee in SKIP_CALLEES:
        p = window.rfind(callee)
        if p < 0:
            continue
        open_par = window.find("(", p)
        if open_par < 0:
            continue
        depth = 0
        ok = True
        for ch in window[open_par:]:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth <= 0:
                    ok = False
                    break
        if ok:
            return True
    return False


def parse_interpolations(inner: str) -> tuple[str, list[str]] | None:
    if "${" not in inner and not re.search(r"(?<!\\)\$[A-Za-z_]", inner):
        return None
    out: list[str] = []
    args: list[str] = []
    i = 0
    n = len(inner)
    while i < n:
        if inner.startswith("\\$", i):
            out.append("\\$")
            i += 2
            continue
        if inner.startswith("${", i):
            j = i + 2
            depth = 1
            while j < n and depth:
                if inner[j] == "{":
                    depth += 1
                elif inner[j] == "}":
                    depth -= 1
                elif inner[j] == '"':
                    # skip nested string roughly
                    j += 1
                    while j < n and inner[j] != '"':
                        if inner[j] == "\\":
                            j += 2
                            continue
                        j += 1
                j += 1
            expr = inner[i + 2 : j - 1].strip()
            if not expr:
                out.append(inner[i])
                i += 1
                continue
            args.append(expr)
            out.append("\x00")
            i = j
            continue
        if inner[i] == "$" and i + 1 < n and (inner[i + 1].isalpha() or inner[i + 1] == "_"):
            j = i + 1
            while j < n and (inner[j].isalnum() or inner[j] == "_"):
                j += 1
            args.append(inner[i + 1 : j])
            out.append("\x00")
            i = j
            continue
        out.append(inner[i])
        i += 1
    if not args:
        return None
    template = "".join(out).replace("%", "%%").replace("\x00", "%s")
    return template, args


def wrap_literal(quoted: str, inner: str, triple: bool) -> str:
    parsed = parse_interpolations(inner)
    if parsed:
        template, args = parsed
        lit = '"""' + template + '"""' if triple else '"' + template + '"'
        return f"t({lit}, {', '.join(args)})"
    return f"t({quoted})"


def should_skip(
    text: str,
    start: int,
    end: int,
    inner: str,
    enums: list[tuple[int, int]],
) -> bool:
    if not CJK.search(inner):
        return True
    if preceded_by_t(text, start):
        return True
    if followed_by_when_arrow(text, end):
        return True
    if preceded_by_compare(text, start):
        return True
    if inside_skip_callee(text, start):
        return True
    if in_enum_const_args(text, start, enums):
        return True
    return False


def ensure_import(text: str) -> str:
    if IMPORT in text:
        return text
    if re.search(r"^package\s+com\.kite\.zmusic\.i18n\b", text, re.M):
        return text
    m = re.search(r"(^import .+\n)+", text, re.M)
    if m:
        return text[: m.end()] + IMPORT + "\n" + text[m.end() :]
    pm = re.search(r"^package .+\n", text, re.M)
    if pm:
        return text[: pm.end()] + "\n" + IMPORT + "\n" + text[pm.end() :]
    return IMPORT + "\n" + text


def transform(text: str) -> tuple[str, int]:
    n = len(text)
    i = 0
    out: list[str] = []
    wrapped = 0
    enums = enum_ranges(text)
    while i < n:
        if text.startswith("//", i):
            j = scan_line_comment(text, i)
            out.append(text[i:j])
            i = j
            continue
        if text.startswith("/*", i):
            j = scan_block_comment(text, i)
            out.append(text[i:j])
            i = j
            continue
        if text.startswith('"""', i) or (text[i] == '"' and not text.startswith('"""', i)):
            end, inner, triple = scan_string(text, i)
            quoted = text[i:end]
            if should_skip(text, i, end, inner, enums):
                out.append(quoted)
            else:
                out.append(wrap_literal(quoted, inner, triple))
                wrapped += 1
            i = end
            continue
        out.append(text[i])
        i += 1
    result = "".join(out)
    if wrapped:
        result = ensure_import(result)
    return result, wrapped


def main() -> int:
    total_files = 0
    total_wraps = 0
    changed = []
    for dirpath, _, fnames in os.walk(ROOT):
        for fn in fnames:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            if is_skipped_path(path):
                continue
            with open(path, encoding="utf-8") as f:
                original = f.read()
            updated, count = transform(original)
            if count == 0 or updated == original:
                continue
            with open(path, "w", encoding="utf-8") as f:
                f.write(updated)
            total_files += 1
            total_wraps += count
            changed.append((os.path.relpath(path, ROOT), count))
    print(f"wrapped {total_wraps} strings in {total_files} files")
    for rel, count in sorted(changed, key=lambda x: -x[1])[:40]:
        print(f"  {count:4d}  {rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
