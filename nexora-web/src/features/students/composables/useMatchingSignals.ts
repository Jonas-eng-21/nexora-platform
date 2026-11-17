import { computed } from 'vue'
import { ApiError } from '@/shared/api/apiError'
import { useLoader } from '@/shared/composables/useLoader'
import { studentApi } from '../api/studentApi'

const orNullWithoutProfile = <T>(e: unknown): T | null => {
  if (e instanceof ApiError && e.code === 'STUDENT_PROFILE_NOT_FOUND') return null
  throw e
}

/**
 * O backend sempre devolve uma nota de 0 a 100. Quando o currículo não tem competências nem áreas de
 * interesse, essa nota quase não tem base: a tela avisa em vez de apresentá-la como análise completa.
 */
export function useMatchingSignals() {
  const skills = useLoader(() => studentApi.getSkills().catch(orNullWithoutProfile<never[]>))
  const resume = useLoader(() => studentApi.getResume().catch(orNullWithoutProfile<never>))
  const lacksData = computed(
    () =>
      !skills.loading.value && !resume.loading.value &&
      skills.data.value !== null && resume.data.value !== null &&
      skills.data.value.length === 0 && resume.data.value.interestAreas.length === 0,
  )
  return { lacksData }
}
