"""YOLOv8-pose 本地姿态检测与动作规则。"""

from __future__ import annotations

import logging
import math
import os
import threading
from collections.abc import Callable, Sequence
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)


class PoseError(RuntimeError):
    """姿态检测基础异常。"""


class PoseUnavailableError(PoseError):
    """姿态模型或运行依赖不可用。"""


class PoseInferenceError(PoseError):
    """姿态模型推理或结果解析失败。"""


ModelFactory = Callable[[str], Any]


class PoseDetector:
    """延迟加载 YOLOv8-pose，并将 COCO 关键点归类为互动动作。"""

    KEYPOINT_CONFIDENCE_THRESHOLD = 0.25

    def __init__(
        self,
        weights: str | None = None,
        hailo_enabled: bool | None = None,
        model: Any | None = None,
        model_factory: ModelFactory | None = None,
    ) -> None:
        self.weights = weights or os.getenv("YOLO_WEIGHTS", "./weights/yolov8s-pose.pt")
        self.hailo_enabled = (
            hailo_enabled
            if hailo_enabled is not None
            else os.getenv("HAILO_ENABLED", "false").strip().lower()
            in {"1", "true", "yes", "on"}
        )
        self.model = model
        self._model_factory = model_factory
        self._load_lock = threading.Lock()
        self._inference_lock = threading.Lock()

    def load(self) -> Any:
        """加载本地姿态权重；依赖和权重缺失时抛出可识别异常。"""
        if self.model is not None:
            return self.model
        with self._load_lock:
            if self.model is not None:
                return self.model
            try:
                if self._model_factory is not None:
                    self.model = self._model_factory(self.weights)
                    return self.model

                if self.hailo_enabled:
                    try:
                        import hailo_platform  # noqa: F401
                    except ImportError:
                        logger.warning("Hailo SDK 不可用，姿态识别回退到本地 YOLO CPU 推理")

                weight_path = Path(self.weights).expanduser().resolve()
                if not weight_path.is_file():
                    raise PoseUnavailableError("本地 YOLOv8-pose 权重不存在")
                try:
                    from ultralytics import YOLO
                except ImportError as exc:
                    raise PoseUnavailableError("ultralytics 未安装") from exc
                self.model = YOLO(str(weight_path))
                return self.model
            except PoseError:
                raise
            except Exception as exc:
                raise PoseUnavailableError("姿态模型加载失败") from exc

    def detect(self, frame: Any) -> dict[str, Any]:
        """检测单帧中的主要人物，并返回动作、置信度和二维关键点。"""
        model = self.load()
        try:
            with self._inference_lock:
                results = model.predict(source=frame, verbose=False)
            result = self._first_result(results)
            if result is None:
                return self._unknown_result()
            points, scores, detection_confidence = self._extract_person(result)
            if not points:
                return self._unknown_result()
            action, rule_confidence = self._classify_with_confidence(points, scores)
            if action == "unknown":
                return {"action": "unknown", "confidence": 0.0, "keypoints": points}
            base_confidence = detection_confidence or self._mean_confidence(scores)
            confidence = self._clamp(base_confidence * (0.8 + 0.2 * rule_confidence))
            return {
                "action": action,
                "confidence": round(confidence, 4),
                "keypoints": points,
            }
        except PoseError:
            raise
        except Exception as exc:
            raise PoseInferenceError("姿态模型推理失败") from exc

    def classify_action(self, keypoints: Any) -> str:
        """根据 COCO 关键点坐标判定 salute、mill、wave 或 unknown。"""
        points = self._normalize_points(keypoints)
        action, _confidence = self._classify_with_confidence(points, [])
        return action

    def _classify_with_confidence(
        self,
        points: list[list[float]],
        scores: list[float],
    ) -> tuple[str, float]:
        if len(points) < 13:
            return "unknown", 0.0
        left_shoulder = self._point(points, scores, 5)
        right_shoulder = self._point(points, scores, 6)
        left_hip = self._point(points, scores, 11)
        right_hip = self._point(points, scores, 12)
        if not all((left_shoulder, right_shoulder, left_hip, right_hip)):
            return "unknown", 0.0

        shoulder_mid = self._midpoint(left_shoulder, right_shoulder)
        hip_mid = self._midpoint(left_hip, right_hip)
        scale = max(
            self._distance(left_shoulder, right_shoulder),
            self._distance(shoulder_mid, hip_mid),
            1.0,
        )

        salute_score = self._salute_score(points, scores, scale)
        if salute_score > 0:
            return "salute", salute_score
        wave_score = self._wave_score(points, scores, scale)
        if wave_score > 0:
            return "wave", wave_score
        mill_score = self._mill_score(points, scores, scale, shoulder_mid, hip_mid)
        if mill_score > 0:
            return "mill", mill_score
        return "unknown", 0.0

    def _salute_score(
        self, points: list[list[float]], scores: list[float], scale: float
    ) -> float:
        candidates = ((9, 3, 7, 5), (10, 4, 8, 6))
        best = 0.0
        for wrist_index, ear_index, elbow_index, shoulder_index in candidates:
            wrist = self._point(points, scores, wrist_index)
            ear = self._point(points, scores, ear_index)
            elbow = self._point(points, scores, elbow_index)
            shoulder = self._point(points, scores, shoulder_index)
            if not all((wrist, ear, elbow, shoulder)):
                continue
            head_distance = self._distance(wrist, ear) / scale
            hand_is_raised = wrist[1] <= shoulder[1] + 0.1 * scale
            elbow_supports_pose = elbow[1] >= wrist[1] - 0.15 * scale
            if head_distance <= 0.55 and hand_is_raised and elbow_supports_pose:
                best = max(best, 1.0 - head_distance / 0.55)
        return self._clamp(0.65 + 0.35 * best) if best > 0 else 0.0

    def _wave_score(
        self, points: list[list[float]], scores: list[float], scale: float
    ) -> float:
        candidates = ((9, 5), (10, 6))
        best = 0.0
        for wrist_index, shoulder_index in candidates:
            wrist = self._point(points, scores, wrist_index)
            shoulder = self._point(points, scores, shoulder_index)
            if wrist is None or shoulder is None:
                continue
            lift = (shoulder[1] - wrist[1]) / scale
            if lift >= 0.45:
                best = max(best, min(1.0, lift))
        return self._clamp(0.65 + 0.35 * best) if best > 0 else 0.0

    def _mill_score(
        self,
        points: list[list[float]],
        scores: list[float],
        scale: float,
        shoulder_mid: list[float],
        hip_mid: list[float],
    ) -> float:
        left_wrist = self._point(points, scores, 9)
        right_wrist = self._point(points, scores, 10)
        left_elbow = self._point(points, scores, 7)
        right_elbow = self._point(points, scores, 8)
        if not all((left_wrist, right_wrist, left_elbow, right_elbow)):
            return 0.0
        upper_y = shoulder_mid[1] - 0.15 * scale
        lower_y = hip_mid[1] + 0.15 * scale
        if not (upper_y <= left_wrist[1] <= lower_y and upper_y <= right_wrist[1] <= lower_y):
            return 0.0
        vertical_gap = abs(left_wrist[1] - right_wrist[1]) / scale
        wrist_gap = self._distance(left_wrist, right_wrist) / scale
        if vertical_gap > 0.45 or not 0.15 <= wrist_gap <= 1.4:
            return 0.0
        alignment = 1.0 - vertical_gap / 0.45
        spacing = 1.0 - min(1.0, abs(wrist_gap - 0.65) / 0.75)
        return self._clamp(0.65 + 0.2 * alignment + 0.15 * spacing)

    def _extract_person(
        self, result: Any
    ) -> tuple[list[list[float]], list[float], float]:
        keypoints = getattr(result, "keypoints", None)
        raw_xy = self._to_list(getattr(keypoints, "xy", None))
        people = self._as_people(raw_xy)
        if not people:
            return [], [], 0.0

        raw_scores = self._to_list(getattr(keypoints, "conf", None))
        score_sets = self._as_score_sets(raw_scores)
        box_scores = self._flatten_numbers(
            self._to_list(getattr(getattr(result, "boxes", None), "conf", None))
        )
        person_index = max(
            range(len(people)),
            key=lambda index: box_scores[index] if index < len(box_scores) else 0.0,
        )
        points = self._normalize_points(people[person_index])
        scores = score_sets[person_index] if person_index < len(score_sets) else []
        detection_confidence = (
            self._clamp(box_scores[person_index])
            if person_index < len(box_scores)
            else self._mean_confidence(scores)
        )
        return points, scores, detection_confidence

    @staticmethod
    def _first_result(results: Any) -> Any | None:
        if results is None:
            return None
        if isinstance(results, Sequence) and not isinstance(results, (str, bytes)):
            return results[0] if results else None
        try:
            return next(iter(results))
        except (StopIteration, TypeError):
            return results

    @classmethod
    def _as_people(cls, raw_xy: Any) -> list[Any]:
        if not isinstance(raw_xy, list) or not raw_xy:
            return []
        first = raw_xy[0]
        if cls._looks_like_point(first):
            return [raw_xy]
        return raw_xy if isinstance(first, list) else []

    @staticmethod
    def _as_score_sets(raw_scores: Any) -> list[list[float]]:
        if not isinstance(raw_scores, list) or not raw_scores:
            return []
        if isinstance(raw_scores[0], (int, float)):
            return [[float(value) for value in raw_scores if isinstance(value, (int, float))]]
        return [
            [float(value) for value in values if isinstance(value, (int, float))]
            for values in raw_scores
            if isinstance(values, list)
        ]

    @classmethod
    def _normalize_points(cls, raw_points: Any) -> list[list[float]]:
        if not isinstance(raw_points, (list, tuple)):
            return []
        points: list[list[float]] = []
        for raw_point in raw_points:
            if not cls._looks_like_point(raw_point):
                points.append([0.0, 0.0])
                continue
            x = float(raw_point[0])
            y = float(raw_point[1])
            if not math.isfinite(x) or not math.isfinite(y):
                points.append([0.0, 0.0])
            else:
                points.append([round(x, 2), round(y, 2)])
        return points

    @staticmethod
    def _looks_like_point(value: Any) -> bool:
        return (
            isinstance(value, (list, tuple))
            and len(value) >= 2
            and isinstance(value[0], (int, float))
            and isinstance(value[1], (int, float))
        )

    def _point(
        self, points: list[list[float]], scores: list[float], index: int
    ) -> list[float] | None:
        if index >= len(points):
            return None
        point = points[index]
        if len(point) < 2 or (point[0] == 0 and point[1] == 0):
            return None
        if index < len(scores) and scores[index] < self.KEYPOINT_CONFIDENCE_THRESHOLD:
            return None
        return point

    @staticmethod
    def _to_list(value: Any) -> Any:
        if value is None:
            return []
        try:
            if hasattr(value, "cpu"):
                value = value.cpu()
            if hasattr(value, "numpy"):
                value = value.numpy()
            if hasattr(value, "tolist"):
                return value.tolist()
        except Exception as exc:
            raise PoseInferenceError("姿态关键点转换失败") from exc
        return value

    @classmethod
    def _flatten_numbers(cls, value: Any) -> list[float]:
        if isinstance(value, (int, float)):
            return [float(value)]
        if not isinstance(value, list):
            return []
        flattened: list[float] = []
        for item in value:
            flattened.extend(cls._flatten_numbers(item))
        return flattened

    @staticmethod
    def _mean_confidence(scores: list[float]) -> float:
        valid = [score for score in scores if isinstance(score, (int, float)) and score >= 0]
        return PoseDetector._clamp(sum(valid) / len(valid)) if valid else 0.0

    @staticmethod
    def _midpoint(first: list[float], second: list[float]) -> list[float]:
        return [(first[0] + second[0]) / 2, (first[1] + second[1]) / 2]

    @staticmethod
    def _distance(first: list[float], second: list[float]) -> float:
        return math.hypot(first[0] - second[0], first[1] - second[1])

    @staticmethod
    def _clamp(value: float) -> float:
        numeric = float(value)
        if not math.isfinite(numeric):
            return 0.0
        return max(0.0, min(1.0, numeric))

    @staticmethod
    def _unknown_result() -> dict[str, Any]:
        return {"action": "unknown", "confidence": 0.0, "keypoints": []}
