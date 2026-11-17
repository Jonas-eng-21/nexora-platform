import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createAppRouter } from '@/app/router'
import { ApiError } from '@/shared/api/apiError'
import App from '@/app/App.vue'
import { authApi } from '../api/authApi'

vi.mock('../api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('@/features/companies/api/companyApi', () => ({ companyApi: { getProfile: vi.fn().mockResolvedValue({ id: 'c', name: 'ACME', sector: null, description: null, location: null }), saveProfile: vi.fn() } }))
vi.mock('@/features/jobs/api/jobManagementApi', () => ({ jobManagementApi: { listMine: vi.fn().mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }) } }))

async function openLogin() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter()
  await router.push('/login')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [pinia, router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

async function fillAndSubmit(wrapper: Awaited<ReturnType<typeof openLogin>>['wrapper'], email: string, password: string) {
  await wrapper.get('input[type="email"]').setValue(email)
  await wrapper.get('input[autocomplete="current-password"]').setValue(password)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('LoginView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    document.body.innerHTML = ''
  })

  it('blocks empty submit with field errors and no request', async () => {
    const { wrapper } = await openLogin()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe seu e-mail.')
    expect(wrapper.text()).toContain('Informe sua senha.')
    expect(authApi.login).not.toHaveBeenCalled()
    expect(wrapper.get('input[type="email"]').attributes('aria-invalid')).toBe('true')
  })

  it('logs in and redirects each role to its own area', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1, role: 'COMPANY' })
    vi.mocked(authApi.me).mockResolvedValue({ id: '1', email: 'e@x.com', role: 'COMPANY', status: 'ACTIVE', createdAt: '2026-11-17T10:00:00Z' })
    const { wrapper, router } = await openLogin()
    await fillAndSubmit(wrapper, 'e@x.com', 'segredo123')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/empresa'))
    await vi.waitFor(() => expect(wrapper.text()).toContain('Vagas abertas e candidaturas aguardando análise'))
  })

  it('shows an announced error for invalid credentials and stays on the page', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'E-mail ou senha incorretos. Confira os dados e tente de novo.'))
    const { wrapper, router } = await openLogin()
    await fillAndSubmit(wrapper, 'a@b.com', 'errada')
    expect(wrapper.get('[role="alert"]').text()).toContain('incorretos')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('explains that the server is unavailable and lets the user retry', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError(503, 'SERVICE_UNAVAILABLE', 'O servidor está indisponível ou ainda iniciando.'))
    const { wrapper } = await openLogin()
    await fillAndSubmit(wrapper, 'a@b.com', 'x')
    expect(wrapper.get('[role="alert"]').text()).toContain('tentar de novo')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('shows the pending-approval message for a university account', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError(403, 'ACCOUNT_PENDING_APPROVAL', 'Esta conta de universidade ainda aguarda aprovação do administrador.'))
    const { wrapper } = await openLogin()
    await fillAndSubmit(wrapper, 'u@x.com', 'x')
    expect(wrapper.get('[role="alert"]').text()).toContain('aguarda aprovação')
  })
})
