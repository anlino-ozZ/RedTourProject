import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 引入 public-common 全局红色主题样式（三端统一）
import '@common/style/index.less'
// 管理后台 Element Plus 主题微调（须在 element-plus 样式之后）
import '@/styles/admin-theme.less'
import App from './App.vue'
import router from './router'
import pinia from './store'

const app = createApp(App)

// 全局错误兜底：未捕获的渲染/生命周期错误只记录日志，不向上抛出。
// 否则 Vue 在 dev 下会把错误 rethrow，打断当前 patch，导致之后路由切换只改 URL、内容区不更新。
app.config.errorHandler = (err, _instance, info) => {
  console.error('[admin] 未捕获错误:', info, err)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
