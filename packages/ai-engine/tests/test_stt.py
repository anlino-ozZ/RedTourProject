"""本地 STT 语音识别测试。"""

from __future__ import annotations

import io
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from engine.stt import (
    SpeechTranscriber,
    SttDurationError,
    SttEmptyAudioError,
    SttFileTooLargeError,
    SttRecognitionError,
    SttValidationError,
)


class FakeClock:
    """提供确定的起止时间。"""

    def __init__(self, *values: float) -> None:
        self._values = iter(values)

    def __call__(self) -> float:
        return next(self._values)


class FakeModel:
    """模拟 faster-whisper 的最小转写契约。"""

    def __init__(
        self,
        texts: tuple[str, ...] = ("遵义会议", "具有重要历史意义。"),
        duration: float = 3.2,
        failure: Exception | None = None,
    ) -> None:
        self.texts = texts
        self.duration = duration
        self.failure = failure
        self.calls = 0
        self.paths: list[Path] = []

    def transcribe(self, path: str, **options: object) -> tuple[list[object], object]:
        self.calls += 1
        target = Path(path)
        self.paths.append(target)
        if self.failure is not None:
            raise self.failure
        if target.read_bytes() != b"fake-audio":
            raise AssertionError("上传内容没有写入临时文件")
        if options != {"language": "zh", "beam_size": 5, "vad_filter": True}:
            raise AssertionError(f"转写参数错误: {options}")
        segments = [SimpleNamespace(text=text) for text in self.texts]
        return segments, SimpleNamespace(duration=self.duration)


class SpeechTranscriberTest(unittest.TestCase):
    """验证格式、资源限制、模型复用和临时文件清理。"""

    def test_transcribes_audio_and_removes_random_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            model = FakeModel()
            transcriber = SpeechTranscriber(
                directory,
                model_factory=lambda: model,
                clock=FakeClock(10.0, 10.125),
            )

            result = transcriber.transcribe(
                io.BytesIO(b"fake-audio"), "visitor.wav", "audio/wav"
            )

            self.assertEqual({"text": "遵义会议具有重要历史意义。", "durationMs": 125}, result)
            self.assertEqual(1, model.calls)
            self.assertRegex(model.paths[0].name, r"^stt_[^/\\]+\.wav$")
            self.assertFalse(model.paths[0].exists())
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_reuses_lazily_loaded_model(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            model = FakeModel(texts=("回答",))
            factory_calls = 0

            def factory() -> FakeModel:
                nonlocal factory_calls
                factory_calls += 1
                return model

            transcriber = SpeechTranscriber(directory, model_factory=factory)
            for _ in range(2):
                transcriber.transcribe(io.BytesIO(b"fake-audio"), "voice.mp3", "audio/mpeg")

            self.assertEqual(1, factory_calls)
            self.assertEqual(2, model.calls)

    def test_rejects_unsupported_extension_and_content_type(self) -> None:
        transcriber = SpeechTranscriber(model_factory=FakeModel)

        with self.assertRaises(SttValidationError):
            transcriber.transcribe(io.BytesIO(b"fake-audio"), "voice.exe", "audio/wav")
        with self.assertRaises(SttValidationError):
            transcriber.transcribe(io.BytesIO(b"fake-audio"), "voice.wav", "image/png")

    def test_rejects_empty_and_oversized_upload_without_loading_model(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            factory_calls = 0

            def factory() -> FakeModel:
                nonlocal factory_calls
                factory_calls += 1
                return FakeModel()

            transcriber = SpeechTranscriber(directory, max_bytes=5, model_factory=factory)

            with self.assertRaises(SttEmptyAudioError):
                transcriber.transcribe(io.BytesIO(b""), "voice.wav", "audio/wav")
            with self.assertRaises(SttFileTooLargeError):
                transcriber.transcribe(io.BytesIO(b"123456"), "voice.wav", "audio/wav")

            self.assertEqual(0, factory_calls)
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_rejects_audio_longer_than_limit_and_cleans_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            model = FakeModel(duration=61.0)
            transcriber = SpeechTranscriber(
                directory,
                max_duration_seconds=60.0,
                model_factory=lambda: model,
            )

            with self.assertRaises(SttDurationError):
                transcriber.transcribe(
                    io.BytesIO(b"fake-audio"), "voice.webm", "audio/webm"
                )

            self.assertEqual([], list(Path(directory).iterdir()))

    def test_silence_returns_explicit_empty_audio_error(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            transcriber = SpeechTranscriber(
                directory,
                model_factory=lambda: FakeModel(texts=(" ", "")),
            )

            with self.assertRaises(SttEmptyAudioError):
                transcriber.transcribe(io.BytesIO(b"fake-audio"), "voice.ogg", "audio/ogg")

    def test_model_failure_is_wrapped_and_temporary_file_is_removed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            transcriber = SpeechTranscriber(
                directory,
                model_factory=lambda: FakeModel(failure=RuntimeError("decoder failed")),
            )

            with self.assertRaises(SttRecognitionError):
                transcriber.transcribe(io.BytesIO(b"fake-audio"), "voice.flac", "audio/flac")

            self.assertEqual([], list(Path(directory).iterdir()))


if __name__ == "__main__":
    unittest.main()
