"""姿态 WebSocket 消息协议与连接配额测试。"""

from __future__ import annotations

import unittest

from engine.pose_stream import (
    PoseStreamConnectionLimiter,
    PoseStreamMessageTooLargeError,
    PoseStreamProtocol,
    PoseStreamProtocolError,
)


class PoseStreamProtocolTest(unittest.TestCase):
    """验证只接受受控的 frame JSON 消息。"""

    def test_extracts_frame_from_json_object(self) -> None:
        protocol = PoseStreamProtocol(max_message_chars=100)

        frame = protocol.parse_frame('{"frame":"  aW1hZ2U=  ","ignored":true}')

        self.assertEqual("aW1hZ2U=", frame)

    def test_rejects_invalid_json_and_missing_frame(self) -> None:
        protocol = PoseStreamProtocol(max_message_chars=100)

        with self.assertRaises(PoseStreamProtocolError):
            protocol.parse_frame("not-json")
        with self.assertRaises(PoseStreamProtocolError):
            protocol.parse_frame("{}")
        with self.assertRaises(PoseStreamProtocolError):
            protocol.parse_frame('[]')

    def test_rejects_oversized_message(self) -> None:
        protocol = PoseStreamProtocol(max_message_chars=20)

        with self.assertRaises(PoseStreamMessageTooLargeError):
            protocol.parse_frame('{"frame":"12345678901234567890"}')


class PoseStreamConnectionLimiterTest(unittest.TestCase):
    """验证连接数不会超出配置且重复释放安全。"""

    def test_enforces_connection_limit_and_releases_capacity(self) -> None:
        limiter = PoseStreamConnectionLimiter(max_connections=2)

        self.assertTrue(limiter.try_acquire())
        self.assertTrue(limiter.try_acquire())
        self.assertFalse(limiter.try_acquire())
        self.assertEqual(2, limiter.active)

        limiter.release()
        self.assertTrue(limiter.try_acquire())
        limiter.release()
        limiter.release()
        limiter.release()
        self.assertEqual(0, limiter.active)


if __name__ == "__main__":
    unittest.main()
