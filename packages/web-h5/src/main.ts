import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 引入 public-common 全局红色主题样式（三端统一）
import '@common/style/index.less'
// 适老大字模式（html 全局 class 开关）
import './styles/font-scale.css'
import App from './App.vue'
import router from './router'
import { getSettings } from './utils/profileStorage'
import { applyFontScale } from './utils/fontScale'

// 挂载前恢复字体档位，避免大字用户刷新时先看到标准字号
applyFontScale(getSettings().fontScale)

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
