<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import TextField from '@/shared/ui/TextField.vue'
import { authApi } from '../api/authApi'
import type { PublicRole, RegisterRequest, RegisterResponse } from '../types'
import { validateRegister, type FieldErrors } from '../validation'

const ROLE_OPTIONS: { value: PublicRole; label: string; help: string }[] = [
  { value: 'STUDENT', label: 'Estudante', help: 'Monta o currículo e se candidata a vagas.' },
  { value: 'COMPANY', label: 'Empresa', help: 'Publica vagas e analisa candidaturas.' },
  { value: 'UNIVERSITY', label: 'Universidade', help: 'Valida o vínculo acadêmico dos estudantes. Exige aprovação do administrador.' },
]

const form = ref<HTMLFormElement | null>(null)
const role = ref<PublicRole>('STUDENT')
const email = ref('')
const password = ref('')
const university = reactive({ name: '', acronym: '', location: '', website: '', description: '' })
const fieldErrors = ref<FieldErrors>({})
const error = ref<ApiError | null>(null)
const loading = ref(false)
const created = ref<RegisterResponse | null>(null)
const doneHeading = ref<HTMLElement | null>(null)

function buildRequest(): RegisterRequest {
  const request: RegisterRequest = { email: email.value.trim(), password: password.value, role: role.value }
  if (role.value === 'UNIVERSITY') {
    const optional = (v: string) => v.trim() || undefined
    request.university = {
      name: university.name.trim(),
      acronym: optional(university.acronym),
      location: optional(university.location),
      website: optional(university.website),
      description: optional(university.description),
    }
  }
  return request
}

async function submit() {
  error.value = null
  fieldErrors.value = validateRegister({ email: email.value, password: password.value, role: role.value, university })
  if (Object.keys(fieldErrors.value).length) return focusFirstInvalid(form.value)

  loading.value = true
  try {
    created.value = await authApi.register(buildRequest())
    password.value = ''
    // Anuncia o resultado: move o foco ao título da confirmação.
    setTimeout(() => doneHeading.value?.focus(), 0)
  } catch (e) {
    const apiError = toApiError(e)
    error.value = apiError
    fieldErrors.value = { ...apiError.fieldErrors }
    if (apiError.code === 'EMAIL_ALREADY_REGISTERED') fieldErrors.value.email = 'Este e-mail já está cadastrado.'
    if (apiError.code === 'PASSWORD_TOO_LONG') fieldErrors.value.password = apiError.message
    await focusFirstInvalid(form.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="rounded-card border border-line bg-surface p-6 shadow-card sm:p-8">
    <!-- Confirmação: universidade fica pendente e não recebe sessão. -->
    <div v-if="created" class="space-y-4">
      <h1 ref="doneHeading" tabindex="-1" class="text-2xl font-semibold focus:outline-none">
        {{ created.status === 'PENDING_APPROVAL' ? 'Cadastro recebido' : 'Conta criada' }}
      </h1>
      <AlertMessage v-if="created.status === 'PENDING_APPROVAL'" tone="warning">
        A conta da universidade foi registrada e aguarda a aprovação do administrador. Você só conseguirá entrar depois
        dessa aprovação.
      </AlertMessage>
      <AlertMessage v-else tone="success">
        Sua conta foi criada com o e-mail {{ created.email }}. Entre para começar.
      </AlertMessage>
      <RouterLink
        :to="{ name: 'login' }"
        class="inline-flex min-h-11 w-full items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-on-primary hover:bg-primary-hover"
      >
        Ir para o login
      </RouterLink>
    </div>

    <template v-else>
      <h1 class="text-2xl font-semibold">Criar conta</h1>
      <p class="mt-1 text-muted">Escolha seu perfil e informe seus dados de acesso.</p>

      <AlertMessage v-if="error" tone="error" class="mt-5">{{ error.message }}</AlertMessage>

      <form ref="form" class="mt-6 space-y-5" novalidate @submit.prevent="submit">
        <fieldset class="space-y-2">
          <legend class="mb-1.5 text-sm font-medium">Perfil</legend>
          <label
            v-for="option in ROLE_OPTIONS"
            :key="option.value"
            class="flex cursor-pointer gap-3 rounded-control border p-3 has-[:checked]:border-primary has-[:checked]:bg-primary-soft has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-primary"
            :class="role === option.value ? 'border-primary' : 'border-line-strong'"
          >
            <input v-model="role" type="radio" name="role" :value="option.value" class="mt-1 size-4 shrink-0 accent-primary" />
            <span>
              <span class="block font-medium">{{ option.label }}</span>
              <span class="block text-sm text-muted">{{ option.help }}</span>
            </span>
          </label>
        </fieldset>

        <TextField v-model="email" label="E-mail" name="email" type="email" autocomplete="email" required :error="fieldErrors.email" />
        <TextField
          v-model="password"
          label="Senha"
          name="password"
          type="password"
          autocomplete="new-password"
          required
          hint="De 8 a 72 caracteres."
          :error="fieldErrors.password"
        />

        <fieldset v-if="role === 'UNIVERSITY'" class="space-y-5 border-t border-line pt-5">
          <legend class="text-base font-semibold">Dados da instituição</legend>
          <TextField v-model="university.name" label="Nome da instituição" name="university-name" required :maxlength="150" :error="fieldErrors['university.name']" />
          <TextField v-model="university.acronym" label="Sigla" name="university-acronym" :maxlength="30" :error="fieldErrors['university.acronym']" />
          <TextField v-model="university.location" label="Cidade e estado" name="university-location" :maxlength="150" :error="fieldErrors['university.location']" />
          <TextField
            v-model="university.website"
            label="Site"
            name="university-website"
            type="url"
            hint="Comece com http:// ou https://."
            :maxlength="300"
            :error="fieldErrors['university.website']"
          />
          <TextField
            v-model="university.description"
            label="Descrição"
            name="university-description"
            multiline
            :maxlength="2000"
            :error="fieldErrors['university.description']"
          />
        </fieldset>

        <BaseButton type="submit" block :loading="loading">{{ loading ? 'Criando conta…' : 'Criar conta' }}</BaseButton>
      </form>

      <p class="mt-6 text-sm text-muted">
        Já tem conta?
        <RouterLink :to="{ name: 'login' }" class="font-medium text-primary underline underline-offset-4">Entrar</RouterLink>
      </p>
    </template>
  </div>
</template>
