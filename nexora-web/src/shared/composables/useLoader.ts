import { ref, type Ref } from 'vue'
import { ApiError, toApiError } from '@/shared/api/apiError'

/** Estado carregando/erro/dados de uma leitura; respostas antigas não sobrescrevem as novas. */
export function useLoader<T>(fetcher: () => Promise<T>, options: { immediate?: boolean } = {}) {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<ApiError | null>(null)
  let sequence = 0

  async function load() {
    const current = ++sequence
    loading.value = true
    error.value = null
    try {
      const result = await fetcher()
      if (current === sequence) data.value = result
    } catch (e) {
      if (current === sequence) error.value = toApiError(e)
    } finally {
      if (current === sequence) loading.value = false
    }
  }

  if (options.immediate !== false) void load()
  return { data, loading, error, load }
}
