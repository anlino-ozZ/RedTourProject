"""Ollama 本地大模型推理客户端。

客户端对 Ollama SDK 做一层很薄的适配：SDK 只在首次调用时导入，非流式结果统一为
纯文本，向量结果统一为 ``list[float]``，依赖不可用时抛出可识别的领域异常。
"""

from __future__ import annotations

import os
import threading
from collections.abc import Iterable, Iterator, Mapping
from typing import Any


class OllamaError(RuntimeError):
    """Ollama 推理基类异常。"""


class OllamaUnavailableError(OllamaError):
    """SDK 未安装或 Ollama 服务不可达。"""


class OllamaModelError(OllamaError):
    """目标模型不存在或不可用。"""


class OllamaResponseError(OllamaError):
    """Ollama 返回结构缺失或内容为空。"""


class OllamaClient:
    """Ollama 客户端：对话推理与文本向量化。"""

    DEFAULT_HOST = "http://localhost:11434"
    DEFAULT_MODEL = "qwen2.5:7b"

    def __init__(
        self,
        host: str | None = None,
        model: str | None = None,
        client: Any | None = None,
        timeout_seconds: float | None = None,
        keep_alive: str | None = None,
    ) -> None:
        """初始化客户端；``ollama`` SDK 延迟到第一次调用时导入。"""
        self.host = (host or os.getenv("OLLAMA_HOST", self.DEFAULT_HOST)).rstrip("/")
        self.model = model or os.getenv("OLLAMA_MODEL", self.DEFAULT_MODEL)
        self.timeout_seconds = self._positive_float(
            timeout_seconds
            if timeout_seconds is not None
            else os.getenv("OLLAMA_TIMEOUT_SECONDS", "5"),
            5.0,
        )
        self.keep_alive = (
            keep_alive if keep_alive is not None else os.getenv("OLLAMA_KEEP_ALIVE", "30m")
        ).strip()
        self._client: Any | None = client
        self._client_lock = threading.Lock()

    def _get_client(self) -> Any:
        """延迟初始化 Ollama SDK 客户端。"""
        if self._client is not None:
            return self._client
        with self._client_lock:
            if self._client is not None:
                return self._client
            try:
                import ollama
            except ImportError as exc:
                raise OllamaUnavailableError("ollama 库未安装，请先安装本地 Ollama SDK") from exc
            try:
                try:
                    self._client = ollama.Client(host=self.host, timeout=self.timeout_seconds)
                except TypeError as exc:
                    if "timeout" not in str(exc):
                        raise
                    self._client = ollama.Client(host=self.host)
            except Exception as exc:
                raise OllamaUnavailableError("Ollama 客户端初始化失败") from exc
            return self._client

    def chat(
        self,
        messages: list[dict[str, str]],
        model: str | None = None,
        stream: bool = False,
        options: Mapping[str, Any] | None = None,
    ) -> str | Iterator[str]:
        """
        执行对话推理。

        非流式调用返回稳定的纯文本；流式调用返回只产生文本片段的迭代器，绝不把
        Ollama SDK 响应对象泄漏到上层接口。
        """
        normalized_messages = self._normalize_messages(messages)
        selected_model = self._normalize_model(model)
        client = self._get_client()
        try:
            response = self._call_with_keep_alive(
                client.chat,
                {
                    "model": selected_model,
                    "messages": normalized_messages,
                    "stream": stream,
                    "options": dict(options) if options else None,
                },
            )
        except Exception as exc:
            raise self._classify_exception(exc, "Ollama 对话推理失败") from exc
        if stream:
            return self._stream_text(response)
        text = self._extract_text(response)
        if not text:
            raise OllamaResponseError("Ollama 返回空回答")
        return text

    def embedding(self, text: str, model: str | None = None) -> list[float]:
        """将文本向量化并返回稳定的浮点数组。"""
        if not isinstance(text, str) or not text.strip():
            raise OllamaResponseError("embedding 文本不能为空")
        selected_model = self._normalize_model(model)
        client = self._get_client()
        method = getattr(client, "embeddings", None)
        method_name = "embeddings"
        if not callable(method):
            method = getattr(client, "embed", None)
            method_name = "embed"
        if not callable(method):
            raise OllamaResponseError("Ollama 客户端不支持 embedding 接口")
        try:
            if method_name == "embeddings":
                response = self._call_with_keep_alive(
                    method, {"model": selected_model, "prompt": text.strip()}
                )
            else:
                response = self._call_with_keep_alive(
                    method, {"model": selected_model, "input": text.strip()}
                )
        except Exception as exc:
            raise self._classify_exception(exc, "Ollama 向量化失败") from exc
        vector = self._extract_embedding(response)
        if not vector:
            raise OllamaResponseError("Ollama 返回空向量")
        return vector

    def _stream_text(self, response: Any) -> Iterator[str]:
        if isinstance(response, (str, bytes, Mapping)) or not isinstance(response, Iterable):
            text = self._extract_text(response)
            if text:
                yield text
            return
        try:
            for chunk in response:
                text = self._extract_text(chunk)
                if text:
                    yield text
        except Exception as exc:
            raise self._classify_exception(exc, "Ollama 流式推理失败") from exc

    @staticmethod
    def _normalize_messages(messages: list[dict[str, str]]) -> list[dict[str, str]]:
        if not isinstance(messages, list) or not messages:
            raise OllamaResponseError("messages 不能为空")
        normalized: list[dict[str, str]] = []
        for message in messages:
            if not isinstance(message, Mapping):
                raise OllamaResponseError("messages 格式错误")
            role = message.get("role")
            content = message.get("content")
            if not isinstance(role, str) or not role.strip() or not isinstance(content, str):
                raise OllamaResponseError("message 必须包含 role 和 content")
            if not content.strip():
                raise OllamaResponseError("message content 不能为空")
            normalized.append({"role": role.strip(), "content": content.strip()})
        return normalized

    def _normalize_model(self, model: str | None) -> str:
        selected_model = (model or self.model or self.DEFAULT_MODEL).strip()
        if not selected_model:
            raise OllamaModelError("OLLAMA_MODEL 未配置")
        return selected_model

    def _call_with_keep_alive(self, method: Any, arguments: dict[str, Any]) -> Any:
        """请求 Ollama 保持模型常驻，并兼容不支持该参数的旧版 SDK。"""
        clean_arguments = {key: value for key, value in arguments.items() if value is not None}
        if self.keep_alive:
            clean_arguments["keep_alive"] = self.keep_alive
        try:
            return method(**clean_arguments)
        except TypeError as exc:
            if "keep_alive" not in str(exc):
                raise
            clean_arguments.pop("keep_alive", None)
            return method(**clean_arguments)

    @classmethod
    def _extract_text(cls, response: Any) -> str:
        if response is None:
            return ""
        if isinstance(response, str):
            return response.strip()
        if isinstance(response, bytes):
            return response.decode("utf-8", errors="replace").strip()
        if isinstance(response, Mapping):
            for key in ("content", "response", "text"):
                value = response.get(key)
                if isinstance(value, (str, bytes)):
                    return cls._extract_text(value)
            for key in ("message", "response"):
                nested = response.get(key)
                text = cls._extract_text(nested)
                if text:
                    return text
            return ""
        for attribute in ("content", "response", "text"):
            value = getattr(response, attribute, None)
            if isinstance(value, (str, bytes)):
                return cls._extract_text(value)
        nested = getattr(response, "message", None)
        if nested is not None:
            text = cls._extract_text(nested)
            if text:
                return text
        if isinstance(response, (list, tuple)):
            return "".join(cls._extract_text(item) for item in response).strip()
        return ""

    @staticmethod
    def _extract_embedding(response: Any) -> list[float]:
        vector: Any = response
        if isinstance(response, Mapping):
            vector = response.get("embedding") or response.get("embeddings")
        else:
            vector = getattr(response, "embedding", None) or getattr(response, "embeddings", None)
        if isinstance(vector, list) and all(isinstance(value, (int, float)) for value in vector):
            return [float(value) for value in vector]
        return []

    @staticmethod
    def _classify_exception(exception: Exception, prefix: str) -> OllamaError:
        message = str(exception).strip() or exception.__class__.__name__
        lowered = message.lower()
        if any(token in lowered for token in ("not found", "model missing", "no such model")):
            return OllamaModelError(f"{prefix}：目标模型不可用")
        unavailable_tokens = ("connection refused", "timed out", "timeout", "unreachable")
        if isinstance(exception, (ConnectionError, TimeoutError, OSError)) or any(
            token in lowered for token in unavailable_tokens
        ):
            return OllamaUnavailableError(f"{prefix}：Ollama 服务不可达")
        return OllamaError(f"{prefix}：{message}")

    @staticmethod
    def _positive_float(value: float | str, default: float) -> float:
        try:
            numeric = float(value)
            return numeric if numeric > 0 else default
        except (TypeError, ValueError):
            return default
