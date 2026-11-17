import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { http } from '@/shared/api/http'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { resetAreasCache } from '@/features/students/composables/useAreas'
import { studentApi } from '@/features/students/api/studentApi'
import { jobManagementApi } from '@/features/jobs/api/jobManagementApi'
import { applicationApi } from '../api/applicationApi'
import { companyApplicationApi } from '../api/companyApplicationApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('@/features/students/api/studentApi', () => ({ studentApi: { listAreas: vi.fn() } }))
vi.mock('@/features/jobs/api/jobManagementApi', () => ({ jobManagementApi: { get: vi.fn() } }))
vi.mock('../api/applicationApi', () => ({ applicationApi: { readResume: vi.fn() } }))
vi.mock('../api/companyApplicationApi', () => ({ companyApplicationApi: { listForJob: vi.fn(), changeStatus: vi.fn(), reactivate: vi.fn() } }))

const app = (id: string, status: string, extra: Record<string, unknown> = {}) => ({
  id, status, resumeType: 'PLATFORM', appliedAt: '2026-11-12T10:00:00Z', updatedAt: '2026-11-12T10:00:00Z', matchScore: 72,
  student: { id: `s-${id}`, fullName: `Estudante ${id}`, course: 'Sistemas', location: 'Garanhuns - PE', summary: 'Resumo.', skills: ['Java'], interestAreas: ['BACKEND'] }, ...extra,
})
const page = (content: unknown[], totalPages = 1) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages })
const job = { id: 'j1', companyId: 'c', companyName: 'Oficina', title: 'Estágio Backend', description: 'D', area: 'BACKEND', location: 'Garanhuns - PE', workModel: 'HYBRID', level: 'INTERNSHIP', status: 'OPEN', requiredSkills: [], desiredSkills: [], createdAt: '', updatedAt: '' }
const open = (list: unknown[], path = '/empresa/vagas/j1/candidaturas') => {
  vi.mocked(companyApplicationApi.listForJob).mockResolvedValue(page(list) as never)
  return mountAt(path, 'COMPANY')
}
const btn = (wrapper: { findAll: (s: string) => { text: () => string; trigger: (e: string) => Promise<void> }[] }, text: string) => wrapper.findAll('button').find((b) => b.text() === text)!

beforeEach(() => {
  resetDom()
  resetAreasCache()
  vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }])
  vi.mocked(jobManagementApi.get).mockResolvedValue(job as never)
})

describe('received applications list', () => {
  it('shows student summary, status, date, resume type and the current score; never an e-mail', async () => {
    const { wrapper } = await open([app('1', 'SUBMITTED', { resumeType: 'PDF' })])
    const text = wrapper.get('article').text()
    for (const t of ['Estudante 1', 'Sistemas · Garanhuns - PE', 'Resumo.', 'Java', 'Backend', 'Enviada', '12 de novembro de 2026', 'PDF', '72/100', 'Alta afinidade']) expect(text).toContain(t)
    expect(wrapper.get('main').text()).not.toContain('@')
    expect(companyApplicationApi.listForJob).toHaveBeenCalledWith('j1', '', 0)
  })

  it('matchScore null is "Sem dados suficientes", while a real zero is 0/100', async () => {
    const { wrapper } = await open([app('1', 'SUBMITTED', { matchScore: null }), app('2', 'SUBMITTED', { matchScore: 0 })])
    const [nul, zero] = wrapper.findAll('article').map((a) => a.text())
    expect(nul).toContain('Sem dados suficientes')
    expect(nul).not.toContain('/100')
    expect(zero).toContain('0/100')
    expect(zero).not.toContain('Sem dados suficientes')
  })

  it('the status filter really filters through the API and the URL; empty states differ', async () => {
    const { wrapper, router } = await open([app('1', 'SUBMITTED')])
    await wrapper.get('select[name="status"]').setValue('SUSPENDED')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ status: 'SUSPENDED' })
    expect(companyApplicationApi.listForJob).toHaveBeenLastCalledWith('j1', 'SUSPENDED', 0)
    resetDom()
    const none = await open([])
    expect(none.wrapper.text()).toContain('Esta vaga ainda não recebeu candidaturas')
    resetDom()
    const filtered = await open([], '/empresa/vagas/j1/candidaturas?status=APPROVED')
    expect(filtered.wrapper.text()).toContain('Nenhuma candidatura com esta situação')
  })

  it('paginates and explains a job that belongs to another company', async () => {
    vi.mocked(companyApplicationApi.listForJob).mockResolvedValue(page([app('1', 'SUBMITTED')], 3) as never)
    const { wrapper } = await mountAt('/empresa/vagas/j1/candidaturas', 'COMPANY')
    await btn(wrapper as never, 'Próxima').trigger('click')
    await settle()
    expect(companyApplicationApi.listForJob).toHaveBeenLastCalledWith('j1', '', 1)
    resetDom()
    vi.mocked(companyApplicationApi.listForJob).mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', messageFor('JOB_NOT_FOUND')))
    const other = await mountAt('/empresa/vagas/jx/candidaturas', 'COMPANY')
    await settle()
    expect(other.wrapper.get('[role="alert"]').text()).toContain('de outra empresa')
  })
})

