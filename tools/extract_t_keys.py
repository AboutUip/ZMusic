# -*- coding: utf-8 -*-
import json
import os
import re

ROOT = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "Android", "app", "src", "main", "java")
)
ASSETS = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "Android", "app", "src", "main", "assets", "i18n")
)
CJK = re.compile(r"[\u4e00-\u9fff]")
T_CALL = re.compile(
    r't\(\s*("(?:\\.|[^"\\])*"|"""[\s\S]*?""")',
    re.M,
)


def decode_kt_string(lit: str) -> str:
    if lit.startswith('"""'):
        return lit[3:-3]
    inner = lit[1:-1]
    return bytes(inner, "utf-8").decode("unicode_escape") if "\\" in inner else inner


def extract_keys() -> list[str]:
    keys: dict[str, int] = {}
    for dirpath, _, fnames in os.walk(ROOT):
        for fn in fnames:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            for m in T_CALL.finditer(text):
                try:
                    key = decode_kt_string(m.group(1))
                except Exception:
                    key = m.group(1)[1:-1]
                if CJK.search(key) or "%s" in key or "%" in key:
                    keys[key] = keys.get(key, 0) + 1
    return sorted(keys, key=lambda k: (-keys[k], k))


def remaining_cjk() -> list[tuple[str, str]]:
    hits = []
    str_re = re.compile(r'"(?:\\.|[^"\\])*"')
    for dirpath, _, fnames in os.walk(ROOT):
        for fn in fnames:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            for m in str_re.finditer(text):
                raw = m.group(0)[1:-1]
                if not CJK.search(raw):
                    continue
                before = text[: m.start()].rstrip()
                if before.endswith("t(") or re.search(r'(^|[^A-Za-z0-9_])t\($', before):
                    continue
                # comments already included if inside strings only
                rel = os.path.relpath(path, ROOT)
                hits.append((rel, raw[:120]))
    return hits


def main() -> None:
    keys = extract_keys()
    os.makedirs(ASSETS, exist_ok=True)
    key_path = os.path.join(os.path.dirname(__file__), "i18n_keys.txt")
    with open(key_path, "w", encoding="utf-8") as f:
        for k in keys:
            f.write(k.replace("\n", "\\n") + "\n")
    print("t() keys", len(keys), "wrote", key_path)
    left = remaining_cjk()
    print("remaining unwrapped CJK literals", len(left))
    rem_path = os.path.join(os.path.dirname(__file__), "i18n_remaining.txt")
    with open(rem_path, "w", encoding="utf-8") as f:
        for rel, raw in left:
            f.write(f"{rel}\t{raw}\n")
    print("wrote", rem_path)


if __name__ == "__main__":
    main()
