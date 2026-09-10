"""
ai-engine 入口
==============
青年红色筑梦之旅 - 红色文旅智能导览系统 AI 引擎
集成 Ollama LLM（Wiki 模式）、YOLOv8-pose 姿态检测、Hailo8L 加速。
"""

import asyncio
import os

import uvicorn
from fastapi import FastAPI, File, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field

from engine.ask_service import AskService
from engine.health import HealthChecker
from engine.llm_wiki import WikiCompileError, WikiCompiler
from engine.pose_service import PoseService, PoseValidationError
from engine.pose_stream import (
    PoseStreamConnectionLimiter,
    PoseStreamMessageTooLargeError,
    PoseStreamProtocol,
    PoseStreamProtocolError,
)
from engine.stt import (
    SpeechTranscriber,
    SttDurationError,
    SttEmptyAudioError,
    SttFileTooLargeError,
    SttRecognitionError,
    SttUnavailableError,
    SttValidationError,
)
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
speech_transcriber = SpeechTranscriber()
pose_service = PoseService()
pose_stream_protocol = PoseStreamProtocol()
pose_stream_limiter = PoseStreamConnectionLimiter()


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


class SttResponse(BaseModel):
    """本地语音识别响应体。"""

    model_config = ConfigDict(populate_by_name=True)

    text: str
    duration_ms: int = Field(alias="durationMs", ge=0)


class PoseRequest(BaseModel):
    """Base64 单帧姿态识别请求体。"""

    model_config = ConfigDict(populate_by_name=True, str_strip_whitespace=True)

    frame: str = Field(min_length=1, max_length=14_100_000)
    scenic_area_id: int | None = Field(default=None, alias="scenicAreaId", gt=0)


class PoseTriggerResponse(BaseModel):
    """动作命中后的互动内容。"""

    model_config = ConfigDict(populate_by_name=True)

    title: str
    audio_url: str = Field(alias="audioUrl")
    wiki_ref: str = Field(alias="wikiRef")


class PoseResponse(BaseModel):
    """姿态识别稳定响应体。"""

    model_config = ConfigDict(populate_by_name=True)

    action: str
    confidence: float = Field(ge=0, le=1)
    keypoints: list[list[float]] = Field(default_factory=list)
    trigger_content: PoseTriggerResponse | None = Field(default=None, alias="triggerContent")
    timestamp: int = Field(ge=0)


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


@app.post("/engine/stt", response_model=SttResponse, response_model_by_alias=True)
def stt(audio: UploadFile = File(...)) -> SttResponse:
    """识别 multipart 字段 ``audio`` 中的本地音频。"""
    try:
        result = speech_transcriber.transcribe(
            audio.file,
            filename=audio.filename,
            content_type=audio.content_type,
        )
        return SttResponse.model_validate(result)
    except SttFileTooLargeError as exc:
        raise HTTPException(status_code=413, detail=str(exc)) from exc
    except (SttDurationError, SttEmptyAudioError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except SttValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except SttUnavailableError as exc:
        raise HTTPException(status_code=503, detail="本地语音识别模型暂不可用") from exc
    except SttRecognitionError as exc:
        raise HTTPException(status_code=500, detail="音频解码或语音识别失败") from exc


@app.post("/engine/pose", response_model=PoseResponse, response_model_by_alias=True)
def pose(req: PoseRequest) -> PoseResponse:
    """解码 Base64 图像并执行单帧姿态动作识别。"""
    try:
        result = pose_service.recognize(req.frame, req.scenic_area_id)
        return PoseResponse.model_validate(result)
    except PoseValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.websocket("/engine/pose/stream")
async def pose_stream(
    websocket: WebSocket,
    scenic_area_id: int | None = Query(default=None, alias="scenicAreaId", gt=0),
) -> None:
    """逐帧处理业务后端转发的姿态 WebSocket 消息。"""
    if not pose_stream_limiter.try_acquire():
        await websocket.accept()
        await websocket.close(code=1013, reason="姿态识别连接已满")
        return

    idle_timeout = _positive_float_env("POSE_WS_IDLE_TIMEOUT_SECONDS", 60.0)
    try:
        await websocket.accept()
        while True:
            try:
                message = await asyncio.wait_for(websocket.receive(), timeout=idle_timeout)
                if message.get("type") == "websocket.disconnect":
                    raise WebSocketDisconnect(code=message.get("code", 1000))
                payload = message.get("text")
                if not isinstance(payload, str):
                    await websocket.close(code=1007, reason="姿态帧消息无效")
                    break
                frame = pose_stream_protocol.parse_frame(payload)
                result = await run_in_threadpool(
                    pose_service.recognize, frame, scenic_area_id
                )
                response = PoseResponse.model_validate(result)
                await websocket.send_json(response.model_dump(by_alias=True))
            except asyncio.TimeoutError:
                await websocket.close(code=1001, reason="姿态连接空闲超时")
                break
            except PoseStreamMessageTooLargeError:
                await websocket.close(code=1009, reason="姿态帧消息过大")
                break
            except (PoseStreamProtocolError, PoseValidationError):
                await websocket.close(code=1007, reason="姿态帧消息无效")
                break
    except WebSocketDisconnect:
        pass
    finally:
        pose_stream_limiter.release()


def _positive_float_env(name: str, default: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
        return value if value > 0 else default
    except (TypeError, ValueError):
        return default


if __name__ == "__main__":
    # 启动 uvicorn，监听 8001 端口
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
