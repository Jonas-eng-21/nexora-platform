import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createAppRouter } from '@/app/router'
import { ApiError } from '@/shared/api/apiError'
import App from '@/app/App.vue'
import { authApi } from '../api/authApi'

vi.mock('../api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))

async function openRegister() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter()
  await router.push('/cadastro')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [pinia, router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof openRegister>>

async function fillCredentials(wrapper: Wrapper) {
  await wrapper.get('input[type="email"]').setValue('nova@exemplo.com')
  await wrapper.get('input[autocomplete="new-password"]').setValue('senha-segura-1')
}

describe('RegisterView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    document.body.innerHTML = ''
    vi.mocked(authApi.register).mockReset()
  })

  it('never offers the ADMIN role', async () => {
    const wrapper = await openRegister()
    const values = wrapper.findAll('input[type="radio"]').map((r) => (r.element as HTMLInputElement).value)
    expect(values).toEqual(['STUDENT', 'COMPANY', 'UNIVERSITY'])
  })

  it('registers a student without sending the university object', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ id: '1', email: 'nova@exemplo.com', role: 'STUDENT', status: 'ACTIVE' })
    const wrapper = await openRegister()
    await fillCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(authApi.register).toHaveBeenCalledWith({ email: 'nova@exemplo.com', password: 'senha-segura-1', role: 'STUDENT' })
    expect(wrapper.text()).toContain('Conta criada')
  })

  it('requires the institution data for universities and sends them trimmed', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ id: '2', email: 'nova@exemplo.com', role: 'UNIVERSITY', status: 'PENDING_APPROVAL' })
    const wrapper = await openRegister()
    await wrapper.get('input[value="UNIVERSITY"]').setValue(true)
    await fillCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o nome da instituição.')
    expect(authApi.register).not.toHaveBeenCalled()

    const name = wrapper.findAll('input[type="text"]')[0]!
    await name.setValue('  Universidade de Garanhuns ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(authApi.register).toHaveBeenCalledWith(
      expect.objectContaining({ role: 'UNIVERSITY', university: expect.objectContaining({ name: 'Universidade de Garanhuns' }) }),
    )
  })

  it('shows the pending-approval confirmation for universities, without opening a session', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ id: '2', email: 'u@x.com', role: 'UNIVERSITY', status: 'PENDING_APPROVAL' })
    const wrapper = await openRegister()
    await wrapper.get('input[value="UNIVERSITY"]').setValue(true)
    await fillCredentials(wrapper)
    await wrapper.findAll('input[type="text"]')[0]!.setValue('Faculdade X')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Cadastro recebido')
    expect(wrapper.text()).toContain('aprovação do administrador')
    expect(sessionStorage.getItem('nexora.accessToken')).toBeNull()
  })

  it('marks the e-mail field when it is already registered (409)', async () => {
    vi.mocked(authApi.register).mockRejectedValue(new ApiError(409, 'EMAIL_ALREADY_REGISTERED', 'Já existe uma conta com este e-mail.'))
    const wrapper = await openRegister()
    await fillCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Já existe uma conta')
    expect(wrapper.get('input[type="email"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('Este e-mail já está cadastrado.')
  })

  it('maps server field errors onto the matching fields', async () => {
    vi.mocked(authApi.register).mockRejectedValue(
      new ApiError(400, 'VALIDATION_ERROR', 'Revise os campos.', { email: 'deve ser um endereço de e-mail' }),
    )
    const wrapper = await openRegister()
    await fillCredentials(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('input[type="email"]').attributes('aria-invalid')).toBe('true')
  })
})
