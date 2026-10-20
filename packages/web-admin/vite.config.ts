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
      // public-common 源码位于本包之外，显式指向本包依赖，避免打包时解析不到 vue / vue-router
      vue: fileURLToPath(new URL('./node_modules/vue', import.meta.url)),
      'vue-router': fileURLToPath(new URL('./node_modules/vue-router', import.meta.url)),
    },
    dedupe: ['vue', 'vue-router'],
  },
  server: {
    port: 5174, // h5=5173 / admin=5174 / touch=5175
    host: '0.0.0.0',
    open: false,
  },
})
