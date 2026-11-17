import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { applicationApi } from '../api/applicationApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/applicationApi', () => ({ applicationApi: { listOwn: vi.fn(), readResume: vi.fn(), apply: vi.fn() } }))

const app = (id: string, status: string, resumeType = 'PLATFORM', title = `Vaga ${id}`) => ({
  id, jobId: `j${id}`, jobTitle: title, companyName: 'ACME Tech', status, resumeType, appliedAt: '2026-11-12T10:00:00Z', updatedAt: '2026-11-12T10:00:00Z',
})
const page = (content: unknown[], totalPages = 1) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages })

describe('ApplicationsView', () => {
  beforeEach(() => resetDom())

  it('lists the history with a text status for each application', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([app('1', 'SUBMITTED'), app('2', 'REJECTED'), app('3', 'APPROVED')]) as never)
    const { wrapper } = await mountAt('/estudante/candidaturas')
    const text = wrapper.text()
    expect(wrapper.findAll('article')).toHaveLength(3)
    expect(text).toContain('Enviada')
    expect(text).toContain('Rejeitada')
    expect(text).toContain('Aprovada')
    expect(text).toContain('Currículo do Nexora')
  })

  it('explains a suspended application and that history is kept', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([app('1', 'SUSPENDED')]) as never)
    const { wrapper } = await mountAt('/estudante/candidaturas')
    expect(wrapper.text()).toContain('Suspensa')
    expect(wrapper.text()).toContain('vínculo acadêmico deixou de valer')
    expect(wrapper.text()).toContain('reativação')
  })

  it('the status filter really filters through the API and the URL', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([app('1', 'SUBMITTED')]) as never)
    const { wrapper, router } = await mountAt('/estudante/candidaturas')
    expect(applicationApi.listOwn).toHaveBeenLastCalledWith('', 0)
    await wrapper.get('select[name="status"]').setValue('SUSPENDED')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ status: 'SUSPENDED' })
    expect(applicationApi.listOwn).toHaveBeenLastCalledWith('SUSPENDED', 0)
  })

  it('has an empty state for no applications and another when the filter matches none', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([]) as never)
    const { wrapper } = await mountAt('/estudante/candidaturas')
    expect(wrapper.text()).toContain('Você ainda não se candidatou a nenhuma vaga')
    const filtered = await mountAt('/estudante/candidaturas?status=REJECTED')
    expect(filtered.wrapper.text()).toContain('Nenhuma candidatura com esta situação')
  })

  it('shows the snapshot actually sent, read-only, and lets the student hide it', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([app('1', 'SUBMITTED')]) as never)
    vi.mocked(applicationApi.readResume).mockResolvedValue({
      kind: 'snapshot',
      snapshot: {
        profile: { fullName: 'Ana Souza', course: 'Sistemas', institution: 'UFAPE', location: 'Garanhuns - PE', summary: 'Quero backend.' },
        resume: { education: [], experiences: [], projects: [{ name: 'Nexora', description: 'TCC', url: 'https://github.com/x/nexora' }], courses: [], softSkills: [], interests: [], links: {}, interestAreas: [], preferredWorkModels: [] },
        skills: [{ id: 's1', name: 'Java' }],
      },
    })
    const { wrapper } = await mountAt('/estudante/candidaturas')
    const button = () => wrapper.findAll('button').find((b) => b.text().includes('currículo enviado'))!
    await button().trigger('click')
    await settle()
    expect(applicationApi.readResume).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('Ana Souza')
    expect(wrapper.text()).toContain('Nexora')
    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('não muda quando você edita')
    await button().trigger('click')
    expect(wrapper.text()).not.toContain('Ana Souza')
  })

  it('downloads the private PDF through an object URL, without a public link', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([app('1', 'IN_REVIEW', 'PDF')]) as never)
    vi.mocked(applicationApi.readResume).mockResolvedValue({ kind: 'pdf', blob: new Blob(['%PDF-']), filename: 'cv-ana.pdf' })
    const create = vi.fn(() => 'blob:fake')
    const revoke = vi.fn()
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const { wrapper } = await mountAt('/estudante/candidaturas')
    await wrapper.findAll('button').find((b) => b.text() === 'Baixar PDF enviado')!.trigger('click')
    await settle()
    expect(create).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    expect(revoke).toHaveBeenCalledWith('blob:fake')
    expect(wrapper.text()).toContain('cv-ana.pdf baixado')
  })

  it('shows the API error when the resume cannot be read', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue(page([app('1', 'SUBMITTED')]) as never)
    vi.mocked(applicationApi.readResume).mockRejectedValue(new ApiError(404, 'APPLICATION_NOT_FOUND', 'Candidatura não encontrada.'))
    const { wrapper } = await mountAt('/estudante/candidaturas')
    await wrapper.findAll('button').find((b) => b.text().includes('currículo enviado'))!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('não encontrada')
  })

  it('paginates with the API page number', async () => {
    vi.mocked(applicationApi.listOwn).mockResolvedValue({ ...page([app('1', 'SUBMITTED')], 3) } as never)
    const { wrapper } = await mountAt('/estudante/candidaturas')
    await wrapper.findAll('button').find((b) => b.text() === 'Próxima')!.trigger('click')
    await settle()
    expect(applicationApi.listOwn).toHaveBeenLastCalledWith('', 1)
  })
})
