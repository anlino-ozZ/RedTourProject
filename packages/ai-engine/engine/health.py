"""AI 引擎运行依赖健康检查。"""

from __future__ import annotations

import importlib
import json
import os
from pathlib import Path
from typing import Any, Callable, TypedDict
from urllib.request import urlopen


class HealthSnapshot(TypedDict):
    """``/engine/health`` 接口响应。"""

    status: str
    engine: str
    ollama: str
    hailo: str
    wikiCount: int


class HealthChecker:
    """探测 Ollama、Hailo8L 和已编译 Wiki，不向接口传播探测异常。"""

    def __init__(
        self,
        ollama_host: str | None = None,
        ollama_model: str | None = None,
        wiki_build_dir: str | Path | None = None,
        hailo_pose_hef: str | Path | None = None,
        hailo_enabled: bool | None = None,
        timeout_seconds: float | None = None,
        url_opener: Callable[..., Any] = urlopen,
    ) -> None:
        configured_ollama_host = ollama_host or os.getenv(
            "OLLAMA_HOST", "http://localhost:11434"
        )
        self.ollama_host = configured_ollama_host.rstrip("/")
        self.ollama_model = ollama_model or os.getenv("OLLAMA_MODEL", "qwen2.5:7b")
        self.wiki_build_dir = Path(
            wiki_build_dir or os.getenv("WIKI_BUILD_DIR", "./wiki_build")
        )
        self.hailo_pose_hef = Path(
            hailo_pose_hef
            or os.getenv("HAILO_POSE_HEF", "./weights/yolov8n_pose_h8l.hef")
        ).expanduser()
        if hailo_enabled is None:
            hailo_enabled = os.getenv("HAILO_ENABLED", "false").strip().lower() in {
                "1",
                "true",
                "yes",
                "on",
            }
        self.hailo_enabled = hailo_enabled
        configured_timeout: float | str = (
            timeout_seconds
            if timeout_seconds is not None
            else os.getenv("HEALTH_CHECK_TIMEOUT_SECONDS", "1.0")
        )
        try:
            self.timeout_seconds = max(float(configured_timeout), 0.1)
        except (TypeError, ValueError):
            self.timeout_seconds = 1.0
        self._url_opener = url_opener

    def check(self) -> HealthSnapshot:
        """返回当前快照；依赖缺失时使用 ``degraded``，但不抛出异常。"""
        ollama_status = self._check_ollama()
        hailo_status = self._check_hailo()
        degraded = ollama_status != "active" or (
            self.hailo_enabled and hailo_status != "active"
        )
        return {
            "status": "degraded" if degraded else "ok",
            "engine": "ai-engine",
            "ollama": ollama_status,
            "hailo": hailo_status,
            "wikiCount": self._count_wiki_entries(),
        }

    def _check_ollama(self) -> str:
        """探测 Ollama 服务及配置的模型是否已安装。"""
        try:
            with self._url_opener(
                f"{self.ollama_host}/api/tags", timeout=self.timeout_seconds
            ) as response:
                payload = json.loads(response.read().decode("utf-8"))
            if not isinstance(payload, dict) or not isinstance(payload.get("models"), list):
                return "unavailable"
            installed_models = {
                str(model.get("name") or model.get("model"))
                for model in payload["models"]
                if isinstance(model, dict) and (model.get("name") or model.get("model"))
            }
            if self.ollama_model not in installed_models:
                return "model_missing"
            return "active"
        # 第三方服务的异常类型并不稳定，健康接口必须兜底而不能拖垮应用。
        except Exception:
            return "unavailable"

    def _check_hailo(self) -> str:
        """Hailo 未启用时返回 disabled；启用后检查设备和 HEF 姿态模型。"""
        if not self.hailo_enabled:
            return "disabled"
        try:
            hailo_platform = importlib.import_module("hailo_platform")
            device_type = getattr(hailo_platform, "Device")
            if not device_type.scan() or not self.hailo_pose_hef.is_file():
                return "unavailable"
            return "active"
        # Hailo SDK 在不同版本和硬件状态下可能抛出不同异常，统一降级。
        except Exception:
            return "unavailable"

    def _count_wiki_entries(self) -> int:
        """统计编译目录中的 Markdown 条目；目录未初始化时按零处理。"""
        try:
            return sum(1 for path in self.wiki_build_dir.rglob("*.md") if path.is_file())
        except OSError:
            return 0
