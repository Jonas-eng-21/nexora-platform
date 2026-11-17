<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    label: string
    modelValue: string
    options: { value: string; label: string }[]
    placeholder?: string
    name?: string
    error?: string
    required?: boolean
    showOptional?: boolean
  }>(),
  { placeholder: undefined, name: undefined, error: '', required: false, showOptional: true },
)
defineEmits<{ 'update:modelValue': [value: string] }>()

const id = useId()
const describedBy = computed(() => (props.error ? `${id}-error` : undefined))
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">
      {{ label }}<span v-if="!required && showOptional" class="font-normal text-muted"> (opcional)</span>
    </label>
    <select
      :id="id"
      :name="name"
      :value="modelValue"
      :required="required"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      class="block min-h-11 w-full rounded-control border bg-surface px-3 text-base text-fg"
      :class="error ? 'border-danger' : 'border-line-strong'"
      @change="$emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-if="placeholder !== undefined" value="">{{ placeholder }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-sm text-danger">{{ error }}</p>
  </div>
</template>
