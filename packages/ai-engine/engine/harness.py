"""本地问答 Harness。

MVP 版本实现本地上下文注入和受控 Wiki 检索，使用 Ollama 进行非流式中文问答。
所有外部依赖均可替换/延迟加载，调用失败由 ``AskService`` 统一降级。
"""

from __future__ import annotations

import logging
import os
import re
from collections.abc import Callable
from pathlib import Path
from time import perf_counter
from typing import Any, Mapping

from engine.cloud_infer import (
    CloudLlmClient,
    LLM_PROVIDER_CLOUD,
    LLM_PROVIDER_OLLAMA,
    resolve_llm_provider,
)
from engine.llm_wiki import WikiCompileError, WikiCompiler
from engine.ollama_infer import OllamaClient

STAGE_TIMINGS_KEY = "_stageTimings"

# 引用校验未通过时的降级文案。必须与"模型不可用"区分开：这里模型其实正常工作，
# 只是本次回答没能落地到已检索的 Wiki，若抛异常会被 AskService 兜底成"模型暂不可用"，
# 误导运维排查方向。
NO_EVIDENCE_ANSWER = "知识库暂无足够依据，暂时无法可靠回答这个问题。"

logger = logging.getLogger(__name__)


class LocalContextMiddleware:
    """本地上下文中间件：注入景区位置和 Wiki 索引信息。"""

    def __init__(self, compiler: WikiCompiler | None = None) -> None:
        self.compiler = compiler or WikiCompiler(
            os.getenv("WIKI_DIR", "./wiki"), os.getenv("WIKI_BUILD_DIR", "./wiki_build")
        )

    def inject_context(self, location: str | None = None) -> dict[str, Any]:
        """将 ``scenic_area:<id>`` 位置解析为可供检索使用的本地上下文。"""
        scenic_area_id: int | None = None
        if isinstance(location, str):
            match = re.fullmatch(r"\s*scenic_area:(\d+)\s*", location)
            if match:
                scenic_area_id = int(match.group(1))
        try:
            entries = self.compiler._read_index().get("entries", [])
        except WikiCompileError:
            entries = []
        return {
            "location": location,
            "scenicAreaId": scenic_area_id,
            "wikiBuildDir": str(self.compiler.build_dir),
            "wikiCount": self._wiki_count(entries),
            "wikiEntries": self._wiki_entries(scenic_area_id, entries),
            "_wikiIndex": {"version": 1, "entries": entries},
            "instructions": "仅依据本地 Wiki 资料回答；资料不足时明确说明知识库暂无依据。",
        }

    def retrieve(
        self, query: str, context: Mapping[str, Any], limit: int = 4
    ) -> list[dict[str, Any]]:
        """检索与问题相关的少量 Wiki 条目，限制传给模型的上下文规模。"""
        scenic_area_id = context.get("scenicAreaId")
        index = context.get("_wikiIndex")
        references = self.compiler.retrieve(
            query,
            scenic_area_id=scenic_area_id,
            index=index if isinstance(index, dict) else None,
        )
        valid_references = [
            reference
            for reference in references
            if self._is_existing_reference(reference)
        ]
        return valid_references[: max(1, min(limit, 8))]

    def _is_existing_reference(self, reference: Mapping[str, Any]) -> bool:
        """仅允许索引中对应实际文件的条目进入回答上下文。"""
        file_path = reference.get("file_path")
        if not isinstance(file_path, str) or not file_path.strip():
            return False
        try:
            target = (self.compiler.build_dir / file_path).resolve()
            return target.is_file() and target.is_relative_to(self.compiler.build_dir)
        except (OSError, ValueError):
            return False

    @staticmethod
    def _wiki_count(entries: Any) -> int:
        """使用已缓存索引计数，避免每次问答递归扫描构建目录。"""
        if not isinstance(entries, list):
            return 0
        return sum(
            1
            for entry in entries
            if isinstance(entry, Mapping) and str(entry.get("file_path") or "").endswith(".md")
        )

    def _wiki_entries(
        self, scenic_area_id: int | None, entries: Any
    ) -> list[dict[str, Any]]:
        """注入当前景区的条目地图，不携带正文，避免上下文无界增长。"""
        if not isinstance(entries, list):
            return []
        result: list[dict[str, Any]] = []
        for entry in entries:
            if not isinstance(entry, Mapping):
                continue
            if scenic_area_id is not None and not self.compiler._entry_matches_scenic_area(
                dict(entry), scenic_area_id
            ):
                continue
            title = entry.get("title")
            file_path = entry.get("file_path")
            if isinstance(title, str) and isinstance(file_path, str):
                result.append(
                    {
                        "title": title.strip(),
                        "filePath": file_path.strip(),
                        "tags": self.compiler._normalize_values(entry.get("tags")),
                    }
                )
            if len(result) >= 20:
                break
        return result