describe('sent resume', () => {
  it('shows the immutable snapshot as sent, not the current base resume', async () => {
    vi.mocked(applicationApi.readResume).mockResolvedValue({
      kind: 'snapshot',
      snapshot: {
        profile: { fullName: 'Ana (no envio)', course: 'Sistemas', institution: 'UFAPE', location: 'Garanhuns - PE', summary: 'Resumo do envio.' },
        resume: { education: [], experiences: [], projects: [{ name: 'Projeto do envio', description: 'x', url: 'https://github.com/x' }], courses: [], softSkills: [], interests: [], links: {}, interestAreas: [], preferredWorkModels: [] },
        skills: [{ id: 's1', name: 'Java' }],
      },
    })
    const { wrapper } = await open([app('1', 'SUBMITTED')])
    await btn(wrapper as never, 'Ver currículo enviado').trigger('click')
    await settle()
    expect(applicationApi.readResume).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('Ana (no envio)')
    expect(wrapper.text()).toContain('Projeto do envio')
    expect(wrapper.text()).toContain('como o estudante enviou')
  })

  it('downloads the private PDF from an authenticated Blob and releases the object URL', async () => {
    vi.mocked(applicationApi.readResume).mockResolvedValue({ kind: 'pdf', blob: new Blob(['%PDF-']), filename: 'cv-ana.pdf' })
    const create = vi.fn(() => 'blob:fake')
    const revoke = vi.fn()
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { expect(this.href).toBe('blob:fake'); expect(this.download).toBe('cv-ana.pdf') })
    const { wrapper } = await open([app('1', 'IN_REVIEW', { resumeType: 'PDF' })])
    await btn(wrapper as never, 'Baixar PDF enviado').trigger('click')
    await settle()
    expect(create).toHaveBeenCalledTimes(1)
    expect(click).toHaveBeenCalledTimes(1)
    expect(revoke).toHaveBeenCalledWith('blob:fake')
    expect(wrapper.text()).toContain('cv-ana.pdf baixado')
    expect(wrapper.html()).not.toContain('blob:')
  })

  it('shows the API error when the document cannot be read (404)', async () => {
    vi.mocked(applicationApi.readResume).mockRejectedValue(new ApiError(404, 'APPLICATION_NOT_FOUND', messageFor('APPLICATION_NOT_FOUND')))
    const { wrapper } = await open([app('1', 'SUBMITTED')])
    await btn(wrapper as never, 'Ver currículo enviado').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('não encontrada')
  })
})

