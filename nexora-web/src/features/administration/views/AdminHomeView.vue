<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import EmptyState from '@/shared/ui/EmptyState.vue'
import LoadState from '@/shared/ui/LoadState.vue'
import SectionCard from '@/shared/ui/SectionCard.vue'
import { adminApi } from '../api/adminApi'
import AdminUniversityCard from '../components/AdminUniversityCard.vue'

const universities = useLoader(() => adminApi.listUniversities('PENDING', 0, 5))
// Só o total importa: size=1 evita trazer registros que a tela não mostra.
const affiliations = useLoader(() => adminApi.listAffiliations('PENDING', 0, 1))
const suspended = useLoader(() => adminApi.listUsers('', 'SUSPENDED', 0, 1))

const notice = ref('')
async function decided({ kind, name }: { kind: 'APPROVE' | 'REJECT'; name: string }) {
  notice.value = kind === 'APPROVE' ? `${name} aprovada. Ela já pode entrar na plataforma.` : `Cadastro de ${name} rejeitado.`
  await universities.load()
}
const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Início</h1>

    <SectionCard title="Universidades aguardando aprovação" description="Uma universidade só entra e valida estudantes depois de aprovada. As mais antigas primeiro.">
      <p v-if="notice" role="status" class="mb-4 rounded-control border border-success bg-success-soft px-4 py-3 text-sm">{{ notice }}</p>
      <LoadState :loading="universities.loading.value" :error="universities.error.value" label="Carregando cadastros…" @retry="universities.load">
        <EmptyState v-if="!universities.data.value?.content.length" title="Nenhum cadastro aguardando aprovação" description="Quando uma universidade se cadastrar, o pedido aparece aqui." />
        <template v-else>
          <p class="mb-4 text-sm" role="status">{{ plural(universities.data.value.totalElements, 'cadastro aguardando aprovação', 'cadastros aguardando aprovação') }}.</p>
          <ul class="space-y-4">
            <li v-for="u in universities.data.value.content" :key="u.id"><AdminUniversityCard :university="u" heading-tag="h3" @decided="decided" @refresh="universities.load" /></li>
          </ul>
          <p v-if="universities.data.value.totalElements > universities.data.value.content.length" class="mt-4 text-sm text-muted">Mostrando {{ universities.data.value.content.length }} de {{ universities.data.value.totalElements }}.</p>
        </template>
      </LoadState>
    </SectionCard>

    <SectionCard title="Outras pendências">
      <ul class="divide-y divide-line">
        <li class="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0">
          <p class="text-sm">
            <template v-if="affiliations.loading.value">Verificando solicitações de vínculo…</template>
            <template v-else-if="affiliations.error.value">Não foi possível contar as solicitações de vínculo.</template>
            <template v-else>{{ plural(affiliations.data.value?.totalElements ?? 0, 'solicitação de vínculo aguardando análise', 'solicitações de vínculo aguardando análise') }}.</template>
          </p>
          <RouterLink :to="{ name: 'admin-affiliations' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver vínculos</RouterLink>
        </li>
        <li class="flex flex-wrap items-center justify-between gap-3 py-3 last:pb-0">
          <p class="text-sm">
            <template v-if="suspended.loading.value">Verificando contas suspensas…</template>
            <template v-else-if="suspended.error.value">Não foi possível contar as contas suspensas.</template>
            <template v-else>{{ plural(suspended.data.value?.totalElements ?? 0, 'conta suspensa', 'contas suspensas') }}.</template>
          </p>
          <RouterLink :to="{ name: 'admin-users', query: { situacao: 'SUSPENDED' } }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Ver contas suspensas</RouterLink>
        </li>
      </ul>
    </SectionCard>

    <nav aria-label="Atalhos administrativos" class="flex flex-wrap gap-x-5">
      <RouterLink :to="{ name: 'admin-users' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Contas</RouterLink>
      <RouterLink :to="{ name: 'admin-universities' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Universidades</RouterLink>
      <RouterLink :to="{ name: 'admin-jobs' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Vagas e intervenções</RouterLink>
      <RouterLink :to="{ name: 'admin-audit' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Auditoria</RouterLink>
    </nav>
  </div>
</template>
