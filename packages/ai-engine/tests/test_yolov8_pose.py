"""YOLOv8-pose 结果解析与动作规则测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from engine.yolov8_pose import PoseDetector, PoseUnavailableError


def base_keypoints() -> list[list[float]]:
    """构造一组完整的 COCO 人体关键点。"""
    return [
        [100, 50],
        [95, 52],
        [105, 52],
        [90, 55],
        [110, 55],
        [80, 100],
        [120, 100],
        [70, 130],
        [130, 130],
        [65, 160],
        [135, 160],
        [85, 180],
        [115, 180],
        [85, 240],
        [115, 240],
        [85, 300],
        [115, 300],
    ]


class FakeModel:
    def __init__(self, results: list[object]) -> None:
        self.results = results
        self.calls = 0

    def predict(self, **options: object) -> list[object]:
        self.calls += 1
        if options.get("source") != "frame" or options.get("verbose") is not False:
            raise AssertionError(f"推理参数错误: {options}")
        return self.results


def pose_result(points: list[list[float]], confidence: float = 0.94) -> object:
    return SimpleNamespace(
        keypoints=SimpleNamespace(xy=[points], conf=[[0.95] * len(points)]),
        boxes=SimpleNamespace(conf=[confidence]),
    )


class PoseDetectorTest(unittest.TestCase):
    """验证四种动作、输出置信度和缺失依赖降级边界。"""

    def test_classifies_salute_wave_mill_and_unknown(self) -> None:
        detector = PoseDetector(model=FakeModel([]))

        salute = base_keypoints()
        salute[7] = [75, 85]
        salute[9] = [88, 58]

        wave = base_keypoints()
        wave[7] = [60, 70]
        wave[9] = [40, 30]

        mill = base_keypoints()
        mill[7] = [65, 120]
        mill[8] = [135, 120]
        mill[9] = [75, 125]
        mill[10] = [125, 130]

        unknown = base_keypoints()
        unknown[9] = [60, 230]
        unknown[10] = [140, 230]

        self.assertEqual("salute", detector.classify_action(salute))
        self.assertEqual("wave", detector.classify_action(wave))
        self.assertEqual("mill", detector.classify_action(mill))
        self.assertEqual("unknown", detector.classify_action(unknown))

    def test_detect_returns_normalized_action_confidence_and_keypoints(self) -> None:
        points = base_keypoints()
        points[7] = [75, 85]
        points[9] = [88, 58]
        model = FakeModel([pose_result(points)])
        detector = PoseDetector(model=model)

        result = detector.detect("frame")

        self.assertEqual("salute", result["action"])
        self.assertGreaterEqual(result["confidence"], 0.85)
        self.assertLessEqual(result["confidence"], 1.0)
        self.assertEqual([88.0, 58.0], result["keypoints"][9])
        self.assertEqual(1, model.calls)

    def test_detect_without_people_returns_complete_unknown_result(self) -> None:
        detector = PoseDetector(model=FakeModel([]))

        result = detector.detect("frame")

        self.assertEqual(
            {"action": "unknown", "confidence": 0.0, "keypoints": []}, result
        )

    def test_missing_local_weights_returns_unavailable_without_downloading(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing-pose.pt"
            detector = PoseDetector(weights=str(missing))

            with self.assertRaises(PoseUnavailableError):
                detector.load()


if __name__ == "__main__":
    unittest.main()
