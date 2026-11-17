import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { resetAreasCache } from '@/features/students/composables/useAreas'
import { studentApi } from '@/features/students/api/studentApi'
import { jobManagementApi } from '@/features/jobs/api/jobManagementApi'
import { companyMatchingApi } from '../api/companyMatchingApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('@/features/students/api/studentApi', () => ({ studentApi: { listAreas: vi.fn() } }))
vi.mock('@/features/jobs/api/jobManagementApi', () => ({ jobManagementApi: { get: vi.fn() } }))
vi.mock('../api/companyMatchingApi', () => ({ companyMatchingApi: { students: vi.fn() } }))

const match = (score: number, extra = {}) => ({
  score, algorithmVersion: 'v1', summary: `Compatibilidade de ${score}%: resumo.`, scoreType: 'STRUCTURED', structuredScore: score, semanticScore: null,
  matchedRequiredSkills: ['Java'], missingRequiredSkills: ['Docker'], matchedDesiredSkills: [], missingDesiredSkills: ['Redis'], criteria: [], ...extra,
})
const student = (name: string, extra = {}) => ({
  id: `id-${name}`, fullName: name, course: 'Sistemas de Informação', location: 'Garanhuns - PE', summary: 'Quero atuar com backend.',
  skills: ['Java', 'Spring Boot'], interestAreas: ['BACKEND'], ...extra,
})
const page = <T>(content: T[]) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })
const job = { id: 'j1', companyId: 'c', companyName: 'Oficina', title: 'Estágio Backend', description: 'D', area: 'BACKEND', location: 'Garanhuns - PE', workModel: 'HYBRID', level: 'INTERNSHIP', status: 'OPEN', requiredSkills: [], desiredSkills: [], createdAt: '', updatedAt: '' }

beforeEach(() => {
  resetDom()
  resetAreasCache()
  vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }])
  vi.mocked(jobManagementApi.get).mockResolvedValue(job as never)
})

describe('RecommendedStudentsView', () => {
  it('shows the authorized data, the score, the explanation and the matched/missing skills', async () => {
    vi.mocked(companyMatchingApi.students).mockResolvedValue(page([{ student: student('Ana Souza'), match: match(88) }, { student: student('Bruno Lima'), match: match(0, { structuredScore: 0 }) }]) as never)
    const { wrapper } = await mountAt('/empresa/vagas/j1/recomendados', 'COMPANY')
    expect(companyMatchingApi.students).toHaveBeenCalledWith('j1', 0)
    const cards = wrapper.findAll('article')
    expect(cards).toHaveLength(2)
    const first = cards[0]!.text()
    for (const text of ['Ana Souza', 'Sistemas de Informação', 'Garanhuns - PE', 'Quero atuar com backend.', 'Java, Spring Boot', 'Backend', '88/100', 'Alta afinidade', 'Compatibilidade de 88%', 'Obrigatórias atendidas pelo estudante', 'Docker']) expect(first).toContain(text)
    expect(cards[1]!.text()).toContain('0/100')
  })

  it('renders STRUCTURED, HYBRID and FALLBACK with their own wording', async () => {
    vi.mocked(companyMatchingApi.students).mockResolvedValue(page([
      { student: student('A'), match: match(80) },
      { student: student('B'), match: match(98, { scoreType: 'HYBRID', structuredScore: 100, semanticScore: 89 }) },
      { student: student('C'), match: match(70, { scoreType: 'FALLBACK' }) },
    ]) as never)
    const { wrapper } = await mountAt('/empresa/vagas/j1/recomendados', 'COMPANY')
    const [a, b, c] = wrapper.findAll('article').map((x) => x.text())
    expect(a).toContain('Cálculo estruturado')
    expect(b).toContain('Estruturado e semântico')
    expect(b).toContain('89/100')
    expect(c).toContain('semântica indisponível')
    expect(c).not.toContain('Similaridade semântica')
  })

  it('never shows private data or a contact action, and says a recommendation is not an application', async () => {
    vi.mocked(companyMatchingApi.students).mockResolvedValue(page([{ student: student('Ana Souza'), match: match(88) }]) as never)
    const { wrapper } = await mountAt('/empresa/vagas/j1/recomendados', 'COMPANY')
    const text = wrapper.get('main').text() // o cabeçalho mostra o e-mail da própria empresa logada
    expect(text).not.toMatch(/@/)
    expect(text).not.toMatch(/e-mail:|instituição:|github|linkedin|portfólio/i)
    expect(text.toLowerCase()).not.toContain('contatar')
    expect(wrapper.findAll('article button, article a').map((e) => e.text().toLowerCase()).join(' ')).not.toMatch(/contat|convid|mensagem/)
    expect(text).toContain('não é uma candidatura')
  })

  it('has an empty state, and says clearly when the job belongs to another company (404)', async () => {
    vi.mocked(companyMatchingApi.students).mockResolvedValueOnce(page([]) as never)
    const empty = await mountAt('/empresa/vagas/j1/recomendados', 'COMPANY')
    expect(empty.wrapper.text()).toContain('Nenhum estudante recomendado no momento')
    resetDom()
    vi.mocked(companyMatchingApi.students).mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', messageFor('JOB_NOT_FOUND')))
    const other = await mountAt('/empresa/vagas/jx/recomendados', 'COMPANY')
    await settle()
    expect(other.wrapper.get('[role="alert"]').text()).toContain('de outra empresa')
    expect(other.wrapper.find('article').exists()).toBe(false)
  })

  it('paginates with the API page number', async () => {
    vi.mocked(companyMatchingApi.students).mockResolvedValue({ ...page([{ student: student('Ana'), match: match(50) }]), totalPages: 3 } as never)
    const { wrapper } = await mountAt('/empresa/vagas/j1/recomendados', 'COMPANY')
    await wrapper.findAll('button').find((b) => b.text() === 'Próxima')!.trigger('click')
    await settle()
    expect(companyMatchingApi.students).toHaveBeenLastCalledWith('j1', 1)
  })
})
