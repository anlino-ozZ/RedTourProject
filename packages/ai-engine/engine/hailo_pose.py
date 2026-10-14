"""Hailo8L 轻量姿态模型的持久化推理适配。"""

from __future__ import annotations

import atexit
import importlib
import logging
import math
import os
import threading
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)


class HailoPoseError(RuntimeError):
    """Hailo 姿态后端加载或推理失败。"""


RuntimeFactory = Callable[[str], Any]


class HailoPoseBackend:
    """延迟创建并复用 HailoRT VStream，输出统一的 COCO 关键点。"""

    KEYPOINT_COUNT = 17

    def __init__(
        self,
        hef_path: str | Path | None = None,
        runtime_factory: RuntimeFactory | None = None,
    ) -> None:
        configured_path = hef_path or os.getenv(
            "HAILO_POSE_HEF", "./weights/yolov8n_pose_h8l.hef"
        )
        self.hef_path = Path(configured_path).expanduser().resolve()
        self.confidence_threshold = self._float_env("HAILO_POSE_CONFIDENCE", 0.25)
        self.keypoint_order = os.getenv("HAILO_POSE_KEYPOINT_ORDER", "yx").strip().lower()
        if self.keypoint_order not in {"xy", "yx"}:
            self.keypoint_order = "yx"
        self._runtime_factory = runtime_factory
        self._runtime: Any | None = None
        self._load_lock = threading.Lock()
        self._inference_lock = threading.Lock()

    def load(self) -> Any:
        """首次调用时配置 Hailo 设备，后续请求复用同一推理管线。"""
        if self._runtime is not None:
            return self._runtime
        with self._load_lock:
            if self._runtime is not None:
                return self._runtime
            try:
                if self._runtime_factory is not None:
                    runtime = self._runtime_factory(str(self.hef_path))
                else:
                    if not self.hef_path.is_file() or self.hef_path.suffix.lower() != ".hef":
                        raise HailoPoseError("Hailo 姿态 HEF 模型不存在")
                    hailo_platform = importlib.import_module("hailo_platform")
                    runtime = _HailoVStreamRuntime(hailo_platform, self.hef_path)
                if not callable(getattr(runtime, "infer", None)):
                    raise HailoPoseError("Hailo 推理运行时缺少 infer 接口")
                self._runtime = runtime
                return runtime
            except HailoPoseError:
                raise
            except Exception as exc:
                raise HailoPoseError("Hailo8L 姿态模型加载失败") from exc

    def predict(self, frame: Any) -> dict[str, Any]:
        """执行一次 Hailo 推理并返回关键点、关键点分数和检测置信度。"""
        runtime = self.load()
        frame_width, frame_height = self._frame_dimensions(frame)
        input_width, input_height = self._runtime_input_size(runtime)
        tensor = self._prepare_input(frame, input_width, input_height)
        try:
            with self._inference_lock:
                outputs = runtime.infer(tensor)
        except Exception as exc:
            raise HailoPoseError("Hailo8L 姿态推理失败") from exc
        return self._parse_outputs(
            outputs,
            frame_width=frame_width,
            frame_height=frame_height,
            input_width=input_width,
            input_height=input_height,
        )

    def _parse_outputs(
        self,
        outputs: Any,
        *,
        frame_width: int,
        frame_height: int,
        input_width: int,
        input_height: int,
    ) -> dict[str, Any]:
        candidates: list[tuple[float, list[float]]] = []
        for row in self._numeric_rows(outputs):
            keypoint_start = self._keypoint_start(row)
            if keypoint_start is None:
                continue
            confidence = self._clamp(row[4])
            if confidence < self.confidence_threshold:
                continue
            candidates.append((confidence, row[keypoint_start : keypoint_start + 51]))
        if not candidates:
            return {"keypoints": [], "scores": [], "confidence": 0.0}

        confidence, raw_keypoints = max(candidates, key=lambda candidate: candidate[0])
        points: list[list[float]] = []
        scores: list[float] = []
        for index in range(self.KEYPOINT_COUNT):
            first, second, score = raw_keypoints[index * 3 : index * 3 + 3]
            x, y = (second, first) if self.keypoint_order == "yx" else (first, second)
            if max(abs(x), abs(y)) <= 1.5:
                x *= frame_width
                y *= frame_height
            else:
                x *= frame_width / max(input_width, 1)
                y *= frame_height / max(input_height, 1)
            points.append([round(float(x), 2), round(float(y), 2)])
            scores.append(self._clamp(score))
        return {"keypoints": points, "scores": scores, "confidence": confidence}

    @staticmethod
    def _keypoint_start(row: list[float]) -> int | None:
        values_after_score = len(row) - 5
        if values_after_score >= 51 and values_after_score % 3 == 0:
            return 5
        # 部分导出模型在 score 后额外输出 class id。
        if values_after_score >= 52 and (values_after_score - 1) % 3 == 0:
            return 6
        return None

    @classmethod
    def _numeric_rows(cls, value: Any) -> list[list[float]]:
        if isinstance(value, Mapping):
            rows: list[list[float]] = []
            for nested in value.values():
                rows.extend(cls._numeric_rows(nested))
            return rows
        if hasattr(value, "tolist"):
            try:
                value = value.tolist()
            except Exception as exc:
                raise HailoPoseError("Hailo 输出无法转换") from exc
        if not isinstance(value, Sequence) or isinstance(value, (str, bytes)):
            return []
        if value and all(isinstance(item, (int, float)) for item in value):
            row = [float(item) for item in value]
            return [row] if all(math.isfinite(item) for item in row) else []
        rows = []
        for nested in value:
            rows.extend(cls._numeric_rows(nested))
        return rows

    @staticmethod
    def _frame_dimensions(frame: Any) -> tuple[int, int]:
        shape = getattr(frame, "shape", None)
        if not isinstance(shape, Sequence) or len(shape) < 2:
            raise HailoPoseError("Hailo 输入帧格式错误")
        height, width = int(shape[0]), int(shape[1])
        if width <= 0 or height <= 0:
            raise HailoPoseError("Hailo 输入帧尺寸无效")
        return width, height

    @staticmethod
    def _runtime_input_size(runtime: Any) -> tuple[int, int]:
        input_size = getattr(runtime, "input_size", (640, 640))
        if not isinstance(input_size, Sequence) or len(input_size) < 2:
            return 640, 640
        width, height = int(input_size[0]), int(input_size[1])
        return (width, height) if width > 0 and height > 0 else (640, 640)

    @staticmethod
    def _prepare_input(frame: Any, width: int, height: int) -> Any:
        try:
            import numpy as np
            from PIL import Image
        except ImportError as exc:
            raise HailoPoseError("Hailo 图像预处理依赖未安装") from exc
        try:
            image = Image.fromarray(np.asarray(frame, dtype=np.uint8), mode="RGB")
            resampling = getattr(Image, "Resampling", Image).BILINEAR
            resized = image.resize((width, height), resampling)
            return np.expand_dims(np.asarray(resized, dtype=np.uint8), axis=0)
        except (TypeError, ValueError, OSError) as exc:
            raise HailoPoseError("Hailo 输入帧预处理失败") from exc

    @staticmethod
    def _float_env(name: str, default: float) -> float:
        try:
            value = float(os.getenv(name, str(default)))
            return value if 0 <= value <= 1 else default
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _clamp(value: float) -> float:
        return max(0.0, min(1.0, float(value))) if math.isfinite(float(value)) else 0.0


