<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'error' | 'success' | 'info' | 'warning' }>(), { tone: 'info' })
</script>

<template>
  <!-- Erros são anunciados de imediato (alert); os demais, de forma educada (status). -->
  <div
    :role="tone === 'error' ? 'alert' : 'status'"
    class="rounded-control border px-4 py-3 text-sm"
    :class="{
      'border-danger bg-danger-soft text-fg': tone === 'error',
      'border-success bg-success-soft text-fg': tone === 'success',
      'border-warning bg-warning-soft text-fg': tone === 'warning',
      'border-line-strong bg-primary-soft text-fg': tone === 'info',
    }"
  >
    <slot />
  </div>
</template>
