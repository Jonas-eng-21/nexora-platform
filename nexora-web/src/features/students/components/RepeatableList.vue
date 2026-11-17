<script setup lang="ts" generic="T">
import BaseButton from '@/shared/ui/BaseButton.vue'

defineProps<{ title: string; hint?: string; items: T[]; max: number; addLabel: string; itemLabel: string; emptyText: string }>()
defineEmits<{ add: []; remove: [index: number] }>()
</script>

<template>
  <fieldset class="space-y-3">
    <legend class="text-base font-semibold">{{ title }}</legend>
    <p v-if="hint" class="text-sm text-muted">{{ hint }}</p>
    <p v-if="!items.length" class="text-sm text-muted">{{ emptyText }}</p>
    <div v-for="(item, index) in items" :key="index" class="space-y-4 rounded-control border border-line p-4">
      <slot :item="item" :index="index" />
      <BaseButton variant="secondary" @click="$emit('remove', index)">Remover {{ itemLabel }} {{ index + 1 }}</BaseButton>
    </div>
    <BaseButton variant="secondary" :disabled="items.length >= max" @click="$emit('add')">{{ addLabel }}</BaseButton>
    <p v-if="items.length >= max" class="text-sm text-muted">Limite de {{ max }} itens atingido.</p>
  </fieldset>
</template>
