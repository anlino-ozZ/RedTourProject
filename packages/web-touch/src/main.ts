import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@common/style/index.less'
import App from './App.vue'
import router from './router'

// 触摸屏无键盘 F12，开发环境和生产都注入 vConsole
// 生产环境可通过 localStorage.setItem('__vconsole_disabled__', '1') 临时关闭
async function initVConsole() {
  if (typeof localStorage !== 'undefined' && localStorage.getItem('__vconsole_disabled__') === '1') return
  const VConsole = (await import('vconsole')).default
  new VConsole()
}
void initVConsole()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
