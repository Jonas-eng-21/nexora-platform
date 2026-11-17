import { http } from '@/shared/api/http'
import type { UniversityProfile, UniversityProfileRequest } from '../types'

export const universityApi = {
  getProfile: () => http.get<UniversityProfile>('/universities/me').then((r) => r.data),
  saveProfile: (payload: UniversityProfileRequest) => http.put<UniversityProfile>('/universities/me', payload).then((r) => r.data),
}
