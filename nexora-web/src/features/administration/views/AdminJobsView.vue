<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import TextField from '@/shared/ui/TextField.vue'
import { jobApi } from '@/features/jobs/api/jobApi'
import type { JobFilters } from '@/features/jobs/types'
import { useAreas } from '@/features/students/composables/useAreas'
import { JOB_LEVEL_LABEL, WORK_MODEL_LABEL } from '@/features/students/labels'

const { label: areaLabel } = useAreas()
const route = useRoute()
const router = useRouter()
const q = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '')
const search = computed(() => q('busca'))
const page = computed(() => Math.max(0, Number.parseInt(q('pagina'), 10) || 0))
const notice = computed(() => (route.query.aviso === 'excluida' ? 'Vaga excluída. Ela deixa de aparecer nas listas e o histórico de candidaturas é mantido.' : ''))
const draft = reactive({ search: '' })

// GET /jobs lista só vagas abertas; uma vaga encerrada é alcançada pelo identificador (ex.: a partir da auditoria).
const list = useLoader(() => jobApi.search({ search: search.value, area: '', workModel: '', level: '', location: '' } as JobFilters, page.value), { immediate: false })
watch(
  () => (route.name === 'admin-jobs' ? route.fullPath : null),
  (path) => { if (path !== null) { draft.search = search.value; void list.load() } },
  { immediate: true },
)
const apply = () => router.push({ name: 'admin-jobs', query: draft.search.trim() ? { busca: draft.search.trim() } : {} })
const setPage = (p: number) => router.push({ name: 'admin-jobs', query: { ...route.query, pagina: String(p), aviso: undefined } })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Vagas</h1>
    <p class="text-sm text-muted">Vagas abertas de todas as empresas. Abra uma vaga para intervir nela, ver as candidaturas ou chegar ao perfil da empresa.</p>
    <p v-if="notice" role="status" class="rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>

    <form class="flex flex-wrap items-end gap-3" aria-label="Buscar vagas" @submit.prevent="apply">
      <TextField v-model="draft.search" label="Buscar por termo" name="busca" :show-optional="false" class="min-w-0 flex-1" />
      <BaseButton type="submit">Buscar</BaseButton>
      <BaseButton variant="secondary" :disabled="!search" @click="router.push({ name: 'admin-jobs' })">Limpar busca</BaseButton>
    </form>

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando vagas…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" :title="search ? 'Nenhuma vaga encontrada' : 'Nenhuma vaga aberta'" :description="search ? 'Nenhuma vaga aberta corresponde a esta busca.' : undefined" />
      <template v-else>
        <ul class="space-y-4">
          <li v-for="job in list.data.value.content" :key="job.id" class="rounded-card border border-line bg-surface p-5 shadow-card">
            <h2 class="text-lg font-semibold">
              <RouterLink :to="{ name: 'admin-job', params: { jobId: job.id } }" class="inline-block py-1 hover:underline">{{ job.title }}</RouterLink>
            </h2>
            <p class="text-muted">{{ job.companyName }}</p>
            <p class="text-sm text-muted">{{ areaLabel(job.area) }} · {{ job.location || 'Localização não informada' }} · {{ WORK_MODEL_LABEL[job.workModel] }} · {{ JOB_LEVEL_LABEL[job.level] }} · publicada em {{ formatDate(job.createdAt) }}</p>
          </li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
