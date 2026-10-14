"""姿态 WebSocket 消息协议和连接配额。"""

from __future__ import annotations

import json
import os
import threading
from typing import Any


class PoseStreamProtocolError(ValueError):
    """WebSocket 帧消息不符合 ``{"frame": ...}`` 契约。"""


class PoseStreamMessageTooLargeError(PoseStreamProtocolError):
    """WebSocket 文本消息超过上限。"""


class PoseStreamProtocol:
    """校验姿态帧 JSON，避免将任意客户端字段转入推理服务。"""

    def __init__(self, max_message_chars: int | None = None) -> None:
        self.max_message_chars = max_message_chars or self._env_positive_int(
            "POSE_WS_MAX_MESSAGE_CHARS", 14_100_000
        )

    def parse_frame(self, payload: str) -> str:
        """提取非空 Base64 frame；格式和大小错误时抛出协议异常。"""
        if not isinstance(payload, str) or not payload:
            raise PoseStreamProtocolError("消息不能为空")
        if len(payload) > self.max_message_chars:
            raise PoseStreamMessageTooLargeError("姿态帧消息超过大小限制")
        try:
            message: Any = json.loads(payload)
        except (json.JSONDecodeError, TypeError) as exc:
            raise PoseStreamProtocolError("消息必须是 JSON 对象") from exc
        if not isinstance(message, dict):
            raise PoseStreamProtocolError("消息必须是 JSON 对象")
        frame = message.get("frame")
        if not isinstance(frame, str) or not frame.strip():
            raise PoseStreamProtocolError("frame 不能为空")
        if len(frame) > self.max_message_chars:
            raise PoseStreamMessageTooLargeError("姿态帧消息超过大小限制")
        return frame.strip()

    @staticmethod
    def _env_positive_int(name: str, default: int) -> int:
        try:
            value = int(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default


class PoseStreamConnectionLimiter:
    """进程内 WebSocket 连接计数器。"""

    def __init__(self, max_connections: int | None = None) -> None:
        self.max_connections = max_connections or self._env_positive_int(
            "POSE_WS_MAX_CONNECTIONS", 20
        )
        self._active = 0
        self._lock = threading.Lock()

    def try_acquire(self) -> bool:
        """在未超过配额时登记连接。"""
        with self._lock:
            if self._active >= self.max_connections:
                return False
            self._active += 1
            return True

    def release(self) -> None:
        """释放一个已登记连接；重复释放不会产生负数。"""
        with self._lock:
            self._active = max(0, self._active - 1)

    @property
    def active(self) -> int:
        """返回当前活动连接数。"""
        with self._lock:
            return self._active

    @staticmethod
    def _env_positive_int(name: str, default: int) -> int:
        try:
            value = int(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default
