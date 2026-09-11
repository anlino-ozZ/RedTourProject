import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 引入 public-common 全局红色主题样式（三端统一）
import '@common/style/index.less'
import App from './App.vue'
import router from './router'
import pinia from './store'

const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
