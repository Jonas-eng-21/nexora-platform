<script setup lang="ts">
import { RouterView } from 'vue-router'
import BrandMark from '@/shared/ui/BrandMark.vue'
import ThemeToggle from '@/shared/ui/ThemeToggle.vue'
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <header class="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-4">
      <BrandMark />
      <ThemeToggle />
    </header>
    <main id="conteudo" class="mx-auto w-full max-w-lg flex-1 px-4 pb-12 pt-4 sm:pt-10">
      <RouterView />
    </main>
  </div>
</template>
