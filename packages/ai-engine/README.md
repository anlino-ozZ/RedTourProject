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
- 姿态检测：`POST /engine/pose`
- API 文档：<http://localhost:8001/docs>

健康检查固定返回 `status`、`engine`、`ollama`、`hailo`、`wikiCount`。Ollama
不可达、目标模型未安装或已启用的 Hailo8L 不可用时，`status` 为 `degraded`，
接口仍正常响应，便于业务后端继续运行降级逻辑。

## 目录结构

```
ai-engine/
├── main.py                  # 应用入口
├── requirements.txt         # 依赖清单
├── .env.example             # 环境变量示例
└── engine/
    ├── llm_wiki.py          # LLM Wiki 知识编译器
    ├── harness.py           # 机架工程（推理编排）
    ├── yolov8_pose.py       # YOLOv8-pose 姿态检测
    └── ollama_infer.py      # Ollama 本地大模型推理
```
