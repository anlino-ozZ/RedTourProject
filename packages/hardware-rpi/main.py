"""
hardware-rpi 入口
=================
青年红色筑梦之旅 - 红色文旅智能导览系统 树莓派硬件控制
树莓派5（BCM2712）外设驱动：串口通信、音频播放、GPIO 控制。

注意：RPi.GPIO 仅在树莓派环境可用，开发机导入时做 try-except 降级。
"""

import signal
import sys
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

# ---- 配置（开发机环境可从 .env 读取，此处用默认值占位）----
SERIAL_PORT = "/dev/ttyAMA0"
BAUDRATE = 115200
AUDIO_DEVICE = "default"
GPIO_LED_PIN = 18
GPIO_BTN_PIN = 17
LOOP_INTERVAL = 0.5

# 运行标志，用于优雅退出
_running = True


def _init_drivers() -> tuple[SerialCtrl, AudioCtrl, GpioCtrl]:
    """初始化各硬件驱动"""
    serial = SerialCtrl(port=SERIAL_PORT, baudrate=BAUDRATE)
    audio = AudioCtrl(device=AUDIO_DEVICE)
    gpio = GpioCtrl(led_pin=GPIO_LED_PIN, btn_pin=GPIO_BTN_PIN)

    # 打开串口、配置 GPIO
    serial.open()
    gpio.setup()

    return serial, audio, gpio


def _cleanup(serial: SerialCtrl, audio: AudioCtrl, gpio: GpioCtrl) -> None:
    """资源清理：关闭串口、停止音频、复位 GPIO"""
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
    """主循环：轮询硬件状态，可对接 AI 引擎事件"""
    serial, audio, gpio = _init_drivers()

    # 注册信号处理
    signal.signal(signal.SIGINT, _signal_handler)
    signal.signal(signal.SIGTERM, _signal_handler)

    print("[hardware-rpi] 硬件控制主循环已启动")

    while _running:
        # TODO: 在此对接 AI 引擎事件，例如：
        #   - 读取按钮状态，触发导览讲解
        #   - 接收 AI 引擎指令，播放 TTS / 背景音乐
        #   - 通过串口发送数据到外部设备

        # 读取按钮状态（骨架）
        # btn_state = gpio.read_button()
        # if btn_state:
        #     gpio.set_led(True)
        #     audio.speak_tts("欢迎来到红色景区")

        time.sleep(LOOP_INTERVAL)

    _cleanup(serial, audio, gpio)


if __name__ == "__main__":
    main()
