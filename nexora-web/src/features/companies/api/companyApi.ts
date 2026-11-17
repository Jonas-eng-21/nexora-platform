import { http } from '@/shared/api/http'
import type { CompanyProfile, CompanyProfileRequest } from '../types'

export const companyApi = {
  getProfile: () => http.get<CompanyProfile>('/companies/me').then((r) => r.data),
  saveProfile: (payload: CompanyProfileRequest) => http.put<CompanyProfile>('/companies/me', payload).then((r) => r.data),
}
