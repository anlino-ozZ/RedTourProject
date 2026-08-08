"""
串口通信驱动
============
基于 pyserial 封装，用于与外部设备（如导览手环、传感器节点）进行串口通信。
"""

from typing import Any

try:
    import serial  # type: ignore[import-not-found]
    import serial.tools.list_ports  # type: ignore[import-not-found]

    _SERIAL_AVAILABLE = True
except ImportError:
    _SERIAL_AVAILABLE = False


class SerialCtrl:
    """串口控制器：打开/关闭/收发数据"""

    def __init__(self, port: str = "/dev/ttyAMA0", baudrate: int = 115200) -> None:
        """
        :param port: 串口设备路径
        :param baudrate: 波特率
        """
        self.port = port
        self.baudrate = baudrate
        self._serial: Any = None  # serial.Serial 实例

    def open(self) -> None:
        """打开串口连接"""
        if not _SERIAL_AVAILABLE:
            print("[SerialCtrl] pyserial 未安装，跳过串口打开（开发机环境）")
            return
        # TODO: 实现串口打开
        # self._serial = serial.Serial(self.port, self.baudrate, timeout=1)
        pass

    def close(self) -> None:
        """关闭串口连接"""
        if self._serial and self._serial.is_open:
            self._serial.close()

    def send(self, data: str | bytes) -> int:
        """
        发送数据到串口。
        :param data: 待发送数据（字符串自动编码为 UTF-8）
        :return: 实际发送字节数

        TODO: 实现数据发送
        """
        pass  # TODO

    def read(self, size: int = 1024) -> bytes:
        """
        从串口读取数据。
        :param size: 最大读取字节数
        :return: 读取到的字节数据

        TODO: 实现数据读取
        """
        pass  # TODO
