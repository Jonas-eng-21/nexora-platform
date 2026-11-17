<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import TextField from '@/shared/ui/TextField.vue'
import SkillPicker from '@/features/students/components/SkillPicker.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import { JOB_LEVEL_OPTIONS, WORK_MODEL_OPTIONS } from '@/features/students/labels'
import { forCompany } from '@/features/companies/errors'
import { jobManagementApi } from '../api/jobManagementApi'
import { MAX_JOB_SKILLS, emptyJobForm, formToJobRequest, jobToForm, validateJobForm, type FieldErrors } from '../jobForm'

const route = useRoute()
const router = useRouter()
const jobId = computed(() => (typeof route.params.jobId === 'string' ? route.params.jobId : ''))
const editing = computed(() => jobId.value !== '')
const { options: areaOptions } = useAreas()

const job = useLoader(() => (editing.value ? jobManagementApi.get(jobId.value) : Promise.resolve(null)))
const form = ref(emptyJobForm())
watch(() => job.data.value, (j) => { form.value = j ? jobToForm(j) : emptyJobForm() })

const formEl = ref<HTMLFormElement | null>(null)
const fieldErrors = ref<FieldErrors>({})
const error = ref<ApiError | null>(null)
const saving = ref(false)
const requiredIds = computed(() => form.value.required.map((s) => s.id))
const desiredIds = computed(() => form.value.desired.map((s) => s.id))

async function save() {
  error.value = null
  fieldErrors.value = validateJobForm(form.value)
  if (Object.keys(fieldErrors.value).length) return focusFirstInvalid(formEl.value)
  saving.value = true
  try {
    const request = formToJobRequest(form.value)
    if (editing.value) await jobManagementApi.update(jobId.value, request)
    else await jobManagementApi.create(request)
    await router.push({ name: 'company-jobs', query: { aviso: editing.value ? 'editada' : 'criada' } })
  } catch (e) {
    error.value = forCompany(toApiError(e))
    fieldErrors.value = { ...error.value!.fieldErrors }
    if (error.value!.code === 'JOB_SKILL_CONFLICT') fieldErrors.value.desired = error.value!.message
    await focusFirstInvalid(formEl.value)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <RouterLink :to="{ name: 'company-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar às vagas</RouterLink>
    <h1 class="text-2xl font-semibold">{{ editing ? 'Editar vaga' : 'Nova vaga' }}</h1>

    <LoadState :loading="job.loading.value" :error="forCompany(job.error.value)" label="Carregando vaga…" @retry="job.load">
      <form ref="formEl" class="space-y-6" novalidate @submit.prevent="save">
        <AlertMessage v-if="error" tone="error">
          {{ error.message }}
          <RouterLink v-if="error.code === 'COMPANY_PROFILE_NOT_FOUND'" :to="{ name: 'company-profile' }" class="font-medium underline underline-offset-4">Ir para o perfil da empresa</RouterLink>
        </AlertMessage>

        <AlertMessage v-if="Object.keys(fieldErrors).length && !error" tone="error">Revise os campos destacados antes de salvar.</AlertMessage>

        <SectionCard title="Sobre a vaga">
          <div class="space-y-5">
            <TextField v-model="form.title" label="Título" name="title" required :maxlength="150" :error="fieldErrors.title" />
            <TextField v-model="form.description" label="Descrição" name="description" multiline required :maxlength="5000" :hint="`${form.description.length} de 5.000 caracteres.`" :error="fieldErrors.description" />
            <div class="grid gap-5 sm:grid-cols-2">
              <SelectField v-model="form.area" label="Área" name="area" placeholder="Selecione…" :options="areaOptions" required :error="fieldErrors.area" />
              <SelectField v-model="form.level" label="Nível" name="level" placeholder="Selecione…" :options="JOB_LEVEL_OPTIONS" required :error="fieldErrors.level" />
              <SelectField v-model="form.workModel" label="Modelo de trabalho" name="workModel" placeholder="Selecione…" :options="WORK_MODEL_OPTIONS" required :error="fieldErrors.workModel" />
              <TextField v-model="form.location" label="Localização" name="location" hint="Cidade e estado, como Garanhuns - PE." :maxlength="150" :error="fieldErrors.location" />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="Competências obrigatórias" description="Ao menos uma. Pesam mais na compatibilidade com os estudantes.">
          <SkillPicker v-model="form.required" :max="MAX_JOB_SKILLS" title="Obrigatórias selecionadas" name="required-search" :blocked-ids="desiredIds" blocked-hint="já é desejável" />
          <p v-if="fieldErrors.required" class="mt-2 text-sm text-danger" role="alert">{{ fieldErrors.required }}</p>
        </SectionCard>

        <SectionCard title="Competências desejáveis" description="Opcionais. Uma competência não pode estar nas duas listas.">
          <SkillPicker v-model="form.desired" :max="MAX_JOB_SKILLS" title="Desejáveis selecionadas" name="desired-search" :blocked-ids="requiredIds" blocked-hint="já é obrigatória" />
          <p v-if="fieldErrors.desired" class="mt-2 text-sm text-danger" role="alert">{{ fieldErrors.desired }}</p>
        </SectionCard>

        <div class="flex flex-wrap items-center gap-3">
          <BaseButton type="submit" :loading="saving">{{ saving ? 'Salvando…' : editing ? 'Salvar alterações' : 'Publicar vaga' }}</BaseButton>
          <RouterLink :to="{ name: 'company-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Cancelar</RouterLink>
        </div>
      </form>
    </LoadState>
  </div>
</template>
