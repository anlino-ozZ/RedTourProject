"""Base64 图像校验、姿态推理编排与触发内容映射。"""

from __future__ import annotations

import base64
import binascii
import io
import logging
import math
import os
from collections.abc import Callable, Mapping
from time import time
from typing import Any, TypedDict

from engine.yolov8_pose import PoseDetector, PoseError

logger = logging.getLogger(__name__)


class PoseValidationError(ValueError):
    """Base64 图像格式、大小或尺寸不符合要求。"""


class PoseDependencyError(RuntimeError):
    """图像解码运行依赖不可用。"""


class PoseTriggerContent(TypedDict):
    """动作命中后的互动内容。"""

    title: str
    audioUrl: str
    wikiRef: str


class PoseResult(TypedDict):
    """``/engine/pose`` 稳定响应字段。"""

    action: str
    confidence: float
    keypoints: list[list[float]]
    triggerContent: PoseTriggerContent | None
    timestamp: int


class PoseFrameDecoder:
    """将纯 Base64 或图片 Data URL 解码为受控 RGB 数组。"""

    ALLOWED_MEDIA_TYPES = frozenset({"image/jpeg", "image/jpg", "image/png", "image/webp"})
    ALLOWED_FORMATS = frozenset({"JPEG", "PNG", "WEBP"})

    def __init__(self, max_bytes: int | None = None, max_pixels: int | None = None) -> None:
        self.max_bytes = max_bytes or self._env_positive_int("POSE_MAX_IMAGE_MB", 10) * 1024 * 1024
        self.max_pixels = max_pixels or self._env_positive_int("POSE_MAX_PIXELS", 12_000_000)

    def decode(self, frame: str) -> Any:
        """校验编码和真实图片内容，返回 RGB numpy 数组。"""
        if not isinstance(frame, str) or not frame.strip():
            raise PoseValidationError("frame 不能为空")
        media_type, encoded = self._split_frame(frame.strip())
        compact = "".join(encoded.split())
        max_encoded_length = 4 * ((self.max_bytes + 2) // 3) + 4
        if not compact or len(compact) > max_encoded_length:
            raise PoseValidationError(
                f"图片解码后不能超过 {self.max_bytes // (1024 * 1024)}MB"
            )
        try:
            raw = base64.b64decode(compact, validate=True)
        except (binascii.Error, ValueError) as exc:
            raise PoseValidationError("frame 不是有效的 Base64 图片") from exc
        if not raw:
            raise PoseValidationError("图片内容不能为空")
        if len(raw) > self.max_bytes:
            raise PoseValidationError(
                f"图片解码后不能超过 {self.max_bytes // (1024 * 1024)}MB"
            )
        return self._decode_image(raw, media_type)

    def _split_frame(self, frame: str) -> tuple[str | None, str]:
        if not frame.lower().startswith("data:"):
            return None, frame
        header, separator, encoded = frame.partition(",")
        if not separator or not header.lower().endswith(";base64"):
            raise PoseValidationError("图片 Data URL 格式错误")
        media_type = header[5:-7].strip().lower()
        if media_type not in self.ALLOWED_MEDIA_TYPES:
            raise PoseValidationError("仅支持 JPEG、PNG 或 WebP 图片")
        if media_type == "image/jpg":
            media_type = "image/jpeg"
        return media_type, encoded

    def _decode_image(self, raw: bytes, declared_media_type: str | None) -> Any:
        try:
            import numpy as np
            from PIL import Image, UnidentifiedImageError
        except ImportError as exc:
            raise PoseDependencyError("图片解码依赖未安装") from exc

        try:
            with Image.open(io.BytesIO(raw)) as image:
                image_format = (image.format or "").upper()
                width, height = image.size
                if image_format not in self.ALLOWED_FORMATS:
                    raise PoseValidationError("仅支持 JPEG、PNG 或 WebP 图片")
                if width <= 0 or height <= 0 or width * height > self.max_pixels:
                    raise PoseValidationError("图片尺寸无效或像素数超过限制")
                if declared_media_type is not None:
                    actual_media_type = "image/jpeg" if image_format == "JPEG" else (
                        f"image/{image_format.lower()}"
                    )
                    if actual_media_type != declared_media_type:
                        raise PoseValidationError("图片内容与 Data URL 类型不一致")
                image.load()
                rgb_image = image.convert("RGB")
                return np.asarray(rgb_image)
        except PoseValidationError:
            raise
        except (Image.DecompressionBombError, UnidentifiedImageError, OSError, ValueError) as exc:
            raise PoseValidationError("frame 不是有效图片") from exc

    @staticmethod
    def _env_positive_int(name: str, default: int) -> int:
        try:
            value = int(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default


class PoseService:
    """编排图片解码、姿态检测、稳定降级和互动内容映射。"""

    ALLOWED_ACTIONS = frozenset({"salute", "mill", "wave", "unknown"})
    DEFAULT_TRIGGERS: dict[str, PoseTriggerContent] = {
        "salute": {
            "title": "军礼的由来",
            "audioUrl": "/audio/pose/salute.mp3",
            "wikiRef": "wiki/军礼",
        },
        "mill": {
            "title": "红军石磨体验",
            "audioUrl": "/audio/pose/mill.mp3",
            "wikiRef": "wiki/红军石磨",
        },
        "wave": {
            "title": "挥手互动",
            "audioUrl": "/audio/pose/wave.mp3",
            "wikiRef": "wiki/挥手互动",
        },
    }

    def __init__(
        self,
        detector: PoseDetector | None = None,
        decoder: PoseFrameDecoder | None = None,
        timestamp_provider: Callable[[], int] | None = None,
        trigger_resolver: Callable[[str, int | None], PoseTriggerContent | None] | None = None,
    ) -> None:
        self.detector = detector or PoseDetector()
        self.decoder = decoder or PoseFrameDecoder()
        self._timestamp_provider = timestamp_provider or (lambda: round(time() * 1000))
        self._trigger_resolver = trigger_resolver or self._default_trigger

    def recognize(self, frame: str, scenic_area_id: int | None = None) -> PoseResult:
        """识别单帧；模型不可用时返回结构完整的 unknown 结果。"""
        try:
            image = self.decoder.decode(frame)
        except PoseValidationError:
            raise
        except PoseDependencyError as exc:
            logger.warning("图片解码依赖不可用，返回 unknown 降级结果: %s", exc)
            return self._unknown_result()
        try:
            raw_result = self.detector.detect(image)
            action = self._normalize_action(raw_result.get("action"))
            confidence = self._normalize_confidence(raw_result.get("confidence"))
            keypoints = self._normalize_keypoints(raw_result.get("keypoints"))
            if action == "unknown":
                confidence = 0.0
        except (PoseError, PoseDependencyError, OSError, ValueError, TypeError) as exc:
            logger.warning("姿态识别不可用，返回 unknown 降级结果: %s", exc)
            action, confidence, keypoints = "unknown", 0.0, []
        except Exception:
            logger.exception("姿态识别发生未预期错误，返回 unknown 降级结果")
            action, confidence, keypoints = "unknown", 0.0, []

        trigger_content = (
            self._trigger_resolver(action, scenic_area_id) if action != "unknown" else None
        )
        return {
            "action": action,
            "confidence": confidence,
            "keypoints": keypoints,
            "triggerContent": trigger_content,
            "timestamp": max(0, int(self._timestamp_provider())),
        }

    def _default_trigger(
        self, action: str, _scenic_area_id: int | None
    ) -> PoseTriggerContent | None:
        trigger = self.DEFAULT_TRIGGERS.get(action)
        if trigger is None:
            return None
        return {
            "title": trigger["title"],
            "audioUrl": trigger["audioUrl"],
            "wikiRef": trigger["wikiRef"],
        }

    def _unknown_result(self) -> PoseResult:
        return {
            "action": "unknown",
            "confidence": 0.0,
            "keypoints": [],
            "triggerContent": None,
            "timestamp": max(0, int(self._timestamp_provider())),
        }

    def _normalize_action(self, value: Any) -> str:
        action = value.strip().lower() if isinstance(value, str) else "unknown"
        return action if action in self.ALLOWED_ACTIONS else "unknown"

    @staticmethod
    def _normalize_confidence(value: Any) -> float:
        if not isinstance(value, (int, float)) or not math.isfinite(float(value)):
            return 0.0
        return round(max(0.0, min(1.0, float(value))), 4)

    @staticmethod
    def _normalize_keypoints(value: Any) -> list[list[float]]:
        if not isinstance(value, list):
            return []
        normalized: list[list[float]] = []
        for point in value:
            if not isinstance(point, (list, tuple)) or len(point) < 2:
                continue
            x, y = point[0], point[1]
            if not isinstance(x, (int, float)) or not isinstance(y, (int, float)):
                continue
            if math.isfinite(float(x)) and math.isfinite(float(y)):
                normalized.append([round(float(x), 2), round(float(y), 2)])
        return normalized
