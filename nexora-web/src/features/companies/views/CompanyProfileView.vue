<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import TextField from '@/shared/ui/TextField.vue'
import { companyApi } from '../api/companyApi'
import { companyFormToRequest, emptyCompanyForm, companyToForm, validateCompanyForm, type CompanyFieldErrors } from '../companyForm'

// Sem perfil o backend responde 404 COMPANY_PROFILE_NOT_FOUND: é o primeiro acesso, não uma falha.
const profile = useLoader(() =>
  companyApi.getProfile().catch((e) => {
    if (e instanceof ApiError && e.code === 'COMPANY_PROFILE_NOT_FOUND') return null
    throw e
  }),
)
const form = ref(emptyCompanyForm())
watch(() => profile.data.value, (p) => { form.value = p ? companyToForm(p) : emptyCompanyForm() })

const formEl = ref<HTMLFormElement | null>(null)
const fieldErrors = ref<CompanyFieldErrors>({})
const error = ref<ApiError | null>(null)
const saved = ref(false)
const saving = ref(false)

async function save() {
  error.value = null
  saved.value = false
  fieldErrors.value = validateCompanyForm(form.value)
  if (Object.keys(fieldErrors.value).length) return focusFirstInvalid(formEl.value)
  saving.value = true
  try {
    profile.data.value = await companyApi.saveProfile(companyFormToRequest(form.value))
    saved.value = true
  } catch (e) {
    error.value = toApiError(e)
    fieldErrors.value = error.value.fieldErrors
    await focusFirstInvalid(formEl.value)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Perfil da empresa</h1>
    <LoadState :loading="profile.loading.value" :error="profile.error.value" label="Carregando perfil…" @retry="profile.load">
      <SectionCard title="Dados da empresa" description="Aparecem nas vagas que você publica.">
        <AlertMessage v-if="profile.data.value === null" tone="info" class="mb-4">
          Você ainda não preencheu o perfil. O nome da empresa é obrigatório para publicar vagas.
        </AlertMessage>
        <AlertMessage v-if="error" tone="error" class="mb-4">{{ error.message }}</AlertMessage>
        <form ref="formEl" class="space-y-5" novalidate @submit.prevent="save">
          <TextField v-model="form.name" label="Nome da empresa" name="name" autocomplete="organization" required :maxlength="150" :error="fieldErrors.name" />
          <TextField v-model="form.sector" label="Setor" name="sector" :maxlength="150" :error="fieldErrors.sector" />
          <TextField v-model="form.location" label="Cidade e estado" name="location" :maxlength="150" :error="fieldErrors.location" />
          <TextField v-model="form.description" label="Descrição" name="description" multiline :maxlength="2000" :error="fieldErrors.description" />
          <div class="flex flex-wrap items-center gap-3">
            <BaseButton type="submit" :loading="saving">{{ saving ? 'Salvando…' : 'Salvar perfil' }}</BaseButton>
            <p v-if="saved" role="status" class="text-sm font-medium text-success">Perfil salvo.</p>
          </div>
        </form>
      </SectionCard>
    </LoadState>
  </div>
</template>
