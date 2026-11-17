<script setup lang="ts">
// Cor semântica fixa + texto sempre presente: a cor nunca é o único sinal.
defineProps<{ tone: 'success' | 'warning' | 'danger' }>()
</script>

<template>
  <span
    class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-sm font-medium"
    :class="{
      'border-success bg-success-soft text-success': tone === 'success',
      'border-warning bg-warning-soft text-warning': tone === 'warning',
      'border-danger bg-danger-soft text-danger': tone === 'danger',
    }"
  >
    <slot />
  </span>
</template>
