import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/apiError'
import { mountAt, resetDom } from '@/test/mountApp'
import { resetAreasCache } from '../composables/useAreas'
import { applicationApi } from '@/features/applications/api/applicationApi'
import { jobApi } from '@/features/jobs/api/jobApi'
import { affiliationApi } from '@/features/universities/api/affiliationApi'
import { studentApi } from '../api/studentApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/studentApi', () => ({ studentApi: { getProfile: vi.fn(), getSkills: vi.fn(), listAreas: vi.fn() } }))
vi.mock('@/features/universities/api/affiliationApi', () => ({ affiliationApi: { eligibility: vi.fn() } }))
vi.mock('@/features/applications/api/applicationApi', () => ({ applicationApi: { listOwn: vi.fn() } }))
vi.mock('@/features/jobs/api/jobApi', () => ({ jobApi: { recommended: vi.fn() } }))

const page = (content: unknown[]) => ({ content, page: 0, size: 3, totalElements: content.length, totalPages: 1 })

describe('StudentHomeView', () => {
  beforeEach(() => {
    resetDom()
    resetAreasCache()
    vi.mocked(studentApi.listAreas).mockResolvedValue([])
  })

  it('opens on the real pending tasks for a brand-new student, each with a working link', async () => {
    vi.mocked(studentApi.getProfile).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    vi.mocked(studentApi.getSkills).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: false })
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([]) as never)
    vi.mocked(jobApi.recommended).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    const { wrapper } = await mountAt('/estudante')
    expect(wrapper.text()).toContain('4 de 4 passos pendentes')
    expect(wrapper.text()).toContain('Preencha seu perfil para ver recomendações')
    const hrefs = wrapper.findAll('ol a').map((a) => a.attributes('href'))
    expect(hrefs).toEqual(['/estudante/perfil', '/estudante/perfil', '/estudante/vinculo', '/estudante/vagas'])
    expect(wrapper.text()).not.toMatch(/bem-vind/i)
  })

  it('marks completed steps and lists recommendations and recent applications', async () => {
    vi.mocked(studentApi.getProfile).mockResolvedValue({ id: 'p', fullName: 'Ana', course: null, institution: null, location: null, summary: null })
    vi.mocked(studentApi.getSkills).mockResolvedValue([{ id: 's1', name: 'Java' }])
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: true })
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([{ id: 'a1', jobId: 'j1', jobTitle: 'Estágio Backend', companyName: 'ACME', status: 'IN_REVIEW', resumeType: 'PDF', appliedAt: '2026-11-12T10:00:00Z', updatedAt: '2026-11-12T10:00:00Z' }]) as never)
    vi.mocked(jobApi.recommended).mockResolvedValue(page([]) as never)
    const { wrapper } = await mountAt('/estudante')
    expect(wrapper.text()).toContain('Tudo pronto')
    expect(wrapper.text()).toContain('Estágio Backend')
    expect(wrapper.text()).toContain('Em análise')
    expect(wrapper.text()).toContain('Nenhuma vaga recomendada no momento')
  })
})
