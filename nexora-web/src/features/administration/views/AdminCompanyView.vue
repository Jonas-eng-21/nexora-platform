<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import TextField from '@/shared/ui/TextField.vue'
import { companyToForm, emptyCompanyForm, validateCompanyForm, type CompanyFieldErrors } from '@/features/companies/companyForm'
import { adminApi } from '../api/adminApi'
import { REASON_MAX, adminCompanyFormToRequest, validateAdminReason } from '../rules'

const route = useRoute()
const companyId = computed(() => String(route.params.companyId))
const company = useLoader(() => adminApi.getCompany(companyId.value))
const form = ref(emptyCompanyForm())
watch(() => company.data.value, (c) => { if (c) form.value = companyToForm(c) })

const reason = ref('')
const formEl = ref<HTMLFormElement | null>(null)
const fieldErrors = ref<CompanyFieldErrors & { reason?: string }>({})
const error = ref<ApiError | null>(null)
const saved = ref(false)
const saving = ref(false)

async function save() {
  error.value = null
  saved.value = false
  const errors: CompanyFieldErrors & { reason?: string } = validateCompanyForm(form.value)
  const reasonError = validateAdminReason(reason.value)
  if (reasonError) errors.reason = reasonError
  fieldErrors.value = errors
  if (Object.keys(errors).length) return focusFirstInvalid(formEl.value)
  saving.value = true
  try {
    company.data.value = await adminApi.updateCompany(companyId.value, adminCompanyFormToRequest(form.value, reason.value))
    saved.value = true
    reason.value = ''
  } catch (e) {
    error.value = toApiError(e)
    fieldErrors.value = { ...error.value.fieldErrors }
    await focusFirstInvalid(formEl.value)
  } finally {
    saving.value = false
  }
}
const loadError = computed(() => (company.error.value instanceof ApiError && company.error.value.code === 'COMPANY_PROFILE_NOT_FOUND' ? new ApiError(404, 'COMPANY_NOT_FOUND', 'Empresa não encontrada.') : company.error.value))
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <RouterLink :to="{ name: 'admin-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar às vagas</RouterLink>
    <h1 class="text-2xl font-semibold">Perfil da empresa</h1>
    <LoadState :loading="company.loading.value" :error="loadError" label="Carregando empresa…" @retry="company.load">
      <SectionCard title="Dados da empresa" description="Intervenção administrativa: o motivo é obrigatório e fica na auditoria.">
        <AlertMessage v-if="error" tone="error" class="mb-4">{{ error.message }}</AlertMessage>
        <form ref="formEl" class="space-y-5" novalidate @submit.prevent="save">
          <TextField v-model="form.name" label="Nome da empresa" name="name" required :maxlength="150" :error="fieldErrors.name" />
          <TextField v-model="form.sector" label="Setor" name="sector" :maxlength="150" :error="fieldErrors.sector" />
          <TextField v-model="form.location" label="Cidade e estado" name="location" :maxlength="150" :error="fieldErrors.location" />
          <TextField v-model="form.description" label="Descrição" name="description" multiline :maxlength="2000" :error="fieldErrors.description" />
          <TextField v-model="reason" label="Motivo da intervenção" name="reason" multiline required :maxlength="REASON_MAX" :hint="`${reason.length} de ${REASON_MAX} caracteres.`" :error="fieldErrors.reason" />
          <div class="flex flex-wrap items-center gap-3">
            <BaseButton type="submit" :loading="saving">{{ saving ? 'Salvando…' : 'Salvar intervenção' }}</BaseButton>
            <p v-if="saved" role="status" class="text-sm font-medium text-success">Perfil atualizado e registrado na auditoria.</p>
          </div>
        </form>
      </SectionCard>
    </LoadState>
  </div>
</template>
