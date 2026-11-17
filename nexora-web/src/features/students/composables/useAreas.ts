import { computed, ref } from 'vue'
import { studentApi } from '../api/studentApi'
import type { AreaOption } from '../types'

// O catálogo de áreas é fixo no backend: uma leitura por sessão da página basta.
const areas = ref<AreaOption[]>([])
let pending: Promise<void> | null = null

export function resetAreasCache() {
  areas.value = []
  pending = null
}

export function useAreas() {
  if (!areas.value.length && !pending) {
    pending = studentApi
      .listAreas()
      .then((list) => { areas.value = list })
      .catch(() => { pending = null })
  }
  const options = computed(() => areas.value.map((a) => ({ value: a.code, label: a.label })))
  const label = (code: string) => areas.value.find((a) => a.code === code)?.label ?? code
  return { areas, options, label }
}
