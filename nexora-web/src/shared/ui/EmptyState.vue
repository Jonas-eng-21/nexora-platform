<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="rounded-card border border-dashed border-line-strong p-6 text-center" role="status">
    <p class="font-semibold">{{ title }}</p>
    <p v-if="description" class="mx-auto mt-1 max-w-prose text-sm text-muted">{{ description }}</p>
    <div class="mt-4 flex justify-center"><slot /></div>
  </div>
</template>
