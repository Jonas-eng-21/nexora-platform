<script setup lang="ts">
import { computed } from 'vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import { describeMatch } from '../scoreDisplay'
import type { MatchResult } from '../types'

const props = defineProps<{ match: MatchResult | null | undefined }>()
const view = computed(() => describeMatch(props.match))
</script>

<template>
  <div v-if="view.state === 'ready'" class="space-y-1.5">
    <p class="flex flex-wrap items-baseline gap-x-2">
      <span class="text-2xl font-semibold tabular-nums">{{ view.score }}<span class="text-base font-normal text-muted">/100</span></span>
      <span class="text-sm font-medium">{{ view.bandLabel }}</span>
    </p>
    <ProgressBar :value="view.score" :label="`Compatibilidade de ${view.score} em 100`" />
    <p class="text-sm text-muted">{{ view.typeLabel }}</p>
  </div>
  <p v-else class="text-sm text-muted">
    <span class="font-medium text-fg">Score indisponível.</span>
    A compatibilidade não pôde ser calculada.
  </p>
</template>
