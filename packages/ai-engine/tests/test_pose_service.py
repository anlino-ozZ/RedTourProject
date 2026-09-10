"""Base64 姿态识别服务测试。"""

from __future__ import annotations

import base64
import io
import unittest

from PIL import Image

from engine.pose_service import PoseFrameDecoder, PoseService, PoseValidationError
from engine.yolov8_pose import PoseUnavailableError


def encoded_image(width: int = 2, height: int = 2, image_format: str = "PNG") -> str:
    output = io.BytesIO()
    Image.new("RGB", (width, height), color=(180, 20, 20)).save(output, format=image_format)
    return base64.b64encode(output.getvalue()).decode("ascii")


class FakeDecoder:
    def __init__(self) -> None:
        self.frames: list[str] = []

    def decode(self, frame: str) -> str:
        self.frames.append(frame)
        return "decoded-frame"


class FakeDetector:
    def __init__(self, result: dict[str, object] | None = None) -> None:
        self.result = result or {
            "action": "salute",
            "confidence": 0.92,
            "keypoints": [[120, 80], [135, 85]],
        }
        self.frames: list[object] = []

    def detect(self, frame: object) -> dict[str, object]:
        self.frames.append(frame)
        return self.result


class FailedDetector:
    def detect(self, _frame: object) -> dict[str, object]:
        raise PoseUnavailableError("model missing")


class PoseFrameDecoderTest(unittest.TestCase):
    """验证纯 Base64、Data URL、真实格式、大小和像素限制。"""

    def test_accepts_raw_base64_and_data_url(self) -> None:
        decoder = PoseFrameDecoder()
        encoded = encoded_image()

        raw_image = decoder.decode(encoded)
        data_url_image = decoder.decode(f"data:image/png;base64,{encoded}")

        self.assertEqual((2, 2, 3), raw_image.shape)
        self.assertEqual((2, 2, 3), data_url_image.shape)

    def test_rejects_invalid_base64_data_url_and_non_image(self) -> None:
        decoder = PoseFrameDecoder()

        with self.assertRaises(PoseValidationError):
            decoder.decode("not-base64!!")
        with self.assertRaises(PoseValidationError):
            decoder.decode(f"data:text/plain;base64,{encoded_image()}")
        with self.assertRaises(PoseValidationError):
            decoder.decode(base64.b64encode(b"not-an-image").decode("ascii"))

    def test_rejects_oversized_or_excessive_pixel_image(self) -> None:
        with self.assertRaises(PoseValidationError):
            PoseFrameDecoder(max_bytes=2).decode(encoded_image())
        with self.assertRaises(PoseValidationError):
            PoseFrameDecoder(max_pixels=3).decode(encoded_image(2, 2))


class PoseServiceTest(unittest.TestCase):
    """验证响应契约、触发内容和模型缺失降级。"""

    def test_recognized_action_contains_trigger_and_timestamp(self) -> None:
        decoder = FakeDecoder()
        detector = FakeDetector()
        service = PoseService(
            detector=detector,
            decoder=decoder,
            timestamp_provider=lambda: 1_730_000_000_000,
        )

        result = service.recognize("encoded", scenic_area_id=1)

        self.assertEqual("salute", result["action"])
        self.assertEqual(0.92, result["confidence"])
        self.assertEqual("军礼的由来", result["triggerContent"]["title"])
        self.assertEqual("wiki/军礼", result["triggerContent"]["wikiRef"])
        self.assertEqual(1_730_000_000_000, result["timestamp"])
        self.assertEqual(["encoded"], decoder.frames)
        self.assertEqual(["decoded-frame"], detector.frames)

    def test_missing_model_returns_complete_unknown_result(self) -> None:
        service = PoseService(
            detector=FailedDetector(),
            decoder=FakeDecoder(),
            timestamp_provider=lambda: 123,
        )

        result = service.recognize("encoded")

        self.assertEqual(
            {
                "action": "unknown",
                "confidence": 0.0,
                "keypoints": [],
                "triggerContent": None,
                "timestamp": 123,
            },
            result,
        )

    def test_invalid_detector_values_are_normalized(self) -> None:
        detector = FakeDetector(
            {"action": "other", "confidence": 2, "keypoints": [[1, 2], ["x", 3]]}
        )
        service = PoseService(
            detector=detector,
            decoder=FakeDecoder(),
            timestamp_provider=lambda: 1,
        )

        result = service.recognize("encoded")

        self.assertEqual("unknown", result["action"])
        self.assertEqual(0.0, result["confidence"])
        self.assertEqual([[1.0, 2.0]], result["keypoints"])
        self.assertIsNone(result["triggerContent"])


if __name__ == "__main__":
    unittest.main()
