"""AI 引擎问答基础链路测试。"""

from __future__ import annotations

import unittest

from engine.ask_service import FALLBACK_ANSWER, AskService


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


if __name__ == "__main__":
    unittest.main()
