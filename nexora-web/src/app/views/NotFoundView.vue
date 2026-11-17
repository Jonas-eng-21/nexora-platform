<script setup lang="ts">
import { RouterLink } from 'vue-router'
import BrandMark from '@/shared/ui/BrandMark.vue'
</script>

<template>
  <main id="conteudo" class="mx-auto flex min-h-dvh max-w-lg flex-col justify-center gap-4 px-4">
    <BrandMark />
    <h1 class="text-2xl font-semibold">Página não encontrada</h1>
    <p class="text-muted">O endereço que você abriu não existe no Nexora.</p>
    <RouterLink to="/" class="font-medium text-primary underline underline-offset-4">Voltar ao início</RouterLink>
  </main>
</template>
