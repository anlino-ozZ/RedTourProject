"""
音频播放驱动
============
基于 pygame.mixer 封装，负责背景音乐播放与讲解/TTS 音频播放。
讲解 / TTS 音频文件由业务端生成、通过 TCP play_audio 指令下发 URL，
由 tcp_server 下载到本地缓存后调用本模块播放。

注意：pygame 仅在树莓派环境安装，开发机导入时做 try-except 降级。
"""

import threading
import time
from pathlib import Path
from typing import Callable, Optional

try:
    import pygame  # type: ignore[import-not-found]

    _PYGAME_AVAILABLE = True
except ImportError:
    _PYGAME_AVAILABLE = False

# 播放结束回调：参数为实际播放的本地文件路径
FinishedCallback = Callable[[str], None]


class AudioCtrl:
    """音频控制器：讲解/TTS 播放 + 背景音乐 + 音量控制"""

    def __init__(self, device: str = "default", volume: int = 80) -> None:
        """
        :param device: 音频输出设备（仅记录，实际输出走系统默认 ALSA 设备）
        :param volume: 初始音量 0-100
        """
        self.device = device
        self._volume = max(0, min(100, volume))
        self._initialized = False
        # 播放代号：每次 play/stop 递增，旧的等待线程据此识别自己已被取代，避免误发结束回调
        self._generation = 0
        self._lock = threading.Lock()

    def _ensure_init(self) -> None:
        """延迟初始化 pygame.mixer（树莓派音频设备就绪后再初始化）"""
        if self._initialized:
            return
        if not _PYGAME_AVAILABLE:
            print("[AudioCtrl] pygame 未安装，跳过音频初始化（开发机环境）")
            return
        try:
            pygame.mixer.init(frequency=44100, size=-16, channels=2, buffer=512)
            pygame.mixer.music.set_volume(self._volume / 100.0)
            self._initialized = True
            print(
                f"[AudioCtrl] 音频输出已初始化（device={self.device}, volume={self._volume}）"
            )
        except Exception as e:  # 音频设备占用/缺失不应中断硬件主进程
            print(f"[AudioCtrl] 音频初始化失败: {e}")

    def play(self, path: str | Path, on_finished: Optional[FinishedCallback] = None) -> bool:
        """
        播放讲解/TTS 音频（会中断当前播放与背景音乐）。
        :param path: 本地音频文件路径
        :param on_finished: 自然播放结束回调（被 stop/新播放取代时不回调）
        :return: 是否成功开始播放
        """
        if not _PYGAME_AVAILABLE:
            print(f"[AudioCtrl] pygame 未安装，跳过播放: {path}")
            return False
        self._ensure_init()
        if not self._initialized:
            return False

        file_path = str(path)
        with self._lock:
            self._generation += 1
            generation = self._generation
            try:
                pygame.mixer.music.stop()
                pygame.mixer.music.load(file_path)
                pygame.mixer.music.play()
            except Exception as e:
                print(f"[AudioCtrl] 播放失败 {file_path}: {e}")
                return False

        thread = threading.Thread(
            target=self._wait_finish,
            args=(generation, file_path, on_finished),
            daemon=True,
        )
        thread.start()
        return True

    def _wait_finish(
        self, generation: int, file_path: str, on_finished: Optional[FinishedCallback]
    ) -> None:
        """等待本次播放自然结束；被更新的播放取代时静默退出"""
        while True:
            time.sleep(0.1)
            with self._lock:
                if generation != self._generation:
                    return  # 已被 stop() 或新的 play() 取代
                if not _PYGAME_AVAILABLE:
                    return
                busy = pygame.mixer.music.get_busy()
            if not busy:
                break
        if on_finished:
            try:
                on_finished(file_path)
            except Exception as e:
                print(f"[AudioCtrl] 播放完成回调异常: {e}")

    def play_bgm(self, path: str | Path) -> None:
        """播放背景音乐（循环）；讲解音频 play() 会临时中断它"""
        if not _PYGAME_AVAILABLE:
            print(f"[AudioCtrl] pygame 未安装，跳过背景音乐: {path}")
            return
        self._ensure_init()
        if not self._initialized:
            return
        with self._lock:
            self._generation += 1
            try:
                pygame.mixer.music.load(str(path))
                pygame.mixer.music.play(-1)
            except Exception as e:
                print(f"[AudioCtrl] 背景音乐播放失败 {path}: {e}")

    def stop(self) -> None:
        """停止所有音频播放（讲解与背景音乐）"""
        if not _PYGAME_AVAILABLE or not self._initialized:
            return
        with self._lock:
            self._generation += 1
            try:
                pygame.mixer.music.stop()
            except Exception:
                pass

    def set_volume(self, volume: int) -> None:
        """设置音量（0-100）"""
        self._volume = max(0, min(100, int(volume)))
        if _PYGAME_AVAILABLE and self._initialized:
            pygame.mixer.music.set_volume(self._volume / 100.0)

    def is_playing(self) -> bool:
        """是否正在播放"""
        if not _PYGAME_AVAILABLE or not self._initialized:
            return False
        return bool(pygame.mixer.music.get_busy())

    def speak_tts(self, text: str) -> None:
        """
        播放 TTS 语音。

        TTS 音频文件由 AI 引擎生成、业务后端经 TCP play_audio 指令下发 URL，
        tcp_server 下载缓存后统一走 play() 播放；此处无法仅凭文案定位文件，
        仅记录日志，避免与上层下载链路重复实现。
        """
        print(f"[AudioCtrl] TTS 文案需由上层下载音频文件后调用 play() 播放: {text}")
