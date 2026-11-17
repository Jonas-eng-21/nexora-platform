<script setup lang="ts">
import { RouterLink } from 'vue-router'
import MatchScore from '@/features/matching/components/MatchScore.vue'
import type { MatchResult } from '@/features/matching/types'
import { useAreas } from '@/features/students/composables/useAreas'
import { JOB_LEVEL_LABEL, WORK_MODEL_LABEL } from '@/features/students/labels'
import type { Job } from '../types'

withDefaults(defineProps<{ job: Job; match?: MatchResult; showMatch?: boolean; headingTag?: 'h2' | 'h3' }>(), { headingTag: 'h2', match: undefined, showMatch: false })
const { label: areaLabel } = useAreas()
</script>

<template>
  <article class="rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-col gap-4 sm:flex-row sm:justify-between">
      <div class="min-w-0 space-y-1">
        <component :is="headingTag" class="text-lg font-semibold">
          <RouterLink :to="{ name: 'student-job', params: { jobId: job.id } }" class="inline-block py-1 hover:underline">{{ job.title }}</RouterLink>
        </component>
        <p class="text-muted">{{ job.companyName }}</p>
        <p class="text-sm text-muted">{{ areaLabel(job.area) }} · {{ job.location }} · {{ WORK_MODEL_LABEL[job.workModel] }} · {{ JOB_LEVEL_LABEL[job.level] }}</p>
      </div>
      <div v-if="showMatch" class="shrink-0 sm:w-52"><MatchScore :match="match" /></div>
    </div>
    <p v-if="showMatch && match" class="mt-3 text-sm">{{ match.summary }}</p>
  </article>
</template>
