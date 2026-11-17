<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import { formatDate } from '@/shared/format'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import ApplyPanel from '@/features/applications/components/ApplyPanel.vue'
import { applicationApi } from '@/features/applications/api/applicationApi'
import MatchBreakdown from '@/features/matching/components/MatchBreakdown.vue'
import MatchScore from '@/features/matching/components/MatchScore.vue'
import { useAreas } from '@/features/students/composables/useAreas'
import { JOB_LEVEL_LABEL, WORK_MODEL_LABEL } from '@/features/students/labels'
import { useEligibility } from '@/features/universities/useEligibility'
import { jobApi } from '../api/jobApi'

const route = useRoute()
const jobId = computed(() => String(route.params.jobId))
const { label: areaLabel } = useAreas()

const job = useLoader(() => jobApi.get(jobId.value))
const match = useLoader(() => jobApi.match(jobId.value))
const eligibility = useEligibility()
// Detecta candidatura já feita; se houver mais de 50, o backend ainda responde 409 ao enviar.
const mine = useLoader(() => applicationApi.listOwn('', 0, 50))
const existing = computed(() => mine.data.value?.content.find((a) => a.jobId === jobId.value) ?? null)
const eligible = computed(() => (eligibility.loading.value || mine.loading.value ? null : (eligibility.data.value ?? false)))

const matchedNames = computed(() => new Set([...(match.data.value?.match.matchedRequiredSkills ?? []), ...(match.data.value?.match.matchedDesiredSkills ?? [])]))
const hasMatch = computed(() => match.data.value !== null)
const matchProfileMissing = computed(() => match.error.value?.code === 'STUDENT_PROFILE_NOT_FOUND')

async function applied() {
  await mine.load()
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <RouterLink :to="{ name: 'student-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar às vagas</RouterLink>

    <LoadState :loading="job.loading.value" :error="job.error.value" label="Carregando vaga…" @retry="job.load">
      <template v-if="job.data.value">
        <header class="space-y-1">
          <h1 class="text-2xl font-semibold">{{ job.data.value.title }}</h1>
          <p class="text-muted">{{ job.data.value.companyName }}</p>
          <p class="text-sm text-muted">
            {{ WORK_MODEL_LABEL[job.data.value.workModel] }} · {{ job.data.value.location }} · {{ JOB_LEVEL_LABEL[job.data.value.level] }} ·
            {{ areaLabel(job.data.value.area) }} · publicada em {{ formatDate(job.data.value.createdAt) }}
          </p>
        </header>

        <SectionCard title="Sobre a vaga">
          <p class="whitespace-pre-line">{{ job.data.value.description }}</p>
        </SectionCard>

        <SectionCard title="Requisitos">
          <div class="grid gap-6 sm:grid-cols-2">
            <section>
              <h3 class="mb-2 font-semibold">Obrigatórios</h3>
              <p v-if="!job.data.value.requiredSkills.length" class="text-sm text-muted">Nenhuma competência obrigatória listada.</p>
              <ul v-else class="space-y-1 text-sm">
                <li v-for="s in job.data.value.requiredSkills" :key="s.id">
                  {{ s.name }}<span v-if="hasMatch" class="text-muted"> — {{ matchedNames.has(s.name) ? 'você tem' : 'você não tem' }}</span>
                </li>
              </ul>
            </section>
            <section>
              <h3 class="mb-2 font-semibold">Desejáveis</h3>
              <p v-if="!job.data.value.desiredSkills.length" class="text-sm text-muted">Nenhuma competência desejável listada.</p>
              <ul v-else class="space-y-1 text-sm">
                <li v-for="s in job.data.value.desiredSkills" :key="s.id">
                  {{ s.name }}<span v-if="hasMatch" class="text-muted"> — {{ matchedNames.has(s.name) ? 'você tem' : 'você não tem' }}</span>
                </li>
              </ul>
            </section>
          </div>
        </SectionCard>

        <SectionCard title="Sua compatibilidade com a vaga" description="Calculada pelo sistema a partir do seu currículo-base.">
          <LoadState :loading="match.loading.value" :error="matchProfileMissing ? null : match.error.value" label="Calculando compatibilidade…" @retry="match.load">
            <AlertMessage v-if="matchProfileMissing" tone="info">
              Preencha seu perfil para ver a compatibilidade com esta vaga.
              <RouterLink to="/estudante/perfil" class="font-medium underline underline-offset-4">Ir para o perfil</RouterLink>
            </AlertMessage>
            <div v-else-if="match.data.value" class="space-y-5">
              <MatchScore :match="match.data.value.match" />
              <MatchBreakdown :match="match.data.value.match" />
            </div>
            <MatchScore v-else :match="null" />
          </LoadState>
        </SectionCard>

        <SectionCard title="Candidatura">
          <LoadState :loading="false" :error="eligibility.error.value ?? mine.error.value" @retry="() => { eligibility.load(); mine.load() }">
            <ApplyPanel :job-id="job.data.value.id" :eligible="eligible" :existing="existing" @applied="applied" />
          </LoadState>
        </SectionCard>
      </template>
    </LoadState>
  </div>
</template>
