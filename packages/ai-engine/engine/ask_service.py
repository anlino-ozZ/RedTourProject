"""AI 引擎问答基础链路。"""

from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from time import perf_counter
from typing import Any, TypedDict

from engine.harness import Harness

FALLBACK_ANSWER = "当前本地问答模型暂不可用，请稍后重试。"


class AskResult(TypedDict):
    """``/engine/ask`` 对外返回字段。"""

    question: str
    answer: str
    sources: list[str]
    durationMs: int
    audioUrl: str | None


AnswerProvider = Callable[[str, int | None], object]


class AskService:
    """编排问答提供者并将结果规范化为稳定的 HTTP 契约。"""

    def __init__(
        self,
        answer_provider: AnswerProvider | None = None,
        clock: Callable[[], float] = perf_counter,
    ) -> None:
        self._harness = Harness()
        self._answer_provider = answer_provider or self._answer_with_harness
        self._clock = clock

    def ask(
        self,
        question: str,
        scenic_area_id: int | None = None,
        use_voice: bool = False,
    ) -> AskResult:
        """执行一次问答；底层能力未就绪或异常时返回可展示的降级答案。"""
        normalized_question = question.strip()
        started_at = self._clock()
        try:
            raw_result = self._answer_provider(normalized_question, scenic_area_id)
            answer, sources, audio_url = self._normalize_result(raw_result, use_voice)
        # Ollama、Wiki 与 TTS 都是可降级依赖，异常不得突破 AI 引擎接口边界。
        except Exception:
            answer, sources, audio_url = FALLBACK_ANSWER, [], None

        duration_ms = max(0, round((self._clock() - started_at) * 1000))
        return {
            "question": normalized_question,
            "answer": answer,
            "sources": sources,
            "durationMs": duration_ms,
            "audioUrl": audio_url,
        }

    def _answer_with_harness(self, question: str, scenic_area_id: int | None) -> object:
        """调用 Harness；后续模块在 Harness 内接入 Wiki 与 Ollama。"""
        location = f"scenic_area:{scenic_area_id}" if scenic_area_id is not None else None
        return self._harness.answer(question, location=location)

    @staticmethod
    def _normalize_result(raw_result: object, use_voice: bool) -> tuple[str, list[str], str | None]:
        """兼容当前字符串结果与后续包含引用、音频的结构化结果。"""
        if isinstance(raw_result, str):
            answer = raw_result.strip()
            return (answer or FALLBACK_ANSWER), [], None

        if not isinstance(raw_result, Mapping):
            return FALLBACK_ANSWER, [], None

        raw_answer = raw_result.get("answer")
        answer = raw_answer.strip() if isinstance(raw_answer, str) else ""
        sources = AskService._normalize_sources(raw_result.get("sources"))
        raw_audio_url = raw_result.get("audioUrl")
        audio_url = (
            raw_audio_url.strip()
            if use_voice and isinstance(raw_audio_url, str) and raw_audio_url.strip()
            else None
        )
        return (answer or FALLBACK_ANSWER), sources, audio_url

    @staticmethod
    def _normalize_sources(raw_sources: Any) -> list[str]:
        """清洗引用路径并去重，避免字符串被误拆成字符列表。"""
        if isinstance(raw_sources, (str, bytes)) or not isinstance(raw_sources, Sequence):
            return []
        return list(
            dict.fromkeys(
                source.strip()
                for source in raw_sources
                if isinstance(source, str) and source.strip()
            )
        )
