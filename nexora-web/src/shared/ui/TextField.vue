<script setup lang="ts">
import { computed, ref, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    label: string
    name?: string
    modelValue: string
    type?: 'text' | 'email' | 'password' | 'url'
    multiline?: boolean
    error?: string
    hint?: string
    autocomplete?: string
    required?: boolean
    /** Falso em buscas e filtros, onde "(opcional)" não faz sentido. */
    showOptional?: boolean
    maxlength?: number
  }>(),
  { name: undefined, type: 'text', multiline: false, error: '', hint: '', autocomplete: undefined, required: false, showOptional: true, maxlength: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const id = useId()
const revealed = ref(false)
const spellcheck = computed(() => (props.type === 'email' || props.type === 'url' || props.type === 'password' ? false : undefined))
const isPassword = computed(() => props.type === 'password')
const inputType = computed(() => (isPassword.value && revealed.value ? 'text' : props.type))
const describedBy = computed(() => {
  const ids = []
  if (props.hint) ids.push(`${id}-hint`)
  if (props.error) ids.push(`${id}-error`)
  return ids.length ? ids.join(' ') : undefined
})

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement | HTMLTextAreaElement).value)
}
</script>

<template>
  <div>
    <label :for="id" class="mb-1.5 block text-sm font-medium">
      {{ label }}
      <span v-if="!required && showOptional" class="font-normal text-muted"> (opcional)</span>
    </label>
    <div class="relative">
      <textarea
        v-if="multiline"
        :id="id"
        :value="modelValue"
        :name="name"
        :maxlength="maxlength"
        rows="3"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="block w-full rounded-control border bg-surface px-3 py-2.5 text-base text-fg placeholder:text-muted"
        :class="error ? 'border-danger' : 'border-line-strong'"
        @input="onInput"
      />
      <input
        v-else
        :id="id"
        :value="modelValue"
        :name="name"
        :spellcheck="spellcheck"
        :type="inputType"
        :autocomplete="autocomplete"
        :required="required"
        :maxlength="maxlength"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="block min-h-11 w-full rounded-control border bg-surface px-3 text-base text-fg placeholder:text-muted"
        :class="[error ? 'border-danger' : 'border-line-strong', isPassword ? 'pr-20' : '']"
        @input="onInput"
      />
      <button
        v-if="isPassword"
        type="button"
        :aria-pressed="revealed"
        class="absolute inset-y-0 right-0 min-w-16 rounded-r-control px-3 text-sm font-medium text-primary hover:underline"
        @click="revealed = !revealed"
      >
        {{ revealed ? 'Ocultar' : 'Mostrar' }}
      </button>
    </div>
    <p v-if="hint" :id="`${id}-hint`" class="mt-1.5 text-sm text-muted">{{ hint }}</p>
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-sm text-danger">{{ error }}</p>
  </div>
</template>
