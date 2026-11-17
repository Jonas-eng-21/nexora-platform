import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { applicationApi } from '@/features/applications/api/applicationApi'
import { resetAreasCache } from '@/features/students/composables/useAreas'
import { studentApi } from '@/features/students/api/studentApi'
import { affiliationApi } from '@/features/universities/api/affiliationApi'
import { jobApi } from '../api/jobApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/jobApi', () => ({ jobApi: { search: vi.fn(), get: vi.fn(), recommended: vi.fn(), match: vi.fn() } }))
vi.mock('@/features/students/api/studentApi', () => ({ studentApi: { listAreas: vi.fn(), getSkills: vi.fn(), getResume: vi.fn() } }))
vi.mock('@/features/universities/api/affiliationApi', () => ({ affiliationApi: { eligibility: vi.fn() } }))
vi.mock('@/features/applications/api/applicationApi', () => ({ applicationApi: { listOwn: vi.fn(), apply: vi.fn() } }))

const skill = (id: string, name: string) => ({ id, name })
const job = (id: string, title: string) => ({
  id, companyId: 'c1', companyName: 'ACME Tech', title, description: 'Atuar no backend.\nSegunda linha.', area: 'BACKEND', location: 'Garanhuns - PE',
  workModel: 'HYBRID', level: 'INTERNSHIP', status: 'OPEN', requiredSkills: [skill('s1', 'Java'), skill('s2', 'Docker')], desiredSkills: [skill('s3', 'Redis')],
  createdAt: '2026-11-10T10:00:00Z', updatedAt: '2026-11-10T10:00:00Z',
})
const match = (score: number, extra = {}) => ({
  score, algorithmVersion: 'v1', summary: `Compatibilidade de ${score}%: resumo.`, scoreType: 'STRUCTURED', structuredScore: score, semanticScore: null,
  matchedRequiredSkills: ['Java'], missingRequiredSkills: ['Docker'], matchedDesiredSkills: [], missingDesiredSkills: ['Redis'], criteria: [], ...extra,
})
const page = <T>(content: T[]) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })

beforeEach(() => {
  resetDom()
  resetAreasCache()
  vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }, { code: 'FRONTEND', label: 'Frontend' }])
  vi.mocked(studentApi.getSkills).mockResolvedValue([skill('s1', 'Java')])
  vi.mocked(studentApi.getResume).mockResolvedValue({ interestAreas: ['BACKEND'] } as never)
})

describe('JobsView', () => {
  it('opens on the recommended list, ordered as the API returns, with score and explanation per job', async () => {
    vi.mocked(jobApi.recommended).mockResolvedValue(page([{ job: job('j1', 'Estágio Backend'), match: match(91) }, { job: job('j2', 'Estágio Dados'), match: match(0, { structuredScore: 0 }) }]) as never)
    const { wrapper } = await mountAt('/estudante/vagas')
    const cards = wrapper.findAll('article')
    expect(cards).toHaveLength(2)
    expect(cards[0]!.text()).toContain('Estágio Backend')
    expect(cards[0]!.text()).toContain('91/100')
    expect(cards[1]!.text()).toContain('0/100')
    expect(cards[0]!.text()).toContain('Compatibilidade de 91%')
    expect(wrapper.find('form[aria-label="Filtros de vagas"]').exists()).toBe(false) // sem filtros que não filtram
  })

  it('warns that scores have little basis when the resume has no skills nor interest areas', async () => {
    vi.mocked(studentApi.getSkills).mockResolvedValue([])
    vi.mocked(studentApi.getResume).mockResolvedValue({ interestAreas: [] } as never)
    vi.mocked(jobApi.recommended).mockResolvedValue(page([{ job: job('j1', 'Estágio Backend'), match: match(30) }]) as never)
    const { wrapper } = await mountAt('/estudante/vagas')
    expect(wrapper.text()).toContain('pouca base')
  })

  it('guides to the profile when the student has none (no error screen)', async () => {
    vi.mocked(jobApi.recommended).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    vi.mocked(studentApi.getSkills).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    vi.mocked(studentApi.getResume).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    const { wrapper } = await mountAt('/estudante/vagas')
    expect(wrapper.text()).toContain('Preencha seu perfil para ver recomendações')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('filters really change the request and the URL, and can be cleared', async () => {
    vi.mocked(jobApi.recommended).mockResolvedValue(page([]) as never)
    vi.mocked(jobApi.search).mockResolvedValue(page([job('j1', 'Estágio Backend')]) as never)
    const { wrapper, router } = await mountAt('/estudante/vagas')
    await wrapper.findAll('button').find((b) => b.text() === 'Todas as vagas')!.trigger('click')
    await settle()
    expect(jobApi.search).toHaveBeenLastCalledWith({ search: '', area: '', workModel: '', level: '', location: '' }, 0)

    await wrapper.get('input[name="busca"]').setValue('java')
    await wrapper.get('select[name="area"]').setValue('BACKEND')
    await wrapper.get('select[name="modelo"]').setValue('REMOTE')
    await wrapper.get('form[aria-label="Filtros de vagas"]').trigger('submit')
    await settle()
    expect(jobApi.search).toHaveBeenLastCalledWith({ search: 'java', area: 'BACKEND', workModel: 'REMOTE', level: '', location: '' }, 0)
    expect(router.currentRoute.value.query).toMatchObject({ visao: 'todas', busca: 'java', area: 'BACKEND', modelo: 'REMOTE' })

    await wrapper.findAll('button').find((b) => b.text() === 'Limpar filtros')!.trigger('click')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ visao: 'todas' })
    expect(jobApi.search).toHaveBeenLastCalledWith({ search: '', area: '', workModel: '', level: '', location: '' }, 0)
  })

  it('restores filters from the URL on load (shareable link)', async () => {
    vi.mocked(jobApi.search).mockResolvedValue(page([]) as never)
    const { wrapper } = await mountAt('/estudante/vagas?visao=todas&busca=vue&nivel=INTERNSHIP')
    expect(jobApi.search).toHaveBeenCalledWith({ search: 'vue', area: '', workModel: '', level: 'INTERNSHIP', location: '' }, 0)
    expect((wrapper.get('input[name="busca"]').element as HTMLInputElement).value).toBe('vue')
    expect(wrapper.text()).toContain('Nenhuma vaga encontrada')
  })

  it('shows an API failure with a retry action', async () => {
    vi.mocked(jobApi.recommended).mockRejectedValueOnce(new ApiError(503, 'SERVICE_UNAVAILABLE', 'Servidor indisponível.')).mockResolvedValue(page([]) as never)
    const { wrapper } = await mountAt('/estudante/vagas')
    expect(wrapper.get('[role="alert"]').text()).toContain('indisponível')
    await wrapper.findAll('button').find((b) => b.text() === 'Tentar novamente')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Nenhuma vaga recomendada')
  })
})

