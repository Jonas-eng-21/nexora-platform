<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useSessionStore } from '@/features/authentication/stores/session'

const session = useSessionStore()
</script>

<template>
  <section class="max-w-lg space-y-4">
    <h1 class="text-2xl font-semibold">Acesso negado</h1>
    <p class="text-muted">Seu perfil não tem permissão para abrir esta área.</p>
    <RouterLink :to="session.homePath" class="font-medium text-primary underline underline-offset-4">
      Ir para a minha área
    </RouterLink>
  </section>
</template>
