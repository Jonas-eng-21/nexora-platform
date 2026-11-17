<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { formatDate } from '@/shared/format'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { applicationApi } from '../api/applicationApi'
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE, RESUME_TYPE_LABEL } from '../labels'
import type { ApplicationSummary, ResumeSnapshot } from '../types'
import ResumeSnapshotView from './ResumeSnapshotView.vue'

const props = defineProps<{ application: ApplicationSummary }>()

const loading = ref(false)
const error = ref<ApiError | null>(null)
const snapshot = ref<ResumeSnapshot | null>(null)
const downloaded = ref('')

function saveFile(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function viewResume() {
  if (snapshot.value) { snapshot.value = null; return }
  error.value = null
  downloaded.value = ''
  loading.value = true
  try {
    const sent = await applicationApi.readResume(props.application.id)
    if (sent.kind === 'snapshot') snapshot.value = sent.snapshot
    else {
      saveFile(sent.blob, sent.filename)
      downloaded.value = sent.filename
    }
  } catch (e) {
    error.value = toApiError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <article class="rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <div class="min-w-0">
        <h2 class="text-lg font-semibold">
          <RouterLink :to="{ name: 'student-job', params: { jobId: application.jobId } }" class="inline-block py-1 hover:underline">{{ application.jobTitle }}</RouterLink>
        </h2>
        <p class="text-muted">{{ application.companyName }}</p>
      </div>
      <StatusBadge :tone="APPLICATION_STATUS_TONE[application.status]">{{ APPLICATION_STATUS_LABEL[application.status] }}</StatusBadge>
    </div>
    <p class="mt-2 text-sm text-muted">Enviada em {{ formatDate(application.appliedAt) }} · {{ RESUME_TYPE_LABEL[application.resumeType] }}</p>
    <p v-if="application.status === 'SUSPENDED'" class="mt-2 text-sm">
      Candidatura suspensa porque seu vínculo acadêmico deixou de valer. O histórico e o documento foram mantidos; a reativação é uma ação da empresa ou do administrador.
    </p>

    <div class="mt-4 space-y-3">
      <BaseButton variant="secondary" :loading="loading" :aria-expanded="application.resumeType === 'PLATFORM' ? snapshot !== null : undefined" @click="viewResume">
        {{ application.resumeType === 'PDF' ? 'Baixar PDF enviado' : snapshot ? 'Ocultar currículo enviado' : 'Ver currículo enviado' }}
      </BaseButton>
      <AlertMessage v-if="error" tone="error">{{ error.message }}</AlertMessage>
      <p v-if="downloaded" role="status" class="text-sm text-muted">Arquivo {{ downloaded }} baixado.</p>
      <div v-if="snapshot" class="rounded-control border border-line p-4">
        <p class="mb-3 text-sm text-muted">Cópia do currículo exatamente como foi enviada. Ela não muda quando você edita o currículo-base.</p>
        <ResumeSnapshotView :snapshot="snapshot" />
      </div>
    </div>
  </article>
</template>
