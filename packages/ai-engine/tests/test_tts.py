"""本地 TTS 合成测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from engine.tts import TtsError, TtsSynthesizer


class FakeEngine:
    def __init__(self, output_bytes: bytes = b"RIFF-audio") -> None:
        self.output_bytes = output_bytes
        self.saved_text: str | None = None
        self.saved_path: Path | None = None
        self.calls = 0

    def save_to_file(self, text: str, path: str) -> None:
        self.saved_text = text
        self.saved_path = Path(path)
        self.saved_path.write_bytes(self.output_bytes)

    def runAndWait(self) -> None:
        self.calls += 1


class TtsSynthesizerTest(unittest.TestCase):
    """验证受控文件名、音频 URL 和失败边界。"""

    def test_synthesize_writes_audio_and_returns_relative_url(self) -> None:
        root = Path(tempfile.mkdtemp())
        engine = FakeEngine()
        synthesizer = TtsSynthesizer(root, "/audio/tts", engine_factory=lambda: engine)

        url = synthesizer.synthesize("  遵义会议是历史转折点。  ")

        self.assertRegex(url, r"^/audio/tts/ask_[0-9a-f]{32}\.wav$")
        filename = url.rsplit("/", 1)[-1]
        target = synthesizer.resolve_audio(filename)
        self.assertEqual(b"RIFF-audio", target.read_bytes())
        self.assertEqual("遵义会议是历史转折点。", engine.saved_text)
        self.assertEqual(1, engine.calls)

    def test_rejects_empty_text_and_unsafe_audio_filename(self) -> None:
        root = Path(tempfile.mkdtemp())
        synthesizer = TtsSynthesizer(root, engine_factory=lambda: FakeEngine())

        with self.assertRaises(TtsError):
            synthesizer.synthesize(" ")
        with self.assertRaises(TtsError):
            synthesizer.resolve_audio("../outside.wav")
        with self.assertRaises(TtsError):
            synthesizer.resolve_audio("ask_123.wav")

    def test_empty_audio_output_is_reported_as_failure(self) -> None:
        root = Path(tempfile.mkdtemp())
        synthesizer = TtsSynthesizer(
            root,
            engine_factory=lambda: FakeEngine(output_bytes=b""),
        )

        with self.assertRaises(TtsError):
            synthesizer.synthesize("有内容的问题")


if __name__ == "__main__":
    unittest.main()
