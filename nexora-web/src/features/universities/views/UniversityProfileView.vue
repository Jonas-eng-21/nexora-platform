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
import { universityApi } from '../api/universityApi'
import { emptyUniversityForm, universityFormToRequest, universityToForm, validateUniversityForm, type UniversityFieldErrors } from '../universityForm'

const profile = useLoader(() => universityApi.getProfile())
const form = ref(emptyUniversityForm())
watch(() => profile.data.value, (p) => { if (p) form.value = universityToForm(p) })

const formEl = ref<HTMLFormElement | null>(null)
const fieldErrors = ref<UniversityFieldErrors>({})
const error = ref<ApiError | null>(null)
const saved = ref(false)
const saving = ref(false)

async function save() {
  error.value = null
  saved.value = false
  fieldErrors.value = validateUniversityForm(form.value)
  if (Object.keys(fieldErrors.value).length) return focusFirstInvalid(formEl.value)
  saving.value = true
  try {
    profile.data.value = await universityApi.saveProfile(universityFormToRequest(form.value))
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
    <h1 class="text-2xl font-semibold">Perfil institucional</h1>
    <LoadState :loading="profile.loading.value" :error="profile.error.value" label="Carregando perfil…" @retry="profile.load">
      <SectionCard title="Dados da instituição" description="Identificam a universidade para os estudantes que pedem vínculo.">
        <AlertMessage v-if="error" tone="error" class="mb-4">{{ error.message }}</AlertMessage>
        <form ref="formEl" class="space-y-5" novalidate @submit.prevent="save">
          <TextField v-model="form.name" label="Nome da instituição" name="name" autocomplete="organization" required :maxlength="150" :error="fieldErrors.name" />
          <TextField v-model="form.acronym" label="Sigla" name="acronym" :maxlength="30" :error="fieldErrors.acronym" />
          <TextField v-model="form.location" label="Cidade e estado" name="location" :maxlength="150" :error="fieldErrors.location" />
          <TextField v-model="form.website" label="Site" name="website" type="url" hint="Comece com http:// ou https://." :maxlength="300" :error="fieldErrors.website" />
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
