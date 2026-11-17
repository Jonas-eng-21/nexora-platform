import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { vi } from 'vitest'
import App from '@/app/App.vue'
import { createAppRouter } from '@/app/router'
import { authApi } from '@/features/authentication/api/authApi'
import { tokenStorage } from '@/shared/api/tokenStorage'

/** Monta o app inteiro numa rota, com sessão do papel indicado (authApi deve estar mockado no arquivo de teste). */
export async function mountAt(path: string, role: 'STUDENT' | 'COMPANY' | 'UNIVERSITY' = 'STUDENT') {
  tokenStorage.set('tok')
  vi.mocked(authApi.me).mockResolvedValue({ id: '1', email: 'ana@teste.dev', role, status: 'ACTIVE', createdAt: '2026-11-17T10:00:00Z' })
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [pinia, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

export const settle = async () => {
  await flushPromises()
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
}

export function resetDom() {
  sessionStorage.clear()
  document.body.innerHTML = ''
}
