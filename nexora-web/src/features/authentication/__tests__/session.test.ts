import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@/shared/api/apiError'
import { tokenStorage } from '@/shared/api/tokenStorage'
import { authApi } from '../api/authApi'
import { useSessionStore } from '../stores/session'

vi.mock('../api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))

const account = (role: 'STUDENT' | 'UNIVERSITY' = 'STUDENT') => ({
  id: '1',
  email: 'ana@exemplo.com',
  role,
  status: 'ACTIVE' as const,
  createdAt: '2026-11-17T10:00:00Z',
})

describe('session store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('logs in, keeps the token in sessionStorage and takes the role from the login response', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 3600, role: 'COMPANY' })
    vi.mocked(authApi.me).mockResolvedValue({ ...account(), role: 'COMPANY' })
    const session = useSessionStore()

    await session.login({ email: 'a@b.com', password: 'x' })

    expect(tokenStorage.get()).toBe('tok')
    expect(session.isAuthenticated).toBe(true)
    expect(session.role).toBe('COMPANY')
    expect(session.homePath).toBe('/empresa')
  })

  it('does not keep the token when login fails', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'x'))
    const session = useSessionStore()
    await expect(session.login({ email: 'a@b.com', password: 'x' })).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })
    expect(tokenStorage.get()).toBeNull()
    expect(session.isAuthenticated).toBe(false)
  })

  it('drops the session when the account lookup after login fails', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 3600, role: 'STUDENT' })
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(503, 'SERVICE_UNAVAILABLE', 'x'))
    const session = useSessionStore()
    await expect(session.login({ email: 'a@b.com', password: 'x' })).rejects.toBeInstanceOf(ApiError)
    expect(tokenStorage.get()).toBeNull()
  })

  it('restores the session on reload by revalidating the account through the API', async () => {
    tokenStorage.set('tok')
    vi.mocked(authApi.me).mockResolvedValue(account('UNIVERSITY'))
    const session = useSessionStore()
    await session.restore()
    expect(session.isAuthenticated).toBe(true)
    expect(session.role).toBe('UNIVERSITY')
    expect(session.initialized).toBe(true)
  })

  it('clears an invalid token on restore (401) and finishes initialization', async () => {
    tokenStorage.set('velho')
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'x'))
    const session = useSessionStore()
    await session.restore()
    expect(tokenStorage.get()).toBeNull()
    expect(session.isAuthenticated).toBe(false)
    expect(session.initialized).toBe(true)
  })

  it('keeps the token and retries later when the API is unavailable during restore', async () => {
    tokenStorage.set('tok')
    vi.mocked(authApi.me).mockRejectedValue(new ApiError(503, 'SERVICE_UNAVAILABLE', 'x'))
    const session = useSessionStore()
    await session.restore()
    expect(tokenStorage.get()).toBe('tok')
    expect(session.isAuthenticated).toBe(false)
    expect(session.initialized).toBe(false)
    expect(session.restoreError?.isUnavailable).toBe(true)
  })

  it('logout clears token and user', async () => {
    tokenStorage.set('tok')
    vi.mocked(authApi.me).mockResolvedValue(account())
    const session = useSessionStore()
    await session.restore()
    session.logout()
    expect(tokenStorage.get()).toBeNull()
    expect(session.user).toBeNull()
  })
})
