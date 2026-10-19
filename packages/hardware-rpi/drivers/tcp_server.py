"""
TCP 服务驱动（对接业务后端 HardwareTcpClient）
=============================================
树莓派作为 TCP 服务端监听（默认 0.0.0.0:9000），业务后端启动时主动接入，
消息为换行符 \\n 分隔的 JSON。

下行指令（后端 → 树莓派）：
    ping         心跳探活，立即回 pong
    play_audio   播放讲解音频 {audioUrl}，相对路径基于业务后端根地址下载缓存
    stop_audio   停止播放
    set_volume   设置音量 {volume: 0-100}
    reboot       远程重启（仅 Linux/树莓派真实执行）
    start_pose / stop_pose  姿态检测启停（视觉链路在触摸屏+AI 引擎，仅回 ack 兼容协议）

上行事件（树莓派 → 后端）：
    audio_finished  音频自然播放完成 {audioUrl}
    error           异常（音频下载失败、未知指令等）
"""

import hashlib
import json
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import TYPE_CHECKING, Any, Optional

if TYPE_CHECKING:
    from drivers.audio_ctrl import AudioCtrl


def _now_ms() -> int:
    return int(time.time() * 1000)


class HardwareTcpServer:
    """硬件 TCP 服务端：单连接（业务后端），阻塞读行 + JSON 分发，后台守护线程运行"""

    def __init__(
        self,
        audio: "AudioCtrl",
        host: str = "0.0.0.0",
        port: int = 9000,
        business_base_url: str = "http://localhost:8000",
        audio_cache_dir: str | Path = "audio_cache",
        download_timeout: float = 10.0,
    ) -> None:
        self._audio = audio
        self._host = host
        self._port = port
        self._business_base_url = business_base_url.rstrip("/")
        self._cache_dir = Path(audio_cache_dir)
        self._download_timeout = download_timeout

        self._server_sock: Optional[socket.socket] = None
        self._client_sock: Optional[socket.socket] = None
        self._sock_lock = threading.Lock()
        self._send_lock = threading.Lock()
        self._running = False
        self._thread: Optional[threading.Thread] = None

    # ---------------- 生命周期 ----------------
    def start(self) -> None:
        """启动监听（守护线程，不阻塞主循环）"""
        self._cache_dir.mkdir(parents=True, exist_ok=True)
        self._server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server_sock.bind((self._host, self._port))
        self._server_sock.listen(1)
        self._server_sock.settimeout(1.0)  # accept 周期超时，保证 stop() 能及时退出
        self._running = True
        self._thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._thread.start()
        print(f"[TcpServer] 已监听 {self._host}:{self._port}，等待业务后端接入")

    def stop(self) -> None:
        """停止服务并释放连接"""
        self._running = False
        with self._sock_lock:
            if self._client_sock:
                self._safe_close(self._client_sock)
                self._client_sock = None
        if self._server_sock:
            self._safe_close(self._server_sock)
            self._server_sock = None
        print("[TcpServer] 已停止监听")

    @staticmethod
    def _safe_close(sock: socket.socket) -> None:
        try:
            sock.close()
        except OSError:
            pass

    # ---------------- 连接处理 ----------------
    def _accept_loop(self) -> None:
        assert self._server_sock is not None
        while self._running:
            try:
                conn, addr = self._server_sock.accept()
            except socket.timeout:
                continue
            except OSError:
                # stop() 关闭监听套接字 → 退出；
                # Windows 上前一连接被 RST 时 accept 可能抛 ConnectionAbortedError，
                # 属于瞬态错误，忽略后继续 accept（否则整个监听线程会死掉）
                if not self._running:
                    break
                time.sleep(0.2)
                continue

            # 业务后端仅一个客户端：新连接接入时替换旧连接
            with self._sock_lock:
                old = self._client_sock
                self._client_sock = conn
                if old is not None:
                    self._safe_close(old)
            print(f"[TcpServer] 业务后端已接入: {addr[0]}:{addr[1]}")

            try:
                self._handle_client(conn)
            except Exception as e:
                print(f"[TcpServer] 连接处理异常: {e}")
            finally:
                with self._sock_lock:
                    if self._client_sock is conn:
                        self._client_sock = None
                self._safe_close(conn)
                print("[TcpServer] 业务后端连接断开，等待重连")

    def _handle_client(self, conn: socket.socket) -> None:
        conn.settimeout(None)
        buffer = ""
        while self._running:
            chunk = conn.recv(4096)
            if not chunk:  # EOF：对端关闭
                return
            buffer += chunk.decode("utf-8", errors="ignore")
            # 按换行拆包，最后一段可能是半包，留在 buffer 等下次数据
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                try:
                    msg = json.loads(line)
                except json.JSONDecodeError:
                    self._send(
                        conn, {"event": "error", "message": "invalid json", "ts": _now_ms()}
                    )
                    continue
                if not isinstance(msg, dict):
                    self._send(
                        conn,
                        {"event": "error", "message": "message must be object", "ts": _now_ms()},
                    )
                    continue

                response = self._dispatch(msg)
                if response is not None:
                    self._send(conn, response)

    def _send(self, conn: socket.socket, obj: dict[str, Any]) -> None:
        data = (json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8")
        with self._send_lock:
            try:
                conn.sendall(data)
            except OSError:
                pass

    def _emit(self, obj: dict[str, Any]) -> None:
        """主动上行事件给当前连接（如 audio_finished）"""
        with self._sock_lock:
            conn = self._client_sock
        if conn is not None:
            self._send(conn, obj)

    # ---------------- 指令分发 ----------------
    def _ok(self, cmd: str, **extra: Any) -> dict[str, Any]:
        result: dict[str, Any] = {"cmd": cmd, "status": "ok", "ts": _now_ms()}
        result.update(extra)
        return result

    def _dispatch(self, msg: dict[str, Any]) -> Optional[dict[str, Any]]:
        cmd = msg.get("cmd")
        ts = _now_ms()

        if cmd == "ping":
            return {"cmd": "pong", "ts": ts}

        if cmd == "play_audio":
            audio_url = msg.get("audioUrl")
            if not isinstance(audio_url, str) or not audio_url:
                return {"cmd": cmd, "status": "error", "message": "audioUrl required", "ts": ts}
            # 下载可能耗时，放到工作线程，先回 ack 避免阻塞读循环
            threading.Thread(
                target=self._play_audio_job, args=(audio_url,), daemon=True
            ).start()
            return self._ok(cmd, audioUrl=audio_url)

        if cmd == "stop_audio":
            self._audio.stop()
            return self._ok(cmd)

        if cmd == "set_volume":
            volume = msg.get("volume")
            if not isinstance(volume, (int, float)) or not 0 <= float(volume) <= 100:
                return {
                    "cmd": cmd,
                    "status": "error",
                    "message": "volume must be 0-100",
                    "ts": ts,
                }
            self._audio.set_volume(int(volume))
            return self._ok(cmd, volume=int(volume))

        if cmd == "reboot":
            self._schedule_reboot()
            return self._ok(cmd)

        if cmd in ("start_pose", "stop_pose"):
            # 姿态识别由触摸屏采集 + AI 引擎推理，树莓派不处理视频帧，仅回 ack 保持协议完整
            print(f"[TcpServer] 收到 {cmd}（姿态检测在触摸屏 + AI 引擎链路执行）")
            return self._ok(cmd)

        return {"event": "error", "message": f"unknown cmd: {cmd}", "ts": ts}

    # ---------------- 音频下载与播放 ----------------
    def _play_audio_job(self, audio_url: str) -> None:
        try:
            local_path = self._fetch_audio(audio_url)
        except Exception as e:
            print(f"[TcpServer] 音频下载失败 {audio_url}: {e}")
            self._emit(
                {
                    "event": "error",
                    "message": f"audio download failed: {e}",
                    "audioUrl": audio_url,
                    "ts": _now_ms(),
                }
            )
            return

        started = self._audio.play(
            local_path,
            on_finished=lambda _path, url=audio_url: self._emit(
                {"event": "audio_finished", "audioUrl": url, "ts": _now_ms()}
            ),
        )
        if started:
            print(f"[TcpServer] 播放讲解音频: {audio_url}")

    def _fetch_audio(self, audio_url: str) -> str:
        """
        下载音频到本地缓存（按 URL hash 命名），已缓存则直接复用。
        完整 http(s) URL 直接下载；相对路径拼业务后端根地址，如 /audio/pose/salute.mp3。
        """
        if audio_url.startswith(("http://", "https://")):
            full_url = audio_url
        else:
            full_url = self._business_base_url + (
                audio_url if audio_url.startswith("/") else f"/{audio_url}"
            )

        suffix = Path(urllib.parse.urlparse(full_url).path).suffix or ".mp3"
        cache_path = self._cache_dir / (hashlib.sha1(full_url.encode("utf-8")).hexdigest()[:16] + suffix)
        if cache_path.exists() and cache_path.stat().st_size > 0:
            return str(cache_path)

        tmp_path = cache_path.with_suffix(cache_path.suffix + ".tmp")
        with urllib.request.urlopen(full_url, timeout=self._download_timeout) as response:
            with open(tmp_path, "wb") as f:
                shutil.copyfileobj(response, f)
        tmp_path.replace(cache_path)
        print(f"[TcpServer] 音频已缓存: {full_url} -> {cache_path}")
        return str(cache_path)

    # ---------------- 远程重启 ----------------
    def _schedule_reboot(self) -> None:
        """ack 返回后延迟 1s 重启，避免客户端读不到响应"""
        def _do() -> None:
            time.sleep(1)
            if sys.platform.startswith("linux"):
                print("[TcpServer] 执行远程重启: sudo reboot")
                subprocess.run(["sudo", "reboot"], check=False)
            else:
                print("[TcpServer] 非 Linux 环境，跳过真实重启（开发机）")

        print("[TcpServer] 收到远程重启指令，1 秒后执行")
        threading.Thread(target=_do, daemon=True).start()
