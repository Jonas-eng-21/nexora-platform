<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import AlertMessage from './AlertMessage.vue'
import BaseButton from './BaseButton.vue'

defineProps<{
  title: string
  message?: string
  confirmLabel: string
  loading?: boolean
  error?: string
  danger?: boolean
}>()
defineEmits<{ confirm: []; cancel: [] }>()

const titleId = useId()
const cancel = ref<InstanceType<typeof BaseButton> | null>(null)
// Quem abriu o painel recupera o foco ao fechá-lo (cancelar ou Esc), em vez de o foco cair no <body>.
const opener = document.activeElement as HTMLElement | null
// O foco começa em "Cancelar": confirmar uma ação irreversível exige um passo deliberado.
onMounted(() => (cancel.value?.$el as HTMLElement | undefined)?.focus())
onBeforeUnmount(() => { if (opener?.isConnected) opener.focus() })
</script>

<template>
  <div role="group" :aria-labelledby="titleId" class="space-y-3 rounded-control border border-line-strong bg-surface p-4" @keydown.esc="$emit('cancel')">
    <p :id="titleId" class="font-semibold">{{ title }}</p>
    <p v-if="message" class="text-sm text-muted">{{ message }}</p>
    <slot />
    <AlertMessage v-if="error" tone="error">{{ error }}</AlertMessage>
    <div class="flex flex-wrap gap-2">
      <BaseButton :variant="danger ? 'danger' : 'primary'" :loading="loading" @click="$emit('confirm')">{{ confirmLabel }}</BaseButton>
      <BaseButton ref="cancel" variant="secondary" :disabled="loading" @click="$emit('cancel')">Cancelar</BaseButton>
    </div>
  </div>
</template>
