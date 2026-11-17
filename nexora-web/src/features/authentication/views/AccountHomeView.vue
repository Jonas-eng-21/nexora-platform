<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { ROLE_LABEL, STATUS_LABEL, STATUS_TONE } from '../roles'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const route = useRoute()
const session = useSessionStore()
const title = computed(() => String(route.meta.title ?? 'Minha área'))
const createdAt = computed(() =>
  session.user ? new Date(session.user.createdAt).toLocaleDateString('pt-BR', { dateStyle: 'long' }) : '',
)
</script>

<template>
  <section v-if="session.user" class="max-w-2xl space-y-6">
    <h1 class="text-2xl font-semibold">{{ title }}</h1>

    <div class="rounded-card border border-line bg-surface p-5 shadow-card">
      <h2 class="text-base font-semibold">Dados da conta</h2>
      <dl class="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-2">
        <div>
          <dt class="text-sm text-muted">E-mail</dt>
          <dd class="break-all font-medium">{{ session.user.email }}</dd>
        </div>
        <div>
          <dt class="text-sm text-muted">Perfil</dt>
          <dd class="font-medium">{{ ROLE_LABEL[session.user.role] }}</dd>
        </div>
        <div>
          <dt class="text-sm text-muted">Situação da conta</dt>
          <dd class="mt-0.5">
            <StatusBadge :tone="STATUS_TONE[session.user.status]">{{ STATUS_LABEL[session.user.status] }}</StatusBadge>
          </dd>
        </div>
        <div>
          <dt class="text-sm text-muted">Conta criada em</dt>
          <dd class="font-medium">{{ createdAt }}</dd>
        </div>
      </dl>
    </div>

    <p class="text-sm text-muted">
      Esta área ainda mostra apenas os dados da conta. As funções do seu perfil entram nas próximas etapas do projeto.
    </p>
  </section>
</template>
