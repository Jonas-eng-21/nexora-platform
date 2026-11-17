<script setup lang="ts">
import { computed, ref } from 'vue'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { formatDate } from '@/shared/format'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { affiliationApi } from '../api/affiliationApi'
import { AFFILIATION_STATUS_LABEL, AFFILIATION_STATUS_TONE } from '../labels'
import { useEligibility } from '../useEligibility'

const eligibility = useEligibility()
const history = useLoader(() => affiliationApi.listOwn())
const universities = useLoader(() => affiliationApi.listUniversities())

const universityId = ref('')
const submitting = ref(false)
const error = ref<ApiError | null>(null)
const fieldError = ref('')
const sent = ref(false)

const open = computed(() => history.data.value?.find((a) => a.status === 'PENDING' || a.status === 'APPROVED') ?? null)
const options = computed(() =>
  (universities.data.value?.content ?? []).map((u) => ({
    value: u.id,
    label: [u.name, u.acronym ? `(${u.acronym})` : '', u.location ? `· ${u.location}` : ''].filter(Boolean).join(' '),
  })),
)

async function submit() {
  error.value = null
  sent.value = false
  fieldError.value = universityId.value ? '' : 'Escolha a universidade.'
  if (fieldError.value) return
  submitting.value = true
  try {
    await affiliationApi.request(universityId.value)
    sent.value = true
    universityId.value = ''
    await Promise.all([history.load(), eligibility.load()])
  } catch (e) {
    error.value = toApiError(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Vínculo acadêmico</h1>

    <LoadState :loading="eligibility.loading.value" :error="eligibility.error.value" label="Verificando sua validação…" @retry="eligibility.load">
      <AlertMessage v-if="eligibility.data.value" tone="success">
        <strong>Você está validado.</strong> Seu vínculo acadêmico está aprovado e vigente, então já pode se candidatar a vagas.
      </AlertMessage>
      <AlertMessage v-else tone="warning">
        <strong>Você ainda não está validado.</strong> Só é possível se candidatar com um vínculo acadêmico aprovado e vigente. Solicite o vínculo abaixo e aguarde a decisão da universidade.
      </AlertMessage>
    </LoadState>

    <SectionCard title="Solicitar vínculo" description="Escolha a universidade em que você estuda. Ela analisa o pedido e decide.">
      <LoadState :loading="universities.loading.value || history.loading.value" :error="universities.error.value ?? history.error.value" label="Carregando…" @retry="() => { universities.load(); history.load() }">
        <AlertMessage v-if="open" tone="info">
          <template v-if="open.status === 'PENDING'">Você já tem um pedido aguardando análise de {{ open.university.name }}. Aguarde a decisão para pedir outro vínculo.</template>
          <template v-else>Seu vínculo com {{ open.university.name }} está aprovado. Para trocar de universidade, peça a revogação a ela.</template>
        </AlertMessage>
        <EmptyState v-else-if="!options.length" title="Nenhuma universidade ativa" description="Ainda não há universidades aprovadas para receber pedidos de vínculo." />
        <form v-else class="space-y-4" novalidate @submit.prevent="submit">
          <AlertMessage v-if="error" tone="error">{{ error.message }}</AlertMessage>
          <AlertMessage v-if="sent" tone="success">Pedido enviado. A universidade vai analisar e você verá a decisão no histórico abaixo.</AlertMessage>
          <SelectField v-model="universityId" label="Universidade" name="universityId" placeholder="Selecione…" :options="options" required :error="fieldError" />
          <BaseButton type="submit" :loading="submitting">{{ submitting ? 'Enviando…' : 'Solicitar vínculo' }}</BaseButton>
        </form>
      </LoadState>
    </SectionCard>

    <SectionCard title="Histórico de pedidos" description="Do mais recente para o mais antigo.">
      <LoadState :loading="history.loading.value" :error="history.error.value" label="Carregando histórico…" @retry="history.load">
        <EmptyState v-if="!history.data.value?.length" title="Nenhum pedido ainda" description="Quando você solicitar um vínculo, ele aparece aqui com a decisão da universidade." />
        <ul v-else class="divide-y divide-line">
          <li v-for="a in history.data.value" :key="a.id" class="space-y-1 py-4 first:pt-0 last:pb-0">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="font-medium">{{ a.university.name }}<span v-if="a.university.acronym" class="text-muted"> ({{ a.university.acronym }})</span></p>
              <StatusBadge :tone="AFFILIATION_STATUS_TONE[a.status]">{{ AFFILIATION_STATUS_LABEL[a.status] }}</StatusBadge>
            </div>
            <p class="text-sm text-muted">Solicitado em {{ formatDate(a.requestedAt) }}<template v-if="a.decidedAt"> · decidido em {{ formatDate(a.decidedAt) }}</template><template v-if="a.revokedAt"> · revogado em {{ formatDate(a.revokedAt) }}</template></p>
            <p v-if="a.decisionReason" class="text-sm"><span class="text-muted">Motivo da decisão:</span> {{ a.decisionReason }}</p>
            <p v-if="a.revocationReason" class="text-sm"><span class="text-muted">Motivo da revogação:</span> {{ a.revocationReason }}</p>
          </li>
        </ul>
      </LoadState>
    </SectionCard>
  </div>
</template>
