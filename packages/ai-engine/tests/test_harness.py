"""Harness 问答链路测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from engine.harness import Harness, LocalContextMiddleware
from engine.llm_wiki import WikiCompiler


class FakeOllama:
    def __init__(self, answer: str) -> None:
        self.answer = answer
        self.messages: list[dict[str, str]] = []

    def chat(self, messages: list[dict[str, str]], **_kwargs: object) -> str:
        self.messages = messages
        return self.answer


class HarnessTest(unittest.TestCase):
    """验证 Wiki 上下文注入、景区过滤和引用输出。"""

    def test_answer_retrieves_relevant_wiki_and_returns_sources(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        compiler.compile_source("遵义会议在1935年1月召开，是伟大的历史转折。", "遵义会议", 1)
        middleware = LocalContextMiddleware(compiler)
        ollama = FakeOllama("遵义会议于1935年1月召开。")
        harness = Harness(ollama_client=ollama, context_middleware=middleware)

        result = harness.answer("遵义会议何时召开？", location="scenic_area:1")

        self.assertEqual(["wiki/scenic_1/遵义会议.md"], result["sources"])
        self.assertIn("遵义会议何时召开？", ollama.messages[-1]["content"])
        self.assertIn("遵义会议在1935年1月召开", ollama.messages[-1]["content"])

    def test_context_parses_scenic_area_and_limits_evidence(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        middleware = LocalContextMiddleware(compiler)

        context = middleware.inject_context("scenic_area:7")

        self.assertEqual(7, context["scenicAreaId"])
        self.assertEqual(0, context["wikiCount"])

    def test_unknown_question_can_be_answered_without_fabricated_sources(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        middleware = LocalContextMiddleware(compiler)
        harness = Harness(ollama_client=FakeOllama("知识库暂无足够依据。"), context_middleware=middleware)

        result = harness.answer("一个没有资料的问题", location="scenic_area:1")

        self.assertEqual([], result["sources"])
        self.assertEqual("知识库暂无足够依据。", result["answer"])


if __name__ == "__main__":
    unittest.main()
