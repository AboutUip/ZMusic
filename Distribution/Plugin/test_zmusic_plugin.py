#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from zmusic_plugin import PackError, cmd_init, cmd_inspect, cmd_pack, main

REPO = Path(__file__).resolve().parents[2]


class ToolkitTest(unittest.TestCase):
    def test_init_pack_inspect_roundtrip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            self.assertTrue((src / "plugin.json").is_file())
            self.assertTrue((src / "index.js").is_file())
            out = Path(tmp) / "demo.zpp"
            cmd_pack(src, out)
            self.assertTrue(out.is_file())
            with zipfile.ZipFile(out) as zf:
                names = sorted(zf.namelist())
            self.assertEqual(
                names,
                ["README.md", "index.js", "plugin.json", "plugin.svg"],
            )
            cmd_inspect(out)

    def test_pack_is_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            a = Path(tmp) / "a.zpp"
            b = Path(tmp) / "b.zpp"
            cmd_pack(src, a)
            cmd_pack(src, b)
            self.assertEqual(a.read_bytes(), b.read_bytes())

    def test_rejects_html(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            (src / "page.html").write_text("<p>x</p>", encoding="utf-8")
            with self.assertRaises(PackError):
                cmd_pack(src, Path(tmp) / "x.zpp")

    def test_rejects_non_string_capabilities(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            data = json.loads((src / "plugin.json").read_text(encoding="utf-8"))
            data["capabilities"] = [1]
            (src / "plugin.json").write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaises(PackError):
                cmd_pack(src, Path(tmp) / "x.zpp")

    def test_unknown_capability_name_ok(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            data = json.loads((src / "plugin.json").read_text(encoding="utf-8"))
            data["capabilities"] = ["theme", "ghost"]
            (src / "plugin.json").write_text(json.dumps(data), encoding="utf-8")
            cmd_pack(src, Path(tmp) / "x.zpp")

    def test_skips_git_and_nested_zpp(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            git = src / ".git"
            git.mkdir()
            (git / "config").write_text("x", encoding="utf-8")
            (src / "old.zpp").write_bytes(b"PK\x05\x06" + b"\x00" * 18)
            out = Path(tmp) / "x.zpp"
            cmd_pack(src, out)
            with zipfile.ZipFile(out) as zf:
                names = zf.namelist()
            self.assertNotIn(".git/config", names)
            self.assertNotIn("old.zpp", names)

    def test_pack_builtin_probe(self) -> None:
        src = REPO / "Android" / "app" / "src" / "main" / "plugin-probe"
        self.assertTrue((src / "plugin.json").is_file())
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "probe.zpp"
            cmd_pack(src, out)
            self.assertTrue(out.is_file())
            cmd_inspect(out)

    def test_cli_missing_readme(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "demo"
            cmd_init(src)
            (src / "README.md").unlink()
            code = main(["pack", str(src), "-o", str(Path(tmp) / "x.zpp")])
            self.assertEqual(code, 1)


if __name__ == "__main__":
    unittest.main()
