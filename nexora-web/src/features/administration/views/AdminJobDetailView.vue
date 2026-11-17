<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import ReasonConfirmPanel from '@/shared/ui/ReasonConfirmPanel.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { forAdmin } from '@/features/companies/errors'
import { jobApi } from '@/features/jobs/api/jobApi'
import type { Job } from '@/features/jobs/types'
import { useAreas } from '@/features/students/composables/useAreas'
import { JOB_LEVEL_LABEL, WORK_MODEL_LABEL } from '@/features/students/labels'
import { adminApi } from '../api/adminApi'
import { validateAdminReason } from '../rules'

const { label: areaLabel } = useAreas()
const route = useRoute()
const router = useRouter()
const jobId = computed(() => String(route.params.jobId))
const job = useLoader(() => jobApi.get(jobId.value))

type Intervention = 'CLOSE' | 'REOPEN' | 'DELETE'
const pending = ref<Intervention | null>(null)
const error = ref<ApiError | null>(null)
const loading = ref(false)
const notice = ref('')

const TEXT: Record<Intervention, { title: string; message: string; confirm: string; danger: boolean }> = {
  CLOSE: { title: 'Encerrar esta vaga?', message: 'A vaga deixa de receber novas candidaturas. A ação é auditada.', confirm: 'Confirmar encerramento', danger: false },
  REOPEN: { title: 'Reabrir esta vaga?', message: 'A vaga volta a receber candidaturas. A ação é auditada.', confirm: 'Confirmar reabertura', danger: false },
  DELETE: { title: 'Excluir esta vaga?', message: 'Ela deixa de aparecer nas listas e encerra novas candidaturas; as candidaturas já recebidas são mantidas no histórico. A ação é auditada.', confirm: 'Confirmar exclusão', danger: true },
}

async function confirm(reason: string) {
  const kind = pending.value
  const current = job.data.value
  if (!kind || !current) return
  error.value = null
  loading.value = true
  try {
    if (kind === 'DELETE') {
      await adminApi.deleteJob(current.id, reason)
      await router.push({ name: 'admin-jobs', query: { aviso: 'excluida' } })
      return
    }
    job.data.value = (kind === 'CLOSE' ? await adminApi.closeJob(current.id, reason) : await adminApi.reopenJob(current.id, reason)) as Job
    notice.value = kind === 'CLOSE' ? 'Vaga encerrada por intervenção administrativa.' : 'Vaga reaberta por intervenção administrativa.'
    pending.value = null
  } catch (e) {
    error.value = forAdmin(toApiError(e))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <RouterLink :to="{ name: 'admin-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar às vagas</RouterLink>

    <LoadState :loading="job.loading.value" :error="forAdmin(job.error.value)" label="Carregando vaga…" @retry="job.load">
      <template v-if="job.data.value">
        <header class="space-y-1">
          <div class="flex flex-wrap items-center gap-2">
            <h1 class="text-2xl font-semibold">{{ job.data.value.title }}</h1>
            <StatusBadge :tone="job.data.value.status === 'OPEN' ? 'success' : 'neutral'">{{ job.data.value.status === 'OPEN' ? 'Aberta' : 'Encerrada' }}</StatusBadge>
          </div>
          <p class="text-muted">
            <RouterLink :to="{ name: 'admin-company', params: { companyId: job.data.value.companyId } }" class="font-medium text-primary underline underline-offset-4">{{ job.data.value.companyName }}</RouterLink>
          </p>
          <p class="text-sm text-muted">{{ areaLabel(job.data.value.area) }} · {{ job.data.value.location || 'Localização não informada' }} · {{ WORK_MODEL_LABEL[job.data.value.workModel] }} · {{ JOB_LEVEL_LABEL[job.data.value.level] }} · publicada em {{ formatDate(job.data.value.createdAt) }}</p>
        </header>

        <p v-if="notice" role="status" class="rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>

        <SectionCard title="Descrição e requisitos">
          <p class="whitespace-pre-line text-sm">{{ job.data.value.description }}</p>
          <p class="mt-3 text-sm"><span class="font-medium">Obrigatórias:</span> {{ job.data.value.requiredSkills.map((s) => s.name).join(', ') || 'nenhuma' }}</p>
          <p class="text-sm"><span class="font-medium">Desejáveis:</span> {{ job.data.value.desiredSkills.map((s) => s.name).join(', ') || 'nenhuma' }}</p>
        </SectionCard>

        <SectionCard title="Intervenção administrativa" description="Todas as ações exigem motivo e ficam registradas na auditoria.">
          <div class="flex flex-wrap gap-2">
            <BaseButton v-if="job.data.value.status === 'OPEN'" variant="secondary" :aria-expanded="pending === 'CLOSE'" @click="pending = 'CLOSE'; error = null; notice = ''">Encerrar vaga</BaseButton>
            <BaseButton v-else variant="secondary" :aria-expanded="pending === 'REOPEN'" @click="pending = 'REOPEN'; error = null; notice = ''">Reabrir vaga</BaseButton>
            <BaseButton variant="secondary" :aria-expanded="pending === 'DELETE'" @click="pending = 'DELETE'; error = null; notice = ''">Excluir vaga</BaseButton>
            <RouterLink :to="{ name: 'admin-job-applications', params: { jobId: job.data.value.id } }" class="inline-flex min-h-11 items-center rounded-control border border-line-strong px-4 text-sm font-semibold hover:bg-primary-soft">Ver candidaturas</RouterLink>
          </div>
          <div v-if="pending" class="mt-4">
            <ReasonConfirmPanel
              :title="TEXT[pending].title"
              :message="TEXT[pending].message"
              :confirm-label="TEXT[pending].confirm"
              reason-label="Motivo da intervenção"
              :danger="TEXT[pending].danger"
              :validate="validateAdminReason"
              :loading="loading"
              :error="error?.message"
              @confirm="confirm"
              @cancel="pending = null"
            />
          </div>
        </SectionCard>
      </template>
    </LoadState>
  </div>
</template>
