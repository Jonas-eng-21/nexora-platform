import { useLoader } from '@/shared/composables/useLoader'
import { affiliationApi } from './api/affiliationApi'

export function useEligibility() {
  return useLoader(() => affiliationApi.eligibility().then((r) => r.eligible))
}
