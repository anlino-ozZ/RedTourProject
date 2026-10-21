# hardware-rpi — 树莓派硬件控制

> 青年红色筑梦之旅 · 红色文旅智能导览系统
>
> 树莓派5 外设驱动：串口通信 / 音频播放 / GPIO 控制

## 模块职责

- **串口通信**：与外部设备（导览手环、传感器节点）交互
- **音频播放**：背景音乐播放 + TTS 语音播报（TTS 音频由 AI 引擎生成）
- **GPIO 控制**：LED 指示灯输出 + 按钮输入检测
- **心跳上报**：定时 POST `/api/v1/devices/heartbeat` 上报在线状态与运行指标，维持管理端设备在线

## 通信方向

业务后端是 TCP **客户端**，本脚本是 TCP **服务端**（监听 `:9000`），由后端主动接入。
反向的 HTTP 心跳上报由本脚本发起，该接口为后端白名单路径，无需 token。

| 方向 | 通道 | 内容 |
| --- | --- | --- |
| 后端 → 硬件 | TCP `:9000` | `ping` / `play_audio` / `stop_audio` / `set_volume` / `reboot` |
| 硬件 → 后端 | TCP 同一连接 | 上行事件 `audio_finished` / `error` |
| 硬件 → 后端 | HTTP | 心跳上报（默认 30s 一次） |

> 注意：本包仅在树莓派环境运行，开发机导入硬件库时会自动降级（打印提示，不报错）。

## 树莓派部署说明

### 硬件配置
- **树莓派5**（Broadcom BCM2712）
- **PCIe Gen3 + NVMe SSD**：系统盘存储
- **官方主动冷却器**：确保长时间运行散热
- **AI HAT+**（配合 ai-engine 的 Hailo8L 加速）

### 系统配置
```bash
# 1. 开启串口（raspi-config）
sudo raspi-config
#    -> Interface Options -> Serial Port
#    -> 关闭登录 Shell，开启硬件串口

# 2. 确认 GPIO 可用（树莓派5 默认启用）

# 3. 配置音频输出
sudo raspi-config
#    -> System Options -> Audio
```

## 环境准备

```bash
# 1. 创建虚拟环境
python -m venv venv

# 2. 激活虚拟环境
source venv/bin/activate

# 3. 安装依赖（仅树莓派环境）
pip install -r requirements.txt

# 4. 配置环境变量
cp .env.example .env
#    编辑 .env 确认串口路径、引脚编号等
```

## 运行

```bash
python main.py
```

程序将启动主循环，轮询硬件状态并可对接 AI 引擎事件。按 `Ctrl+C` 优雅退出。

## 目录结构

```
hardware-rpi/
├── main.py                  # 硬件控制主入口
├── requirements.txt         # 依赖清单
├── .env.example             # 环境变量示例
└── drivers/
    ├── serial_ctrl.py       # 串口通信驱动
    ├── audio_ctrl.py        # 音频播放驱动
    ├── gpio_ctrl.py         # GPIO 驱动
    ├── tcp_server.py        # TCP 指令服务（后端接入）
    └── heartbeat.py         # 设备心跳上报
```

## 心跳上报

`main.py` 启动后由后台守护线程按 `HEARTBEAT_INTERVAL`（默认 30s）上报一次，内容包括：

| 字段 | 树莓派来源 | 开发机降级 |
| --- | --- | --- |
| `cpuTemp` | `/sys/class/thermal/thermal_zone0/temp` | `null` |
| `memoryUsage` | `/proc/meminfo` | `null` |
| `diskUsage` | `shutil.disk_usage("/")` | 实际值 |
| `hailoStatus` | `/dev/hailo0` 存在即 `active` | `inactive` |
| `cameraConnected` | `/dev/video*` | `0` |
| `speakerConnected` | `/proc/asound/cards` | `1` |

后端超过 **2 分钟**未收到心跳即判定离线（`device_status` 查询按 `last_heartbeat` 实时计算）。
`DEVICE_ID` 需与管理端设备列表、`device_status.device_id` 保持一致，默认 `rpi-001`。
