<script setup lang="ts">
import { computed, ref } from 'vue'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ReasonConfirmPanel from '@/shared/ui/ReasonConfirmPanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { adminApi } from '@/features/administration/api/adminApi'
import { affiliationDecisionApi } from '../api/affiliationDecisionApi'
import { REASON_MAX, availableActions, validateReason, type DecisionKind } from '../decisionRules'
import { AFFILIATION_STATUS_LABEL, AFFILIATION_STATUS_TONE } from '../labels'
import type { ReceivedAffiliation } from '../types'

// `scope="admin"`: mesmas regras de estado, mas pelas rotas /admin/affiliations (motivo sempre obrigatório, inclusive ao aprovar).
const props = withDefaults(defineProps<{ request: ReceivedAffiliation; headingTag?: 'h2' | 'h3'; scope?: 'university' | 'admin' }>(), { headingTag: 'h2', scope: 'university' })
const emit = defineEmits<{ decided: [payload: { kind: DecisionKind; studentName: string }]; refresh: [] }>()

const admin = computed(() => props.scope === 'admin')
const actions = computed(() => availableActions(props.request.status))
const name = computed(() => props.request.student.fullName)

const PANEL: Record<DecisionKind, { title: (n: string) => string; message: string; confirm: string; field: string; danger: boolean }> = {
  APPROVE: {
    title: (n) => `Aprovar o vínculo de ${n}?`,
    message: 'O estudante passa a estar validado e poderá se candidatar a vagas. A decisão fica registrada no histórico.',
    confirm: 'Confirmar aprovação', field: 'Observação', danger: false,
  },
  REJECT: {
    title: (n) => `Rejeitar a solicitação de ${n}?`,
    message: 'O motivo é exibido ao estudante e a solicitação fica no histórico.',
    confirm: 'Confirmar rejeição', field: 'Motivo da rejeição', danger: true,
  },
  REVOKE: {
    title: (n) => `Revogar o vínculo de ${n}?`,
    message:
      'A revogação elimina a elegibilidade acadêmica do estudante e suspende automaticamente as candidaturas dele que estão em andamento. O histórico é mantido e o vínculo não volta sozinho: o estudante precisará pedir um novo. O motivo é exibido ao estudante.',
    confirm: 'Confirmar revogação', field: 'Motivo da revogação', danger: true,
  },
}
const BUTTON_LABEL: Record<DecisionKind, string> = { APPROVE: 'Aprovar vínculo', REJECT: 'Rejeitar solicitação', REVOKE: 'Revogar vínculo' }

const pending = ref<DecisionKind | null>(null)
const error = ref<ApiError | null>(null)
const loading = ref(false)
const stale = computed(() => ['AFFILIATION_ALREADY_DECIDED', 'AFFILIATION_NOT_APPROVED', 'AFFILIATION_NOT_FOUND'].includes(error.value?.code ?? ''))

function open(kind: DecisionKind) {
  pending.value = kind
  error.value = null
}

async function confirm(reason: string) {
  const kind = pending.value
  if (!kind) return
  error.value = null
  loading.value = true
  try {
    const id = props.request.id
    const api = admin.value
      ? { APPROVE: adminApi.approveAffiliation, REJECT: adminApi.rejectAffiliation, REVOKE: adminApi.revokeAffiliation }
      : { APPROVE: affiliationDecisionApi.approve, REJECT: affiliationDecisionApi.reject, REVOKE: affiliationDecisionApi.revoke }
    await api[kind](id, reason)
    pending.value = null
    // Sem atualização otimista: quem manda é o backend; o pai recarrega a lista a partir dele.
    emit('decided', { kind, studentName: name.value })
  } catch (e) {
    error.value = toApiError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <article class="space-y-3 rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <div class="min-w-0">
        <component :is="headingTag" class="text-lg font-semibold">{{ name }}</component>
        <p class="text-sm text-muted">{{ request.student.course || 'Curso não informado' }}</p>
        <p v-if="request.student.institution" class="text-sm text-muted">Instituição informada pelo estudante: {{ request.student.institution }}</p>
        <p v-if="admin" class="text-sm text-muted">Universidade: {{ request.university.name }}<template v-if="request.university.acronym"> ({{ request.university.acronym }})</template></p>
      </div>
      <StatusBadge :tone="AFFILIATION_STATUS_TONE[request.status]">{{ AFFILIATION_STATUS_LABEL[request.status] }}</StatusBadge>
    </div>

    <p class="text-sm text-muted">
      Solicitado em {{ formatDate(request.requestedAt) }}<template v-if="request.decidedAt"> · decidido em {{ formatDate(request.decidedAt) }}</template><template v-if="request.revokedAt"> · revogado em {{ formatDate(request.revokedAt) }}</template>
    </p>
    <p v-if="request.decisionReason" class="text-sm"><span class="text-muted">Motivo da decisão:</span> {{ request.decisionReason }}</p>
    <p v-if="request.revocationReason" class="text-sm"><span class="text-muted">Motivo da revogação:</span> {{ request.revocationReason }}</p>

    <div v-if="actions.length" class="flex flex-wrap gap-2" role="group" :aria-label="`Decisões sobre ${name}`">
      <BaseButton v-for="kind in actions" :key="kind" variant="secondary" :aria-expanded="pending === kind" @click="open(kind)">{{ BUTTON_LABEL[kind] }}</BaseButton>
    </div>
    <p v-else class="text-sm text-muted">Registro mantido no histórico. Não há novas ações para esta solicitação.</p>

    <ReasonConfirmPanel
      v-if="pending"
      :title="PANEL[pending].title(name)"
      :message="PANEL[pending].message"
      :confirm-label="PANEL[pending].confirm"
      :reason-label="PANEL[pending].field"
      :required="pending !== 'APPROVE' || admin"
      :danger="PANEL[pending].danger"
      :validate="(r: string) => validateReason(pending!, r, admin)"
      :maxlength="REASON_MAX"
      :loading="loading"
      :error="error?.message"
      @confirm="confirm"
      @cancel="pending = null"
    />
    <BaseButton v-if="pending && stale" variant="secondary" @click="pending = null; $emit('refresh')">Atualizar lista</BaseButton>
  </article>
</template>
