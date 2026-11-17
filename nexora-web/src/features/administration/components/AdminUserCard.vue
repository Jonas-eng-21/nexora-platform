<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { formatDate } from '@/shared/format'
import BaseButton from '@/shared/ui/BaseButton.vue'
import ReasonConfirmPanel from '@/shared/ui/ReasonConfirmPanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { ROLE_LABEL } from '@/features/authentication/roles'
import { useSessionStore } from '@/features/authentication/stores/session'
import type { UserAccount } from '@/features/authentication/types'
import { adminApi } from '../api/adminApi'
import { ACCOUNT_STATUS_LABEL, ACCOUNT_STATUS_TONE } from '../labels'
import { suspensionConsequence, userActions, validateAdminReason, type UserAction } from '../rules'

const props = withDefaults(defineProps<{ user: UserAccount; headingTag?: 'h2' | 'h3'; detailLink?: boolean }>(), { headingTag: 'h2', detailLink: false })
const emit = defineEmits<{ changed: [payload: { action: UserAction; email: string }]; refresh: [] }>()

const session = useSessionStore()
const isSelf = computed(() => props.user.id === session.user?.id)
const actions = computed(() => userActions(props.user, session.user?.id ?? null))
const pending = ref<UserAction | null>(null)
const error = ref<ApiError | null>(null)
const loading = ref(false)
const stale = computed(() => ['INVALID_STATUS_TRANSITION', 'USER_NOT_FOUND', 'CANNOT_SUSPEND_SELF'].includes(error.value?.code ?? ''))

function open(action: UserAction) {
  pending.value = action
  error.value = null
}

async function confirm(reason: string) {
  const action = pending.value
  if (!action) return
  error.value = null
  loading.value = true
  try {
    if (action === 'SUSPEND') await adminApi.suspendUser(props.user.id, reason)
    else await adminApi.reactivateUser(props.user.id, reason)
    pending.value = null
    emit('changed', { action, email: props.user.email })
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
        <component :is="headingTag" class="break-all text-lg font-semibold">{{ user.email }}</component>
        <p class="text-sm text-muted">{{ ROLE_LABEL[user.role] }} · criada em {{ formatDate(user.createdAt) }}</p>
      </div>
      <StatusBadge :tone="ACCOUNT_STATUS_TONE[user.status]">{{ ACCOUNT_STATUS_LABEL[user.status] }}</StatusBadge>
    </div>

    <p v-if="isSelf" class="text-sm text-muted">Esta é a sua conta: ela não pode ser suspensa por você.</p>
    <p v-else-if="user.status === 'PENDING_APPROVAL'" class="text-sm text-muted">Aguardando aprovação do cadastro. A decisão é feita em <RouterLink to="/admin/universidades" class="font-medium text-primary underline underline-offset-4">Universidades</RouterLink>, não por aqui.</p>
    <p v-else-if="user.status === 'REJECTED'" class="text-sm text-muted">Cadastro rejeitado. Não há ações de conta para ela.</p>

    <div v-if="actions.length" class="flex flex-wrap gap-2" role="group" :aria-label="`Ações sobre ${user.email}`">
      <BaseButton v-if="actions.includes('SUSPEND')" variant="secondary" :aria-expanded="pending === 'SUSPEND'" @click="open('SUSPEND')">Suspender conta</BaseButton>
      <BaseButton v-if="actions.includes('REACTIVATE')" variant="secondary" :aria-expanded="pending === 'REACTIVATE'" @click="open('REACTIVATE')">Reativar conta</BaseButton>
    </div>
    <RouterLink v-if="detailLink" :to="{ name: 'admin-user', params: { userId: user.id } }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver conta</RouterLink>

    <ReasonConfirmPanel
      v-if="pending"
      :title="pending === 'SUSPEND' ? `Suspender a conta de ${user.email}?` : `Reativar a conta de ${user.email}?`"
      :message="pending === 'SUSPEND' ? suspensionConsequence(user.role) : 'A conta volta a ficar ativa. Se for uma universidade, as candidaturas suspensas por causa da suspensão não são reativadas automaticamente. A ação fica registrada na auditoria.'"
      :confirm-label="pending === 'SUSPEND' ? 'Confirmar suspensão' : 'Confirmar reativação'"
      reason-label="Motivo"
      :danger="pending === 'SUSPEND'"
      :validate="validateAdminReason"
      :loading="loading"
      :error="error?.message"
      @confirm="confirm"
      @cancel="pending = null"
    />
    <BaseButton v-if="pending && stale" variant="secondary" @click="pending = null; $emit('refresh')">Atualizar lista</BaseButton>
  </article>
</template>
