<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import TextField from '@/shared/ui/TextField.vue'
import { useSessionStore } from '../stores/session'
import { validateLogin, type FieldErrors } from '../validation'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()

const form = ref<HTMLFormElement | null>(null)
const email = ref('')
const password = ref('')
const fieldErrors = ref<FieldErrors>({})
const error = ref<ApiError | null>(null)
const loading = ref(false)

const notice = computed(() => {
  if (route.query.reason === 'expired') return 'Sua sessão expirou. Entre novamente para continuar.'
  if (route.query.reason === 'unavailable')
    return 'Não foi possível confirmar sua sessão porque o servidor está indisponível. Tente entrar novamente em instantes.'
  return ''
})

async function submit() {
  error.value = null
  fieldErrors.value = validateLogin({ email: email.value, password: password.value })
  if (Object.keys(fieldErrors.value).length) return focusFirstInvalid(form.value)

  loading.value = true
  try {
    await session.login({ email: email.value.trim(), password: password.value })
    await router.replace(session.homePath)
  } catch (e) {
    error.value = toApiError(e)
    fieldErrors.value = error.value.fieldErrors
    await focusFirstInvalid(form.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="rounded-card border border-line bg-surface p-6 shadow-card sm:p-8">
    <h1 class="text-2xl font-semibold">Entrar no Nexora</h1>
    <p class="mt-1 text-muted">Use o e-mail e a senha da sua conta.</p>

    <AlertMessage v-if="notice && !error" tone="warning" class="mt-5">{{ notice }}</AlertMessage>
    <AlertMessage v-if="error" tone="error" class="mt-5">
      {{ error.message }}
      <template v-if="error.isUnavailable"> Use “Entrar” para tentar de novo.</template>
    </AlertMessage>

    <form ref="form" class="mt-6 space-y-5" novalidate @submit.prevent="submit">
      <TextField v-model="email" label="E-mail" name="email" type="email" autocomplete="email" required :error="fieldErrors.email" />
      <TextField
        v-model="password"
        label="Senha"
        name="password"
        type="password"
        autocomplete="current-password"
        required
        :error="fieldErrors.password"
      />
      <BaseButton type="submit" block :loading="loading">{{ loading ? 'Entrando…' : 'Entrar' }}</BaseButton>
    </form>

    <p class="mt-6 text-sm text-muted">
      Ainda não tem conta?
      <RouterLink :to="{ name: 'register' }" class="font-medium text-primary underline underline-offset-4">
        Criar conta
      </RouterLink>
    </p>
  </div>
</template>
