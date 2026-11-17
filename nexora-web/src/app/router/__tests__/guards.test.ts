import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { tokenStorage } from '@/shared/api/tokenStorage'
import { authApi } from '@/features/authentication/api/authApi'
import { createAppRouter } from '..'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))

const me = (role: string) => ({ id: '1', email: 'a@b.com', role, status: 'ACTIVE', createdAt: '2026-11-17T10:00:00Z' })

async function navigate(path: string) {
  const router = createAppRouter()
  await router.push(path)
  await router.isReady()
  return router.currentRoute.value
}

describe('route guards', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('sends visitors on protected routes to login', async () => {
    expect((await navigate('/estudante')).name).toBe('login')
  })

  it('sends the root to login when logged out', async () => {
    expect((await navigate('/')).name).toBe('login')
  })

  it.each([
    ['STUDENT', '/estudante'],
    ['COMPANY', '/empresa'],
    ['UNIVERSITY', '/universidade'],
    ['ADMIN', '/admin'],
  ])('redirects %s from the root to its own area', async (role, path) => {
    tokenStorage.set('tok')
    vi.mocked(authApi.me).mockResolvedValue(me(role) as never)
    expect((await navigate('/')).path).toBe(path)
  })

  it('keeps a student out of the company area (forbidden page)', async () => {
    tokenStorage.set('tok')
    vi.mocked(authApi.me).mockResolvedValue(me('STUDENT') as never)
    expect((await navigate('/empresa')).name).toBe('forbidden')
  })

  it('bounces authenticated users away from login and register', async () => {
    tokenStorage.set('tok')
    vi.mocked(authApi.me).mockResolvedValue(me('ADMIN') as never)
    expect((await navigate('/login')).path).toBe('/admin')
    expect((await navigate('/cadastro')).path).toBe('/admin')
  })

  it('shows a not-found page for unknown routes', async () => {
    expect((await navigate('/nao-existe')).name).toBe('not-found')
  })
})
