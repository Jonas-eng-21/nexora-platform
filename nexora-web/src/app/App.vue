<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <a
    href="#conteudo"
    class="sr-only rounded-control bg-surface px-4 py-2 focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-10"
  >
    Pular para o conteúdo
  </a>
  <RouterView />
</template>
