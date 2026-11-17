<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import CheckboxGroup from '@/shared/ui/CheckboxGroup.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import TextField from '@/shared/ui/TextField.vue'
import { studentApi } from '../api/studentApi'
import RepeatableList from '../components/RepeatableList.vue'
import TagListEditor from '../components/TagListEditor.vue'
import { useAreas } from '../composables/useAreas'
import { WORK_MODEL_OPTIONS } from '../labels'
import { RESUME_LIMITS, emptyResumeForm, formToResume, resumeToForm, validateResumeForm, type FieldErrors } from '../resumeForm'
import type { ProfessionalArea, WorkModel } from '../types'

const { options: areaOptions } = useAreas()
// Sem perfil, o backend responde 404 STUDENT_PROFILE_NOT_FOUND: orientamos em vez de mostrar erro.
const resume = useLoader(() =>
  studentApi.getResume().catch((e) => {
    if (e instanceof ApiError && e.code === 'STUDENT_PROFILE_NOT_FOUND') return null
    throw e
  }),
)
const missingProfile = computed(() => !resume.loading.value && !resume.error.value && resume.data.value === null)
const form = ref(emptyResumeForm())
watch(() => resume.data.value, (r) => { if (r) form.value = resumeToForm(r) })

const formEl = ref<HTMLFormElement | null>(null)
const fieldErrors = ref<FieldErrors>({})
const error = ref<ApiError | null>(null)
const saved = ref(false)
const saving = ref(false)

