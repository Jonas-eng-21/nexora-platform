import axios from 'axios'
import { toApiError } from './apiError'
import { tokenStorage } from './tokenStorage'

type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null

/** Registrado no bootstrap para evitar dependência circular entre cliente HTTP e store. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler
}

export const http = axios.create({
  baseURL: '/api/v1',
  headers: { Accept: 'application/json' },
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error)
    // /auth/* devolve 401 para credencial inválida: isso é erro de formulário, não sessão expirada.
    const url = axios.isAxiosError(error) ? (error.config?.url ?? '') : ''
    if (apiError.status === 401 && !url.startsWith('/auth/')) onUnauthorized?.()
    return Promise.reject(apiError)
  },
)
