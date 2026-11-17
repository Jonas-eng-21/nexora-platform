<script setup lang="ts">
import type { ApiError } from '@/shared/api/apiError'
import AlertMessage from './AlertMessage.vue'
import BaseButton from './BaseButton.vue'

withDefaults(defineProps<{ loading: boolean; error: ApiError | null; label?: string }>(), {
  label: 'Carregando…',
})
defineEmits<{ retry: [] }>()
</script>

<template>
  <p v-if="loading" role="status" class="flex items-center gap-2 py-6 text-muted">
    <span class="size-4 animate-spin-slow rounded-full border-2 border-current border-t-transparent" aria-hidden="true" />
    {{ label }}
  </p>
  <div v-else-if="error" class="space-y-3">
    <AlertMessage tone="error">{{ error.message }}</AlertMessage>
    <BaseButton variant="secondary" @click="$emit('retry')">Tentar novamente</BaseButton>
  </div>
  <slot v-else />
</template>
