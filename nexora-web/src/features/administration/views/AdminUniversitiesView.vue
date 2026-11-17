<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import { adminApi } from '../api/adminApi'
import AdminUniversityCard from '../components/AdminUniversityCard.vue'
import type { UniversityApprovalStatus } from '../types'

const route = useRoute()
const router = useRouter()

// Sem `status` na URL vale a fila de análise (pendentes); "Todas" é explícito.
const FILTERS = [
  { value: 'PENDING', label: 'Aguardando aprovação' },
  { value: 'APPROVED', label: 'Aprovadas' },
  { value: 'REJECTED', label: 'Rejeitadas' },
  { value: 'ALL', label: 'Todas' },
]
const filter = computed(() => {
  const v = route.query.status
  return typeof v === 'string' && FILTERS.some((f) => f.value === v) ? v : 'PENDING'
})
const apiStatus = computed<UniversityApprovalStatus | ''>(() => (filter.value === 'ALL' ? '' : (filter.value as UniversityApprovalStatus)))
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const list = useLoader(() => adminApi.listUniversities(apiStatus.value, page.value), { immediate: false })
watch(
  () => (route.name === 'admin-universities' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)

const notice = ref('')
async function decided({ kind, name }: { kind: 'APPROVE' | 'REJECT'; name: string }) {
  notice.value = kind === 'APPROVE' ? `${name} aprovada. Ela já pode entrar na plataforma.` : `Cadastro de ${name} rejeitado.`
  await list.load()
}
const setFilter = (value: string) => { notice.value = ''; return router.push({ name: 'admin-universities', query: value === 'PENDING' ? {} : { status: value } }) }
const setPage = (p: number) => router.push({ name: 'admin-universities', query: { ...route.query, pagina: String(p) } })
const filterLabel = computed(() => (FILTERS.find((f) => f.value === filter.value)?.label ?? '').toLowerCase())
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Universidades</h1>
    <p v-if="notice" role="status" class="rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>
    <SelectField :model-value="filter" label="Situação do cadastro" name="status" :options="FILTERS" :show-optional="false" class="max-w-xs" @update:model-value="setFilter" />

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando universidades…" @retry="list.load">
      <EmptyState
        v-if="!list.data.value?.content.length"
        :title="filter === 'PENDING' ? 'Nenhum cadastro aguardando aprovação' : filter === 'ALL' ? 'Nenhuma universidade cadastrada' : `Nenhuma universidade com situação “${filterLabel}”`"
        :description="filter === 'PENDING' ? 'Quando uma universidade se cadastrar, o pedido aparece aqui.' : 'Mude o filtro para ver outros cadastros.'"
      >
        <BaseButton v-if="filter !== 'ALL'" variant="secondary" @click="setFilter('ALL')">Ver todas</BaseButton>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="u in list.data.value.content" :key="u.id"><AdminUniversityCard :university="u" @decided="decided" @refresh="list.load" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
