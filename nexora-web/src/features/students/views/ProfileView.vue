<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError, toApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { focusFirstInvalid } from '@/shared/ui/focusFirstInvalid'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import TextField from '@/shared/ui/TextField.vue'
import { studentApi } from '../api/studentApi'
import SkillPicker from '../components/SkillPicker.vue'
import { emptyProfileForm, formToProfile, profileToForm, validateProfile, type FieldErrors } from '../resumeForm'
import type { Skill } from '../types'

// 404 STUDENT_PROFILE_NOT_FOUND não é falha: é o primeiro acesso, com o formulário vazio.
const profile = useLoader(() =>
  studentApi.getProfile().catch((e) => {
    if (e instanceof ApiError && e.code === 'STUDENT_PROFILE_NOT_FOUND') return null
    throw e
  }),
)
const skills = useLoader(() =>
  studentApi.getSkills().catch((e) => {
    if (e instanceof ApiError && e.code === 'STUDENT_PROFILE_NOT_FOUND') return null
    throw e
  }),
)

const form = ref(emptyProfileForm())
const selected = ref<Skill[]>([])
watch(() => profile.data.value, (p) => { form.value = p ? profileToForm(p) : emptyProfileForm() })
watch(() => skills.data.value, (s) => { selected.value = s ? [...s] : [] })

const hasProfile = computed(() => profile.data.value != null)
const formEl = ref<HTMLFormElement | null>(null)
const fieldErrors = ref<FieldErrors>({})
const profileError = ref<ApiError | null>(null)
const profileSaved = ref(false)
const savingProfile = ref(false)
const skillsError = ref<ApiError | null>(null)
const skillsSaved = ref(false)
const savingSkills = ref(false)

async function saveProfile() {
  profileError.value = null
  profileSaved.value = false
  fieldErrors.value = validateProfile(form.value)
  if (Object.keys(fieldErrors.value).length) return focusFirstInvalid(formEl.value)
  savingProfile.value = true
  try {
    profile.data.value = await studentApi.saveProfile(formToProfile(form.value))
    profileSaved.value = true
  } catch (e) {
    profileError.value = toApiError(e)
    fieldErrors.value = profileError.value.fieldErrors
    await focusFirstInvalid(formEl.value)
  } finally {
    savingProfile.value = false
  }
}

async function saveSkills() {
  skillsError.value = null
  skillsSaved.value = false
  savingSkills.value = true
  try {
    skills.data.value = await studentApi.saveSkills(selected.value.map((s) => s.id))
    skillsSaved.value = true
  } catch (e) {
    skillsError.value = toApiError(e)
  } finally {
    savingSkills.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Perfil</h1>

    <LoadState :loading="profile.loading.value" :error="profile.error.value" label="Carregando perfil…" @retry="profile.load">
      <SectionCard title="Dados do perfil" description="Quem você é. Estes dados aparecem no currículo enviado nas candidaturas.">
        <AlertMessage v-if="!hasProfile" tone="info" class="mb-4">
          Você ainda não preencheu seu perfil. O nome completo é obrigatório para usar o currículo, o vínculo e as candidaturas.
        </AlertMessage>
        <AlertMessage v-if="profileError" tone="error" class="mb-4">{{ profileError.message }}</AlertMessage>
        <AlertMessage v-if="profileSaved" tone="success" class="mb-4">Perfil salvo.</AlertMessage>
        <form ref="formEl" class="space-y-5" novalidate @submit.prevent="saveProfile">
          <TextField v-model="form.fullName" label="Nome completo" name="fullName" autocomplete="name" required :maxlength="150" :error="fieldErrors.fullName" />
          <TextField v-model="form.course" label="Curso" name="course" :maxlength="150" :error="fieldErrors.course" />
          <TextField v-model="form.institution" label="Instituição de ensino" name="institution" hint="Informada por você. O vínculo validado é pedido na aba Vínculo acadêmico." :maxlength="150" :error="fieldErrors.institution" />
          <TextField v-model="form.location" label="Cidade e estado" name="location" hint="Usada para comparar com a localização das vagas." :maxlength="150" :error="fieldErrors.location" />
          <TextField v-model="form.summary" label="Objetivo de carreira e resumo" name="summary" multiline :maxlength="2000" :error="fieldErrors.summary" />
          <BaseButton type="submit" :loading="savingProfile">{{ savingProfile ? 'Salvando…' : 'Salvar perfil' }}</BaseButton>
        </form>
      </SectionCard>

      <SectionCard title="Competências técnicas" description="Escolhidas do catálogo. São a base do cálculo de compatibilidade com as vagas.">
        <p v-if="!hasProfile" class="text-sm text-muted">Salve os dados do perfil acima para escolher suas competências.</p>
        <LoadState v-else :loading="skills.loading.value" :error="skills.error.value" label="Carregando competências…" @retry="skills.load">
          <AlertMessage v-if="skillsError" tone="error" class="mb-4">{{ skillsError.message }}</AlertMessage>
          <AlertMessage v-if="skillsSaved" tone="success" class="mb-4">Competências salvas.</AlertMessage>
          <SkillPicker v-model="selected" />
          <BaseButton class="mt-5" :loading="savingSkills" @click="saveSkills">{{ savingSkills ? 'Salvando…' : 'Salvar competências' }}</BaseButton>
        </LoadState>
      </SectionCard>

      <p class="text-sm text-muted">
        Projetos, formação, links e áreas de interesse ficam em
        <RouterLink to="/estudante/curriculo" class="font-medium text-primary underline underline-offset-4">Currículo</RouterLink>.
      </p>
    </LoadState>
  </div>
</template>
