<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import BaseButton from '@/shared/ui/BaseButton.vue'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import Pagination from '@/shared/ui/Pagination.vue'
import SelectField from '@/shared/ui/SelectField.vue'
import { ROLE_LABEL } from '@/features/authentication/roles'
import type { AccountStatus, UserRole } from '@/features/authentication/types'
import { adminApi } from '../api/adminApi'
import AdminUserCard from '../components/AdminUserCard.vue'
import { ACCOUNT_STATUS_LABEL } from '../labels'

const route = useRoute()
const router = useRouter()

const ROLE_OPTIONS = (Object.keys(ROLE_LABEL) as UserRole[]).map((value) => ({ value, label: ROLE_LABEL[value] }))
const STATUS_OPTIONS = (Object.keys(ACCOUNT_STATUS_LABEL) as AccountStatus[]).map((value) => ({ value, label: ACCOUNT_STATUS_LABEL[value] }))
const pick = <T extends string>(key: string, options: { value: string }[]): T | '' => {
  const v = route.query[key]
  return typeof v === 'string' && options.some((o) => o.value === v) ? (v as T) : ''
}
const role = computed(() => pick<UserRole>('papel', ROLE_OPTIONS))
const status = computed(() => pick<AccountStatus>('situacao', STATUS_OPTIONS))
const page = computed(() => Math.max(0, Number.parseInt(String(route.query.pagina ?? ''), 10) || 0))
const filtered = computed(() => role.value !== '' || status.value !== '')

const list = useLoader(() => adminApi.listUsers(role.value, status.value, page.value), { immediate: false })
watch(
  () => (route.name === 'admin-users' ? route.fullPath : null),
  (path) => { if (path !== null) void list.load() },
  { immediate: true },
)

const notice = ref('')
async function changed({ action, email }: { action: 'SUSPEND' | 'REACTIVATE'; email: string }) {
  notice.value = action === 'SUSPEND' ? `Conta de ${email} suspensa.` : `Conta de ${email} reativada.`
  await list.load()
}
const go = (query: Record<string, string>) => { notice.value = ''; return router.push({ name: 'admin-users', query }) }
const setRole = (v: string) => go({ ...(v ? { papel: v } : {}), ...(status.value ? { situacao: status.value } : {}) })
const setStatus = (v: string) => go({ ...(role.value ? { papel: role.value } : {}), ...(v ? { situacao: v } : {}) })
const setPage = (p: number) => router.push({ name: 'admin-users', query: { ...route.query, pagina: String(p) } })
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <h1 class="text-2xl font-semibold">Contas</h1>
    <p v-if="notice" role="status" class="rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>
    <div class="grid gap-4 sm:grid-cols-2">
      <SelectField :model-value="role" label="Perfil" name="papel" placeholder="Todos" :options="ROLE_OPTIONS" :show-optional="false" @update:model-value="setRole" />
      <SelectField :model-value="status" label="Situação" name="situacao" placeholder="Todas" :options="STATUS_OPTIONS" :show-optional="false" @update:model-value="setStatus" />
    </div>

    <LoadState :loading="list.loading.value" :error="list.error.value" label="Carregando contas…" @retry="list.load">
      <EmptyState v-if="!list.data.value?.content.length" :title="filtered ? 'Nenhuma conta com estes filtros' : 'Nenhuma conta encontrada'" :description="filtered ? 'Limpe os filtros para ver todas as contas.' : undefined">
        <BaseButton v-if="filtered" variant="secondary" @click="go({})">Limpar filtros</BaseButton>
      </EmptyState>
      <template v-else>
        <ul class="space-y-4">
          <li v-for="u in list.data.value.content" :key="u.id"><AdminUserCard :user="u" detail-link @changed="changed" @refresh="list.load" /></li>
        </ul>
        <Pagination class="mt-6" :page="list.data.value.page" :total-pages="list.data.value.totalPages" @update:page="setPage" />
      </template>
    </LoadState>
  </div>
</template>
