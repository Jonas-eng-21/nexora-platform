<script setup lang="ts">
import { RouterLink } from 'vue-router'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import type { Job } from '../types'

defineProps<{ job: Job }>()
</script>

<template>
  <header class="space-y-2">
    <RouterLink :to="{ name: 'company-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar às vagas</RouterLink>
    <div class="flex flex-wrap items-center gap-2">
      <h1 class="text-2xl font-semibold">{{ job.title }}</h1>
      <StatusBadge :tone="job.status === 'OPEN' ? 'success' : 'neutral'">{{ job.status === 'OPEN' ? 'Aberta' : 'Encerrada' }}</StatusBadge>
    </div>
    <nav aria-label="Seções da vaga" class="flex flex-wrap gap-1">
      <RouterLink :to="{ name: 'company-job-applications', params: { jobId: job.id } }" class="inline-flex min-h-11 items-center rounded-control px-3 text-sm hover:bg-primary-soft" active-class="bg-primary-soft font-semibold">Candidaturas</RouterLink>
      <RouterLink :to="{ name: 'company-job-students', params: { jobId: job.id } }" class="inline-flex min-h-11 items-center rounded-control px-3 text-sm hover:bg-primary-soft" active-class="bg-primary-soft font-semibold">Estudantes recomendados</RouterLink>
      <RouterLink :to="{ name: 'company-job-edit', params: { jobId: job.id } }" class="inline-flex min-h-11 items-center rounded-control px-3 text-sm hover:bg-primary-soft" active-class="bg-primary-soft font-semibold">Editar</RouterLink>
    </nav>
  </header>
</template>
