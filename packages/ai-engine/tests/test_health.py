"""AI 引擎健康检查测试。"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from engine.health import HealthChecker


class FakeHttpResponse:
    """为 Ollama tags 接口提供最小上下文管理响应。"""

    def __init__(self, payload: dict[str, object]) -> None:
        self.payload = payload

    def __enter__(self) -> FakeHttpResponse:
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def read(self) -> bytes:
        return json.dumps(self.payload).encode("utf-8")


class HealthCheckerTest(unittest.TestCase):
    """验证正常状态及依赖缺失时的安全降级。"""

    def test_returns_ok_with_active_model_and_optional_hailo_disabled(self) -> None:
        def opener(*_args: object, **_kwargs: object) -> FakeHttpResponse:
            return FakeHttpResponse({"models": [{"name": "qwen2.5:7b"}]})

        with tempfile.TemporaryDirectory() as directory:
            wiki_dir = Path(directory)
            (wiki_dir / "entry.md").write_text("# 条目", encoding="utf-8")
            checker = HealthChecker(
                ollama_model="qwen2.5:7b",
                wiki_build_dir=wiki_dir,
                hailo_enabled=False,
                url_opener=opener,
            )

            snapshot = checker.check()

        self.assertEqual("ok", snapshot["status"])
        self.assertEqual("active", snapshot["ollama"])
        self.assertEqual("disabled", snapshot["hailo"])
        self.assertEqual(1, snapshot["wikiCount"])

    def test_missing_ollama_model_returns_degraded_without_exception(self) -> None:
        def opener(*_args: object, **_kwargs: object) -> FakeHttpResponse:
            return FakeHttpResponse({"models": []})

        checker = HealthChecker(hailo_enabled=False, url_opener=opener)

        snapshot = checker.check()

        self.assertEqual("degraded", snapshot["status"])
        self.assertEqual("model_missing", snapshot["ollama"])
        self.assertEqual(0, snapshot["wikiCount"])

    def test_unavailable_dependencies_return_degraded_without_exception(self) -> None:
        def unavailable_opener(*_args: object, **_kwargs: object) -> FakeHttpResponse:
            raise OSError("connection refused")

        checker = HealthChecker(hailo_enabled=True, url_opener=unavailable_opener)
        with patch("engine.health.importlib.import_module", side_effect=ImportError):
            snapshot = checker.check()

        self.assertEqual("degraded", snapshot["status"])
        self.assertEqual("unavailable", snapshot["ollama"])
        self.assertEqual("unavailable", snapshot["hailo"])

    def test_malformed_ollama_response_is_safely_degraded(self) -> None:
        def opener(*_args: object, **_kwargs: object) -> FakeHttpResponse:
            return FakeHttpResponse({"models": "invalid"})

        checker = HealthChecker(hailo_enabled=False, url_opener=opener)

        snapshot = checker.check()

        self.assertEqual("degraded", snapshot["status"])
        self.assertEqual("unavailable", snapshot["ollama"])


if __name__ == "__main__":
    unittest.main()