describe('JobDetailView', () => {
  beforeEach(() => {
    vi.mocked(jobApi.get).mockResolvedValue(job('j1', 'Estágio Backend') as never)
    vi.mocked(jobApi.match).mockResolvedValue({ jobId: 'j1', jobTitle: 'Estágio Backend', match: match(66) } as never)
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([]) as never)
  })

  it('separates required from desired skills and marks which ones the student has', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: true })
    const { wrapper } = await mountAt('/estudante/vagas/j1')
    const text = wrapper.text()
    expect(text).toContain('Estágio Backend')
    expect(text).toContain('Obrigatórios')
    expect(text).toContain('Desejáveis')
    expect(wrapper.findAll('section').find((s) => s.text().startsWith('Obrigatórios'))!.text()).toContain('Java — você tem')
    expect(wrapper.findAll('section').find((s) => s.text().startsWith('Obrigatórios'))!.text()).toContain('Docker — você não tem')
    expect(text).toContain('66/100')
    expect(text).toContain('Enviar candidatura')
  })

  it('blocks the application and explains why when the student is not validated', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: false })
    const { wrapper } = await mountAt('/estudante/vagas/j1')
    expect(wrapper.text()).toContain('Candidatura bloqueada')
    expect(wrapper.text()).not.toContain('Enviar candidatura')
  })

  it('applies and then shows the existing application', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: true })
    vi.mocked(applicationApi.apply).mockResolvedValue({ id: 'ap1', status: 'SUBMITTED' } as never)
    const { wrapper } = await mountAt('/estudante/vagas/j1')
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([{ id: 'ap1', jobId: 'j1', jobTitle: 'Estágio Backend', companyName: 'ACME Tech', status: 'SUBMITTED', resumeType: 'PLATFORM', appliedAt: '2026-11-17T10:00:00Z', updatedAt: '2026-11-17T10:00:00Z' }]) as never)
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(applicationApi.apply).toHaveBeenCalledWith('j1', 'PLATFORM', undefined)
    expect(wrapper.text()).toContain('Candidatura enviada.')
  })

  it('keeps the page useful when the match cannot be computed because the profile is missing', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: false })
    vi.mocked(jobApi.match).mockRejectedValue(new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x'))
    const { wrapper } = await mountAt('/estudante/vagas/j1')
    expect(wrapper.text()).toContain('Preencha seu perfil para ver a compatibilidade')
    expect(wrapper.text()).toContain('Atuar no backend.')
  })

  it('shows a clear message when the job is no longer available', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: true })
    vi.mocked(jobApi.get).mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', 'Esta vaga não está mais disponível.'))
    const { wrapper } = await mountAt('/estudante/vagas/j1')
    expect(wrapper.get('[role="alert"]').text()).toContain('não está mais disponível')
  })
})
