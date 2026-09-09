"""LLM Wiki 编译器 MVP 测试。"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from engine.llm_wiki import WikiCompileError, WikiCompiler


class WikiCompilerTest(unittest.TestCase):
    """验证编译、链接、索引、检索和路径边界。"""

    def make_compiler(self) -> tuple[WikiCompiler, Path, Path]:
        directory = Path(tempfile.mkdtemp())
        source = directory / "wiki"
        build = directory / "wiki_build"
        source.mkdir()
        return WikiCompiler(source, build), source, build

    def test_compiles_markdown_and_builds_index(self) -> None:
        compiler, _source, build = self.make_compiler()

        path = compiler.compile_source(
            "# 遵义会议\n\n1935年1月，中共中央政治局在遵义召开扩大会议。",
            scenic_area_id=1,
            tags=[" 长征 ", "长征"],
        )

        output = build / path
        self.assertTrue(output.is_file())
        self.assertIn("# 遵义会议", output.read_text(encoding="utf-8"))
        index = json.loads((build / "index.json").read_text(encoding="utf-8"))
        self.assertEqual(1, len(index["entries"]))
        self.assertEqual(["长征"], index["entries"][0]["tags"])
        self.assertEqual("wiki/scenic_1/遵义会议.md", path)

    def test_build_links_and_retrieve_use_indexed_entries(self) -> None:
        compiler, _source, _build = self.make_compiler()
        compiler.compile_source("遵义会议发生在长征时期。", title="遵义会议", scenic_area_id=1)
        compiler.compile_source("长征是重要历史进程。", title="长征", scenic_area_id=1)

        links = compiler.build_links("wiki/scenic_1/遵义会议.md")
        results = compiler.retrieve("长征", scenic_area_id=1)

        self.assertEqual(["wiki/长征"], links)
        self.assertGreaterEqual(len(results), 1)
        self.assertEqual("长征", results[0]["title"])

    def test_build_all_compiles_supported_files_and_skips_others(self) -> None:
        compiler, source, build = self.make_compiler()
        (source / "a.md").write_text("# 条目甲\n\n这是足够长的素材内容。", encoding="utf-8")
        (source / "b.txt").write_text("条目乙的纯文本素材足够长。", encoding="utf-8")
        (source / "ignore.bin").write_bytes(b"ignored")

        outputs = compiler.build_all()

        self.assertEqual(2, len(outputs))
        self.assertTrue((build / "wiki/a.md").is_file())
        self.assertTrue((build / "wiki/b.md").is_file())
        self.assertFalse((build / "wiki/ignore.bin.md").exists())

    def test_rejects_short_content_and_path_traversal(self) -> None:
        compiler, _source, _build = self.make_compiler()

        with self.assertRaises(WikiCompileError):
            compiler.compile_source("太短")
        with self.assertRaises(WikiCompileError):
            compiler._resolve_under_build("../../outside.md")

    def test_rejects_invalid_scenic_area_id(self) -> None:
        compiler, _source, _build = self.make_compiler()

        with self.assertRaises(WikiCompileError):
            compiler.compile_source("这是足够长的素材内容。", scenic_area_id=0)


if __name__ == "__main__":
    unittest.main()
