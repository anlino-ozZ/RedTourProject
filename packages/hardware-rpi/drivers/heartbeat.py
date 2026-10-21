"""
设备心跳上报驱动
================
定时向业务后端上报本机运行状态，接口：

    POST {BUSINESS_BASE_URL}/api/v1/devices/heartbeat

该接口为后端白名单路径（WebMvcConfig 已排除鉴权），无需 token。
后端 DeviceStatusMapper.upsert 按 device_id 做 ON DUPLICATE KEY UPDATE，
因此本模块只需周期 POST 即可维持设备在线。

采集策略：
- 树莓派：读取 /sys/class/thermal/thermal_zone0/temp、/proc/meminfo、/dev/hailo0、/dev/video*
- 开发机（非 Linux 或文件缺失）：对应字段降级为默认值，保证接口可联调

上报在守护线程中执行，异常只打印日志，不影响主循环与 TCP 指令链路。
"""

import json
import shutil
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Optional

# 默认上报间隔（秒），与后端"超过 2 分钟未上报判离线"的口径留足余量
DEFAULT_INTERVAL = 30.0
DEFAULT_TIMEOUT = 5.0

_CPU_TEMP_PATH = "/sys/class/thermal/thermal_zone0/temp"
_MEMINFO_PATH = "/proc/meminfo"
_HAILO_DEVICE = "/dev/hailo0"


def _now_str() -> str:
    return time.strftime("%H:%M:%S")


class HeartbeatReporter:
    """设备心跳上报器：后台守护线程按固定间隔 POST 状态，支持优雅停止"""

    def __init__(
        self,
        business_base_url: str = "http://localhost:8000",
        device_id: str = "rpi-001",
        scenic_area_id: int = 1,
        interval: float = DEFAULT_INTERVAL,
        timeout: float = DEFAULT_TIMEOUT,
    ) -> None:
        """
        :param business_base_url: 业务后端根地址（如 http://192.168.1.10:8000）
        :param device_id: 设备标识，对应 device_status.device_id 主键
        :param scenic_area_id: 绑定景区 ID
        :param interval: 上报间隔（秒）
        :param timeout: 单次 HTTP 超时（秒）
        """
        self._url = business_base_url.rstrip("/") + "/api/v1/devices/heartbeat"
        self._device_id = device_id
        self._scenic_area_id = scenic_area_id
        self._interval = max(1.0, float(interval))
        self._timeout = float(timeout)

        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

    # ---------------- 生命周期 ----------------
    def start(self) -> None:
        """启动上报线程（守护线程，不阻塞主循环）"""
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run, daemon=True, name="heartbeat")
        self._thread.start()
        print(
            f"[Heartbeat] 已启动：{self._url} "
            f"device={self._device_id} 景区={self._scenic_area_id} 间隔={self._interval:.0f}s"
        )

    def stop(self) -> None:
        """停止上报线程"""
        self._stop_event.set()
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=self._timeout + 1.0)
        self._thread = None
        print("[Heartbeat] 已停止")

    def _run(self) -> None:
        # 启动即上报一次，避免刚连上后端时列表里显示离线
        while not self._stop_event.is_set():
            try:
                self.report_once()
            except Exception as e:  # 兜底：心跳失败绝不影响主流程
                print(f"[Heartbeat] 上报线程异常: {e}")
            self._stop_event.wait(self._interval)

    # ---------------- 上报 ----------------
    def report_once(self) -> bool:
        """上报一次心跳，成功返回 True"""
        payload = self.collect_status()
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self._url,
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                body = response.read().decode("utf-8", errors="ignore")
        except (urllib.error.URLError, OSError) as e:
            print(f"[Heartbeat] 上报失败 {self._url}: {e}")
            return False

        print(f"[Heartbeat] {_now_str()} 上报成功 device={self._device_id} resp={body[:120]}")
        return True

    def collect_status(self) -> dict[str, Any]:
        """采集本机状态，拼成心跳接口请求体"""
        return {
            "deviceId": self._device_id,
            "scenicAreaId": self._scenic_area_id,
            "cpuTemp": self._read_cpu_temp(),
            "memoryUsage": self._read_memory_usage(),
            "diskUsage": self._read_disk_usage(),
            "hailoStatus": self._detect_hailo(),
            "cameraConnected": self._detect_camera(),
            "speakerConnected": self._detect_speaker(),
        }

    # ---------------- 指标采集 ----------------
    @staticmethod
    def _read_cpu_temp() -> Optional[float]:
        """CPU 温度（℃）。树莓派读 thermal_zone0，单位为毫摄氏度；读不到返回 None"""
        try:
            raw = Path(_CPU_TEMP_PATH).read_text(encoding="utf-8").strip()
        except OSError:
            return None
        try:
            return round(int(raw) / 1000.0, 1)
        except ValueError:
            return None

    @staticmethod
    def _read_memory_usage() -> Optional[float]:
        """内存占用率（%），基于 /proc/meminfo 的 MemTotal 与 MemAvailable"""
        try:
            text = Path(_MEMINFO_PATH).read_text(encoding="utf-8", errors="ignore")
        except OSError:
            return None
        values: dict[str, int] = {}
        for line in text.splitlines():
            parts = line.split()
            if len(parts) >= 2 and parts[0] in ("MemTotal:", "MemAvailable:"):
                try:
                    values[parts[0]] = int(parts[1])
                except ValueError:
                    continue
        total = values.get("MemTotal:")
        available = values.get("MemAvailable:")
        if not total or available is None:
            return None
        return round((total - available) * 100.0 / total, 1)

    @staticmethod
    def _read_disk_usage(path: str = "/") -> Optional[float]:
        """磁盘占用率（%）"""
        try:
            usage = shutil.disk_usage(path)
        except OSError:
            return None
        if usage.total == 0:
            return None
        return round(usage.used * 100.0 / usage.total, 1)

    @staticmethod
    def _detect_hailo() -> str:
        """Hailo8L 加速卡状态：/dev/hailo0 存在视为 active，否则 inactive"""
        return "active" if Path(_HAILO_DEVICE).exists() else "inactive"

    @staticmethod
    def _detect_camera() -> int:
        """摄像头连接：存在任一 /dev/video* 视为已接（1）"""
        return 1 if list(Path("/dev").glob("video*")) else 0

    @staticmethod
    def _detect_speaker() -> int:
        """音箱连接：/proc/asound/cards 有声卡视为已接（1），开发机降级为 1 便于联调"""
        try:
            cards = Path("/proc/asound/cards").read_text(encoding="utf-8", errors="ignore")
        except OSError:
            return 1
        return 1 if cards.strip() else 0