describe('selection process', () => {
  it('offers only valid actions for each status (final states and suspended have none)', async () => {
    const { wrapper } = await open([app('1', 'SUBMITTED'), app('2', 'IN_REVIEW'), app('3', 'APPROVED'), app('4', 'REJECTED'), app('5', 'SUSPENDED')])
    const labels = (i: number) => wrapper.findAll('article')[i]!.findAll('button').map((b) => b.text()).filter((t) => !t.includes('currículo'))
    expect(labels(0)).toEqual(['Iniciar análise', 'Aprovar', 'Rejeitar'])
    expect(labels(1)).toEqual(['Aprovar', 'Rejeitar'])
    expect(labels(2)).toEqual([])
    expect(labels(3)).toEqual([])
    expect(labels(4)).toEqual(['Reativar candidatura'])
    expect(wrapper.findAll('article')[2]!.text()).toContain('Decisão final')
  })

  it('moves SUBMITTED to IN_REVIEW directly and updates the card', async () => {
    vi.mocked(companyApplicationApi.changeStatus).mockResolvedValue({ id: '1', previousStatus: 'SUBMITTED', status: 'IN_REVIEW', reason: null, updatedAt: '' })
    const { wrapper } = await open([app('1', 'SUBMITTED')])
    await btn(wrapper as never, 'Iniciar análise').trigger('click')
    await settle()
    expect(companyApplicationApi.changeStatus).toHaveBeenCalledWith('1', 'IN_REVIEW', '')
    const text = wrapper.get('article').text()
    expect(text).toContain('Em análise')
    expect(text).toContain('Enviada → Em análise')
    expect(wrapper.get('article').findAll('button').map((b) => b.text())).not.toContain('Iniciar análise')
  })

  it('approving asks for confirmation, warns it is final, and the reason is optional', async () => {
    vi.mocked(companyApplicationApi.changeStatus).mockResolvedValue({ id: '1', previousStatus: 'IN_REVIEW', status: 'APPROVED', reason: null, updatedAt: '' })
    const { wrapper } = await open([app('1', 'IN_REVIEW')])
    await btn(wrapper as never, 'Aprovar').trigger('click')
    expect(wrapper.text()).toContain('Aprovar esta candidatura?')
    expect(wrapper.text()).toContain('final')
    expect(companyApplicationApi.changeStatus).not.toHaveBeenCalled()
    await btn(wrapper as never, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(companyApplicationApi.changeStatus).toHaveBeenCalledWith('1', 'APPROVED', '')
    expect(wrapper.get('article').text()).toContain('Aprovada')
    expect(wrapper.get('article').text()).toContain('Decisão final')
  })

  it('rejecting requires a reason (no request without it) and limits it to 500 characters', async () => {
    vi.mocked(companyApplicationApi.changeStatus).mockResolvedValue({ id: '1', previousStatus: 'SUBMITTED', status: 'REJECTED', reason: 'Perfil fora', updatedAt: '' })
    const { wrapper } = await open([app('1', 'SUBMITTED')])
    await btn(wrapper as never, 'Rejeitar').trigger('click')
    await btn(wrapper as never, 'Confirmar rejeição').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da rejeição.')
    expect(document.activeElement).toBe(wrapper.get('textarea[name="reason"]').element)
    expect(companyApplicationApi.changeStatus).not.toHaveBeenCalled()
    const field = wrapper.get('textarea[name="reason"]')
    expect(field.attributes('maxlength')).toBe('500')
    await field.setValue('x'.repeat(501))
    await btn(wrapper as never, 'Confirmar rejeição').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('até 500 caracteres')
    expect(companyApplicationApi.changeStatus).not.toHaveBeenCalled()
    await field.setValue('Perfil fora')
    await btn(wrapper as never, 'Confirmar rejeição').trigger('click')
    await settle()
    expect(companyApplicationApi.changeStatus).toHaveBeenCalledWith('1', 'REJECTED', 'Perfil fora')
    expect(wrapper.get('article').text()).toContain('Rejeitada')
  })

  it('cancelling a decision changes nothing', async () => {
    const { wrapper } = await open([app('1', 'SUBMITTED')])
    await btn(wrapper as never, 'Rejeitar').trigger('click')
    await btn(wrapper as never, 'Cancelar').trigger('click')
    expect(wrapper.text()).not.toContain('Rejeitar esta candidatura?')
    expect(companyApplicationApi.changeStatus).not.toHaveBeenCalled()
  })

  it('the backend stays the authority: a 409 transition and a 403 validation are shown in company wording', async () => {
    vi.mocked(companyApplicationApi.changeStatus).mockRejectedValue(new ApiError(409, 'INVALID_APPLICATION_STATUS_TRANSITION', messageFor('INVALID_APPLICATION_STATUS_TRANSITION')))
    const { wrapper } = await open([app('1', 'SUBMITTED')])
    await btn(wrapper as never, 'Iniciar análise').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('não é permitida')
    resetDom()
    vi.mocked(companyApplicationApi.changeStatus).mockRejectedValue(new ApiError(403, 'STUDENT_NOT_VALIDATED', messageFor('STUDENT_NOT_VALIDATED')))
    const other = await open([app('2', 'SUBMITTED')])
    await btn(other.wrapper as never, 'Iniciar análise').trigger('click')
    await settle()
    const text = other.wrapper.get('[role="alert"]').text()
    expect(text).toContain('O estudante não tem')
    expect(text).not.toContain('Você só pode se candidatar')
  })

  it('a suspended application cannot advance: only an explicit reactivation, with the conditions explained', async () => {
    vi.mocked(companyApplicationApi.reactivate).mockResolvedValue({ id: '1', previousStatus: 'SUSPENDED', status: 'IN_REVIEW', updatedAt: '' })
    const { wrapper } = await open([app('1', 'SUSPENDED')])
    const text = wrapper.get('article').text()
    expect(text).toContain('vínculo acadêmico do estudante deixou de valer')
    expect(text).toContain('vínculo aprovado e vigente')
    expect(text).toContain('vaga aberta')
    await btn(wrapper as never, 'Reativar candidatura').trigger('click')
    expect(companyApplicationApi.reactivate).not.toHaveBeenCalled()
    await btn(wrapper as never, 'Confirmar reativação').trigger('click')
    await settle()
    expect(companyApplicationApi.reactivate).toHaveBeenCalledWith('1')
    expect(wrapper.get('article').text()).toContain('Em análise')
    expect(wrapper.get('article').text()).toContain('Suspensa → Em análise')
  })

  it('shows why a reactivation was refused (409 job not available)', async () => {
    vi.mocked(companyApplicationApi.reactivate).mockRejectedValue(new ApiError(409, 'JOB_NOT_AVAILABLE', messageFor('JOB_NOT_AVAILABLE')))
    const { wrapper } = await open([app('1', 'SUSPENDED')])
    await btn(wrapper as never, 'Reativar candidatura').trigger('click')
    await btn(wrapper as never, 'Confirmar reativação').trigger('click')
    await settle()
    expect(wrapper.text()).toContain('A vaga precisa estar aberta')
    expect(wrapper.get('article').text()).toContain('Suspensa')
  })
})

describe('companyApplicationApi', () => {
  it('sends PATCH with status and a trimmed reason, omitting a blank one', async () => {
    const real = (await vi.importActual<typeof import('../api/companyApplicationApi')>('../api/companyApplicationApi')).companyApplicationApi
    const patch = vi.spyOn(http, 'patch').mockResolvedValue({ data: {} })
    await real.changeStatus('1', 'REJECTED', '  Perfil fora  ')
    expect(patch).toHaveBeenCalledWith('/applications/1/status', { status: 'REJECTED', reason: 'Perfil fora' })
    await real.changeStatus('1', 'APPROVED', '   ')
    expect(patch).toHaveBeenLastCalledWith('/applications/1/status', { status: 'APPROVED', reason: undefined })
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: {} })
    await real.reactivate('1')
    expect(post).toHaveBeenCalledWith('/applications/1/reactivate')
  })
})
