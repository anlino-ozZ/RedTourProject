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
      '@red-tour/common': fileURLToPath(new URL('../../public-common', import.meta.url)),
    },
  },
  server: {
    port: 5175, // h5=5173 / admin=5174 / touch=5175
    host: '0.0.0.0',
    open: false,
  },
})
