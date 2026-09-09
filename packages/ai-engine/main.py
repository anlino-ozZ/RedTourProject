"""
ai-engine 入口
==============
青年红色筑梦之旅 - 红色文旅智能导览系统 AI 引擎
集成 Ollama LLM（Wiki 模式）、YOLOv8-pose 姿态检测、Hailo8L 加速。
"""

import os

import uvicorn
from fastapi import FastAPI
from fastapi import HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field

from engine.ask_service import AskService
from engine.health import HealthChecker
from engine.llm_wiki import WikiCompileError, WikiCompiler
from engine.tts import TtsError, TtsSynthesizer

# 创建 FastAPI 应用实例
app = FastAPI(
    title="红色文旅智能导览系统 - AI 引擎",
    description="LLM Wiki 知识问答 + YOLOv8-pose 姿态动作识别",
    version="0.1.0",
)

health_checker = HealthChecker()
ask_service = AskService()
wiki_compiler = WikiCompiler(
    wiki_dir=os.getenv("WIKI_DIR", "./wiki"),
    build_dir=os.getenv("WIKI_BUILD_DIR", "./wiki_build"),
)
tts_synthesizer = TtsSynthesizer()


class AskRequest(BaseModel):
    """问答请求体。"""

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    question: str = Field(min_length=1, max_length=500)
    scenic_area_id: int | None = Field(default=None, alias="scenicAreaId", gt=0)
    use_voice: bool = Field(default=False, alias="useVoice")


class AskResponse(BaseModel):
    """问答响应体，别名保证 HTTP JSON 使用 camelCase。"""

    model_config = ConfigDict(populate_by_name=True)

    question: str
    answer: str
    sources: list[str]
    duration_ms: int = Field(alias="durationMs", ge=0)
    audio_url: str | None = Field(default=None, alias="audioUrl")


class WikiCompileRequest(BaseModel):
    """Wiki 编译请求体，兼容业务后端 camelCase 与旧文档 snake_case。"""

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    scenic_area_id: int | None = Field(default=None, alias="scenicAreaId", gt=0)
    title: str | None = Field(default=None, max_length=100)
    content: str = Field(min_length=1)
    tags: list[str] = Field(default_factory=list, max_length=50)


class WikiCompileResponse(BaseModel):
    """Wiki 编译稳定响应。"""

    status: str
    file_path: str | None = None
    links: list[str] = Field(default_factory=list)
    error: str | None = None


class PoseRequest(BaseModel):
    """姿态检测请求体"""
    frame_path: str  # 图像路径（骨架阶段用路径占位，后续可改为 base64）


@app.get("/engine/health")
def health() -> dict[str, str | int]:
    """返回 Ollama、Hailo8L 和 Wiki 的健康状态。"""
    return health_checker.check()


@app.post("/engine/ask", response_model=AskResponse, response_model_by_alias=True)
def ask(req: AskRequest) -> AskResponse:
    """执行知识问答并返回稳定的 camelCase 响应。"""
    result = ask_service.ask(req.question, req.scenic_area_id, req.use_voice)
    return AskResponse.model_validate(result)


@app.post("/engine/wiki/compile", response_model=WikiCompileResponse)
def compile_wiki(req: WikiCompileRequest) -> WikiCompileResponse:
    """将原始素材编译为结构化 Wiki；失败时返回 failed 而非 HTTP 500。"""
    try:
        file_path = wiki_compiler.compile_source(
            req.content,
            title=req.title,
            scenic_area_id=req.scenic_area_id,
            tags=req.tags,
        )
        index = wiki_compiler._read_index()
        entry = wiki_compiler._find_index_entry(index, file_path) or {}
        return WikiCompileResponse(
            status="done",
            file_path=file_path,
            links=wiki_compiler._normalize_values(entry.get("links")),
        )
    except WikiCompileError as exc:
        return WikiCompileResponse(status="failed", error=str(exc))
    except (OSError, ValueError, TypeError) as exc:
        # 文件系统和格式异常都在接口边界转成可识别失败，避免堆栈泄漏。
        return WikiCompileResponse(status="failed", error="Wiki 编译或写入失败")


@app.get("/engine/audio/{filename}", response_class=FileResponse)
def get_audio(filename: str) -> FileResponse:
    """向业务后端提供受控的 TTS 音频文件。"""
    try:
        target = tts_synthesizer.resolve_audio(filename)
    except TtsError as exc:
        raise HTTPException(status_code=404, detail="音频文件不存在") from exc
    return FileResponse(target, media_type="audio/wav", filename=filename)


@app.post("/engine/pose")
def pose(req: PoseRequest) -> dict[str, str]:
    """调用姿态检测器进行动作识别"""
    # TODO: 接入 engine.yolov8_pose.PoseDetector.detect()
    return {"action": "[TODO] 待接入 PoseDetector 处理图像", "path": req.frame_path}


if __name__ == "__main__":
    # 启动 uvicorn，监听 8001 端口
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