class Harness:
    """AI 推理机架：Wiki 检索 + Ollama 对话 + 最小验证门。"""

    MAX_CONTEXT_CHARS = 6000

    def __init__(
        self,
        ollama_client: OllamaClient | None = None,
        context_middleware: LocalContextMiddleware | None = None,
        clock: Callable[[], float] = perf_counter,
    ) -> None:
        self.context_middleware = context_middleware or LocalContextMiddleware()
        # 显式注入客户端时以客户端类型为准，否则按 LLM_PROVIDER 装配
        if ollama_client is not None:
            self.llm_provider = (
                LLM_PROVIDER_CLOUD
                if isinstance(ollama_client, CloudLlmClient)
                else LLM_PROVIDER_OLLAMA
            )
            self.ollama_client = ollama_client
        else:
            self.llm_provider = self._resolve_provider()
            self.ollama_client = (
                CloudLlmClient() if self.llm_provider == LLM_PROVIDER_CLOUD else OllamaClient()
            )
        self._clock = clock
        default_model = getattr(self.ollama_client, "model", OllamaClient.DEFAULT_MODEL)
        # 模型名按 provider 各自取值：云端绝不能回退到 OLLAMA_*，否则会把本地模型名发给云端
        if self.llm_provider == LLM_PROVIDER_CLOUD:
            self.reasoning_model = (
                self._clean_env("LLM_REASONING_MODEL")
                or self._clean_env("LLM_MODEL")
                or default_model
            )
            self.polish_model = (
                self._clean_env("LLM_LIGHT_MODEL")
                or self._clean_env("LLM_MODEL")
                or default_model
            )
        else:
            self.reasoning_model = self._clean_env("OLLAMA_REASONING_MODEL") or default_model
            self.polish_model = (
                self._clean_env("OLLAMA_LIGHT_MODEL")
                or self._clean_env("OLLAMA_MODEL")
                or default_model
            )
        self.max_context_chars = self._positive_int_env(
            "WIKI_MAX_CONTEXT_CHARS", self.MAX_CONTEXT_CHARS
        )
        # 云端（尤其推理型模型）需要更大的输出预算：本地 128/256 会被思考过程耗尽导致空回答
        if self.llm_provider == LLM_PROVIDER_CLOUD:
            self.reasoning_max_tokens = self._positive_int_env("LLM_REASONING_MAX_TOKENS", 512)
            self.answer_max_tokens = self._positive_int_env("LLM_ANSWER_MAX_TOKENS", 1024)
        else:
            self.reasoning_max_tokens = self._positive_int_env("OLLAMA_REASONING_MAX_TOKENS", 128)
            self.answer_max_tokens = self._positive_int_env("OLLAMA_ANSWER_MAX_TOKENS", 256)

    @staticmethod
    def _resolve_provider() -> str:
        """解析 LLM 提供者；未配置或取值未知时一律回退 ollama，保证离线部署不被误切换。"""
        return resolve_llm_provider()

    @staticmethod
    def _clean_env(name: str) -> str:
        """读取环境变量并剔除误写入的行内注释，避免模型名被 ``key=value  # 注释`` 污染。"""
        raw = (os.getenv(name) or "").strip()
        return raw.split("#", 1)[0].strip() if "#" in raw else raw

    def reasoning_sandwich(
        self, query: str, context: dict[str, Any], references: list[dict[str, Any]] | None = None
    ) -> str:
        """执行规划提示、资料问答和轻量润色的单次本地推理。"""
        if references is None:
            references = self.context_middleware.retrieve(query, context)
        evidence = self._format_evidence(references)
        planning_prompt = (
            "你是红色文旅问答规划器。请基于游客问题和本地 Wiki 证据，提炼回答要点、"
            "需要避免的不确定说法，以及可使用的来源路径。只输出简短规划，不要编造事实。\n"
            f"景区上下文：{context.get('location') or '未指定'}\n"
            f"条目地图：{context.get('wikiEntries', [])}\n"
            f"本地资料：\n{evidence or '（没有检索到相关 Wiki 资料）'}\n\n"
            f"游客问题：{query}"
        )
        plan = self.ollama_client.chat(
            [
                {
                    "role": "system",
                    "content": "你负责高可靠的历史问答规划，必须尊重本地资料边界。",
                },
                {"role": "user", "content": planning_prompt},
            ],
            model=self.reasoning_model,
            stream=False,
            options={"num_predict": self.reasoning_max_tokens},
        )
        if not isinstance(plan, str) or not plan.strip():
            raise RuntimeError("推理规划为空")

        polish_prompt = (
            "你是红色文旅离线讲解员。请根据问题规划和本地 Wiki 资料输出最终回答。"
            "回答必须使用简体中文、简洁易懂；资料不足时明确说‘知识库暂无足够依据’，"
            "不要编造史实、不要输出思考过程、不要虚构 Wiki 路径；"
            "回答正文中不要出现任何以 wiki/ 开头的路径，来源由系统另行标注。\n"
            f"问题规划：{plan.strip()}\n"
            f"本地资料：\n{evidence or '（没有检索到相关 Wiki 资料）'}\n\n"
            f"游客问题：{query}\n\n请给出最终回答。"
        )
        response = self.ollama_client.chat(
            [
                {
                    "role": "system",
                    "content": "你负责将有依据的规划润色成游客可读答案。",
                },
                {"role": "user", "content": polish_prompt},
            ],
            model=self.polish_model,
            stream=False,
            options={"num_predict": self.answer_max_tokens},
        )
        if not isinstance(response, str) or not response.strip():
            raise RuntimeError("Ollama 返回空回答")
        return response.strip()

    def verify_gate(self, answer: str, references: list[dict[str, Any]]) -> bool:
        """拒绝空回答；有引用时确保引用路径来自本次检索结果。"""
        if not isinstance(answer, str) or not answer.strip():
            return False
        allowed_paths = {
            str(reference.get("file_path"))
            for reference in references
            if isinstance(reference, Mapping) and reference.get("file_path")
        }
        if not allowed_paths:
            return True
        # 模型回答中若主动输出 wiki/路径，只允许本次命中的路径。
        mentioned_paths = self._extract_source_paths(answer)
        normalized_allowed = {path.removesuffix(".md") for path in allowed_paths}
        return not mentioned_paths or all(
            path in allowed_paths or path.removesuffix(".md") in normalized_allowed
            for path in mentioned_paths
        )

    @staticmethod
    def _no_evidence_result(
        retrieval_ms: int, inference_ms: int, verification_ms: int
    ) -> dict[str, Any]:
        """构造"资料不足"的降级结果，保持各阶段耗时可观测。"""
        return {
            "answer": NO_EVIDENCE_ANSWER,
            "sources": [],
            STAGE_TIMINGS_KEY: {
                "retrievalMs": retrieval_ms,
                "inferenceMs": inference_ms,
                "verificationMs": verification_ms,
            },
        }

    def update_todo(self, tasks: list[str]) -> None:
        """保留 Harness 状态协议入口；MVP 不写用户目录，避免污染运行环境。"""
        _ = tasks

    def answer(self, question: str, location: str | None = None) -> dict[str, Any]:
        """检索 Wiki 并调用 Ollama，返回 AskService 可直接规范化的结构化结果。"""
        normalized_question = question.strip() if isinstance(question, str) else ""
        if not normalized_question:
            raise ValueError("问题不能为空")

        retrieval_started_at = self._clock()
        context = self.context_middleware.inject_context(location)
        references = self.context_middleware.retrieve(normalized_question, context)
        retrieval_ms = self._elapsed_ms(retrieval_started_at)
        if not references:
            return self._no_evidence_result(retrieval_ms, 0, 0)

        inference_started_at = self._clock()
        answer = self.reasoning_sandwich(normalized_question, context, references)
        inference_ms = self._elapsed_ms(inference_started_at)

        verification_started_at = self._clock()
        if not self.verify_gate(answer, references):
            # 模型可用但本次回答未落地到已检索资料：绝不展示未校验内容，
            # 也不抛异常（否则会被兜底成"模型暂不可用"，掩盖真实原因）。
            verification_ms = self._elapsed_ms(verification_started_at)
            logger.warning(
                "wiki_answer_rejected question=%r allowed=%s mentioned=%s",
                normalized_question,
                sorted(
                    str(reference.get("file_path"))
                    for reference in references
                    if isinstance(reference, Mapping)
                ),
                sorted(self._extract_source_paths(answer)),
            )
            return self._no_evidence_result(retrieval_ms, inference_ms, verification_ms)
        verification_ms = self._elapsed_ms(verification_started_at)
        sources = [
            str(reference["file_path"])
            for reference in references
            if isinstance(reference, Mapping) and reference.get("file_path")
        ]
        return {
            "answer": answer,
            "sources": list(dict.fromkeys(sources)),
            STAGE_TIMINGS_KEY: {
                "retrievalMs": retrieval_ms,
                "inferenceMs": inference_ms,
                "verificationMs": verification_ms,
            },
        }

    def _format_evidence(self, references: list[dict[str, Any]]) -> str:
        chunks: list[str] = []
        remaining = self.max_context_chars
        for reference in references:
            title = str(reference.get("title") or "未命名条目")
            file_path = str(reference.get("file_path") or "")
            content = str(reference.get("content") or "").strip()
            chunk = f"### {title}\n来源：{file_path}\n{content}\n"
            if len(chunk) > remaining:
                chunk = chunk[:remaining]
            chunks.append(chunk)
            remaining -= len(chunk)
            if remaining <= 0:
                break
        return "\n".join(chunks)

    @staticmethod
    def _extract_source_paths(answer: str) -> set[str]:
        """提取模型可能输出的 Wiki 路径，去除中文标点造成的误差。"""
        matches = re.findall(r"wiki/[^\s，。！？；：,\)）\]】]+", answer)
        return {match.rstrip(".。；，,") for match in matches}

    def _elapsed_ms(self, started_at: float) -> int:
        return max(0, round((self._clock() - started_at) * 1000))

    @staticmethod
    def _positive_int_env(name: str, default: int) -> int:
        try:
            value = int(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default
