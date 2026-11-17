<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { toApiError } from '@/shared/api/apiError'
import { forCompany } from '@/features/companies/errors'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ConfirmPanel from '@/shared/ui/ConfirmPanel.vue'
import TextField from '@/shared/ui/TextField.vue'
import { companyApplicationApi, type Reactivation, type StatusChange } from '../api/companyApplicationApi'
import { APPLICATION_STATUS_LABEL } from '../labels'
import { REASON_MAX, availableActions, canReactivate, validateDecision, type DecisionStatus } from '../statusRules'
import type { ApplicationStatus } from '../types'

const props = defineProps<{ applicationId: string; status: ApplicationStatus }>()
const emit = defineEmits<{ changed: [result: StatusChange | Reactivation] }>()

const ACTION_LABEL: Record<DecisionStatus, string> = { IN_REVIEW: 'Iniciar análise', APPROVED: 'Aprovar', REJECTED: 'Rejeitar' }
const actions = computed(() => availableActions(props.status))

const pending = ref<DecisionStatus | 'REACTIVATE' | null>(null)
const reason = ref('')
const reasonError = ref('')
const error = ref('')
const loading = ref(false)
const root = ref<HTMLElement | null>(null)

function open(target: DecisionStatus | 'REACTIVATE') {
  pending.value = target
  reason.value = ''
  reasonError.value = ''
  error.value = ''
}
const close = () => { pending.value = null }

async function run(target: DecisionStatus | 'REACTIVATE') {
  error.value = ''
  if (target !== 'REACTIVATE') {
    reasonError.value = validateDecision(target, reason.value) ?? ''
    if (reasonError.value) {
      await nextTick()
      root.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
      return
    }
  }
  loading.value = true
  try {
    const result = target === 'REACTIVATE'
      ? await companyApplicationApi.reactivate(props.applicationId)
      : await companyApplicationApi.changeStatus(props.applicationId, target, reason.value)
    pending.value = null
    emit('changed', result)
  } catch (e) {
    error.value = forCompany(toApiError(e))!.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div ref="root" class="space-y-3">
    <p v-if="status === 'APPROVED' || status === 'REJECTED'" class="text-sm text-muted">Decisão final: {{ APPLICATION_STATUS_LABEL[status].toLowerCase() }}. Não há novas ações para esta candidatura.</p>

    <template v-if="actions.length">
      <div class="flex flex-wrap gap-2" role="group" aria-label="Atualizar situação da candidatura">
        <BaseButton v-if="actions.includes('IN_REVIEW')" variant="secondary" :loading="loading" @click="run('IN_REVIEW')">{{ ACTION_LABEL.IN_REVIEW }}</BaseButton>
        <BaseButton v-if="actions.includes('APPROVED')" variant="secondary" @click="open('APPROVED')">{{ ACTION_LABEL.APPROVED }}</BaseButton>
        <BaseButton v-if="actions.includes('REJECTED')" variant="secondary" @click="open('REJECTED')">{{ ACTION_LABEL.REJECTED }}</BaseButton>
      </div>
      <p v-if="error" role="alert" class="text-sm text-danger">{{ error }}</p>
    </template>

    <ConfirmPanel
      v-if="pending === 'APPROVED' || pending === 'REJECTED'"
      :title="pending === 'APPROVED' ? 'Aprovar esta candidatura?' : 'Rejeitar esta candidatura?'"
      message="A decisão é final e não poderá ser desfeita."
      :confirm-label="pending === 'APPROVED' ? 'Confirmar aprovação' : 'Confirmar rejeição'"
      :danger="pending === 'REJECTED'"
      :loading="loading"
      :error="error"
      @confirm="run(pending as DecisionStatus)"
      @cancel="close"
    >
      <TextField v-model="reason" :label="pending === 'REJECTED' ? 'Motivo da rejeição' : 'Motivo (opcional)'" name="reason" multiline :required="pending === 'REJECTED'" :maxlength="REASON_MAX" :error="reasonError" :hint="`${reason.length} de ${REASON_MAX} caracteres.`" />
    </ConfirmPanel>

    <template v-if="canReactivate(status)">
      <p class="text-sm">
        Suspensa porque o vínculo acadêmico do estudante deixou de valer. A reativação é uma ação sua e só funciona depois que o estudante recupera um vínculo aprovado e vigente, com a vaga aberta e a conta ativa.
      </p>
      <BaseButton variant="secondary" @click="open('REACTIVATE')">Reativar candidatura</BaseButton>
      <ConfirmPanel
        v-if="pending === 'REACTIVATE'"
        title="Reativar esta candidatura?"
        message="Ela volta exatamente ao estado de antes da suspensão. O backend confere a validação acadêmica atual, a vaga e a conta antes de reativar."
        confirm-label="Confirmar reativação"
        :loading="loading"
        :error="error"
        @confirm="run('REACTIVATE')"
        @cancel="close"
      />
    </template>
  </div>
</template>
