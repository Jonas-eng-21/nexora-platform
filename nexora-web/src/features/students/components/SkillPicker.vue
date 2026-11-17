<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { toApiError, type ApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import AlertMessage from '@/shared/ui/AlertMessage.vue'
import BaseButton from '@/shared/ui/BaseButton.vue'
import TextField from '@/shared/ui/TextField.vue'
import { studentApi } from '../api/studentApi'
import type { Skill } from '../types'

const props = withDefaults(
  defineProps<{
    modelValue: Skill[]
    max?: number
    title?: string
    name?: string
    /** Competências que não podem ser escolhidas aqui (ex.: já são obrigatórias numa vaga). */
    blockedIds?: string[]
    blockedHint?: string
  }>(),
  { max: 50, title: 'Competências selecionadas', name: 'skill-search', blockedIds: () => [], blockedHint: 'já escolhida na outra lista' },
)
const blocked = computed(() => new Set(props.blockedIds))
const emit = defineEmits<{ 'update:modelValue': [value: Skill[]] }>()

const search = ref('')
const catalog = useLoader(() => studentApi.searchCatalog(search.value.trim()))
const addError = ref<ApiError | null>(null)
const adding = ref(false)

let timer: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(timer)
  timer = setTimeout(() => void catalog.load(), 250)
})
onBeforeUnmount(() => clearTimeout(timer))

const selectedIds = computed(() => new Set(props.modelValue.map((s) => s.id)))
const results = computed(() => catalog.data.value?.content ?? [])
const full = computed(() => props.modelValue.length >= props.max)
const canAddToCatalog = computed(() => {
  const term = search.value.trim().toLowerCase()
  return term.length > 0 && !catalog.loading.value && !results.value.some((s) => s.name.toLowerCase() === term)
})

function toggle(skill: Skill, checked: boolean) {
  emit('update:modelValue', checked ? [...props.modelValue, skill] : props.modelValue.filter((s) => s.id !== skill.id))
}

async function addToCatalog() {
  addError.value = null
  adding.value = true
  try {
    const skill = await studentApi.addCatalogSkill(search.value.trim())
    if (!selectedIds.value.has(skill.id) && !full.value && !blocked.value.has(skill.id)) emit('update:modelValue', [...props.modelValue, skill])
    await catalog.load()
  } catch (e) {
    addError.value = toApiError(e)
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <div>
      <h3 class="mb-2 text-sm font-medium">{{ title }} ({{ modelValue.length }} de {{ max }})</h3>
      <p v-if="!modelValue.length" class="text-sm text-muted">Nenhuma competência selecionada ainda.</p>
      <ul v-else class="flex flex-wrap gap-2">
        <li v-for="skill in modelValue" :key="skill.id" class="inline-flex items-center gap-1 rounded-full border border-line-strong bg-surface py-0.5 pl-3 pr-1 text-sm">
          {{ skill.name }}
          <button type="button" class="inline-flex size-8 items-center justify-center rounded-full hover:bg-primary-soft" :aria-label="`Remover ${skill.name}`" @click="toggle(skill, false)">
            <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </li>
      </ul>
    </div>

    <TextField v-model="search" label="Buscar no catálogo" :name="name" :show-optional="false" hint="A busca ignora acentos e maiúsculas." :maxlength="80" />

    <p v-if="catalog.loading.value" role="status" class="text-sm text-muted">Buscando competências…</p>
    <AlertMessage v-else-if="catalog.error.value" tone="error">{{ catalog.error.value.message }}</AlertMessage>
    <p v-else-if="!results.length" role="status" class="text-sm text-muted">Nenhuma competência encontrada para esta busca.</p>
    <div v-else role="group" aria-label="Competências do catálogo" tabindex="0" class="max-h-64 overflow-y-auto rounded-control border border-line p-2">
      <label v-for="skill in results" :key="skill.id" class="flex min-h-9 cursor-pointer items-center gap-2 rounded px-2 text-sm hover:bg-primary-soft">
        <input type="checkbox" class="size-4 shrink-0 accent-primary" :checked="selectedIds.has(skill.id)" :disabled="(full || blocked.has(skill.id)) && !selectedIds.has(skill.id)" @change="toggle(skill, ($event.target as HTMLInputElement).checked)" />
        {{ skill.name }}<span v-if="blocked.has(skill.id) && !selectedIds.has(skill.id)" class="text-muted"> — {{ blockedHint }}</span>
      </label>
    </div>
    <p v-if="catalog.data.value && catalog.data.value.totalElements > results.length" class="text-sm text-muted">
      Mostrando {{ results.length }} de {{ catalog.data.value.totalElements }}. Refine a busca para ver as demais.
    </p>

    <div v-if="canAddToCatalog" class="space-y-2">
      <AlertMessage v-if="addError" tone="error">{{ addError.message }}</AlertMessage>
      <BaseButton variant="secondary" :loading="adding" :disabled="full" @click="addToCatalog">Adicionar “{{ search.trim() }}” ao catálogo</BaseButton>
    </div>
  </div>
</template>
