<script setup lang="ts">
import { computed } from 'vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import { describeScore } from '../scoreDisplay'

const props = defineProps<{ score: number | null | undefined }>()
const view = computed(() => describeScore(props.score))
</script>

<template>
  <div v-if="view.state === 'ready'" class="space-y-1.5">
    <p class="flex flex-wrap items-baseline gap-x-2">
      <span class="text-2xl font-semibold tabular-nums">{{ view.score }}<span class="text-base font-normal text-muted">/100</span></span>
      <span class="text-sm font-medium">{{ view.bandLabel }}</span>
    </p>
    <ProgressBar :value="view.score" :label="`Compatibilidade atual de ${view.score} em 100`" />
    <p class="text-sm text-muted">Calculada pelo currículo-base atual.</p>
  </div>
  <p v-else class="text-sm">
    <span class="font-medium">Sem dados suficientes.</span>
    <span class="text-muted"> O currículo-base do estudante não tem competências nem áreas de interesse para comparar.</span>
  </p>
</template>
