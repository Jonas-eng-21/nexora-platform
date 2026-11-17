<script setup lang="ts">
import { computed } from 'vue'
import ProgressBar from '@/shared/ui/ProgressBar.vue'
import { CRITERION_LABEL, describeMatch } from '../scoreDisplay'
import type { MatchResult } from '../types'

const props = withDefaults(
  defineProps<{ match: MatchResult; perspective?: 'student' | 'company'; headingTag?: 'h3' | 'h4' }>(),
  { perspective: 'student', headingTag: 'h3' },
)
const view = computed(() => describeMatch(props.match))

const TITLES = {
  student: ['Obrigatórias que você tem', 'Obrigatórias que faltam', 'Desejáveis que você tem', 'Desejáveis que faltam'],
  company: ['Obrigatórias atendidas pelo estudante', 'Obrigatórias ausentes no currículo', 'Desejáveis atendidas pelo estudante', 'Desejáveis ausentes no currículo'],
} as const

const skillGroups = computed(() => {
  const t = TITLES[props.perspective]
  return [
    { title: t[0], items: props.match.matchedRequiredSkills },
    { title: t[1], items: props.match.missingRequiredSkills },
    { title: t[2], items: props.match.matchedDesiredSkills },
    { title: t[3], items: props.match.missingDesiredSkills },
  ]
})
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
      <component :is="headingTag" class="mb-2 font-semibold">Como a nota foi calculada</component>
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
        <component :is="headingTag" class="mb-1 text-sm font-semibold">{{ group.title }}</component>
        <p v-if="!group.items.length" class="text-sm text-muted">Nenhuma.</p>
        <ul v-else class="list-inside list-disc text-sm">
          <li v-for="name in group.items" :key="name">{{ name }}</li>
        </ul>
      </section>
    </div>
  </div>
</template>
