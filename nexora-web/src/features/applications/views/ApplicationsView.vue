<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import { applicationApi } from '../api/applicationApi'
import ApplicationCard from '../components/ApplicationCard.vue'
import { APPLICATION_STATUS_OPTIONS } from '../labels'
import type { ApplicationStatus } from '../types'

const route = useRoute()
const router = useRouter()

const status = computed(() => {
  const v = route.query.status
  return typeof v === 'string' && APPLICATION_STATUS_OPTIONS.some((o) => o.value === v) ? (v as ApplicationStatus) : ''
})
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const list = useLoader(() => applicationApi.listOwn(status.value, page.value), { immediate: false })
watch(
  () => (route.name === 'student-applications' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)

const setStatus = (value: string) => router.push({ name: 'student-applications', query: value ? { status: value } : {} })
const setPage = (p: number) => router.push({ name: 'student-applications', query: { ...route.query, pagina: String(p) } })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Candidaturas</h1>

    <SelectField :model-value="status" label="Situação" name="status" :show-optional="false" placeholder="Todas" :options="APPLICATION_STATUS_OPTIONS" class="max-w-xs" @update:model-value="setStatus" />

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando candidaturas…" @retry="list.load">
      <EmptyState
        v-if="!list.data.value?.content.length"
        :title="status ? 'Nenhuma candidatura com esta situação' : 'Você ainda não se candidatou a nenhuma vaga'"
        :description="status ? 'Limpe o filtro para ver todas as suas candidaturas.' : 'Quando você se candidatar, o acompanhamento aparece aqui.'"
      >
        <BaseButton v-if="status" variant="secondary" @click="setStatus('')">Limpar filtro</BaseButton>
        <RouterLink v-else to="/estudante/vagas" class="font-medium text-primary underline underline-offset-4">Ver vagas</RouterLink>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="a in list.data.value.content" :key="a.id"><ApplicationCard :application="a" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
