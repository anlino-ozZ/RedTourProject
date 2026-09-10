# ai-engine — AI 引擎

> 青年红色筑梦之旅 · 红色文旅智能导览系统
>
> Ollama + YOLOv8-pose + Hailo8L + LLM Wiki + 机架工程

## 模块职责

- **LLM Wiki 知识问答**：将党史/地方志/老兵家书编译为结构化互联 Markdown 百科，离线智能问答
- **YOLOv8-pose 姿态识别**：检测人体关键点，触发敬礼/推磨/挥手等红色主题互动动作
- **Hailo8L 硬件加速**：树莓派5 + AI HAT+，13~26 TOPS，30+ FPS 实时推理

## 技术原理简述

### LLM Wiki 模式（Karpathy）
将原始素材"编译"为结构化互联 Markdown 百科，摄入时合成、建立 `[[wiki-links]]`，知识复利。相比 RAG 切片式检索，规避幻觉与知识蒸发问题。

### 机架工程 Harness（六组件）
- **执行环境**、**工具注册表**、**上下文管理器**、**状态存储**、**生命周期钩子**、**评估接口**
- **LocalContextMiddleware**：注入环境地图（景点分布/历史条目索引/CV 指令），实现零回合定向
- **推理三明治**：关键决策用高推理模型，润色阶段用轻量模型
- **验证门 + todo.md 协议**：输出前核对 Wiki 权威引用，冲突则回滚

### YOLOv8-pose
Hailo8L 13~26 TOPS，Pi5+AI HAT+ 下 30+ FPS，检测关节点触发互动动作。

启用 `HAILO_ENABLED=true` 后，引擎从 `HAILO_POSE_HEF` 加载已编译的轻量姿态 HEF，
常驻复用 VStream 推理管线；SDK、设备或 HEF 不可用时自动回退到本地 YOLO。
`pose_performance` 日志按窗口记录 Hailo FPS，用于在目标树莓派上验收 30+ FPS。
HEF 需要包含 Hailo NMS 后处理，单个检测行为边界框、置信度及 17 组 COCO 关键点。

## 环境准备

```bash
# 1. 创建虚拟环境
python -m venv venv

# 2. 激活虚拟环境
#    Windows PowerShell:
.\venv\Scripts\Activate.ps1
#    Linux / macOS:
source venv/bin/activate

# 3. 安装依赖
pip install -r requirements.txt

# 4. 配置环境变量
cp .env.example .env
```

> 注意：`hailo-platform` 仅在树莓派环境安装，开发机跳过。代码已做 try-except 降级。

## 启动服务

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8001
```

启动后访问：

- 健康检查：<http://localhost:8001/engine/health>
- 问答接口：`POST /engine/ask`
- 语音识别：`POST /engine/stt`（multipart 字段 `audio`）
- 姿态检测：`POST /engine/pose`（JSON 字段 `frame`，支持纯 Base64/Data URL）
- 姿态流：`WS /engine/pose/stream?scenicAreaId=<id>`（仅供业务后端代理）
- API 文档：<http://localhost:8001/docs>

健康检查固定返回 `status`、`engine`、`ollama`、`hailo`、`wikiCount`。Ollama
不可达、目标模型未安装或已启用的 Hailo8L 不可用时，`status` 为 `degraded`，
接口仍正常响应，便于业务后端继续运行降级逻辑。

问答接口接收 `question`、可选的 `scenicAreaId` 与 `useVoice`，固定返回
`question`、`answer`、`sources`、`durationMs`、`audioUrl`。底层 Ollama、Wiki
或 TTS 尚未就绪时会返回可展示的降级答案，不会向调用方抛出依赖异常。
Ollama 模型通过 `OLLAMA_KEEP_ALIVE` 保持常驻，Wiki 索引按文件变更自动刷新并在请求间
复用。`ai_ask_performance` 日志分别记录检索、推理、验证、TTS 和总耗时；超过
`ASK_TARGET_MS` 时使用 warning 级别，便于定位未达到 3 秒目标的阶段。

语音识别接口接收 WAV、MP3、M4A、WebM、OGG 或 FLAC，默认限制 20MB、60 秒，返回
`text` 与 `durationMs`。模型仅在首次识别时加载并复用；默认要求 `small` 模型已缓存在
`STT_MODEL_DIR`，运行期间不会联网下载。空音频、静音、超限及模型不可用均返回明确错误。

姿态检测接口接收 Base64 图片及可选 `scenicAreaId`，限制 JPEG/PNG/WebP、10MB 和
1200 万像素，固定返回 `action/confidence/keypoints/triggerContent/timestamp`。YOLO 权重或
运行依赖不可用时返回 `action=unknown`，不会使接口崩溃。

姿态 WebSocket 接收 `{"frame":"Base64/Data URL"}`，逐帧返回相同姿态结果结构；默认单条
消息上限 1410 万字符、最大 20 个连接、空闲 60 秒关闭。稳定动作门控由业务后端代理执行。

## 目录结构

```
ai-engine/
├── main.py                  # 应用入口
├── requirements.txt         # 依赖清单
├── .env.example             # 环境变量示例
└── engine/
    ├── llm_wiki.py          # LLM Wiki 知识编译器
    ├── harness.py           # 机架工程（推理编排）
    ├── hailo_pose.py        # Hailo8L HEF 常驻姿态推理
    ├── yolov8_pose.py       # YOLOv8-pose 姿态检测
    └── ollama_infer.py      # Ollama 本地大模型推理
```
