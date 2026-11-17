<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger'
    type?: 'button' | 'submit'
    loading?: boolean
    block?: boolean
  }>(),
  { variant: 'primary', type: 'button', loading: false, block: false },
)
</script>

<template>
  <button
    :type="type"
    :disabled="loading"
    :aria-busy="loading"
    class="inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-4 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-70"
    :class="[
      block ? 'w-full' : '',
      variant === 'primary' ? 'bg-primary text-on-primary hover:bg-primary-hover' : '',
      variant === 'secondary' ? 'border border-line-strong bg-surface text-fg hover:bg-primary-soft' : '',
      variant === 'danger' ? 'bg-danger text-canvas hover:opacity-90' : '',
    ]"
  >
    <span
      v-if="loading"
      class="size-4 animate-spin-slow rounded-full border-2 border-current border-t-transparent"
      aria-hidden="true"
    />
    <slot />
  </button>
</template>
