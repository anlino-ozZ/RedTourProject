"""
GPIO 驱动
=========
基于 RPi.GPIO 封装，控制 LED 指示灯与读取按钮输入。

注意：RPi.GPIO 仅在树莓派环境可用，开发机导入时做 try-except 降级。
"""

from typing import Any

# 尝试导入 RPi.GPIO，非树莓派环境打印提示
try:
    import RPi.GPIO as GPIO  # type: ignore[import-not-found]

    _GPIO_AVAILABLE = True
except ImportError:
    print("[GpioCtrl] RPi.GPIO 未安装，当前为开发机环境（GPIO 功能不可用）")
    _GPIO_AVAILABLE = False


class GpioCtrl:
    """GPIO 控制器：LED 指示灯 + 按钮输入"""

    def __init__(self, led_pin: int = 18, btn_pin: int = 17) -> None:
        """
        :param led_pin: LED 引脚（BCM 编号）
        :param btn_pin: 按钮引脚（BCM 编号）
        """
        self.led_pin = led_pin
        self.btn_pin = btn_pin

    def setup(self) -> None:
        """
        初始化 GPIO：设置引脚模式与方向。
        - LED 引脚设为输出
        - 按钮引脚设为输入（上拉）

        TODO: 实现初始化
        - GPIO.setmode(GPIO.BCM)
        - GPIO.setup(self.led_pin, GPIO.OUT)
        - GPIO.setup(self.btn_pin, GPIO.IN, pull_up_down=GPIO.PUD_UP)
        """
        if not _GPIO_AVAILABLE:
            print("[GpioCtrl] RPi.GPIO 不可用，跳过 GPIO 初始化（开发机环境）")
            return
        pass  # TODO

    def read_button(self) -> bool:
        """
        读取按钮状态。
        :return: True 表示按下（低电平），False 表示未按下

        TODO: 实现按钮读取 GPIO.input(self.btn_pin)
        """
        pass  # TODO

    def set_led(self, on: bool) -> None:
        """
        控制 LED 开关。
        :param on: True 点亮，False 熄灭

        TODO: 实现 GPIO.output(self.led_pin, GPIO.HIGH / GPIO.LOW)
        """
        pass  # TODO

    def cleanup(self) -> None:
        """清理 GPIO 资源，复位所有引脚"""
        if _GPIO_AVAILABLE:
            # TODO: GPIO.cleanup([self.led_pin, self.btn_pin])
            pass
