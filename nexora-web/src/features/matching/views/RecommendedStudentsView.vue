<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import { forCompany } from '@/features/companies/errors'
import { jobManagementApi } from '@/features/jobs/api/jobManagementApi'
import JobPageHeader from '@/features/jobs/components/JobPageHeader.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import { companyMatchingApi } from '../api/companyMatchingApi'
import RecommendedStudentCard from '../components/RecommendedStudentCard.vue'

useAreas()
const route = useRoute()
const router = useRouter()
const jobId = computed(() => String(route.params.jobId))
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const job = useLoader(() => jobManagementApi.get(jobId.value))
const list = useLoader(() => companyMatchingApi.students(jobId.value, page.value), { immediate: false })
watch(
  () => (route.name === 'company-job-students' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)

const listError = computed(() => forCompany(list.error.value))
const setPage = (p: number) => router.push({ name: 'company-job-students', params: { jobId: jobId.value }, query: { pagina: String(p) } })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <JobPageHeader v-if="job.data.value && !listError" :job="job.data.value" />
    <h2 class="text-xl font-semibold">Estudantes recomendados</h2>
    <AlertMessage tone="info">
      Estudantes com vínculo acadêmico validado, ordenados pela compatibilidade com a vaga. Esta lista não é uma candidatura: o estudante só entra no processo seletivo ao se candidatar. Por privacidade, não há e-mail nem o currículo completo aqui.
    </AlertMessage>

    <LoadState :loading="list.loading.value" :error="listError" label="Calculando recomendações…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" title="Nenhum estudante recomendado no momento" description="Ainda não há estudantes validados academicamente com perfil para comparar com esta vaga." />
      <template v-else>
        <ul class="space-y-4">
          <li v-for="item in list.data.value.content" :key="item.student.id"><RecommendedStudentCard :item="item" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
