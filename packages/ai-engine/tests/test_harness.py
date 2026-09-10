"""Harness 问答链路测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from engine.harness import STAGE_TIMINGS_KEY, Harness, LocalContextMiddleware
from engine.llm_wiki import WikiCompiler


class FakeOllama:
    def __init__(self, answer: str | list[str]) -> None:
        self.answer = answer
        self.messages: list[dict[str, str]] = []
        self.calls = 0
        self.models: list[str | None] = []

    def chat(self, messages: list[dict[str, str]], **kwargs: object) -> str:
        self.calls += 1
        self.messages = messages
        self.models.append(kwargs.get("model") if isinstance(kwargs.get("model"), str) else None)
        if isinstance(self.answer, list):
            return self.answer[min(self.calls - 1, len(self.answer) - 1)]
        return self.answer


class FakeClock:
    def __init__(self, *values: float) -> None:
        self._values = iter(values)

    def __call__(self) -> float:
        return next(self._values)


class HarnessTest(unittest.TestCase):
    """验证 Wiki 上下文注入、景区过滤和引用输出。"""

    def test_answer_retrieves_relevant_wiki_and_returns_sources(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        compiler.compile_source("遵义会议在1935年1月召开，是伟大的历史转折。", "遵义会议", 1)
        middleware = LocalContextMiddleware(compiler)
        ollama = FakeOllama(["规划：回答会议时间。", "遵义会议于1935年1月召开。"])
        harness = Harness(ollama_client=ollama, context_middleware=middleware)

        result = harness.answer("遵义会议何时召开？", location="scenic_area:1")

        self.assertEqual(["wiki/scenic_1/遵义会议.md"], result["sources"])
        self.assertEqual(2, ollama.calls)
        self.assertEqual([ollama.models[0], ollama.models[1]], ollama.models)
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
        self.assertEqual("知识库暂无足够依据，暂时无法可靠回答这个问题。", result["answer"])
        self.assertEqual(0, harness.ollama_client.calls)

    def test_ignores_index_entries_without_real_files(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        compiler._atomic_write_json(
            compiler._index_path(),
            {
                "version": 1,
                "entries": [
                    {
                        "title": "伪造条目",
                        "file_path": "wiki/scenic_1/missing.md",
                        "tags": [],
                        "links": [],
                        "content": "不应进入上下文",
                    }
                ],
            },
        )
        middleware = LocalContextMiddleware(compiler)
        ollama = FakeOllama("不应被调用")
        harness = Harness(ollama_client=ollama, context_middleware=middleware)

        result = harness.answer("伪造条目", location="scenic_area:1")

        self.assertEqual([], result["sources"])
        self.assertEqual(0, ollama.calls)

    def test_verify_gate_rejects_sources_not_in_retrieved_references(self) -> None:
        root = Path(tempfile.mkdtemp())
        harness = Harness(
            ollama_client=FakeOllama("回答"),
            context_middleware=LocalContextMiddleware(WikiCompiler(root / "wiki", root / "build")),
        )

        self.assertFalse(
            harness.verify_gate(
                "参考 wiki/未命中条目",
                [{"file_path": "wiki/scenic_1/遵义会议.md"}],
            )
        )
        self.assertTrue(
            harness.verify_gate(
                "这是保守回答。",
                [{"file_path": "wiki/scenic_1/遵义会议.md"}],
            )
        )

    def test_verify_gate_accepts_markdown_suffix_for_allowed_source(self) -> None:
        root = Path(tempfile.mkdtemp())
        harness = Harness(
            ollama_client=FakeOllama("回答"),
            context_middleware=LocalContextMiddleware(WikiCompiler(root / "wiki", root / "build")),
        )

        self.assertTrue(
            harness.verify_gate(
                "参考 wiki/scenic_1/遵义会议",
                [{"file_path": "wiki/scenic_1/遵义会议.md"}],
            )
        )

    def test_reasoning_sandwich_uses_configured_reasoning_and_light_models(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        compiler.compile_source("遵义会议在1935年1月召开，是历史转折。", "遵义会议", 1)
        middleware = LocalContextMiddleware(compiler)
        ollama = FakeOllama(["规划", "最终回答"])
        with patch.dict(
            "os.environ",
            {"OLLAMA_REASONING_MODEL": "reasoning-model", "OLLAMA_LIGHT_MODEL": "light-model"},
            clear=False,
        ):
            harness = Harness(ollama_client=ollama, context_middleware=middleware)
            result = harness.answer("遵义会议何时召开？", location="scenic_area:1")

        self.assertEqual("最终回答", result["answer"])
        self.assertEqual(["reasoning-model", "light-model"], ollama.models)

    def test_answer_records_retrieval_inference_and_verification_timings(self) -> None:
        root = Path(tempfile.mkdtemp())
        compiler = WikiCompiler(root / "wiki", root / "build")
        compiler.compile_source("遵义会议在1935年1月召开，是历史转折。", "遵义会议", 1)
        harness = Harness(
            ollama_client=FakeOllama(["规划", "最终回答"]),
            context_middleware=LocalContextMiddleware(compiler),
            clock=FakeClock(0.0, 0.01, 0.02, 0.22, 0.23, 0.235),
        )

        result = harness.answer("遵义会议何时召开？", location="scenic_area:1")

        self.assertEqual(
            {"retrievalMs": 10, "inferenceMs": 200, "verificationMs": 5},
            result[STAGE_TIMINGS_KEY],
        )


if __name__ == "__main__":
    unittest.main()
