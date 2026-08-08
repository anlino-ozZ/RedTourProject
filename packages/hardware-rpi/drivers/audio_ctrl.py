"""
音频播放驱动
============
基于 pygame.mixer 封装，负责背景音乐播放与 TTS 语音播报。
TTS 音频文件由 AI 引擎生成，本模块只负责播放。
"""

from pathlib import Path

try:
    import pygame  # type: ignore[import-not-found]

    _PYGAME_AVAILABLE = True
except ImportError:
    _PYGAME_AVAILABLE = False


class AudioCtrl:
    """音频控制器：背景音乐播放/停止 + TTS 语音播报"""

    def __init__(self, device: str = "default") -> None:
        """
        :param device: 音频输出设备
        """
        self.device = device
        self._initialized = False

    def _ensure_init(self) -> None:
        """延迟初始化 pygame.mixer"""
        if not self._initialized:
            if not _PYGAME_AVAILABLE:
                print("[AudioCtrl] pygame 未安装，跳过音频初始化（开发机环境）")
                return
            # TODO: pygame.mixer.init() 初始化
            self._initialized = True

    def play_bgm(self, path: str) -> None:
        """
        播放背景音乐（循环）。
        :param path: 音频文件路径

        TODO: 实现 pygame.mixer.music 背景音乐播放
        """
        pass  # TODO

    def stop(self) -> None:
        """
        停止所有音频播放。

        TODO: 实现 pygame.mixer 停止逻辑
        """
        pass  # TODO

    def speak_tts(self, text: str) -> None:
        """
        播放 TTS 语音（音频文件由 AI 引擎生成）。
        :param text: 语音文本（用于查找对应的 TTS 音频文件）

        TODO: 实现逻辑
        - 根据 text 查找 AI 引擎生成的 TTS 音频文件
        - 通过 pygame.mixer.Channel 播放（不影响背景音乐）
        """
        pass  # TODO
