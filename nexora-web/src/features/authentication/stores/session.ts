import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { tokenStorage } from '@/shared/api/tokenStorage'
import { authApi } from '../api/authApi'
import { ROLE_HOME } from '../roles'
import type { LoginRequest, UserAccount, UserRole } from '../types'

/** Sessão JWT: token no sessionStorage; papel e status sempre revalidados em /users/me. */
export const useSessionStore = defineStore('session', () => {
  const token = ref<string | null>(tokenStorage.get())
  const user = ref<UserAccount | null>(null)
  const loginRole = ref<UserRole | null>(null)
  const initialized = ref(false)
  const restoreError = ref<ApiError | null>(null)

  const hasToken = computed(() => token.value !== null)
  const isAuthenticated = computed(() => token.value !== null && user.value !== null)
  const role = computed<UserRole | null>(() => user.value?.role ?? loginRole.value)
  const homePath = computed(() => (role.value ? ROLE_HOME[role.value] : '/login'))

  function clear() {
    tokenStorage.clear()
    token.value = null
    user.value = null
    loginRole.value = null
  }

  async function login(credentials: LoginRequest) {
    const response = await authApi.login(credentials)
    tokenStorage.set(response.accessToken)
    token.value = response.accessToken
    loginRole.value = response.role
    try {
      user.value = await authApi.me()
    } catch (error) {
      clear()
      throw toApiError(error)
    }
    initialized.value = true
  }

  /** Recupera a sessão da aba (recarga). Falha de disponibilidade mantém o token e permite nova tentativa. */
  async function restore() {
    restoreError.value = null
    if (token.value === null) {
      initialized.value = true
      return
    }
    try {
      user.value = await authApi.me()
      initialized.value = true
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.isUnavailable) {
        restoreError.value = apiError
        return
      }
      clear()
      initialized.value = true
    }
  }

  function logout() {
    clear()
  }

  return { user, role, initialized, restoreError, hasToken, isAuthenticated, homePath, login, restore, logout, clear }
})
