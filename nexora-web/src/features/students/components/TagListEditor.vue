<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import BaseButton from '@/shared/ui/BaseButton.vue'

const props = defineProps<{ label: string; name: string; modelValue: string[]; max: number; hint?: string; maxlength?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const id = useId()
const draft = ref('')
const duplicate = computed(() => props.modelValue.some((v) => v.toLowerCase() === draft.value.trim().toLowerCase()))
const full = computed(() => props.modelValue.length >= props.max)

function add() {
  const value = draft.value.trim()
  if (!value || duplicate.value || full.value) return
  emit('update:modelValue', [...props.modelValue, value])
  draft.value = ''
}
function remove(index: number) {
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
}
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">{{ label }}</label>
    <div class="flex gap-2">
      <input
        :id="id"
        v-model="draft"
        :name="name"
        type="text"
        :maxlength="maxlength ?? 80"
        :disabled="full"
        class="block min-h-11 w-full min-w-0 rounded-control border border-line-strong bg-surface px-3 text-base text-fg"
        @keydown.enter.prevent="add"
      />
      <BaseButton variant="secondary" :disabled="full || !draft.trim() || duplicate" @click="add">Adicionar</BaseButton>
    </div>
    <p class="mt-1.5 text-sm text-muted">{{ hint ? `${hint} ` : '' }}{{ modelValue.length }} de {{ max }}.</p>
    <ul v-if="modelValue.length" class="mt-2 flex flex-wrap gap-2">
      <li v-for="(tag, i) in modelValue" :key="tag" class="inline-flex items-center gap-1 rounded-full border border-line-strong bg-surface py-0.5 pl-3 pr-1 text-sm">
        {{ tag }}
        <button type="button" class="inline-flex size-8 items-center justify-center rounded-full hover:bg-primary-soft" :aria-label="`Remover ${tag}`" @click="remove(i)">
          <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
        </button>
      </li>
    </ul>
  </div>
</template>
