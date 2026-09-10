"""AI 引擎问答基础链路。"""

from __future__ import annotations

import logging
import os
from collections.abc import Callable, Mapping, Sequence
from time import perf_counter
from typing import Any, TypedDict

from engine.harness import STAGE_TIMINGS_KEY, Harness
from engine.tts import TtsSynthesizer

FALLBACK_ANSWER = "当前本地问答模型暂不可用，请稍后重试。"
logger = logging.getLogger(__name__)


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
        tts_synthesizer: TtsSynthesizer | None = None,
        stage_clock: Callable[[], float] = perf_counter,
        harness: Harness | None = None,
    ) -> None:
        self._harness = harness or Harness()
        self._answer_provider = answer_provider or self._answer_with_harness
        self._clock = clock
        self._stage_clock = stage_clock
        self._tts_synthesizer = tts_synthesizer or TtsSynthesizer()
        self._target_ms = self._positive_int_env("ASK_TARGET_MS", 3000)
        self._offline_limit_ms = self._positive_int_env("ASK_OFFLINE_LIMIT_MS", 5000)

    def ask(
        self,
        question: str,
        scenic_area_id: int | None = None,
        use_voice: bool = False,
    ) -> AskResult:
        """执行一次问答；底层能力未就绪或异常时返回可展示的降级答案。"""
        normalized_question = question.strip()
        started_at = self._clock()
        provider_started_at = self._stage_clock()
        raw_result: object = None
        try:
            raw_result = self._answer_provider(normalized_question, scenic_area_id)
            answer, sources, audio_url = self._normalize_result(raw_result, use_voice)
        # Ollama、Wiki 与 TTS 都是可降级依赖，异常不得突破 AI 引擎接口边界。
        except Exception:
            answer, sources, audio_url = FALLBACK_ANSWER, [], None
        provider_ms = self._elapsed_stage_ms(provider_started_at)
        stage_timings = self._extract_stage_timings(raw_result, provider_ms)

        tts_ms = 0
        if use_voice and audio_url is None:
            tts_started_at = self._stage_clock()
            try:
                audio_url = self._tts_synthesizer.synthesize(answer)
            except Exception:
                # TTS 属于旁路能力，合成失败不能覆盖已生成的文字回答。
                audio_url = None
            finally:
                tts_ms = self._elapsed_stage_ms(tts_started_at)

        duration_ms = max(0, round((self._clock() - started_at) * 1000))
        self._log_performance(stage_timings, tts_ms, duration_ms, answer)
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

    @staticmethod
    def _extract_stage_timings(raw_result: object, provider_ms: int) -> dict[str, int]:
        raw_timings = raw_result.get(STAGE_TIMINGS_KEY) if isinstance(raw_result, Mapping) else None
        if not isinstance(raw_timings, Mapping):
            return {"retrievalMs": 0, "inferenceMs": provider_ms, "verificationMs": 0}
        timings: dict[str, int] = {}
        for key in ("retrievalMs", "inferenceMs", "verificationMs"):
            value = raw_timings.get(key)
            timings[key] = max(0, round(value)) if isinstance(value, (int, float)) else 0
        return timings

    def _log_performance(
        self,
        timings: Mapping[str, int],
        tts_ms: int,
        duration_ms: int,
        answer: str,
    ) -> None:
        log = logger.warning if duration_ms > self._target_ms else logger.info
        log(
            "ai_ask_performance retrievalMs=%d inferenceMs=%d verificationMs=%d "
            "ttsMs=%d durationMs=%d targetMs=%d offlineLimitMs=%d outcome=%s",
            timings.get("retrievalMs", 0),
            timings.get("inferenceMs", 0),
            timings.get("verificationMs", 0),
            tts_ms,
            duration_ms,
            self._target_ms,
            self._offline_limit_ms,
            "fallback" if answer == FALLBACK_ANSWER else "ok",
        )

    def _elapsed_stage_ms(self, started_at: float) -> int:
        return max(0, round((self._stage_clock() - started_at) * 1000))

    @staticmethod
    def _positive_int_env(name: str, default: int) -> int:
        try:
            value = int(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default
