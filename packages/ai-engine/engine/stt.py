"""本地离线语音识别适配。

使用可选的 ``faster-whisper`` 小模型识别触摸屏音频。模型延迟加载并复用，上传内容只会写入
受控临时目录中的随机文件，识别结束后立即清理。
"""

from __future__ import annotations

import os
import tempfile
import threading
from collections.abc import Callable, Iterable, Mapping
from pathlib import Path
from time import perf_counter
from typing import Any, BinaryIO, TypedDict


class SttError(RuntimeError):
    """语音识别基础异常。"""


class SttValidationError(SttError):
    """上传音频格式或内容不符合要求。"""


class SttFileTooLargeError(SttValidationError):
    """上传音频超过大小限制。"""


class SttDurationError(SttValidationError):
    """上传音频超过时长限制。"""


class SttEmptyAudioError(SttValidationError):
    """音频为空、静音或没有可识别语音。"""


class SttUnavailableError(SttError):
    """本地 STT 依赖或模型不可用。"""


class SttRecognitionError(SttError):
    """音频解码或识别失败。"""


class SttResult(TypedDict):
    """``/engine/stt`` 对外返回字段。"""

    text: str
    durationMs: int


ModelFactory = Callable[[], Any]


class SpeechTranscriber:
    """校验上传音频并使用本地 Whisper 模型转写为文本。"""

    SUPPORTED_EXTENSIONS = frozenset({".wav", ".mp3", ".m4a", ".webm", ".ogg", ".flac"})
    SUPPORTED_MEDIA_TYPES = frozenset(
        {
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/mpeg",
            "audio/mp3",
            "audio/mp4",
            "audio/x-m4a",
            "audio/webm",
            "audio/ogg",
            "audio/flac",
            "audio/x-flac",
            "application/ogg",
            "application/octet-stream",
        }
    )
    DEFAULT_MAX_BYTES = 20 * 1024 * 1024
    DEFAULT_MAX_DURATION_SECONDS = 60.0
    COPY_CHUNK_SIZE = 64 * 1024

    def __init__(
        self,
        temp_dir: str | Path | None = None,
        max_bytes: int | None = None,
        max_duration_seconds: float | None = None,
        model_factory: ModelFactory | None = None,
        clock: Callable[[], float] = perf_counter,
    ) -> None:
        configured_dir = temp_dir or os.getenv("STT_TEMP_DIR", "./stt_temp")
        self.temp_dir = Path(configured_dir).expanduser().resolve()
        self.max_bytes = (
            max_bytes
            if max_bytes is not None
            else self._env_positive_int("STT_MAX_FILE_MB", 20) * 1024 * 1024
        )
        self.max_duration_seconds = (
            max_duration_seconds
            if max_duration_seconds is not None
            else self._env_positive_float(
                "STT_MAX_DURATION_SECONDS", self.DEFAULT_MAX_DURATION_SECONDS
            )
        )
        self._model_factory = model_factory
        self._clock = clock
        self._model: Any | None = None
        self._model_lock = threading.Lock()
        self._inference_lock = threading.Lock()

    def transcribe(
        self,
        stream: BinaryIO,
        filename: str | None,
        content_type: str | None,
    ) -> SttResult:
        """识别一个音频流，并返回文本和本次识别耗时。"""
        suffix = self._validate_metadata(filename, content_type)
        started_at = self._clock()
        temporary: Path | None = None
        try:
            temporary = self._copy_to_temporary(stream, suffix)
            model = self._get_model()
            with self._inference_lock:
                segments, info = model.transcribe(
                    str(temporary),
                    language=os.getenv("STT_LANGUAGE", "zh"),
                    beam_size=5,
                    vad_filter=True,
                )
                duration = self._extract_duration(info)
                if duration is None:
                    raise SttRecognitionError("无法读取音频时长")
                if duration > self.max_duration_seconds:
                    raise SttDurationError(
                        f"音频时长不能超过 {self.max_duration_seconds:g} 秒"
                    )
                text = self._collect_text(segments)
            if not text:
                raise SttEmptyAudioError("音频中未识别到有效语音")
            duration_ms = max(0, round((self._clock() - started_at) * 1000))
            return {"text": text, "durationMs": duration_ms}
        except SttError:
            raise
        except (OSError, ValueError, TypeError) as exc:
            raise SttRecognitionError("音频解码或语音识别失败") from exc
        except Exception as exc:
            raise SttRecognitionError("音频解码或语音识别失败") from exc
        finally:
            if temporary is not None:
                self._remove_if_exists(temporary)

    def _get_model(self) -> Any:
        if self._model is not None:
            return self._model
        with self._model_lock:
            if self._model is not None:
                return self._model
            factory = self._model_factory or self._create_local_model
            try:
                self._model = factory()
            except SttError:
                raise
            except Exception as exc:
                raise SttUnavailableError("本地 STT 模型初始化失败") from exc
            if self._model is None:
                raise SttUnavailableError("本地 STT 模型初始化失败")
            return self._model

    @staticmethod
    def _create_local_model() -> Any:
        try:
            from faster_whisper import WhisperModel
        except ImportError as exc:
            raise SttUnavailableError("faster-whisper 未安装") from exc

        model_name = os.getenv("STT_MODEL", "small").strip() or "small"
        device = os.getenv("STT_DEVICE", "auto").strip() or "auto"
        compute_type = os.getenv("STT_COMPUTE_TYPE", "int8").strip() or "int8"
        download_root = os.getenv("STT_MODEL_DIR", "").strip() or None
        local_only = os.getenv("STT_LOCAL_FILES_ONLY", "true").strip().lower() in {
            "1",
            "true",
            "yes",
            "on",
        }
        try:
            return WhisperModel(
                model_name,
                device=device,
                compute_type=compute_type,
                download_root=download_root,
                local_files_only=local_only,
            )
        except Exception as exc:
            raise SttUnavailableError("本地 Whisper 模型不可用") from exc

    def _validate_metadata(self, filename: str | None, content_type: str | None) -> str:
        if not isinstance(filename, str) or not filename.strip():
            raise SttValidationError("音频文件名不能为空")
        suffix = Path(filename.strip()).suffix.lower()
        if suffix not in self.SUPPORTED_EXTENSIONS:
            raise SttValidationError("不支持的音频格式")
        normalized_type = (content_type or "").split(";", 1)[0].strip().lower()
        if normalized_type and normalized_type not in self.SUPPORTED_MEDIA_TYPES:
            raise SttValidationError("不支持的音频 Content-Type")
        return suffix

    def _copy_to_temporary(self, stream: BinaryIO, suffix: str) -> Path:
        self.temp_dir.mkdir(parents=True, exist_ok=True)
        descriptor: int | None = None
        target: Path | None = None
        try:
            descriptor, name = tempfile.mkstemp(prefix="stt_", suffix=suffix, dir=self.temp_dir)
            target = Path(name).resolve()
            target.relative_to(self.temp_dir)
        except (OSError, ValueError) as exc:
            if descriptor is not None:
                os.close(descriptor)
            if target is not None:
                self._remove_if_exists(target)
            raise SttRecognitionError("无法创建受控音频临时文件") from exc

        assert descriptor is not None and target is not None
        size = 0
        try:
            with os.fdopen(descriptor, "wb") as output:
                while True:
                    chunk = stream.read(self.COPY_CHUNK_SIZE)
                    if not chunk:
                        break
                    if not isinstance(chunk, bytes):
                        raise SttValidationError("音频上传内容无效")
                    size += len(chunk)
                    if size > self.max_bytes:
                        raise SttFileTooLargeError(
                            f"音频文件不能超过 {self.max_bytes // (1024 * 1024)}MB"
                        )
                    output.write(chunk)
            if size == 0:
                raise SttEmptyAudioError("音频文件不能为空")
            return target
        except Exception:
            self._remove_if_exists(target)
            raise

    @staticmethod
    def _extract_duration(info: Any) -> float | None:
        raw_duration: Any
        if isinstance(info, Mapping):
            raw_duration = info.get("duration")
        else:
            raw_duration = getattr(info, "duration", None)
        if isinstance(raw_duration, (int, float)) and raw_duration >= 0:
            return float(raw_duration)
        return None

    @staticmethod
    def _collect_text(segments: Iterable[Any]) -> str:
        parts: list[str] = []
        for segment in segments:
            raw_text = segment.get("text") if isinstance(segment, Mapping) else getattr(
                segment, "text", None
            )
            if isinstance(raw_text, str) and raw_text.strip():
                parts.append(raw_text.strip())
        return "".join(parts).strip()

    @staticmethod
    def _remove_if_exists(path: Path) -> None:
        try:
            path.unlink(missing_ok=True)
        except OSError:
            pass

    @staticmethod
    def _env_positive_int(name: str, default: int) -> int:
        try:
            value = int(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _env_positive_float(name: str, default: float) -> float:
        try:
            value = float(os.getenv(name, str(default)))
            return value if value > 0 else default
        except (TypeError, ValueError):
            return default
