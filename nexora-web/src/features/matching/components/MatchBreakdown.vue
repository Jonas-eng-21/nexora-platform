<script setup lang="ts">
import { computed } from 'vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import { CRITERION_LABEL, describeMatch } from '../scoreDisplay'
import type { MatchResult } from '../types'

const props = defineProps<{ match: MatchResult }>()
const view = computed(() => describeMatch(props.match))

const skillGroups = computed(() => [
  { title: 'Obrigatórias que você tem', items: props.match.matchedRequiredSkills },
  { title: 'Obrigatórias que faltam', items: props.match.missingRequiredSkills },
  { title: 'Desejáveis que você tem', items: props.match.matchedDesiredSkills },
  { title: 'Desejáveis que faltam', items: props.match.missingDesiredSkills },
])
</script>

<template>
  <div class="space-y-5">
    <p>{{ match.summary }}</p>

    <dl v-if="view.state === 'ready'" class="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
      <div>
        <dt class="text-muted">Tipo de cálculo</dt>
        <dd class="font-medium">{{ view.typeLabel }}</dd>
      </div>
      <div>
        <dt class="text-muted">Nota estruturada</dt>
        <dd class="font-medium tabular-nums">{{ view.structured }}/100</dd>
      </div>
      <div v-if="view.semantic !== null">
        <dt class="text-muted">Similaridade semântica</dt>
        <dd class="font-medium tabular-nums">{{ view.semantic }}/100</dd>
      </div>
    </dl>
    <p v-if="view.state === 'ready'" class="text-sm text-muted">{{ view.typeNote }}</p>

    <div>
      <h3 class="mb-2 font-semibold">Como a nota foi calculada</h3>
      <ul class="space-y-4">
        <li v-for="c in match.criteria" :key="c.criterion">
          <div class="flex items-baseline justify-between gap-3">
            <span class="font-medium">{{ CRITERION_LABEL[c.criterion] }}</span>
            <span v-if="c.applicable" class="text-sm tabular-nums text-muted">{{ c.points }} de {{ c.weight }} pontos</span>
            <span v-else class="text-sm text-muted">Não se aplica a esta vaga</span>
          </div>
          <ProgressBar v-if="c.applicable" class="mt-1" :value="c.points" :max="c.weight" :label="`${CRITERION_LABEL[c.criterion]}: ${c.points} de ${c.weight} pontos`" />
          <p class="mt-1 text-sm text-muted">{{ c.explanation }}</p>
        </li>
      </ul>
    </div>

    <div class="grid gap-4 sm:grid-cols-2">
      <section v-for="group in skillGroups" :key="group.title">
        <h3 class="mb-1 text-sm font-semibold">{{ group.title }}</h3>
        <p v-if="!group.items.length" class="text-sm text-muted">Nenhuma.</p>
        <ul v-else class="list-inside list-disc text-sm">
          <li v-for="name in group.items" :key="name">{{ name }}</li>
        </ul>
      </section>
    </div>
  </div>
</template>
