<script setup lang="ts">
import { ref } from 'vue'
import { formatDate } from '@/shared/format'
import ApplicationScore from '@/features/matching/components/ApplicationScore.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import type { JobApplication, Reactivation, StatusChange } from '../api/companyApplicationApi'
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE, RESUME_TYPE_LABEL } from '../labels'
import ResumeViewer from './ResumeViewer.vue'
import StatusActions from './StatusActions.vue'

const props = withDefaults(defineProps<{ application: JobApplication; scope?: 'company' | 'admin'; headingTag?: 'h2' | 'h3' }>(), { scope: 'company', headingTag: 'h3' })
const { label: areaLabel } = useAreas()

const status = ref(props.application.status)
const notice = ref('')

function changed(result: StatusChange | Reactivation) {
  status.value = result.status
  notice.value = `Situação atualizada: ${APPLICATION_STATUS_LABEL[result.previousStatus]} → ${APPLICATION_STATUS_LABEL[result.status]}.`
}
</script>

<template>
  <article class="space-y-4 rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-col gap-4 sm:flex-row sm:justify-between">
      <div class="min-w-0 space-y-1">
        <div class="flex flex-wrap items-center gap-2">
          <component :is="headingTag" class="text-lg font-semibold">{{ application.student.fullName }}</component>
          <StatusBadge :tone="APPLICATION_STATUS_TONE[status]">{{ APPLICATION_STATUS_LABEL[status] }}</StatusBadge>
        </div>
        <p class="text-sm text-muted">{{ [application.student.course, application.student.location].filter(Boolean).join(' · ') || 'Curso e localização não informados' }}</p>
        <p v-if="application.student.summary" class="whitespace-pre-line pt-1 text-sm">{{ application.student.summary }}</p>
        <dl class="space-y-1 pt-2 text-sm">
          <div><dt class="inline font-medium">Competências: </dt><dd class="inline">{{ application.student.skills.length ? application.student.skills.join(', ') : 'nenhuma informada' }}</dd></div>
          <div><dt class="inline font-medium">Áreas de interesse: </dt><dd class="inline">{{ application.student.interestAreas.length ? application.student.interestAreas.map(areaLabel).join(', ') : 'nenhuma informada' }}</dd></div>
        </dl>
      </div>
      <div class="shrink-0 sm:w-52"><ApplicationScore :score="application.matchScore" /></div>
    </div>
    <p class="text-sm text-muted">Enviada em {{ formatDate(application.appliedAt) }} · {{ RESUME_TYPE_LABEL[application.resumeType] }}</p>

    <ResumeViewer :application-id="application.id" :resume-type="application.resumeType" note="Cópia do currículo exatamente como o estudante enviou. Ela não reflete alterações feitas depois no currículo-base." />

    <p v-if="notice" role="status" class="text-sm font-medium">{{ notice }}</p>
    <StatusActions :application-id="application.id" :status="status" :scope="scope" @changed="changed" />
  </article>
</template>
