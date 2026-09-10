"""Hailo8L 姿态后端缓存、解析和吞吐打点测试。"""

from __future__ import annotations

import unittest

import numpy as np

from engine.yolov8_pose import PoseDetector, PosePerformanceTracker


def salute_keypoints() -> list[list[float]]:
    return [
        [100, 50], [95, 52], [105, 52], [90, 55], [110, 55],
        [80, 100], [120, 100], [75, 85], [130, 130], [88, 58],
        [135, 160], [85, 180], [115, 180], [85, 240], [115, 240],
        [85, 300], [115, 300],
    ]


class FakeClock:
    def __init__(self, *values: float) -> None:
        self._values = iter(values)

    def __call__(self) -> float:
        return next(self._values)


class FakeHailoRuntime:
    input_size = (64, 64)

    def __init__(self) -> None:
        self.calls = 0

    def infer(self, tensor: object) -> dict[str, object]:
        self.calls += 1
        if getattr(tensor, "shape", None) != (1, 64, 64, 3):
            raise AssertionError(f"Hailo 输入尺寸错误: {getattr(tensor, 'shape', None)}")
        row = [0.0, 0.0, 1.0, 1.0, 0.94]
        for x, y in salute_keypoints():
            row.extend([y / 400, x / 400, 0.95])
        return {"pose_nms": [[[row]]]}


class FakeCpuModel:
    def __init__(self) -> None:
        self.calls = 0

    def predict(self, **_options: object) -> list[object]:
        self.calls += 1
        return []


class HailoPoseBackendTest(unittest.TestCase):
    """验证 Hailo 运行时只加载一次，并产生可分类的关键点。"""

    def test_reuses_runtime_and_reports_hailo_fps(self) -> None:
        runtime = FakeHailoRuntime()
        factory_calls: list[str] = []

        def runtime_factory(path: str) -> FakeHailoRuntime:
            factory_calls.append(path)
            return runtime

        tracker = PosePerformanceTracker(target_fps=30, window_frames=2)
        detector = PoseDetector(
            hailo_enabled=True,
            hailo_runtime_factory=runtime_factory,
            performance_tracker=tracker,
            clock=FakeClock(0.0, 0.01, 0.02, 0.03),
        )
        frame = np.zeros((400, 400, 3), dtype=np.uint8)

        first = detector.detect(frame)
        second = detector.detect(frame)

        self.assertEqual("salute", first["action"])
        self.assertEqual("salute", second["action"])
        self.assertGreaterEqual(first["confidence"], 0.85)
        self.assertEqual(1, len(factory_calls))
        self.assertEqual(2, runtime.calls)
        self.assertAlmostEqual(100.0, tracker.last_fps)

    def test_unavailable_hailo_falls_back_to_cpu_model(self) -> None:
        cpu_model = FakeCpuModel()

        def failed_runtime_factory(_path: str) -> object:
            raise RuntimeError("Hailo device unavailable")

        detector = PoseDetector(
            hailo_enabled=True,
            hailo_runtime_factory=failed_runtime_factory,
            model_factory=lambda _path: cpu_model,
        )

        result = detector.detect("frame")

        self.assertEqual("unknown", result["action"])
        self.assertEqual(1, cpu_model.calls)


if __name__ == "__main__":
    unittest.main()
