"""云端大模型推理客户端（OpenAI 兼容 Chat Completions 接口）。

仅在 ``LLM_PROVIDER=cloud`` 时启用，用于开发机联网调试或以云端模型替代本地 Ollama。
树莓派离线部署请保持 ``LLM_PROVIDER=ollama``，本地链路不受本模块影响。

与 ``engine.ollama_infer.OllamaClient`` 保持相同调用形态（``chat`` 方法 + ``model`` 属性），
便于 ``Harness`` 无感替换。实现只依赖标准库，避免为可选能力引入新依赖。
"""

from __future__ import annotations

import json
import os
import ssl
import urllib.error
import urllib.request
from collections.abc import Mapping
from typing import Any


# LLM 提供者：默认 ollama 保持树莓派离线能力，cloud 仅供开发机联网调试
LLM_PROVIDER_OLLAMA = "ollama"
LLM_PROVIDER_CLOUD = "cloud"
CLOUD_PROVIDER_ALIASES = {"cloud", "cloud_llm", "online"}


def resolve_llm_provider() -> str:
    """解析当前生效的 LLM 提供者；未配置或取值未知时一律回退 ollama。"""
    value = os.getenv("LLM_PROVIDER", LLM_PROVIDER_OLLAMA).strip().lower()
    return LLM_PROVIDER_CLOUD if value in CLOUD_PROVIDER_ALIASES else LLM_PROVIDER_OLLAMA


class CloudLlmError(RuntimeError):
    """云端推理基类异常。"""


class CloudLlmConfigError(CloudLlmError):
    """缺少 API Key、Base URL 或模型名等必要配置。"""


class CloudLlmUnavailableError(CloudLlmError):
    """网络不可达、鉴权失败或服务端异常。"""


class CloudLlmResponseError(CloudLlmError):
    """返回结构缺失或内容为空。"""


