/**
 * 触摸屏端接口超时统一配置（毫秒）
 *
 * 问答链路从外到内共 3 层，任何一层先超时，游客看到的就是失败，所以必须逐层放大：
 *   1. 浏览器 axios（本文件 AI_REQUEST_TIMEOUT_MS）
 *   2. server-business 读超时 ai-engine.read-timeout-ms（application.yml）
 *   3. ai-engine → 模型：本地 Ollama 跑 qwen2.5:7b 单次问答实测 110~215s
 *
 * 与 web-h5 的差别：触摸屏直连后端 8000，没有 Vite dev 代理这一层，其余一致。
 * 取值与 web-h5 对齐：后端读超时按 300s 对齐（仓库 application.yml 仍是 30s，
 * 需服务端负责人调高），前端取 330s，即等后端读完 300s 并返回降级文案后再留
 * 30s 传输缓冲，避免前端比后端先断开、只看到“网络异常”。
 */
export const AI_REQUEST_TIMEOUT_MS = 330000

/** 短请求（STT 语音转文字、推荐问题、健康检查等）：后端秒级返回，超时仅作兜底 */
export const SHORT_REQUEST_TIMEOUT_MS = 30000
