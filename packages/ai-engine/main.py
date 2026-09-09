"""
ai-engine 入口
==============
青年红色筑梦之旅 - 红色文旅智能导览系统 AI 引擎
集成 Ollama LLM（Wiki 模式）、YOLOv8-pose 姿态检测、Hailo8L 加速。
"""

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel

from engine.health import HealthChecker

# 创建 FastAPI 应用实例
app = FastAPI(
    title="红色文旅智能导览系统 - AI 引擎",
    description="LLM Wiki 知识问答 + YOLOv8-pose 姿态动作识别",
    version="0.1.0",
)

health_checker = HealthChecker()


class AskRequest(BaseModel):
    """问答请求体"""
    question: str


class PoseRequest(BaseModel):
    """姿态检测请求体"""
    frame_path: str  # 图像路径（骨架阶段用路径占位，后续可改为 base64）


@app.get("/engine/health")
def health() -> dict[str, str | int]:
    """返回 Ollama、Hailo8L 和 Wiki 的健康状态。"""
    return health_checker.check()


@app.post("/engine/ask")
def ask(req: AskRequest) -> dict[str, str]:
    """调用机架 Harness 进行知识问答"""
    # TODO: 接入 engine.harness.Harness.answer()
    return {"answer": f"[TODO] 待接入 Harness 处理问题：{req.question}"}


@app.post("/engine/pose")
def pose(req: PoseRequest) -> dict[str, str]:
    """调用姿态检测器进行动作识别"""
    # TODO: 接入 engine.yolov8_pose.PoseDetector.detect()
    return {"action": "[TODO] 待接入 PoseDetector 处理图像", "path": req.frame_path}


if __name__ == "__main__":
    # 启动 uvicorn，监听 8001 端口
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