class CloudLlmClient:
    """云端大模型客户端：仅实现 Harness 所需的非流式对话推理。"""

    # DeepSeek 为默认示例，任何 OpenAI 兼容端点均可直接改 LLM_BASE_URL 接入
    DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    DEFAULT_MODEL = "deepseek-chat"
    DEFAULT_TIMEOUT_SECONDS = 30.0
    CHAT_COMPLETIONS_PATH = "/chat/completions"

    def __init__(
        self,
        api_key: str | None = None,
        base_url: str | None = None,
        model: str | None = None,
        timeout_seconds: float | None = None,
    ) -> None:
        self.api_key = self._clean_env(
            api_key if api_key is not None else os.getenv("LLM_API_KEY", "")
        )
        self.base_url = self._clean_env(
            base_url if base_url is not None else os.getenv("LLM_BASE_URL", self.DEFAULT_BASE_URL)
        ).rstrip("/")
        self.model = self._clean_env(
            model if model is not None else os.getenv("LLM_MODEL", self.DEFAULT_MODEL)
        )
        self.timeout_seconds = self._positive_float(
            timeout_seconds
            if timeout_seconds is not None
            else os.getenv("LLM_TIMEOUT_SECONDS", str(self.DEFAULT_TIMEOUT_SECONDS)),
            self.DEFAULT_TIMEOUT_SECONDS,
        )

    def chat(
        self,
        messages: list[dict[str, str]],
        model: str | None = None,
        stream: bool = False,
        options: Mapping[str, Any] | None = None,
    ) -> str:
        """执行对话推理并返回纯文本。

        ``stream`` 与 ``options`` 仅为与 ``OllamaClient`` 保持签名一致：
        云端统一走非流式，``options`` 中的 ``num_predict`` 等价映射为 ``max_tokens``。
        """
        selected_model = self._normalize_model(model)
        if not self.api_key:
            raise CloudLlmConfigError("未配置 LLM_API_KEY，无法调用云端大模型")
        if not self.base_url:
            raise CloudLlmConfigError("未配置 LLM_BASE_URL，无法调用云端大模型")

        payload: dict[str, Any] = {
            "model": selected_model,
            "messages": [dict(message) for message in messages],
            "stream": False,
        }
        max_tokens = self._extract_max_tokens(options)
        if max_tokens is not None:
            payload["max_tokens"] = max_tokens

        response = self._post_json(payload)
        text = self._extract_text(response)
        if not text:
            raise CloudLlmResponseError("云端模型返回空回答")
        return text

    def is_configured(self) -> bool:
        """是否已具备调用云端模型的最小配置（用于健康检查，不发起真实请求）。"""
        return bool(self.api_key and self.base_url and self.model)

    def _post_json(self, payload: Mapping[str, Any]) -> Any:
        """向 Chat Completions 端点发起 POST 并解析 JSON。"""
        request = urllib.request.Request(
            f"{self.base_url}{self.CHAT_COMPLETIONS_PATH}",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(
                request, timeout=self.timeout_seconds, context=ssl.create_default_context()
            ) as response:
                return json.loads(response.read().decode("utf-8", errors="replace"))
        except urllib.error.HTTPError as exc:
            raise self._classify_http_error(exc) from exc
        except json.JSONDecodeError as exc:
            raise CloudLlmResponseError("云端返回内容不是合法 JSON") from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise CloudLlmUnavailableError(f"云端大模型服务不可达：{self._message(exc)}") from exc

    @classmethod
    def _extract_text(cls, response: Any) -> str:
        """从各家略有差异的返回结构中稳健提取文本内容。"""
        if not isinstance(response, Mapping):
            return ""
        choices = response.get("choices")
        if isinstance(choices, list) and choices:
            first = choices[0]
            if isinstance(first, Mapping):
                message = first.get("message")
                content = (
                    message.get("content") if isinstance(message, Mapping) else first.get("text")
                )
                if isinstance(content, str) and content.strip():
                    return content.strip()
                # 推理型模型（如 DeepSeek 推理系列）会把正文留在 reasoning_content，
                # 且 max_tokens 偏小时 content 可能整个为空，此时用推理内容兜底
                reasoning = (
                    message.get("reasoning_content") if isinstance(message, Mapping) else None
                )
                if isinstance(reasoning, str) and reasoning.strip():
                    return reasoning.strip()
                # 部分厂商返回分段内容数组
                if isinstance(content, list):
                    return "".join(
                        part.get("text", "")
                        for part in content
                        if isinstance(part, Mapping) and isinstance(part.get("text"), str)
                    ).strip()
        direct = response.get("content") or response.get("text")
        return direct.strip() if isinstance(direct, str) else ""

    @classmethod
    def _classify_http_error(cls, exc: urllib.error.HTTPError) -> CloudLlmError:
        """把 HTTP 状态码翻译为可识别的领域异常。"""
        detail = cls._read_error_detail(exc)
        if exc.code in (401, 403):
            return CloudLlmUnavailableError(f"云端鉴权失败（HTTP {exc.code}）：请检查 LLM_API_KEY")
        if exc.code == 429:
            return CloudLlmUnavailableError("云端调用限流（HTTP 429）：请稍后重试")
        if exc.code == 404:
            return CloudLlmUnavailableError(
                f"云端接口或模型不存在（HTTP 404）：请检查 LLM_BASE_URL / LLM_MODEL"
            )
        if exc.code >= 500:
            return CloudLlmUnavailableError(f"云端服务端异常（HTTP {exc.code}）：{detail}")
        return CloudLlmUnavailableError(f"云端调用失败（HTTP {exc.code}）：{detail}")

    @staticmethod
    def _read_error_detail(exc: urllib.error.HTTPError) -> str:
        try:
            detail = exc.read().decode("utf-8", errors="replace").strip()
        except OSError:
            return ""
        return detail[:200]

    @staticmethod
    def _extract_max_tokens(options: Mapping[str, Any] | None) -> int | None:
        """把 Ollama 的 ``num_predict`` 映射为云端的 ``max_tokens``。"""
        if not isinstance(options, Mapping):
            return None
        raw = options.get("max_tokens")
        if raw is None:
            raw = options.get("num_predict")
        try:
            value = int(raw)
        except (TypeError, ValueError):
            return None
        return value if value > 0 else None

    def _normalize_model(self, model: str | None) -> str:
        selected_model = (model or self.model or self.DEFAULT_MODEL).strip()
        if not selected_model:
            raise CloudLlmConfigError("未配置 LLM_MODEL")
        return selected_model

    @staticmethod
    def _message(exception: Exception) -> str:
        return str(exception).strip() or exception.__class__.__name__

    @staticmethod
    def _clean_env(value: str) -> str:
        """剔除 ``key=value  # 注释`` 误写入的行内注释，防止 Key / URL / 模型名被污染。"""
        raw = (value or "").strip()
        return raw.split("#", 1)[0].strip() if "#" in raw else raw

    @staticmethod
    def _positive_float(value: float | str, default: float) -> float:
        try:
            numeric = float(value)
            return numeric if numeric > 0 else default
        except (TypeError, ValueError):
            return default
