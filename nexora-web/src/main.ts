import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from '@/app/App.vue'
import { createAppRouter } from '@/app/router'
import { setUnauthorizedHandler } from '@/shared/api/http'
import { useSessionStore } from '@/features/authentication/stores/session'
import '@/style.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const router = createAppRouter()
app.use(router)

// 401 em qualquer rota protegida: limpa a sessão e volta ao login uma única vez.
setUnauthorizedHandler(() => {
  const session = useSessionStore(pinia)
  if (!session.hasToken) return
  session.clear()
  void router.replace({ name: 'login', query: { reason: 'expired' } })
})

app.mount('#app')
