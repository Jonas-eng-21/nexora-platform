<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { formatDate } from '@/shared/format'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE, RESUME_TYPE_LABEL } from '../labels'
import type { ApplicationSummary } from '../types'
import ResumeViewer from './ResumeViewer.vue'

defineProps<{ application: ApplicationSummary }>()
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

    <div class="mt-4"><ResumeViewer :application-id="application.id" :resume-type="application.resumeType" /></div>
  </article>
</template>
