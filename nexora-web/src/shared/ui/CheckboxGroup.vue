<script setup lang="ts">
const props = defineProps<{
  legend: string
  hint?: string
  options: { value: string; label: string }[]
  modelValue: string[]
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

function toggle(value: string, checked: boolean) {
  const next = checked ? [...props.modelValue, value] : props.modelValue.filter((v) => v !== value)
  emit('update:modelValue', next)
}
</script>

<template>
  <fieldset>
    <legend class="mb-1 text-sm font-medium">{{ legend }}</legend>
    <p v-if="hint" class="mb-2 text-sm text-muted">{{ hint }}</p>
    <div class="grid gap-x-4 gap-y-1 sm:grid-cols-2">
      <label v-for="option in options" :key="option.value" class="flex min-h-9 cursor-pointer items-center gap-2 text-sm">
        <input
          type="checkbox"
          class="size-4 shrink-0 accent-primary"
          :checked="modelValue.includes(option.value)"
          @change="toggle(option.value, ($event.target as HTMLInputElement).checked)"
        />
        {{ option.label }}
      </label>
    </div>
  </fieldset>
</template>
