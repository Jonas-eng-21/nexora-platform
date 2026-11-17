import { http } from '@/shared/api/http'
import type { AreaOption, Page, Resume, Skill, StudentProfile, StudentProfileRequest } from '../types'

export const studentApi = {
  getProfile: () => http.get<StudentProfile>('/students/me').then((r) => r.data),
  saveProfile: (payload: StudentProfileRequest) => http.put<StudentProfile>('/students/me', payload).then((r) => r.data),
  getResume: () => http.get<Resume>('/students/me/resume').then((r) => r.data),
  saveResume: (payload: Resume) => http.put<Resume>('/students/me/resume', payload).then((r) => r.data),
  getSkills: () => http.get<Skill[]>('/students/me/skills').then((r) => r.data),
  saveSkills: (skillIds: string[]) => http.put<Skill[]>('/students/me/skills', { skillIds }).then((r) => r.data),
  searchCatalog: (search: string, page = 0, size = 100) =>
    http.get<Page<Skill>>('/skills', { params: { search: search || undefined, page, size } }).then((r) => r.data),
  addCatalogSkill: (name: string) => http.post<Skill>('/skills', { name }).then((r) => r.data),
  listAreas: () => http.get<AreaOption[]>('/areas').then((r) => r.data),
}
