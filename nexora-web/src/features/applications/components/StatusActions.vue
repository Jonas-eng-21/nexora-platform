<script setup lang="ts">
import { computed, ref } from 'vue'
import { toApiError } from '@/shared/api/apiError'
import { forCompany } from '@/features/companies/errors'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ConfirmPanel from '@/shared/ui/ConfirmPanel.vue'
import ReasonConfirmPanel from '@/shared/ui/ReasonConfirmPanel.vue'
import { adminApi } from '@/features/administration/api/adminApi'
import { companyApplicationApi, type Reactivation, type StatusChange } from '../api/companyApplicationApi'
import { APPLICATION_STATUS_LABEL } from '../labels'
import { REASON_MAX, availableActions, canReactivate, validateDecision, type DecisionStatus } from '../statusRules'
import type { ApplicationStatus } from '../types'

// `scope="admin"` usa as rotas /admin/applications (mesmas transições, com auditoria no backend).
const props = withDefaults(defineProps<{ applicationId: string; status: ApplicationStatus; scope?: 'company' | 'admin' }>(), { scope: 'company' })
const emit = defineEmits<{ changed: [result: StatusChange | Reactivation] }>()

const ACTION_LABEL: Record<DecisionStatus, string> = { IN_REVIEW: 'Iniciar análise', APPROVED: 'Aprovar', REJECTED: 'Rejeitar' }
const actions = computed(() => availableActions(props.status))

const pending = ref<DecisionStatus | 'REACTIVATE' | null>(null)
const error = ref('')
const loading = ref(false)

function open(target: DecisionStatus | 'REACTIVATE') {
  pending.value = target
  error.value = ''
}
const close = () => { pending.value = null }

async function run(target: DecisionStatus | 'REACTIVATE', reason = '') {
  error.value = ''
  loading.value = true
  try {
    const admin = props.scope === 'admin'
    const result = target === 'REACTIVATE'
      ? await (admin ? adminApi.reactivateApplication(props.applicationId) : companyApplicationApi.reactivate(props.applicationId))
      : await (admin ? adminApi.changeApplicationStatus(props.applicationId, target, reason) : companyApplicationApi.changeStatus(props.applicationId, target, reason))
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
  <div class="space-y-3">
    <p v-if="status === 'APPROVED' || status === 'REJECTED'" class="text-sm text-muted">Decisão final: {{ APPLICATION_STATUS_LABEL[status].toLowerCase() }}. Não há novas ações para esta candidatura.</p>

    <template v-if="actions.length">
      <div class="flex flex-wrap gap-2" role="group" aria-label="Atualizar situação da candidatura">
        <BaseButton v-if="actions.includes('IN_REVIEW')" variant="secondary" :loading="loading" @click="run('IN_REVIEW')">{{ ACTION_LABEL.IN_REVIEW }}</BaseButton>
        <BaseButton v-if="actions.includes('APPROVED')" variant="secondary" @click="open('APPROVED')">{{ ACTION_LABEL.APPROVED }}</BaseButton>
        <BaseButton v-if="actions.includes('REJECTED')" variant="secondary" @click="open('REJECTED')">{{ ACTION_LABEL.REJECTED }}</BaseButton>
      </div>
      <p v-if="error" role="alert" class="text-sm text-danger">{{ error }}</p>
    </template>

    <ReasonConfirmPanel
      v-if="pending === 'APPROVED' || pending === 'REJECTED'"
      :title="pending === 'APPROVED' ? 'Aprovar esta candidatura?' : 'Rejeitar esta candidatura?'"
      message="A decisão é final e não poderá ser desfeita."
      :confirm-label="pending === 'APPROVED' ? 'Confirmar aprovação' : 'Confirmar rejeição'"
      :reason-label="pending === 'REJECTED' ? 'Motivo da rejeição' : 'Motivo'"
      :required="pending === 'REJECTED'"
      :danger="pending === 'REJECTED'"
      :validate="(r: string) => validateDecision(pending as DecisionStatus, r)"
      :maxlength="REASON_MAX"
      :loading="loading"
      :error="error"
      @confirm="(r: string) => run(pending as DecisionStatus, r)"
      @cancel="close"
    />

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
