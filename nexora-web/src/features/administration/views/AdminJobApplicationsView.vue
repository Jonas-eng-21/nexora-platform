<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import CompanyApplicationCard from '@/features/applications/components/CompanyApplicationCard.vue'
import { APPLICATION_STATUS_OPTIONS } from '@/features/applications/labels'
import type { ApplicationStatus } from '@/features/applications/types'
import { forAdmin } from '@/features/companies/errors'
import { jobApi } from '@/features/jobs/api/jobApi'
import { useAreas } from '@/features/students/composables/useAreas'
import { adminApi } from '../api/adminApi'

useAreas()
const route = useRoute()
const router = useRouter()
const jobId = computed(() => String(route.params.jobId))
const status = computed(() => {
  const v = route.query.status
  return typeof v === 'string' && APPLICATION_STATUS_OPTIONS.some((o) => o.value === v) ? (v as ApplicationStatus) : ''
})
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const job = useLoader(() => jobApi.get(jobId.value))
const list = useLoader(() => adminApi.listJobApplications(jobId.value, status.value, page.value), { immediate: false })
watch(
  () => (route.name === 'admin-job-applications' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)
const listError = computed(() => forAdmin(list.error.value))
const go = (query: Record<string, string>) => router.push({ name: 'admin-job-applications', params: { jobId: jobId.value }, query })
const setStatus = (v: string) => go(v ? { status: v } : {})
const setPage = (p: number) => go({ ...(route.query as Record<string, string>), pagina: String(p) })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <RouterLink :to="{ name: 'admin-job', params: { jobId } }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar à vaga</RouterLink>
    <h1 class="text-2xl font-semibold">Candidaturas<span v-if="job.data.value && !listError" class="font-normal text-muted"> · {{ job.data.value.title }}</span></h1>
    <p class="text-sm text-muted">Leitura e decisões administrativas, com as mesmas regras de transição da empresa e auditoria no backend.</p>

    <SelectField :model-value="status" label="Situação" name="status" placeholder="Todas" :options="APPLICATION_STATUS_OPTIONS" :show-optional="false" class="max-w-xs" @update:model-value="setStatus" />

    <LoadState :loading="list.loading.value" :error="listError" label="Carregando candidaturas…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" :title="status ? 'Nenhuma candidatura com esta situação' : 'Esta vaga ainda não recebeu candidaturas'">
        <BaseButton v-if="status" variant="secondary" @click="setStatus('')">Limpar filtro</BaseButton>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="a in list.data.value.content" :key="a.id"><CompanyApplicationCard :application="a" scope="admin" heading-tag="h2" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
