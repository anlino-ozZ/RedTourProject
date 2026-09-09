"""本地问答 Harness。

MVP 版本实现本地上下文注入和受控 Wiki 检索，使用 Ollama 进行非流式中文问答。
所有外部依赖均可替换/延迟加载，调用失败由 ``AskService`` 统一降级。
"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any, Mapping

from engine.llm_wiki import WikiCompiler
from engine.ollama_infer import OllamaClient


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
        return {
            "location": location,
            "scenicAreaId": scenic_area_id,
            "wikiBuildDir": str(self.compiler.build_dir),
            "wikiCount": self._wiki_count(),
            "instructions": "仅依据本地 Wiki 资料回答；资料不足时明确说明知识库暂无依据。",
        }

    def retrieve(
        self, query: str, context: Mapping[str, Any], limit: int = 4
    ) -> list[dict[str, Any]]:
        """检索与问题相关的少量 Wiki 条目，限制传给模型的上下文规模。"""
        scenic_area_id = context.get("scenicAreaId")
        references = self.compiler.retrieve(query, scenic_area_id=scenic_area_id)
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

    def _wiki_count(self) -> int:
        try:
            return sum(1 for path in self.compiler.build_dir.rglob("*.md") if path.is_file())
        except OSError:
            return 0


class Harness:
    """AI 推理机架：Wiki 检索 + Ollama 对话 + 最小验证门。"""

    MAX_CONTEXT_CHARS = 6000

    def __init__(
        self,
        ollama_client: OllamaClient | None = None,
        context_middleware: LocalContextMiddleware | None = None,
    ) -> None:
        self.context_middleware = context_middleware or LocalContextMiddleware()
        self.ollama_client = ollama_client or OllamaClient()

    def reasoning_sandwich(
        self, query: str, context: dict[str, Any], references: list[dict[str, Any]] | None = None
    ) -> str:
        """执行规划提示、资料问答和轻量润色的单次本地推理。"""
        if references is None:
            references = self.context_middleware.retrieve(query, context)
        evidence = self._format_evidence(references)
        system_prompt = (
            "你是红色文旅离线讲解员。回答必须使用简体中文，优先依据给定 Wiki 资料。"
            "如果资料没有回答问题，请明确说‘知识库暂无足够依据’，不要编造史实。"
            "不要输出 JSON、不要输出思考过程、不要虚构 Wiki 路径。"
        )
        user_prompt = (
            f"景区上下文：{context.get('location') or '未指定'}\n"
            f"本地资料：\n{evidence or '（没有检索到相关 Wiki 资料）'}\n\n"
            f"游客问题：{query}\n\n请给出简洁、适合游客阅读的回答。"
        )
        response = self.ollama_client.chat(
            [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            stream=False,
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
        mentioned_paths = set(re.findall(r"wiki/[\w\-一-龥/ .]+", answer))
        return not mentioned_paths or mentioned_paths.issubset(allowed_paths)

    def update_todo(self, tasks: list[str]) -> None:
        """保留 Harness 状态协议入口；MVP 不写用户目录，避免污染运行环境。"""
        _ = tasks

    def answer(self, question: str, location: str | None = None) -> dict[str, Any]:
        """检索 Wiki 并调用 Ollama，返回 AskService 可直接规范化的结构化结果。"""
        normalized_question = question.strip() if isinstance(question, str) else ""
        if not normalized_question:
            raise ValueError("问题不能为空")
        context = self.context_middleware.inject_context(location)
        references = self.context_middleware.retrieve(normalized_question, context)
        if not references:
            return {
                "answer": "知识库暂无足够依据，暂时无法可靠回答这个问题。",
                "sources": [],
            }
        answer = self.reasoning_sandwich(normalized_question, context, references)
        if not self.verify_gate(answer, references):
            raise RuntimeError("回答引用未通过验证")
        sources = [
            str(reference["file_path"])
            for reference in references
            if isinstance(reference, Mapping) and reference.get("file_path")
        ]
        return {"answer": answer, "sources": list(dict.fromkeys(sources))}

    def _format_evidence(self, references: list[dict[str, Any]]) -> str:
        chunks: list[str] = []
        remaining = self.MAX_CONTEXT_CHARS
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
