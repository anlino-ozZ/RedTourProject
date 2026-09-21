"""
hardware-rpi 入口
=================
青年红色筑梦之旅 - 红色文旅智能导览系统 树莓派硬件控制
树莓派5（BCM2712）外设驱动：串口通信、音频播放、GPIO 控制，
并通过 TCP 服务（:9000）接收业务后端指令：播放讲解音频 / 音量 / 重启等。

注意：RPi.GPIO 仅在树莓派环境可用，开发机导入时做 try-except 降级。
配置项从环境变量读取（见 .env.example），均有默认值，开发机可直接运行。
"""

import os
import signal
import time

# 尝试导入 RPi.GPIO，开发机缺失时打印提示但不报错
try:
    import RPi.GPIO as GPIO  # type: ignore[import-not-found]

    _ON_RPI = True
except ImportError:
    print("[hardware-rpi] RPi.GPIO 未安装，当前为开发机环境（硬件功能不可用）")
    _ON_RPI = False

from drivers.serial_ctrl import SerialCtrl
from drivers.audio_ctrl import AudioCtrl
from drivers.gpio_ctrl import GpioCtrl
from drivers.tcp_server import HardwareTcpServer

# ---- 配置（环境变量优先，默认值保证开发机可启动）----
SERIAL_PORT = os.getenv("SERIAL_PORT", "/dev/ttyAMA0")
BAUDRATE = int(os.getenv("BAUDRATE", "115200"))
AUDIO_DEVICE = os.getenv("AUDIO_DEVICE", "default")
AUDIO_VOLUME = int(os.getenv("AUDIO_VOLUME", "80"))
GPIO_LED_PIN = int(os.getenv("GPIO_LED_PIN", "18"))
GPIO_BTN_PIN = int(os.getenv("GPIO_BTN_PIN", "17"))
LOOP_INTERVAL = float(os.getenv("LOOP_INTERVAL", "0.5"))
# TCP 服务：业务后端 HardwareTcpClient 主动接入
TCP_HOST = os.getenv("TCP_HOST", "0.0.0.0")
TCP_PORT = int(os.getenv("TCP_PORT", "9000"))
# 业务后端根地址（play_audio 相对路径音频的下载源）
BUSINESS_BASE_URL = os.getenv("BUSINESS_BASE_URL", "http://localhost:8000")
AUDIO_CACHE_DIR = os.getenv("AUDIO_CACHE_DIR", "audio_cache")

# 运行标志，用于优雅退出
_running = True


def _init_drivers() -> tuple[SerialCtrl, AudioCtrl, GpioCtrl, HardwareTcpServer]:
    """初始化各硬件驱动与 TCP 服务"""
    serial = SerialCtrl(port=SERIAL_PORT, baudrate=BAUDRATE)
    audio = AudioCtrl(device=AUDIO_DEVICE, volume=AUDIO_VOLUME)
    gpio = GpioCtrl(led_pin=GPIO_LED_PIN, btn_pin=GPIO_BTN_PIN)
    tcp_server = HardwareTcpServer(
        audio,
        host=TCP_HOST,
        port=TCP_PORT,
        business_base_url=BUSINESS_BASE_URL,
        audio_cache_dir=AUDIO_CACHE_DIR,
    )

    # 打开串口、配置 GPIO、启动 TCP 监听
    serial.open()
    gpio.setup()
    tcp_server.start()

    return serial, audio, gpio, tcp_server


def _cleanup(
    serial: SerialCtrl,
    audio: AudioCtrl,
    gpio: GpioCtrl,
    tcp_server: HardwareTcpServer,
) -> None:
    """资源清理：停止 TCP、关闭串口、停止音频、复位 GPIO"""
    tcp_server.stop()
    serial.close()
    audio.stop()
    gpio.cleanup()
    print("[hardware-rpi] 资源已清理，程序退出")


def _signal_handler(signum, frame) -> None:
    """信号处理：Ctrl+C / kill 时优雅退出"""
    global _running
    print(f"\n[hardware-rpi] 收到信号 {signum}，准备退出...")
    _running = False


def main() -> None:
    """主循环：轮询硬件状态；讲解音频等事件由 TCP 服务线程驱动"""
    serial, audio, gpio, tcp_server = _init_drivers()

    # 注册信号处理
    signal.signal(signal.SIGINT, _signal_handler)
    signal.signal(signal.SIGTERM, _signal_handler)

    print("[hardware-rpi] 硬件控制主循环已启动")

    # 按钮边沿检测状态（按下时点亮 LED 1.5s 作为现场反馈）
    btn_held = False
    btn_led_until = 0.0

    while _running:
        # 现场按钮：下降沿触发。后续可在此接入本地讲解播放等离线交互
        pressed = bool(gpio.read_button())
        if pressed and not btn_held:
            btn_held = True
            gpio.set_led(True)
            btn_led_until = time.time() + 1.5
            print("[hardware-rpi] 现场按钮按下")
        elif not pressed:
            btn_held = False

        if btn_led_until and time.time() >= btn_led_until:
            gpio.set_led(False)
            btn_led_until = 0.0

        time.sleep(LOOP_INTERVAL)

    _cleanup(serial, audio, gpio, tcp_server)


if __name__ == "__main__":
    main()