async function save() {
  error.value = null
  saved.value = false
  fieldErrors.value = validateResumeForm(form.value)
  if (Object.keys(fieldErrors.value).length) {
    error.value = null
    return focusFirstInvalid(formEl.value)
  }
  saving.value = true
  try {
    resume.data.value = await studentApi.saveResume(formToResume(form.value))
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
    <h1 class="text-2xl font-semibold">Currículo</h1>
    <p class="text-muted">
      O currículo-base é a única fonte da compatibilidade com as vagas. Projetos e competências valem tanto quanto a experiência profissional.
    </p>

    <LoadState :loading="resume.loading.value" :error="resume.error.value" label="Carregando currículo…" @retry="resume.load">
      <AlertMessage v-if="missingProfile" tone="warning">
        Você ainda não preencheu seu perfil. Preencha o perfil antes de montar o currículo.
        <RouterLink to="/estudante/perfil" class="font-medium underline underline-offset-4">Ir para o perfil</RouterLink>
      </AlertMessage>
      <form v-else ref="formEl" class="space-y-6" novalidate @submit.prevent="save">
        <AlertMessage v-if="error" tone="error">{{ error.message }}</AlertMessage>
        <AlertMessage v-if="Object.keys(fieldErrors).length" tone="error">Revise os campos destacados antes de salvar.</AlertMessage>

        <SectionCard title="Áreas de interesse e preferências" description="Comparadas com a área e o modelo de trabalho de cada vaga.">
          <div class="space-y-5">
            <CheckboxGroup :model-value="form.interestAreas" legend="Áreas de interesse" :options="areaOptions" @update:model-value="form.interestAreas = $event as ProfessionalArea[]" />
            <CheckboxGroup :model-value="form.preferredWorkModels" legend="Modelos de trabalho aceitos" hint="Sem nenhuma marcação, vale qualquer modelo." :options="WORK_MODEL_OPTIONS" @update:model-value="form.preferredWorkModels = $event as WorkModel[]" />
          </div>
        </SectionCard>

        <SectionCard title="Links" description="GitHub, LinkedIn e portfólio, começando com http:// ou https://.">
          <div class="space-y-5">
            <TextField v-model="form.links.github" label="GitHub" name="github" type="url" :error="fieldErrors['links.github']" />
            <TextField v-model="form.links.linkedin" label="LinkedIn" name="linkedin" type="url" :error="fieldErrors['links.linkedin']" />
            <TextField v-model="form.links.portfolio" label="Portfólio" name="portfolio" type="url" :error="fieldErrors['links.portfolio']" />
          </div>
        </SectionCard>

        <SectionCard title="Projetos" description="Acadêmicos e pessoais. Mostre o que você já construiu.">
          <RepeatableList :items="form.projects" :max="RESUME_LIMITS.projects" title="Lista de projetos" add-label="Adicionar projeto" item-label="projeto" empty-text="Nenhum projeto cadastrado." @add="form.projects.push({ name: '', description: '', url: '' })" @remove="form.projects.splice($event, 1)">
            <template #default="{ item, index }">
              <TextField v-model="item.name" label="Nome do projeto" required :maxlength="150" :error="fieldErrors[`projects[${index}].name`]" />
              <TextField v-model="item.description" label="Descrição" multiline :maxlength="2000" :error="fieldErrors[`projects[${index}].description`]" />
              <TextField v-model="item.url" label="Endereço do projeto" type="url" :error="fieldErrors[`projects[${index}].url`]" />
            </template>
          </RepeatableList>
        </SectionCard>

        <SectionCard title="Formação">
          <RepeatableList :items="form.education" :max="RESUME_LIMITS.education" title="Lista de formações" add-label="Adicionar formação" item-label="formação" empty-text="Nenhuma formação cadastrada." @add="form.education.push({ institution: '', course: '', degree: '', startYear: '', endYear: '' })" @remove="form.education.splice($event, 1)">
            <template #default="{ item, index }">
              <TextField v-model="item.institution" label="Instituição" required :maxlength="150" :error="fieldErrors[`education[${index}].institution`]" />
              <TextField v-model="item.course" label="Curso" required :maxlength="150" :error="fieldErrors[`education[${index}].course`]" />
              <TextField v-model="item.degree" label="Grau" hint="Por exemplo: Bacharelado, Tecnólogo." :maxlength="80" :error="fieldErrors[`education[${index}].degree`]" />
              <div class="grid gap-5 sm:grid-cols-2">
                <TextField v-model="item.startYear" label="Ano de início" required :error="fieldErrors[`education[${index}].startYear`]" />
                <TextField v-model="item.endYear" label="Ano de conclusão" :error="fieldErrors[`education[${index}].endYear`]" />
              </div>
            </template>
          </RepeatableList>
        </SectionCard>

        <SectionCard title="Cursos e certificações">
          <RepeatableList :items="form.courses" :max="RESUME_LIMITS.courses" title="Lista de cursos" add-label="Adicionar curso" item-label="curso" empty-text="Nenhum curso cadastrado." @add="form.courses.push({ name: '', institution: '', year: '', certificateUrl: '' })" @remove="form.courses.splice($event, 1)">
            <template #default="{ item, index }">
              <TextField v-model="item.name" label="Nome do curso" required :maxlength="150" :error="fieldErrors[`courses[${index}].name`]" />
              <TextField v-model="item.institution" label="Instituição" :maxlength="150" :error="fieldErrors[`courses[${index}].institution`]" />
              <TextField v-model="item.year" label="Ano" :error="fieldErrors[`courses[${index}].year`]" />
              <TextField v-model="item.certificateUrl" label="Endereço do certificado" type="url" :error="fieldErrors[`courses[${index}].certificateUrl`]" />
            </template>
          </RepeatableList>
        </SectionCard>

        <SectionCard title="Soft skills e interesses">
          <div class="space-y-5">
            <TagListEditor v-model="form.softSkills" label="Soft skills" name="softSkills" :max="RESUME_LIMITS.tags" hint="Por exemplo: comunicação, trabalho em equipe." />
            <TagListEditor v-model="form.interests" label="Interesses" name="interests" :max="RESUME_LIMITS.tags" hint="Texto livre; não entra no cálculo de compatibilidade." />
          </div>
        </SectionCard>

        <SectionCard title="Experiência profissional" description="Opcional. Estágios e trabalhos, se houver.">
          <RepeatableList :items="form.experiences" :max="RESUME_LIMITS.experiences" title="Lista de experiências" add-label="Adicionar experiência" item-label="experiência" empty-text="Nenhuma experiência cadastrada." @add="form.experiences.push({ company: '', role: '', description: '', startDate: '', endDate: '' })" @remove="form.experiences.splice($event, 1)">
            <template #default="{ item, index }">
              <TextField v-model="item.company" label="Empresa" required :maxlength="150" :error="fieldErrors[`experiences[${index}].company`]" />
              <TextField v-model="item.role" label="Cargo" required :maxlength="150" :error="fieldErrors[`experiences[${index}].role`]" />
              <TextField v-model="item.description" label="Descrição" multiline :maxlength="2000" :error="fieldErrors[`experiences[${index}].description`]" />
              <div class="grid gap-5 sm:grid-cols-2">
                <TextField v-model="item.startDate" label="Início (AAAA-MM)" required hint="Exemplo: 2025-03" :error="fieldErrors[`experiences[${index}].startDate`]" />
                <TextField v-model="item.endDate" label="Fim (AAAA-MM)" hint="Deixe em branco se atual." :error="fieldErrors[`experiences[${index}].endDate`]" />
              </div>
            </template>
          </RepeatableList>
        </SectionCard>

        <div class="flex flex-wrap items-center gap-3">
          <BaseButton type="submit" :loading="saving">{{ saving ? 'Salvando…' : 'Salvar currículo' }}</BaseButton>
          <p v-if="saved" role="status" class="text-sm font-medium text-success">Currículo salvo.</p>
        </div>
      </form>
    </LoadState>
  </div>
</template>
