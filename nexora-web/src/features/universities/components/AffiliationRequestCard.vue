<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ConfirmPanel from '@/shared/ui/ConfirmPanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import TextField from '@/shared/ui/TextField.vue'
import { affiliationDecisionApi } from '../api/affiliationDecisionApi'
import { REASON_MAX, availableActions, validateReason, type DecisionKind } from '../decisionRules'
import { AFFILIATION_STATUS_LABEL, AFFILIATION_STATUS_TONE } from '../labels'
import type { ReceivedAffiliation } from '../types'

const props = withDefaults(defineProps<{ request: ReceivedAffiliation; headingTag?: 'h2' | 'h3' }>(), { headingTag: 'h2' })
const emit = defineEmits<{ decided: [payload: { kind: DecisionKind; studentName: string }]; refresh: [] }>()

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

const root = ref<HTMLElement | null>(null)
const pending = ref<DecisionKind | null>(null)
const reason = ref('')
const reasonError = ref('')
const error = ref<ApiError | null>(null)
const loading = ref(false)
const stale = computed(() => ['AFFILIATION_ALREADY_DECIDED', 'AFFILIATION_NOT_APPROVED', 'AFFILIATION_NOT_FOUND'].includes(error.value?.code ?? ''))

function open(kind: DecisionKind) {
  pending.value = kind
  reason.value = ''
  reasonError.value = ''
  error.value = null
}

async function confirm() {
  const kind = pending.value
  if (!kind) return
  error.value = null
  reasonError.value = validateReason(kind, reason.value) ?? ''
  if (reasonError.value) {
    await nextTick()
    root.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    return
  }
  loading.value = true
  try {
    const id = props.request.id
    if (kind === 'APPROVE') await affiliationDecisionApi.approve(id, reason.value)
    else if (kind === 'REJECT') await affiliationDecisionApi.reject(id, reason.value)
    else await affiliationDecisionApi.revoke(id, reason.value)
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
  <article ref="root" class="space-y-3 rounded-card border border-line bg-surface p-5 shadow-card">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <div class="min-w-0">
        <component :is="headingTag" class="text-lg font-semibold">{{ name }}</component>
        <p class="text-sm text-muted">{{ request.student.course || 'Curso não informado' }}</p>
        <p v-if="request.student.institution" class="text-sm text-muted">Instituição informada pelo estudante: {{ request.student.institution }}</p>
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

    <ConfirmPanel
      v-if="pending"
      :title="PANEL[pending].title(name)"
      :message="PANEL[pending].message"
      :confirm-label="PANEL[pending].confirm"
      :danger="PANEL[pending].danger"
      :loading="loading"
      :error="error?.message"
      @confirm="confirm"
      @cancel="pending = null"
    >
      <TextField v-model="reason" :label="PANEL[pending].field" name="reason" multiline :required="pending !== 'APPROVE'" :maxlength="REASON_MAX" :error="reasonError" :hint="`${reason.length} de ${REASON_MAX} caracteres.`" />
    </ConfirmPanel>
    <BaseButton v-if="pending && stale" variant="secondary" @click="pending = null; $emit('refresh')">Atualizar lista</BaseButton>
  </article>
</template>
