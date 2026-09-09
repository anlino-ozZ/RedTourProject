"""本地离线 TTS 适配。

默认使用可选的 ``pyttsx3`` 输出 WAV。依赖在真正合成时延迟导入，开发机缺少 TTS
运行库时只抛出可识别异常，由 AskService 保留文本答案并将 audioUrl 置空。
"""

from __future__ import annotations

import os
import secrets
import threading
from pathlib import Path
from typing import Any, Callable


class TtsError(RuntimeError):
    """TTS 合成失败。"""


class TtsSynthesizer:
    """本地 TTS 合成器，返回业务端可使用的相对音频 URL。"""

    def __init__(
        self,
        audio_dir: str | Path | None = None,
        public_prefix: str | None = None,
        engine_factory: Callable[[], Any] | None = None,
    ) -> None:
        """初始化受控音频目录；TTS 引擎本身延迟到首次合成时创建。"""
        configured_dir = audio_dir or os.getenv("AUDIO_DIR", "./audio")
        self.audio_dir = Path(configured_dir).expanduser().resolve()
        configured_prefix = public_prefix or os.getenv("AUDIO_URL_PREFIX", "/audio/tts")
        self.public_prefix = configured_prefix.rstrip("/")
        self._engine_factory = engine_factory
        self._engine: Any | None = None
        self._lock = threading.Lock()

    def synthesize(self, text: str) -> str:
        """将文本合成为 WAV 文件并返回相对 URL。"""
        normalized_text = text.strip() if isinstance(text, str) else ""
        if not normalized_text:
            raise TtsError("TTS 文本不能为空")
        engine = self._get_engine()
        self.audio_dir.mkdir(parents=True, exist_ok=True)
        token = secrets.token_hex(16)
        filename = f"ask_{token}.wav"
        target = self._resolve_target(filename)
        temporary = target.with_name(f".{target.stem}.tmp{target.suffix}")
        try:
            with self._lock:
                engine.save_to_file(normalized_text, str(temporary))
                engine.runAndWait()
            if not temporary.is_file() or temporary.stat().st_size <= 0:
                raise TtsError("TTS 未生成有效音频文件")
            os.replace(temporary, target)
            return f"{self.public_prefix}/{filename}"
        except TtsError:
            self._remove_if_exists(temporary)
            raise
        except Exception as exc:
            self._remove_if_exists(temporary)
            raise TtsError("TTS 合成失败") from exc

    def _get_engine(self) -> Any:
        if self._engine is not None:
            return self._engine
        if self._engine_factory is not None:
            factory = self._engine_factory
        else:
            try:
                import pyttsx3
            except ImportError as exc:
                raise TtsError("本地 TTS 引擎未安装") from exc
            factory = pyttsx3.init
        try:
            self._engine = factory()
        except Exception as exc:
            raise TtsError("本地 TTS 引擎初始化失败") from exc
        return self._engine

    def _resolve_target(self, filename: str) -> Path:
        target = (self.audio_dir / filename).resolve()
        try:
            target.relative_to(self.audio_dir)
        except ValueError as exc:
            raise TtsError("音频路径越界") from exc
        return target

    def resolve_audio(self, filename: str) -> Path:
        """解析已有 TTS 文件，只允许随机问答 WAV 文件。"""
        if not isinstance(filename, str) or not filename.startswith("ask_"):
            raise TtsError("音频文件名不合法")
        token = filename.removeprefix("ask_").removesuffix(".wav")
        if not filename.endswith(".wav") or len(token) != 32:
            raise TtsError("音频文件名不合法")
        if any(character not in "0123456789abcdef" for character in token):
            raise TtsError("音频文件名不合法")
        target = self._resolve_target(filename)
        if not target.is_file():
            raise TtsError("音频文件不存在")
        return target

    @staticmethod
    def _remove_if_exists(path: Path) -> None:
        try:
            path.unlink(missing_ok=True)
        except OSError:
            pass
