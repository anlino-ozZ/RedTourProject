"""AI 引擎问答基础链路测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from engine.ask_service import FALLBACK_ANSWER, AskService
from engine.tts import TtsSynthesizer


class FakeClock:
    """提供确定的起止时间。"""

    def __init__(self, *values: float) -> None:
        self._values = iter(values)

    def __call__(self) -> float:
        return next(self._values)


class AskServiceTest(unittest.TestCase):
    """验证问答字段契约及依赖异常降级。"""

    def test_string_answer_uses_complete_camel_case_contract(self) -> None:
        def provider(_question: str, _scenic_id: int | None) -> str:
            return "  遵义会议具有重要意义。  "

        service = AskService(
            answer_provider=provider,
            clock=FakeClock(10.0, 12.1),
        )

        result = service.ask("  遵义会议的意义是什么？  ", scenic_area_id=1)

        self.assertEqual(
            {
                "question": "遵义会议的意义是什么？",
                "answer": "遵义会议具有重要意义。",
                "sources": [],
                "durationMs": 2100,
                "audioUrl": None,
            },
            result,
        )

    def test_structured_answer_normalizes_sources_and_voice_url(self) -> None:
        def provider(_question: str, scenic_id: int | None) -> dict[str, object]:
            self.assertEqual(1, scenic_id)
            return {
                "answer": "遵义会议是重要转折点。",
                "sources": ["wiki/遵义会议", "wiki/遵义会议", "", 123],
                "audioUrl": "/audio/tts/ask_1.mp3",
            }

        service = AskService(answer_provider=provider, clock=FakeClock(1.0, 1.01))

        result = service.ask("遵义会议是什么？", scenic_area_id=1, use_voice=True)

        self.assertEqual(["wiki/遵义会议"], result["sources"])
        self.assertEqual("/audio/tts/ask_1.mp3", result["audioUrl"])
        self.assertEqual(10, result["durationMs"])

    def test_audio_url_is_null_when_voice_is_not_requested(self) -> None:
        def provider(_question: str, _scenic_id: int | None) -> dict[str, str]:
            return {
                "answer": "回答",
                "audioUrl": "/audio/tts/unused.mp3",
            }

        service = AskService(answer_provider=provider)

        result = service.ask("问题", use_voice=False)

        self.assertIsNone(result["audioUrl"])

    def test_voice_request_synthesizes_after_answer_generation(self) -> None:
        root = Path(tempfile.mkdtemp())

        class FakeEngine:
            def save_to_file(self, _text: str, path: str) -> None:
                Path(path).write_bytes(b"audio")

            def runAndWait(self) -> None:
                return None

        tts = TtsSynthesizer(root, "/audio/tts", engine_factory=FakeEngine)
        service = AskService(
            answer_provider=lambda _question, _scenic_id: "回答文本",
            tts_synthesizer=tts,
        )

        result = service.ask("问题", use_voice=True)

        self.assertRegex(result["audioUrl"], r"^/audio/tts/ask_[0-9a-f]{32}\.wav$")

    def test_tts_failure_does_not_replace_text_answer(self) -> None:
        class FailedTts:
            def synthesize(self, _text: str) -> str:
                raise RuntimeError("tts unavailable")

        service = AskService(
            answer_provider=lambda _question, _scenic_id: "仍可展示的回答",
            tts_synthesizer=FailedTts(),
        )

        result = service.ask("问题", use_voice=True)

        self.assertEqual("仍可展示的回答", result["answer"])
        self.assertIsNone(result["audioUrl"])

    def test_text_request_does_not_call_tts(self) -> None:
        class CountingTts:
            def __init__(self) -> None:
                self.calls = 0

            def synthesize(self, _text: str) -> str:
                self.calls += 1
                return "/audio/tts/unused.wav"

        tts = CountingTts()
        service = AskService(
            answer_provider=lambda _question, _scenic_id: "文本回答",
            tts_synthesizer=tts,
        )

        result = service.ask("问题", use_voice=False)

        self.assertEqual("文本回答", result["answer"])
        self.assertIsNone(result["audioUrl"])
        self.assertEqual(0, tts.calls)

    def test_provider_failure_returns_fallback_instead_of_raising(self) -> None:
        def failed_provider(_question: str, _scenic_id: int | None) -> object:
            raise ConnectionError("Ollama unavailable")

        service = AskService(answer_provider=failed_provider)

        result = service.ask("离线问题", scenic_area_id=2, use_voice=True)

        self.assertEqual(FALLBACK_ANSWER, result["answer"])
        self.assertEqual([], result["sources"])
        self.assertIsNone(result["audioUrl"])

    def test_unimplemented_harness_result_returns_fallback(self) -> None:
        def provider(_question: str, _scenic_id: int | None) -> None:
            return None

        service = AskService(answer_provider=provider)

        result = service.ask("尚未接入模型的问题")

        self.assertEqual(FALLBACK_ANSWER, result["answer"])

    def test_logs_all_performance_stages_without_changing_contract(self) -> None:
        def provider(_question: str, _scenic_id: int | None) -> dict[str, object]:
            return {
                "answer": "回答",
                "sources": [],
                "_stageTimings": {
                    "retrievalMs": 12,
                    "inferenceMs": 3200,
                    "verificationMs": 3,
                },
            }

        service = AskService(
            answer_provider=provider,
            clock=FakeClock(1.0, 4.5),
            stage_clock=FakeClock(10.0, 10.1),
        )

        with self.assertLogs("engine.ask_service", level="WARNING") as captured:
            result = service.ask("问题")

        self.assertEqual(
            {"question", "answer", "sources", "durationMs", "audioUrl"}, set(result)
        )
        self.assertIn("retrievalMs=12", captured.output[0])
        self.assertIn("inferenceMs=3200", captured.output[0])
        self.assertIn("verificationMs=3", captured.output[0])
        self.assertIn("ttsMs=0", captured.output[0])
        self.assertIn("durationMs=3500", captured.output[0])


if __name__ == "__main__":
    unittest.main()
