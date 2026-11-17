<script setup lang="ts">
import { ref } from 'vue'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import { applicationApi } from '../api/applicationApi'
import type { ResumeSnapshot, ResumeType } from '../types'
import ResumeSnapshotView from './ResumeSnapshotView.vue'

const props = withDefaults(
  defineProps<{ applicationId: string; resumeType: ResumeType; note?: string }>(),
  { note: 'Cópia do currículo exatamente como foi enviada. Ela não muda quando você edita o currículo-base.' },
)

const loading = ref(false)
const error = ref<ApiError | null>(null)
const snapshot = ref<ResumeSnapshot | null>(null)
const downloaded = ref('')

// O PDF privado chega como Blob autenticado (Bearer) e vira um Object URL temporário, liberado após o clique.
function saveFile(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function toggle() {
  if (snapshot.value) { snapshot.value = null; return }
  error.value = null
  downloaded.value = ''
  loading.value = true
  try {
    const sent = await applicationApi.readResume(props.applicationId)
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
  <div class="space-y-3">
    <BaseButton variant="secondary" :loading="loading" :aria-expanded="resumeType === 'PLATFORM' ? snapshot !== null : undefined" @click="toggle">
      {{ resumeType === 'PDF' ? 'Baixar PDF enviado' : snapshot ? 'Ocultar currículo enviado' : 'Ver currículo enviado' }}
    </BaseButton>
    <AlertMessage v-if="error" tone="error">{{ error.message }}</AlertMessage>
    <p v-if="downloaded" role="status" class="text-sm text-muted">Arquivo {{ downloaded }} baixado.</p>
    <div v-if="snapshot" class="rounded-control border border-line p-4">
      <p class="mb-3 text-sm text-muted">{{ note }}</p>
      <ResumeSnapshotView :snapshot="snapshot" />
    </div>
  </div>
</template>