class _HailoVStreamRuntime:
    """基于 HailoRT VStreams 的常驻设备和网络组。"""

    def __init__(self, hailo_platform: Any, hef_path: Path) -> None:
        self._device_context: Any | None = None
        self._pipeline_context: Any | None = None
        self._activation_context: Any | None = None
        try:
            hef = hailo_platform.HEF(str(hef_path))
            input_infos = hef.get_input_vstream_infos()
            if not input_infos:
                raise HailoPoseError("Hailo HEF 不包含输入流")
            input_info = input_infos[0]
            shape = tuple(int(value) for value in input_info.shape)
            if len(shape) < 2:
                raise HailoPoseError("Hailo HEF 输入尺寸无效")
            self.input_size = (shape[1], shape[0])
            self._input_name = str(input_info.name)

            self._device_context = hailo_platform.VDevice()
            device = self._device_context.__enter__()
            stream_interface = self._pcie_interface(hailo_platform)
            configure_params = hailo_platform.ConfigureParams.create_from_hef(
                hef, interface=stream_interface
            )
            network_groups = device.configure(hef, configure_params)
            if not network_groups:
                raise HailoPoseError("Hailo HEF 网络组配置失败")
            network_group = network_groups[0]
            network_params = network_group.create_params()
            format_type = hailo_platform.FormatType
            input_params = hailo_platform.InputVStreamParams.make_from_network_group(
                network_group,
                quantized=True,
                format_type=format_type.UINT8,
            )
            output_params = hailo_platform.OutputVStreamParams.make_from_network_group(
                network_group,
                quantized=False,
                format_type=format_type.FLOAT32,
            )
            self._pipeline_context = hailo_platform.InferVStreams(
                network_group, input_params, output_params
            )
            self._pipeline = self._pipeline_context.__enter__()
            self._activation_context = network_group.activate(network_params)
            self._activation_context.__enter__()
            atexit.register(self.close)
        except Exception:
            self.close()
            raise

    def infer(self, tensor: Any) -> Any:
        """使用已激活的网络组推理，避免逐帧重新配置设备。"""
        return self._pipeline.infer({self._input_name: tensor})

    def close(self) -> None:
        for context in (
            self._activation_context,
            self._pipeline_context,
            self._device_context,
        ):
            if context is not None:
                try:
                    context.__exit__(None, None, None)
                except Exception:
                    logger.debug("关闭 Hailo 运行时资源失败", exc_info=True)
        self._activation_context = None
        self._pipeline_context = None
        self._device_context = None

    @staticmethod
    def _pcie_interface(hailo_platform: Any) -> Any:
        interface_type = hailo_platform.HailoStreamInterface
        for name in ("PCIe", "PCIE"):
            if hasattr(interface_type, name):
                return getattr(interface_type, name)
        raise HailoPoseError("Hailo SDK 不支持 PCIe 接口")
