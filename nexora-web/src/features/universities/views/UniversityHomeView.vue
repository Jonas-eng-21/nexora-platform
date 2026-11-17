<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import { affiliationDecisionApi } from '../api/affiliationDecisionApi'
import AffiliationRequestCard from '../components/AffiliationRequestCard.vue'
import type { DecisionKind } from '../decisionRules'

const SHOWN = 5
const pending = useLoader(() => affiliationDecisionApi.listReceived('PENDING', 0, SHOWN))

const notice = ref('')
async function decided({ kind, studentName }: { kind: DecisionKind; studentName: string }) {
  notice.value = kind === 'APPROVE' ? `Vínculo de ${studentName} aprovado.` : `Solicitação de ${studentName} rejeitada.`
  await pending.load()
}
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Início</h1>

    <SectionCard title="Solicitações aguardando análise" description="As mais antigas primeiro.">
      <p v-if="notice" role="status" class="mb-4 rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>
      <LoadState :loading="pending.loading.value" :error="pending.error.value" label="Carregando solicitações…" @retry="pending.load">
        <EmptyState v-if="!pending.data.value?.content.length" title="Nenhuma solicitação aguardando análise" description="Quando um estudante pedir vínculo com a sua universidade, a solicitação aparece aqui." />
        <template v-else>
          <p class="mb-4 text-sm" role="status">{{ pending.data.value.totalElements }} {{ pending.data.value.totalElements === 1 ? 'solicitação aguardando análise' : 'solicitações aguardando análise' }}.</p>
          <ul class="space-y-4">
            <li v-for="r in pending.data.value.content" :key="r.id"><AffiliationRequestCard :request="r" heading-tag="h3" @decided="decided" @refresh="pending.load" /></li>
          </ul>
          <p v-if="pending.data.value.totalElements > pending.data.value.content.length" class="mt-4 text-sm text-muted">Mostrando {{ pending.data.value.content.length }} de {{ pending.data.value.totalElements }}.</p>
        </template>
        <div class="mt-4 flex flex-wrap gap-x-5">
          <RouterLink :to="{ name: 'university-affiliations' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver todas as solicitações e o histórico</RouterLink>
          <RouterLink :to="{ name: 'university-profile' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Perfil institucional</RouterLink>
        </div>
      </LoadState>
    </SectionCard>
  </div>
</template>
