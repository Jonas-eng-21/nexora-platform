<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useLoader } from '@/shared/composables/useLoader'
import LoadState from '@/shared/ui/LoadState.vue'
import { adminApi } from '../api/adminApi'
import AdminUserCard from '../components/AdminUserCard.vue'

const route = useRoute()
const userId = computed(() => String(route.params.userId))
const user = useLoader(() => adminApi.getUser(userId.value))
</script>

<template>
  <div class="max-w-3xl space-y-6">
    <RouterLink :to="{ name: 'admin-users' }" class="inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4">Voltar às contas</RouterLink>
    <h1 class="text-2xl font-semibold">Conta</h1>
    <LoadState :loading="user.loading.value" :error="user.error.value" label="Carregando conta…" @retry="user.load">
      <AdminUserCard v-if="user.data.value" :user="user.data.value" heading-tag="h2" @changed="user.load" @refresh="user.load" />
    </LoadState>
  </div>
</template>
