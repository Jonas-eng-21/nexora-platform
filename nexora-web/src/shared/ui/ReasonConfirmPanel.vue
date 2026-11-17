<script setup lang="ts">
import { nextTick, ref } from 'vue'
import ConfirmPanel from './ConfirmPanel.vue'
import TextField from './TextField.vue'

const props = withDefaults(
  defineProps<{
    title: string
    message?: string
    confirmLabel: string
    reasonLabel: string
    /** Valida o motivo; devolve a mensagem de erro ou null. */
    validate: (reason: string) => string | null
    required?: boolean
    danger?: boolean
    loading?: boolean
    error?: string
    maxlength?: number
  }>(),
  { message: undefined, required: true, danger: false, loading: false, error: undefined, maxlength: 500 },
)
const emit = defineEmits<{ confirm: [reason: string]; cancel: [] }>()

const root = ref<HTMLElement | null>(null)
const reason = ref('')
const reasonError = ref('')

async function submit() {
  reasonError.value = props.validate(reason.value) ?? ''
  if (reasonError.value) {
    // O foco vai ao campo inválido, para teclado e leitores de tela.
    await nextTick()
    root.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    return
  }
  emit('confirm', reason.value)
}
</script>

<template>
  <div ref="root">
    <ConfirmPanel :title="title" :message="message" :confirm-label="confirmLabel" :danger="danger" :loading="loading" :error="error" @confirm="submit" @cancel="$emit('cancel')">
      <TextField v-model="reason" :label="reasonLabel" name="reason" multiline :required="required" :maxlength="maxlength" :error="reasonError" :hint="`${reason.length} de ${maxlength} caracteres.`" />
    </ConfirmPanel>
  </div>
</template>
