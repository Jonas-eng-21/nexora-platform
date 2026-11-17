<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import { affiliationDecisionApi } from '../api/affiliationDecisionApi'
import AffiliationRequestCard from '../components/AffiliationRequestCard.vue'
import type { DecisionKind } from '../decisionRules'
import { AFFILIATION_STATUS_LABEL } from '../labels'
import type { AffiliationStatus } from '../types'

const route = useRoute()
const router = useRouter()

// Sem `status` na URL vale a fila de análise (pendentes); "Todas" é explícito.
const FILTERS = [
  { value: 'PENDING', label: 'Aguardando análise' },
  { value: 'APPROVED', label: 'Aprovados' },
  { value: 'REJECTED', label: 'Rejeitados' },
  { value: 'REVOKED', label: 'Revogados' },
  { value: 'ALL', label: 'Todas' },
]
const filter = computed(() => {
  const v = route.query.status
  return typeof v === 'string' && FILTERS.some((f) => f.value === v) ? v : 'PENDING'
})
const apiStatus = computed<AffiliationStatus | ''>(() => (filter.value === 'ALL' ? '' : (filter.value as AffiliationStatus)))
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const list = useLoader(() => affiliationDecisionApi.listReceived(apiStatus.value, page.value), { immediate: false })
watch(
  () => (route.name === 'university-affiliations' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)

const notice = ref('')
async function decided({ kind, studentName }: { kind: DecisionKind; studentName: string }) {
  notice.value =
    kind === 'APPROVE' ? `Vínculo de ${studentName} aprovado.`
    : kind === 'REJECT' ? `Solicitação de ${studentName} rejeitada.`
    : `Vínculo de ${studentName} revogado. As candidaturas dele em andamento são suspensas automaticamente.`
  await list.load()
}

const setFilter = (value: string) => { notice.value = ''; return router.push({ name: 'university-affiliations', query: value === 'PENDING' ? {} : { status: value } }) }
const setPage = (p: number) => router.push({ name: 'university-affiliations', query: { ...route.query, pagina: String(p) } })
const filterLabel = computed(() => (filter.value === 'ALL' ? '' : AFFILIATION_STATUS_LABEL[filter.value as AffiliationStatus].toLowerCase()))
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Solicitações de vínculo</h1>

    <p v-if="notice" role="status" class="rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>

    <SelectField :model-value="filter" label="Situação" name="status" :options="FILTERS" :show-optional="false" class="max-w-xs" @update:model-value="setFilter" />

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando solicitações…" @retry="list.load">
      <EmptyState
        v-if="!list.data.value?.content.length"
        :title="filter === 'PENDING' ? 'Nenhuma solicitação aguardando análise' : filter === 'ALL' ? 'Nenhuma solicitação recebida' : `Nenhuma solicitação com situação “${filterLabel}”`"
        :description="filter === 'PENDING' ? 'Quando um estudante pedir vínculo com a sua universidade, a solicitação aparece aqui.' : 'Mude a situação para ver outros registros do histórico.'"
      >
        <BaseButton v-if="filter !== 'ALL'" variant="secondary" @click="setFilter('ALL')">Ver todas</BaseButton>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="r in list.data.value.content" :key="r.id"><AffiliationRequestCard :request="r" @decided="decided" @refresh="list.load" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
