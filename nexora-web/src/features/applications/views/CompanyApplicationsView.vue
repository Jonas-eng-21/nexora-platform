<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import { forCompany } from '@/features/companies/errors'
import { jobManagementApi } from '@/features/jobs/api/jobManagementApi'
import JobPageHeader from '@/features/jobs/components/JobPageHeader.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import { companyApplicationApi } from '../api/companyApplicationApi'
import CompanyApplicationCard from '../components/CompanyApplicationCard.vue'
import { APPLICATION_STATUS_OPTIONS } from '../labels'
import type { ApplicationStatus } from '../types'

useAreas()
const route = useRoute()
const router = useRouter()
const jobId = computed(() => String(route.params.jobId))

const status = computed(() => {
  const v = route.query.status
  return typeof v === 'string' && APPLICATION_STATUS_OPTIONS.some((o) => o.value === v) ? (v as ApplicationStatus) : ''
})
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const job = useLoader(() => jobManagementApi.get(jobId.value))
const list = useLoader(() => companyApplicationApi.listForJob(jobId.value, status.value, page.value), { immediate: false })
watch(
  () => (route.name === 'company-job-applications' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)

const listError = computed(() => forCompany(list.error.value))
const setStatus = (value: string) => router.push({ name: 'company-job-applications', params: { jobId: jobId.value }, query: value ? { status: value } : {} })
const setPage = (p: number) => router.push({ name: 'company-job-applications', params: { jobId: jobId.value }, query: { ...route.query, pagina: String(p) } })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <JobPageHeader v-if="job.data.value && !listError" :job="job.data.value" />
    <h2 class="text-xl font-semibold">Candidaturas recebidas</h2>

    <SelectField :model-value="status" label="Situação" name="status" placeholder="Todas" :options="APPLICATION_STATUS_OPTIONS" :show-optional="false" class="max-w-xs" @update:model-value="setStatus" />

    <LoadState :loading="list.loading.value" :error="listError" label="Carregando candidaturas…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" :title="status ? 'Nenhuma candidatura com esta situação' : 'Esta vaga ainda não recebeu candidaturas'" :description="status ? 'Limpe o filtro para ver todas as candidaturas.' : 'Quando um estudante validado se candidatar, ele aparece aqui.'">
        <BaseButton v-if="status" variant="secondary" @click="setStatus('')">Limpar filtro</BaseButton>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="a in list.data.value.content" :key="a.id"><CompanyApplicationCard :application="a" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
