<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import { formatDateTime } from '@/shared/format'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import { useSessionStore } from '@/features/authentication/stores/session'
import { adminApi } from '../api/adminApi'
import { ACTION_LABEL, TARGET_LABEL } from '../labels'
import type { AuditLog } from '../types'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))

const list = useLoader(() => adminApi.listAuditLogs(page.value), { immediate: false })
watch(
  () => (route.name === 'admin-audit' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)
const setPage = (p: number) => router.push({ name: 'admin-audit', query: { pagina: String(p) } })

// Só alvos que têm tela própria viram link; os demais mostram o identificador.
function targetLink(log: AuditLog) {
  if (log.targetType === 'JOB') return { name: 'admin-job', params: { jobId: log.targetId } }
  if (log.targetType === 'COMPANY') return { name: 'admin-company', params: { companyId: log.targetId } }
  if (log.targetType === 'USER') return { name: 'admin-user', params: { userId: log.targetId } }
  return null
}
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Auditoria</h1>
    <p class="text-sm text-muted">Ações administrativas, da mais recente para a mais antiga. Os registros são criados pelo backend e não podem ser alterados aqui.</p>

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando registros…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" title="Nenhuma ação registrada" description="As intervenções administrativas aparecem aqui assim que forem feitas." />
      <template v-else>
        <ol class="space-y-3">
          <li v-for="log in list.data.value.content" :key="log.id" class="space-y-1 rounded-card border border-line bg-surface p-4">
            <div class="flex flex-wrap items-baseline justify-between gap-x-3">
              <h2 class="font-semibold">{{ ACTION_LABEL[log.action] }}</h2>
              <time :datetime="log.createdAt" class="text-sm tabular-nums text-muted">{{ formatDateTime(log.createdAt) }}</time>
            </div>
            <p class="break-all text-sm">
              <span class="text-muted">{{ TARGET_LABEL[log.targetType] }}:</span>{{ ' ' }}<RouterLink v-if="targetLink(log)" :to="targetLink(log)!" class="font-medium text-primary underline underline-offset-4">{{ log.targetId }}</RouterLink>
              <template v-else> {{ log.targetId }}</template>
            </p>
            <p class="break-all text-sm"><span class="text-muted">Administrador:</span> {{ log.adminId }}<span v-if="log.adminId === session.user?.id"> (você)</span></p>
            <p v-if="log.reason" class="text-sm"><span class="text-muted">Motivo:</span> {{ log.reason }}</p>
            <p v-if="log.previousValue || log.newValue" class="break-words text-sm">
              <span class="text-muted">Alteração:</span> {{ log.previousValue ?? '—' }} → {{ log.newValue ?? '—' }}
            </p>
          </li>
        </ol>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
