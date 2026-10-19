import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 红色文旅前端 Vite 配置：注入 public-common 别名，统一引用公共包源码
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@common': fileURLToPath(new URL('../../public-common', import.meta.url)),
      '@red-tour-project/common': fileURLToPath(new URL('../../public-common', import.meta.url)),
    },
  },
  server: {
    port: 5173, // h5=5173 / admin=5174 / touch=5175
    host: '0.0.0.0',
    open: false,
    proxy: {
      // 后端 API 与 TTS 音频资源统一代理到 server-business（8000）
      // timeout / proxyTimeout 必须不小于前端 axios 超时（见 src/api/timeouts.ts）：
      // 问答走本地模型推理需 110~215s，dev 代理默认可能先断开连接
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        timeout: 360000,
        proxyTimeout: 360000,
      },
      '/audio': { target: 'http://localhost:8000', changeOrigin: true },
    },
  },
})
