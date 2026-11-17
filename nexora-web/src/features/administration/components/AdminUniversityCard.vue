<script setup lang="ts">
import { computed, ref } from 'vue'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ReasonConfirmPanel from '@/shared/ui/ReasonConfirmPanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { adminApi } from '../api/adminApi'
import { ACCOUNT_STATUS_LABEL, APPROVAL_LABEL, APPROVAL_TONE } from '../labels'
import { REASON_MAX, universityActions, validateAdminReason } from '../rules'
import type { AdminUniversity } from '../types'

const props = withDefaults(defineProps<{ university: AdminUniversity; headingTag?: 'h2' | 'h3' }>(), { headingTag: 'h2' })
const emit = defineEmits<{ decided: [payload: { kind: 'APPROVE' | 'REJECT'; name: string }]; refresh: [] }>()

const actions = computed(() => universityActions(props.university.approvalStatus))
const pending = ref<'APPROVE' | 'REJECT' | null>(null)
const error = ref<ApiError | null>(null)
const loading = ref(false)
const stale = computed(() => ['UNIVERSITY_ALREADY_DECIDED', 'UNIVERSITY_NOT_FOUND'].includes(error.value?.code ?? ''))

function open(kind: 'APPROVE' | 'REJECT') {
  pending.value = kind
  error.value = null
}

async function confirm(reason: string) {
  const kind = pending.value
  if (!kind) return
  error.value = null
  loading.value = true
  try {
    if (kind === 'APPROVE') await adminApi.approveUniversity(props.university.id, reason)
    else await adminApi.rejectUniversity(props.university.id, reason)
    pending.value = null
    // O backend é a fonte de verdade: o pai recarrega a lista, sem atualização otimista.
    emit('decided', { kind, name: props.university.name })
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
        <component :is="headingTag" class="text-lg font-semibold">{{ university.name }}<span v-if="university.acronym" class="font-normal text-muted"> ({{ university.acronym }})</span></component>
        <p class="text-sm text-muted">{{ university.location || 'Localização não informada' }}</p>
      </div>
      <StatusBadge :tone="APPROVAL_TONE[university.approvalStatus]">{{ APPROVAL_LABEL[university.approvalStatus] }}</StatusBadge>
    </div>

    <dl class="space-y-1 text-sm">
      <div><dt class="inline text-muted">E-mail da conta: </dt><dd class="inline break-all">{{ university.email }}</dd></div>
      <div><dt class="inline text-muted">Situação da conta: </dt><dd class="inline">{{ ACCOUNT_STATUS_LABEL[university.accountStatus] }}</dd></div>
      <div v-if="university.website"><dt class="inline text-muted">Site: </dt><dd class="inline break-all">{{ university.website }}</dd></div>
      <div><dt class="inline text-muted">Cadastrada em: </dt><dd class="inline">{{ formatDate(university.createdAt) }}</dd></div>
    </dl>
    <p v-if="university.description" class="whitespace-pre-line text-sm">{{ university.description }}</p>
    <p v-if="university.approvalDecidedAt" class="text-sm text-muted">Decisão em {{ formatDate(university.approvalDecidedAt) }}<template v-if="university.approvalReason"> · Motivo: {{ university.approvalReason }}</template></p>

    <div v-if="actions.length" class="flex flex-wrap gap-2" role="group" :aria-label="`Decisões sobre ${university.name}`">
      <BaseButton variant="secondary" :aria-expanded="pending === 'APPROVE'" @click="open('APPROVE')">Aprovar universidade</BaseButton>
      <BaseButton variant="secondary" :aria-expanded="pending === 'REJECT'" @click="open('REJECT')">Rejeitar cadastro</BaseButton>
    </div>
    <p v-else class="text-sm text-muted">Cadastro já analisado. Não há novas decisões possíveis para ele.</p>

    <ReasonConfirmPanel
      v-if="pending"
      :title="pending === 'APPROVE' ? `Aprovar ${university.name}?` : `Rejeitar o cadastro de ${university.name}?`"
      :message="pending === 'APPROVE' ? 'A conta passa a ficar ativa: a universidade poderá entrar na plataforma e validar estudantes. A decisão é auditada.' : 'A conta é rejeitada e a universidade não poderá entrar nem validar estudantes. O motivo fica registrado. A decisão é auditada.'"
      :confirm-label="pending === 'APPROVE' ? 'Confirmar aprovação' : 'Confirmar rejeição'"
      :reason-label="pending === 'APPROVE' ? 'Observação' : 'Motivo da rejeição'"
      :required="pending === 'REJECT'"
      :danger="pending === 'REJECT'"
      :validate="(r: string) => (pending === 'REJECT' ? validateAdminReason(r) : r.length > REASON_MAX ? `O motivo pode ter até ${REASON_MAX} caracteres.` : null)"
      :loading="loading"
      :error="error?.message"
      @confirm="confirm"
      @cancel="pending = null"
    />
    <BaseButton v-if="pending && stale" variant="secondary" @click="pending = null; $emit('refresh')">Atualizar lista</BaseButton>
  </article>
</template>
