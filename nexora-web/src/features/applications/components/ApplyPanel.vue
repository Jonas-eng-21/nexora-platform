<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { MAX_PDF_BYTES, formatDate, formatFileSize, formatMiB } from '@/shared/format'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { applicationApi } from '../api/applicationApi'
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE } from '../labels'
import { validatePdf } from '../resumeFile'
import type { ApplicationCreated, ApplicationSummary, ResumeType } from '../types'

const props = defineProps<{
  jobId: string
  /** null enquanto a elegibilidade carrega. */
  eligible: boolean | null
  existing: ApplicationSummary | null
}>()
const emit = defineEmits<{ applied: [application: ApplicationCreated] }>()

const resumeType = ref<ResumeType>('PLATFORM')
const file = ref<File | null>(null)
const fileError = ref('')
const submitError = ref<ApiError | null>(null)
const submitting = ref(false)
const done = ref<ApplicationCreated | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const sizeText = computed(() => (file.value ? `${formatFileSize(file.value.size)} de ${formatMiB(MAX_PDF_BYTES)}` : ''))

function choose(type: ResumeType) {
  resumeType.value = type
  fileError.value = ''
  if (type === 'PLATFORM') file.value = null
}

function onFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
  fileError.value = file.value ? (validatePdf(file.value) ?? '') : ''
}

async function submit() {
  submitError.value = null
  if (resumeType.value === 'PDF') {
    fileError.value = validatePdf(file.value) ?? ''
    if (fileError.value) return fileInput.value?.focus()
  }
  submitting.value = true
  try {
    done.value = await applicationApi.apply(props.jobId, resumeType.value, file.value ?? undefined)
    emit('applied', done.value)
  } catch (e) {
    submitError.value = toApiError(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <AlertMessage v-if="done" tone="success">
      <strong>Candidatura enviada.</strong> Ela entra como “{{ APPLICATION_STATUS_LABEL[done.status] }}”.
      <RouterLink to="/estudante/candidaturas" class="font-medium underline underline-offset-4">Ver minhas candidaturas</RouterLink>
    </AlertMessage>

    <div v-else-if="existing" class="space-y-2">
      <p>Você se candidatou a esta vaga em {{ formatDate(existing.appliedAt) }}.</p>
      <p class="flex items-center gap-2 text-sm">Situação: <StatusBadge :tone="APPLICATION_STATUS_TONE[existing.status]">{{ APPLICATION_STATUS_LABEL[existing.status] }}</StatusBadge></p>
      <RouterLink to="/estudante/candidaturas" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver minhas candidaturas</RouterLink>
    </div>

    <p v-else-if="eligible === null" role="status" class="text-sm text-muted">Verificando sua validação acadêmica…</p>

    <AlertMessage v-else-if="!eligible" tone="warning">
      <strong>Candidatura bloqueada: você ainda não está validado.</strong>
      É preciso ter um vínculo acadêmico aprovado e vigente.
      <RouterLink to="/estudante/vinculo" class="font-medium underline underline-offset-4">Solicitar vínculo acadêmico</RouterLink>
    </AlertMessage>

    <form v-else class="space-y-5" novalidate @submit.prevent="submit">
      <AlertMessage v-if="submitError" tone="error">
        {{ submitError.message }}
        <RouterLink v-if="submitError.code === 'STUDENT_PROFILE_NOT_FOUND'" to="/estudante/perfil" class="font-medium underline underline-offset-4">Ir para o perfil</RouterLink>
        <RouterLink v-if="submitError.code === 'STUDENT_NOT_VALIDATED'" to="/estudante/vinculo" class="font-medium underline underline-offset-4">Ir para o vínculo</RouterLink>
      </AlertMessage>

      <fieldset class="space-y-2">
        <legend class="mb-1 text-sm font-medium">Currículo desta candidatura (escolha uma opção)</legend>
        <label class="flex cursor-pointer gap-3 rounded-control border p-3 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-primary" :class="resumeType === 'PLATFORM' ? 'border-primary bg-primary-soft' : 'border-line-strong'">
          <input type="radio" name="resumeType" value="PLATFORM" class="mt-1 size-4 shrink-0 accent-primary" :checked="resumeType === 'PLATFORM'" @change="choose('PLATFORM')" />
          <span><span class="block font-medium">Currículo do Nexora</span><span class="block text-sm text-muted">Envia uma cópia do seu currículo-base de agora. Mudanças futuras não alteram esta candidatura.</span></span>
        </label>
        <label class="flex cursor-pointer gap-3 rounded-control border p-3 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-primary" :class="resumeType === 'PDF' ? 'border-primary bg-primary-soft' : 'border-line-strong'">
          <input type="radio" name="resumeType" value="PDF" class="mt-1 size-4 shrink-0 accent-primary" :checked="resumeType === 'PDF'" @change="choose('PDF')" />
          <span><span class="block font-medium">Arquivo PDF</span><span class="block text-sm text-muted">Até {{ formatMiB(MAX_PDF_BYTES) }}. O PDF é só um anexo: a compatibilidade continua calculada pelo currículo-base.</span></span>
        </label>
      </fieldset>

      <div v-if="resumeType === 'PDF'" class="space-y-2">
        <label for="resume-file" class="block text-sm font-medium">Arquivo PDF</label>
        <input id="resume-file" ref="fileInput" type="file" name="file" accept="application/pdf,.pdf" :aria-invalid="fileError ? 'true' : undefined" aria-describedby="resume-file-help resume-file-error" class="block w-full text-sm file:mr-3 file:min-h-11 file:rounded-control file:border file:border-line-strong file:bg-surface file:px-4 file:text-fg" @change="onFile" />
        <p id="resume-file-help" class="text-sm text-muted">Limite: {{ formatMiB(MAX_PDF_BYTES) }} (5.242.880 bytes).</p>
        <template v-if="file">
          <ProgressBar :value="Math.min(file.size, MAX_PDF_BYTES)" :max="MAX_PDF_BYTES" label="Tamanho do arquivo em relação ao limite" />
          <p class="text-sm tabular-nums" role="status">{{ file.name }} · {{ sizeText }}</p>
        </template>
        <p id="resume-file-error" class="text-sm text-danger">{{ fileError }}</p>
      </div>

      <BaseButton type="submit" :loading="submitting" :disabled="submitting || (resumeType === 'PDF' && !!fileError)">{{ submitting ? 'Enviando…' : 'Enviar candidatura' }}</BaseButton>
    </form>
  </div>
</template>
