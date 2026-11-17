<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import { companyApplicationApi } from '@/features/applications/api/companyApplicationApi'
import { jobManagementApi } from '@/features/jobs/api/jobManagementApi'
import type { Job } from '@/features/jobs/types'
import { companyApi } from '../api/companyApi'

const profile = useLoader(() =>
  companyApi.getProfile().catch((e) => {
    if (e instanceof ApiError && e.code === 'COMPANY_PROFILE_NOT_FOUND') return null
    throw e
  }),
)

interface OpenJob { job: Job; waiting: number | null }
// Não há endpoint agregado: para cada vaga aberta (até 10) lê-se só o total de candidaturas "Enviada" (page size 1).
const open = useLoader<{ jobs: OpenJob[]; total: number }>(async () => {
  const page = await jobManagementApi.listMine('OPEN', 0, 10)
  const jobs = await Promise.all(
    page.content.map(async (job): Promise<OpenJob> => {
      try {
        const waiting = await companyApplicationApi.listForJob(job.id, 'SUBMITTED', 0, 1)
        return { job, waiting: waiting.totalElements }
      } catch {
        return { job, waiting: null }
      }
    }),
  )
  return { jobs, total: page.totalElements }
})
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Início</h1>

    <LoadState :loading="profile.loading.value" :error="profile.error.value" label="Carregando perfil…" @retry="profile.load">
      <AlertMessage v-if="profile.data.value === null" tone="warning">
        <strong>Falta preencher o perfil da empresa.</strong> Sem ele não é possível publicar vagas.
        <RouterLink :to="{ name: 'company-profile' }" class="font-medium underline underline-offset-4">Preencher perfil</RouterLink>
      </AlertMessage>
    </LoadState>

    <SectionCard title="Vagas abertas e candidaturas aguardando análise">
      <LoadState :loading="open.loading.value" :error="open.error.value" label="Carregando vagas…" @retry="open.load">
        <EmptyState v-if="!open.data.value?.jobs.length" title="Nenhuma vaga aberta" description="Publique uma vaga para receber candidaturas e ver estudantes recomendados.">
          <RouterLink :to="{ name: 'company-job-new' }" class="font-medium text-primary underline underline-offset-4">Publicar vaga</RouterLink>
        </EmptyState>
        <template v-else>
          <ul class="divide-y divide-line">
            <li v-for="item in open.data.value.jobs" :key="item.job.id" class="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
              <div class="min-w-0">
                <p class="font-medium">{{ item.job.title }}</p>
                <p class="text-sm text-muted">
                  <template v-if="item.waiting === null">Não foi possível contar as candidaturas.</template>
                  <template v-else-if="item.waiting === 0">Nenhuma candidatura aguardando análise.</template>
                  <template v-else>{{ item.waiting }} {{ item.waiting === 1 ? 'candidatura aguardando' : 'candidaturas aguardando' }} análise.</template>
                </p>
              </div>
              <div class="flex flex-wrap gap-x-4">
                <RouterLink :to="{ name: 'company-job-applications', params: { jobId: item.job.id }, query: { status: 'SUBMITTED' } }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Analisar candidaturas</RouterLink>
                <RouterLink :to="{ name: 'company-job-students', params: { jobId: item.job.id } }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Estudantes recomendados</RouterLink>
              </div>
            </li>
          </ul>
          <p v-if="open.data.value.total > open.data.value.jobs.length" class="mt-3 text-sm text-muted">
            Mostrando {{ open.data.value.jobs.length }} de {{ open.data.value.total }} vagas abertas.
            <RouterLink :to="{ name: 'company-jobs', query: { status: 'OPEN' } }" class="font-medium text-primary underline underline-offset-4">Ver todas</RouterLink>
          </p>
        </template>
      </LoadState>
    </SectionCard>
  </div>
</template>
