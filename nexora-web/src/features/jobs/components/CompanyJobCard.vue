<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toApiError } from '@/shared/api/apiError'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ConfirmPanel from '@/shared/ui/ConfirmPanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import { JOB_LEVEL_LABEL, WORK_MODEL_LABEL } from '@/features/students/labels'
import { jobManagementApi } from '../api/jobManagementApi'
import type { Job } from '../types'

const props = defineProps<{ job: Job }>()
const emit = defineEmits<{ removed: [jobId: string] }>()
const { label: areaLabel } = useAreas()

const current = ref<Job>(props.job)
const loading = ref(false)
const error = ref('')
const confirming = ref(false)
const notice = ref('')

async function toggleStatus() {
  error.value = ''
  notice.value = ''
  loading.value = true
  try {
    const closing = current.value.status === 'OPEN'
    current.value = closing ? await jobManagementApi.close(current.value.id) : await jobManagementApi.reopen(current.value.id)
    notice.value = closing ? 'Vaga encerrada: não recebe novas candidaturas.' : 'Vaga reaberta: volta a receber candidaturas.'
  } catch (e) {
    error.value = toApiError(e).message
  } finally {
    loading.value = false
  }
}

async function remove() {
  error.value = ''
  loading.value = true
  try {
    await jobManagementApi.remove(current.value.id)
    emit('removed', current.value.id)
  } catch (e) {
    error.value = toApiError(e).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <article class="space-y-3 rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <div class="min-w-0">
        <h2 class="text-lg font-semibold">{{ current.title }}</h2>
        <p class="text-sm text-muted">{{ areaLabel(current.area) }} · {{ current.location || 'Localização não informada' }} · {{ WORK_MODEL_LABEL[current.workModel] }} · {{ JOB_LEVEL_LABEL[current.level] }}</p>
        <p class="text-sm text-muted">Publicada em {{ formatDate(current.createdAt) }}</p>
      </div>
      <StatusBadge :tone="current.status === 'OPEN' ? 'success' : 'neutral'">{{ current.status === 'OPEN' ? 'Aberta' : 'Encerrada' }}</StatusBadge>
    </div>

    <nav :aria-label="`Ações da vaga ${current.title}`" class="flex flex-wrap items-center gap-x-5 gap-y-1 text-sm font-medium">
      <RouterLink :to="{ name: 'company-job-applications', params: { jobId: current.id } }" class="inline-flex min-h-11 items-center text-primary underline underline-offset-4">Candidaturas</RouterLink>
      <RouterLink :to="{ name: 'company-job-students', params: { jobId: current.id } }" class="inline-flex min-h-11 items-center text-primary underline underline-offset-4">Estudantes recomendados</RouterLink>
      <RouterLink :to="{ name: 'company-job-edit', params: { jobId: current.id } }" class="inline-flex min-h-11 items-center text-primary underline underline-offset-4">Editar</RouterLink>
    </nav>

    <div class="flex flex-wrap gap-2">
      <BaseButton variant="secondary" :loading="loading" @click="toggleStatus">{{ current.status === 'OPEN' ? 'Encerrar vaga' : 'Reabrir vaga' }}</BaseButton>
      <BaseButton variant="secondary" @click="confirming = true; notice = ''" :aria-expanded="confirming">Excluir vaga</BaseButton>
    </div>
    <ConfirmPanel
      v-if="confirming"
      title="Excluir esta vaga?"
      message="Ela deixa de aparecer nas listas e encerra novas candidaturas. As candidaturas já recebidas são mantidas no histórico."
      confirm-label="Confirmar exclusão"
      danger
      :loading="loading"
      :error="error"
      @confirm="remove"
      @cancel="confirming = false"
    />
    <p v-if="error && !confirming" role="alert" class="text-sm text-danger">{{ error }}</p>
    <p v-if="notice" role="status" class="text-sm font-medium">{{ notice }}</p>
  </article>
</template>
