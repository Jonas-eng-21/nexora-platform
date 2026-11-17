<script setup lang="ts">
import BaseButton from './BaseButton.vue'

defineProps<{ page: number; totalPages: number }>()
defineEmits<{ 'update:page': [page: number] }>()
</script>

<template>
  <nav v-if="totalPages > 1" aria-label="Paginação" class="flex items-center justify-between gap-3">
    <BaseButton variant="secondary" :disabled="page <= 0" @click="$emit('update:page', page - 1)">Anterior</BaseButton>
    <p class="text-sm text-muted">Página {{ page + 1 }} de {{ totalPages }}</p>
    <BaseButton variant="secondary" :disabled="page >= totalPages - 1" @click="$emit('update:page', page + 1)">
      Próxima
    </BaseButton>
  </nav>
</template>
