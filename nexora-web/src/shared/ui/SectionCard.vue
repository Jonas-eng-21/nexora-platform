<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <section class="rounded-card border border-line bg-surface p-5 shadow-card sm:p-6">
    <header class="mb-4">
      <h2 class="text-lg font-semibold">{{ title }}</h2>
      <p v-if="description" class="mt-0.5 text-sm text-muted">{{ description }}</p>
    </header>
    <slot />
  </section>
</template>
