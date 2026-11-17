<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { formatDate } from '@/shared/format'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { applicationApi } from '@/features/applications/api/applicationApi'
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE } from '@/features/applications/labels'
import JobCard from '@/features/jobs/components/JobCard.vue'
import { jobApi } from '@/features/jobs/api/jobApi'
import { useEligibility } from '@/features/universities/useEligibility'
import { studentApi } from '../api/studentApi'

const missingProfile = (e: unknown) => {
  if (e instanceof ApiError && e.code === 'STUDENT_PROFILE_NOT_FOUND') return null
  throw e
}
const profile = useLoader(() => studentApi.getProfile().catch(missingProfile))
const skills = useLoader(() => studentApi.getSkills().catch(missingProfile))
const eligibility = useEligibility()
const applications = useLoader(() => applicationApi.listOwn('', 0, 3))
const recommended = useLoader(() => jobApi.recommended(0, 3).catch(missingProfile))

const checklistLoading = computed(() => profile.loading.value || skills.loading.value || eligibility.loading.value || applications.loading.value)
const checklistError = computed(() => profile.error.value ?? skills.error.value ?? eligibility.error.value ?? applications.error.value)
const steps = computed(() => [
  { done: profile.data.value != null, title: 'Preencher o perfil', todo: 'Informe nome, curso e localização.', to: '/estudante/perfil', action: 'Preencher perfil' },
  { done: (skills.data.value?.length ?? 0) > 0, title: 'Escolher competências técnicas', todo: 'Elas alimentam a compatibilidade com as vagas.', to: '/estudante/perfil', action: 'Escolher competências' },
  { done: eligibility.data.value === true, title: 'Validar o vínculo acadêmico', todo: 'Sem vínculo aprovado não é possível se candidatar.', to: '/estudante/vinculo', action: 'Solicitar vínculo' },
  { done: (applications.data.value?.totalElements ?? 0) > 0, title: 'Enviar a primeira candidatura', todo: 'Escolha uma vaga e envie seu currículo.', to: '/estudante/vagas', action: 'Ver vagas' },
])
const pending = computed(() => steps.value.filter((s) => !s.done).length)
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Início</h1>

    <SectionCard title="Para se candidatar" description="O que falta para você usar o Nexora por completo.">
      <LoadState :loading="checklistLoading" :error="checklistError" label="Carregando seu andamento…" @retry="() => { profile.load(); skills.load(); eligibility.load(); applications.load() }">
        <p class="mb-3 text-sm" role="status">{{ pending === 0 ? 'Tudo pronto: você já cumpriu os quatro passos.' : `${pending} de 4 passos pendentes.` }}</p>
        <ol class="divide-y divide-line">
          <li v-for="step in steps" :key="step.title" class="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
            <div>
              <p class="font-medium">{{ step.title }}</p>
              <p v-if="!step.done" class="text-sm text-muted">{{ step.todo }}</p>
            </div>
            <StatusBadge v-if="step.done" tone="success">Concluído</StatusBadge>
            <RouterLink v-else :to="step.to" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">{{ step.action }}</RouterLink>
          </li>
        </ol>
      </LoadState>
    </SectionCard>

    <SectionCard title="Vagas com maior compatibilidade com o seu currículo">
      <LoadState :loading="recommended.loading.value" :error="recommended.error.value" label="Carregando recomendações…" @retry="recommended.load">
        <EmptyState v-if="recommended.data.value === null" title="Preencha seu perfil para ver recomendações" description="As recomendações usam o seu currículo-base.">
          <RouterLink to="/estudante/perfil" class="font-medium text-primary underline underline-offset-4">Ir para o perfil</RouterLink>
        </EmptyState>
        <EmptyState v-else-if="!recommended.data.value.content.length" title="Nenhuma vaga recomendada no momento" description="Não há vagas abertas para avaliar agora." />
        <template v-else>
          <ul class="space-y-4">
            <li v-for="r in recommended.data.value.content" :key="r.job.id"><JobCard :job="r.job" :match="r.match" show-match heading-tag="h3" /></li>
          </ul>
          <RouterLink to="/estudante/vagas" class="mt-4 inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver todas as recomendações</RouterLink>
        </template>
      </LoadState>
    </SectionCard>

    <SectionCard title="Candidaturas mais recentes">
      <LoadState :loading="applications.loading.value" :error="applications.error.value" label="Carregando candidaturas…" @retry="applications.load">
        <EmptyState v-if="!applications.data.value?.content.length" title="Você ainda não se candidatou a nenhuma vaga" description="Quando você se candidatar, o acompanhamento aparece aqui." />
        <template v-else>
          <ul class="divide-y divide-line">
            <li v-for="a in applications.data.value.content" :key="a.id" class="flex flex-wrap items-center justify-between gap-2 py-3 first:pt-0 last:pb-0">
              <div class="min-w-0">
                <RouterLink :to="{ name: 'student-job', params: { jobId: a.jobId } }" class="font-medium hover:underline">{{ a.jobTitle }}</RouterLink>
                <p class="text-sm text-muted">{{ a.companyName }} · {{ formatDate(a.appliedAt) }}</p>
              </div>
              <StatusBadge :tone="APPLICATION_STATUS_TONE[a.status]">{{ APPLICATION_STATUS_LABEL[a.status] }}</StatusBadge>
            </li>
          </ul>
          <RouterLink to="/estudante/candidaturas" class="mt-4 inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver todas as candidaturas</RouterLink>
        </template>
      </LoadState>
    </SectionCard>
  </div>
</template>
