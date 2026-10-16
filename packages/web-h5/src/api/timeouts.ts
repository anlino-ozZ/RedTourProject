/**
 * 前端接口超时统一配置（毫秒）
 *
 * 问答/讲解链路从外到内共 4 层，任何一层先超时，用户看到的就是失败，所以必须逐层放大：
 *   1. 浏览器 axios（本文件 AI_REQUEST_TIMEOUT_MS）
 *   2. Vite dev 代理（vite.config.ts 的 timeout / proxyTimeout）
 *   3. server-business 读超时 ai-engine.read-timeout-ms（application.yml）
 *   4. ai-engine → Ollama：本机纯 CPU 跑 qwen2.5:7b，单次问答实测 110~215s
 *
 * 取值依据：后端读超时按 300s 对齐（本地由启动脚本用 SPRING_APPLICATION_JSON 覆盖，
 * 仓库 application.yml 仍是 30s，需服务端负责人调高）；前端取 330s，即等后端读完
 * 300s 并返回降级文案后再留 30s 传输缓冲，避免前端比后端先断开、只看到"网络异常"。
 */
export const AI_REQUEST_TIMEOUT_MS = 330000

/** 短请求（STT 语音转文字、健康检查等）：后端秒级返回，超时仅作兜底 */
export const SHORT_REQUEST_TIMEOUT_MS = 30000

/** 单帧姿态识别：首帧需要加载模型，放宽到 60s */
export const POSE_RECOGNIZE_TIMEOUT_MS = 60000
