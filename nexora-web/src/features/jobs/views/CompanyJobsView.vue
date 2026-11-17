<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import { useAreas } from '@/features/students/composables/useAreas'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import { jobManagementApi } from '../api/jobManagementApi'
import CompanyJobCard from '../components/CompanyJobCard.vue'

useAreas() // aquece o catálogo de áreas usado pelos cartões
const route = useRoute()
const router = useRouter()

const STATUS_OPTIONS = [{ value: 'OPEN', label: 'Abertas' }, { value: 'CLOSED', label: 'Encerradas' }]
const status = computed(() => (route.query.status === 'OPEN' || route.query.status === 'CLOSED' ? route.query.status : ''))
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))
const notice = computed(() => (route.query.aviso === 'criada' ? 'Vaga publicada.' : route.query.aviso === 'editada' ? 'Vaga atualizada.' : ''))

const list = useLoader(() => jobManagementApi.listMine(status.value, page.value), { immediate: false })
watch(
  () => (route.name === 'company-jobs' ? `${status.value}|${page.value}` : null),
  (key) => { if (key !== null) void list.load() },
  { immediate: true },
)

const setStatus = (value: string) => router.push({ name: 'company-jobs', query: value ? { status: value } : {} })
const setPage = (p: number) => router.push({ name: 'company-jobs', query: { ...route.query, pagina: String(p), aviso: undefined } })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-2xl font-semibold">Vagas</h1>
      <RouterLink :to="{ name: 'company-job-new' }" class="inline-flex min-h-11 items-center rounded-control bg-primary px-4 text-sm font-semibold text-on-primary hover:bg-primary-hover">Nova vaga</RouterLink>
    </div>

    <p v-if="notice" role="status" class="rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>

    <SelectField :model-value="status" label="Situação" name="status" placeholder="Todas" :options="STATUS_OPTIONS" :show-optional="false" class="max-w-xs" @update:model-value="setStatus" />

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando vagas…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" :title="status ? 'Nenhuma vaga com esta situação' : 'Você ainda não publicou vagas'" :description="status ? 'Limpe o filtro para ver todas as suas vagas.' : 'Publique a primeira vaga para receber candidaturas e ver estudantes recomendados.'">
        <BaseButton v-if="status" variant="secondary" @click="setStatus('')">Limpar filtro</BaseButton>
        <RouterLink v-else :to="{ name: 'company-job-new' }" class="font-medium text-primary underline underline-offset-4">Publicar vaga</RouterLink>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="job in list.data.value.content" :key="job.id"><CompanyJobCard :job="job" @removed="list.load" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
