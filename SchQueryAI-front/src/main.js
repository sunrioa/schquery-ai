import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/theme.css'
import './styles/admin-theme.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { initTheme } from './utils/theme'

initTheme()

// 避免 Element Plus / 浏览器 ResizeObserver 警告在开发环境被 overlay 当成致命错误遮罩页面
// 参考报错：ResizeObserver loop completed with undelivered notifications.
window.addEventListener(
  'error',
  (event) => {
    const message = event?.message || ''
    if (
      message.includes('ResizeObserver loop limit exceeded') ||
      message.includes('ResizeObserver loop completed with undelivered notifications')
    ) {
      event.stopImmediatePropagation()
    }
  },
  true
)

window.addEventListener(
  'unhandledrejection',
  (event) => {
    const reason = event?.reason
    const message = reason?.message || String(reason || '')
    if (
      message.includes('ResizeObserver loop limit exceeded') ||
      message.includes('ResizeObserver loop completed with undelivered notifications')
    ) {
      event.preventDefault()
      event.stopImmediatePropagation()
    }
  },
  true
)

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})
app.mount('#app')
