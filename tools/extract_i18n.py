# -*- coding: utf-8 -*-
import collections
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "Android", "app", "src", "main", "java")
SKIP_NAME = (
    "NcmJson.kt",
    "NcmCommentParse",
    "NcmLibraryParse",
    "NcmHomeParse",
    "NcmArtistParse",
    "NcmCloudParse",
    "AnnualReportParse",
)
STR = re.compile(r'"(?:\\.|[^"\\])*"')
CJK = re.compile(r"[\u4e00-\u9fff]")


def main() -> None:
    simple: collections.Counter[str] = collections.Counter()
    interp: collections.Counter[str] = collections.Counter()
    files = 0
    for dirpath, _, fnames in os.walk(os.path.abspath(ROOT)):
        for fn in fnames:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            if any(s in fn or s in path for s in SKIP_NAME):
                continue
            files += 1
            with open(path, encoding="utf-8") as f:
                text = f.read()
            for m in STR.finditer(text):
                raw = m.group(0)[1:-1]
                if not CJK.search(raw):
                    continue
                if "${" in raw or r"\$" in raw:
                    interp[raw] += 1
                else:
                    simple[raw] += 1
    print("files", files)
    print("simple unique", len(simple), "occ", sum(simple.values()))
    print("interp unique", len(interp), "occ", sum(interp.values()))
    out = os.path.join(os.path.dirname(__file__), "i18n_extract.txt")
    with open(out, "w", encoding="utf-8") as f:
        f.write("## SIMPLE\n")
        for s, c in simple.most_common():
            f.write(f"{c}\t{s}\n")
        f.write("\n## INTERP\n")
        for s, c in interp.most_common():
            f.write(f"{c}\t{s}\n")
    print("wrote", out)


if __name__ == "__main__":
    main()
