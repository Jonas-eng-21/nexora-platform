<script setup lang="ts">
import { useAreas } from '@/features/students/composables/useAreas'
import MatchBreakdown from './MatchBreakdown.vue'
import MatchScore from './MatchScore.vue'
import type { RecommendedStudent } from '../api/companyMatchingApi'

defineProps<{ item: RecommendedStudent }>()
const { label: areaLabel } = useAreas()
</script>

<template>
  <article class="rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-col gap-4 sm:flex-row sm:justify-between">
      <div class="min-w-0 space-y-1">
        <h3 class="text-lg font-semibold">{{ item.student.fullName }}</h3>
        <p class="text-sm text-muted">{{ [item.student.course, item.student.location].filter(Boolean).join(' · ') || 'Curso e localização não informados' }}</p>
        <p v-if="item.student.summary" class="whitespace-pre-line pt-1 text-sm">{{ item.student.summary }}</p>
        <dl class="space-y-1 pt-2 text-sm">
          <div>
            <dt class="inline font-medium">Competências: </dt>
            <dd class="inline">{{ item.student.skills.length ? item.student.skills.join(', ') : 'nenhuma informada' }}</dd>
          </div>
          <div>
            <dt class="inline font-medium">Áreas de interesse: </dt>
            <dd class="inline">{{ item.student.interestAreas.length ? item.student.interestAreas.map(areaLabel).join(', ') : 'nenhuma informada' }}</dd>
          </div>
        </dl>
      </div>
      <div class="shrink-0 sm:w-52"><MatchScore :match="item.match" /></div>
    </div>
    <p class="mt-3 text-sm">{{ item.match.summary }}</p>
    <details class="mt-3">
      <summary class="inline-flex min-h-11 cursor-pointer items-center text-sm font-medium text-primary underline underline-offset-4">Ver como a nota foi calculada</summary>
      <div class="pt-3"><MatchBreakdown :match="item.match" perspective="company" heading-tag="h4" /></div>
    </details>
  </article>
</template>
