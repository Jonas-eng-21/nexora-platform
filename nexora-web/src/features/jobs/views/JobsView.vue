<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import TextField from '@/shared/ui/TextField.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import { useMatchingSignals } from '@/features/students/composables/useMatchingSignals'
import { JOB_LEVEL_OPTIONS, WORK_MODEL_OPTIONS } from '@/features/students/labels'
import { jobApi } from '../api/jobApi'
import JobCard from '../components/JobCard.vue'
import type { Job, JobFilters, RecommendedJob } from '../types'
import type { Page } from '@/features/students/types'

const route = useRoute()
const router = useRouter()
const { options: areaOptions } = useAreas()
const { lacksData } = useMatchingSignals()

// Filtros e página ficam na URL: o link é compartilhável e o botão voltar funciona.
const q = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '')
const view = computed(() => (q('visao') === 'todas' ? 'todas' : 'recomendadas'))
const page = computed(() => Math.max(0, Number.parseInt(q('pagina'), 10) || 0))
const filters = computed<JobFilters>(() => ({ search: q('busca'), area: q('area'), workModel: q('modelo'), level: q('nivel'), location: q('local') }))
const draft = reactive<JobFilters>({ search: '', area: '', workModel: '', level: '', location: '' })
const hasFilters = computed(() => Object.values(filters.value).some(Boolean))

const list = useLoader<Page<Job> | Page<RecommendedJob>>(
  () => (view.value === 'todas' ? jobApi.search(filters.value, page.value) : jobApi.recommended(page.value)),
  { immediate: false },
)
watch(
  () => (route.name === 'student-jobs' ? route.fullPath : null),
  (path) => {
    if (path === null) return
    Object.assign(draft, filters.value)
    void list.load()
  },
  { immediate: true },
)

const profileMissing = computed(() => list.error.value?.code === 'STUDENT_PROFILE_NOT_FOUND')
const items = computed(() => list.data.value?.content ?? [])
const asJob = (item: Job | RecommendedJob) => ('job' in item ? item.job : item)
const asMatch = (item: Job | RecommendedJob) => ('match' in item ? item.match : undefined)

const go = (query: Record<string, string>) => router.push({ name: 'student-jobs', query })
const setView = (visao: 'recomendadas' | 'todas') => go({ visao })
const applyFilters = () => {
  const query: Record<string, string> = { visao: 'todas' }
  if (draft.search.trim()) query.busca = draft.search.trim()
  if (draft.area) query.area = draft.area
  if (draft.workModel) query.modelo = draft.workModel
  if (draft.level) query.nivel = draft.level
  if (draft.location.trim()) query.local = draft.location.trim()
  return go(query)
}
const clearFilters = () => go({ visao: 'todas' })
const setPage = (p: number) => router.push({ name: 'student-jobs', query: { ...route.query, pagina: String(p) } })
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Vagas</h1>

    <div role="group" aria-label="Tipo de listagem" class="inline-flex rounded-control border border-line-strong">
      <button type="button" :aria-pressed="view === 'recomendadas'" class="min-h-11 rounded-l-control px-4 text-sm" :class="view === 'recomendadas' ? 'bg-primary-soft font-semibold' : 'hover:bg-primary-soft'" @click="setView('recomendadas')">Recomendadas</button>
      <button type="button" :aria-pressed="view === 'todas'" class="min-h-11 rounded-r-control border-l border-line-strong px-4 text-sm" :class="view === 'todas' ? 'bg-primary-soft font-semibold' : 'hover:bg-primary-soft'" @click="setView('todas')">Todas as vagas</button>
    </div>

    <form v-if="view === 'todas'" class="space-y-4 rounded-card border border-line bg-surface p-5" aria-label="Filtros de vagas" @submit.prevent="applyFilters">
      <TextField v-model="draft.search" label="Buscar por termo" name="busca" :show-optional="false" />
      <div class="grid gap-4 sm:grid-cols-2">
        <SelectField v-model="draft.area" label="Área" name="area" :show-optional="false" placeholder="Todas" :options="areaOptions" />
        <SelectField v-model="draft.workModel" label="Modelo de trabalho" name="modelo" :show-optional="false" placeholder="Todos" :options="WORK_MODEL_OPTIONS" />
        <SelectField v-model="draft.level" label="Nível" name="nivel" :show-optional="false" placeholder="Todos" :options="JOB_LEVEL_OPTIONS" />
        <TextField v-model="draft.location" label="Localização" name="local" :show-optional="false" />
      </div>
      <div class="flex flex-wrap gap-2">
        <BaseButton type="submit">Filtrar</BaseButton>
        <BaseButton variant="secondary" :disabled="!hasFilters" @click="clearFilters">Limpar filtros</BaseButton>
      </div>
    </form>

    <AlertMessage v-if="view === 'recomendadas' && lacksData" tone="info">
      Seu currículo ainda não tem competências nem áreas de interesse, então as notas abaixo têm pouca base.
      <RouterLink to="/estudante/perfil" class="font-medium underline underline-offset-4">Complete o perfil</RouterLink> para recomendações mais úteis.
    </AlertMessage>

    <EmptyState v-if="profileMissing" title="Preencha seu perfil para ver recomendações" description="As recomendações usam o seu currículo-base. Salve o perfil e escolha suas competências.">
      <RouterLink to="/estudante/perfil" class="font-medium text-primary underline underline-offset-4">Ir para o perfil</RouterLink>
    </EmptyState>
    <LoadState v-else :loading="list.loading.value" :error="list.error.value" label="Carregando vagas…" @retry="list.load">
      <EmptyState v-if="!items.length" :title="view === 'todas' ? 'Nenhuma vaga encontrada' : 'Nenhuma vaga recomendada no momento'" :description="view === 'todas' ? 'Nenhuma vaga aberta corresponde a estes filtros.' : 'Não há vagas abertas para avaliar agora.'">
        <BaseButton v-if="view === 'todas' && hasFilters" variant="secondary" @click="clearFilters">Limpar filtros</BaseButton>
      </EmptyState>
      <ul v-else class="space-y-4">
        <li v-for="item in items" :key="asJob(item).id">
          <JobCard :job="asJob(item)" :match="asMatch(item)" :show-match="view === 'recomendadas'" />
        </li>
      </ul>
      <Pagination v-if="list.data.value" class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
    </LoadState>
  </div>
</template>
