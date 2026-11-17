import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { resetAreasCache } from '@/features/students/composables/useAreas'
import { studentApi } from '@/features/students/api/studentApi'
import { jobApi } from '@/features/jobs/api/jobApi'
import { adminApi } from '../api/adminApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('@/features/students/api/studentApi', () => ({ studentApi: { listAreas: vi.fn() } }))
vi.mock('@/features/jobs/api/jobApi', () => ({ jobApi: { search: vi.fn(), get: vi.fn() } }))
vi.mock('../api/adminApi', () => ({
  adminApi: {
    listAffiliations: vi.fn(), approveAffiliation: vi.fn(), rejectAffiliation: vi.fn(), revokeAffiliation: vi.fn(), listAuditLogs: vi.fn(),
    closeJob: vi.fn(), reopenJob: vi.fn(), deleteJob: vi.fn(), listJobApplications: vi.fn(), changeApplicationStatus: vi.fn(), reactivateApplication: vi.fn(),
    getCompany: vi.fn(), updateCompany: vi.fn(),
  },
}))

const uniSummary = { id: 'u1', name: 'Universidade de Garanhuns', acronym: 'UG', location: 'Garanhuns - PE', website: null }
const aff = (id: string, status: string, extra: Record<string, unknown> = {}) => ({
  id, status, university: uniSummary, requestedAt: '2026-11-10T10:00:00Z', decidedAt: null, decisionReason: null, revokedAt: null, revocationReason: null,
  student: { id: `s-${id}`, fullName: `Estudante ${id}`, course: 'Sistemas', institution: 'UFAPE' }, ...extra,
})
const page = (content: unknown[], totalPages = 1, total = content.length) => ({ content, page: 0, size: 10, totalElements: total, totalPages })
type W = { findAll: (s: string) => { text: () => string; trigger: (e: string) => Promise<void> }[] }
const btn = (w: unknown, text: string) => (w as W).findAll('button').find((b) => b.text() === text)!
const job = (status = 'OPEN') => ({
  id: 'j1', companyId: 'c1', companyName: 'Oficina Digital', title: 'Estágio Backend', description: 'Atuar com APIs.', area: 'BACKEND', location: 'Garanhuns - PE', workModel: 'HYBRID',
  level: 'INTERNSHIP', status, requiredSkills: [{ id: 's1', name: 'Java' }], desiredSkills: [], createdAt: '2026-11-10T10:00:00Z', updatedAt: '',
})

beforeEach(() => {
  resetDom()
  resetAreasCache()
  vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }])
})

describe('admin affiliations', () => {
  const open = (list: unknown[], path = '/admin/vinculos') => {
    vi.mocked(adminApi.listAffiliations).mockResolvedValue(page(list) as never)
    return mountAt(path, 'ADMIN')
  }

  it('lists requests of every university (showing which one) and filters through the API and the URL', async () => {
    const { wrapper, router } = await open([aff('1', 'PENDING')])
    expect(adminApi.listAffiliations).toHaveBeenLastCalledWith('PENDING', 0)
    expect(wrapper.get('article').text()).toContain('Universidade: Universidade de Garanhuns (UG)')
    expect(wrapper.get('main').text()).not.toContain('@')
    await wrapper.get('select[name="status"]').setValue('REVOKED')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ status: 'REVOKED' })
    expect(adminApi.listAffiliations).toHaveBeenLastCalledWith('REVOKED', 0)
  })

  it('offers only valid actions per status (history has none)', async () => {
    const { wrapper } = await open([aff('1', 'PENDING'), aff('2', 'APPROVED'), aff('3', 'REJECTED'), aff('4', 'REVOKED')], '/admin/vinculos?status=ALL')
    const labels = (i: number) => wrapper.findAll('article')[i]!.findAll('button').map((b) => b.text())
    expect(labels(0)).toEqual(['Aprovar vínculo', 'Rejeitar solicitação'])
    expect(labels(1)).toEqual(['Revogar vínculo'])
    expect(labels(2)).toEqual([])
    expect(labels(3)).toEqual([])
  })

  it('the admin must give a reason even to approve (backend contract), and the call uses the admin route', async () => {
    vi.mocked(adminApi.approveAffiliation).mockResolvedValue(aff('1', 'APPROVED') as never)
    const { wrapper } = await open([aff('1', 'PENDING')])
    await btn(wrapper, 'Aprovar vínculo').trigger('click')
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da aprovação.')
    expect(adminApi.approveAffiliation).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Matrícula conferida pela secretaria')
    vi.mocked(adminApi.listAffiliations).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(adminApi.approveAffiliation).toHaveBeenCalledWith('1', 'Matrícula conferida pela secretaria')
    expect(wrapper.get('[role="status"]').text()).toContain('aprovado por intervenção administrativa')
  })

  it('rejecting and revoking require a reason; revoking explains the automatic suspension and never calls applications', async () => {
    vi.mocked(adminApi.revokeAffiliation).mockResolvedValue(aff('2', 'REVOKED') as never)
    const { wrapper } = await open([aff('2', 'APPROVED')])
    await btn(wrapper, 'Revogar vínculo').trigger('click')
    expect(wrapper.text()).toContain('suspende automaticamente as candidaturas')
    await btn(wrapper, 'Confirmar revogação').trigger('click')
    await flushPromises()
    expect(adminApi.revokeAffiliation).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Trancamento')
    vi.mocked(adminApi.listAffiliations).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar revogação').trigger('click')
    await settle()
    expect(adminApi.revokeAffiliation).toHaveBeenCalledWith('2', 'Trancamento')
    expect(adminApi.changeApplicationStatus).not.toHaveBeenCalled()
    expect(adminApi.reactivateApplication).not.toHaveBeenCalled()
    expect(wrapper.get('[role="status"]').text()).toContain('suspensas automaticamente')
  })

  it('a concurrent decision (409) is explained with a refresh action', async () => {
    vi.mocked(adminApi.rejectAffiliation).mockRejectedValue(new ApiError(409, 'AFFILIATION_ALREADY_DECIDED', messageFor('AFFILIATION_ALREADY_DECIDED')))
    const { wrapper } = await open([aff('1', 'PENDING')])
    await btn(wrapper, 'Rejeitar solicitação').trigger('click')
    await wrapper.get('textarea[name="reason"]').setValue('Motivo')
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('já foi analisada')
    expect(btn(wrapper, 'Atualizar lista')).toBeDefined()
  })
})

describe('audit log', () => {
  const log = (id: string, extra: Record<string, unknown> = {}) => ({
    id, adminId: '1', action: 'USER_SUSPENDED', targetType: 'USER', targetId: 'user-123', reason: 'Uso indevido', previousValue: 'ACTIVE', newValue: 'SUSPENDED', createdAt: '2026-11-17T13:05:00Z', ...extra,
  })

  it('shows what the backend returns: action, target, admin, reason, before and after, time — and nothing invented', async () => {
    vi.mocked(adminApi.listAuditLogs).mockResolvedValue(page([log('a'), log('b', { action: 'JOB_CLOSED', targetType: 'JOB', targetId: 'job-9', adminId: 'outro-admin', previousValue: null, newValue: null, reason: null })]) as never)
    const { wrapper } = await mountAt('/admin/auditoria', 'ADMIN')
    expect(adminApi.listAuditLogs).toHaveBeenCalledWith(0)
    const [first, second] = wrapper.findAll('li').filter((l) => l.find('h2').exists()).map((l) => l.text())
    for (const t of ['Conta suspensa', 'Conta: user-123', 'Administrador: 1 (você)', 'Motivo: Uso indevido', 'ACTIVE → SUSPENDED']) expect(first).toContain(t)
    expect(second).toContain('Vaga encerrada')
    expect(second).toContain('Administrador: outro-admin')
    expect(second).not.toContain('(você)')
    expect(second).not.toContain('Motivo:')
    expect(second).not.toContain('→')
  })

  it('links only the targets that have a screen, and has no way to create or edit entries', async () => {
    vi.mocked(adminApi.listAuditLogs).mockResolvedValue(page([log('a'), log('b', { targetType: 'JOB', targetId: 'job-9' }), log('c', { targetType: 'COMPANY', targetId: 'co-1' }), log('d', { targetType: 'AFFILIATION', targetId: 'af-1' })]) as never)
    const { wrapper } = await mountAt('/admin/auditoria', 'ADMIN')
    expect(wrapper.find('a[href="/admin/usuarios/user-123"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/admin/vagas/job-9"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/admin/empresas/co-1"]').exists()).toBe(true)
    expect(wrapper.get('main').text()).toContain('af-1')
    expect(wrapper.findAll('main a').map((a) => a.attributes('href')).join(' ')).not.toContain('af-1')
    expect(wrapper.findAll('main button').filter((b) => !/Anterior|Próxima/.test(b.text()))).toHaveLength(0)
    expect(wrapper.find('main form').exists()).toBe(false)
  })

  it('paginates with the API page, and has an empty state', async () => {
    vi.mocked(adminApi.listAuditLogs).mockResolvedValue({ ...page([log('a')]), totalPages: 3 } as never)
    const { wrapper } = await mountAt('/admin/auditoria', 'ADMIN')
    await btn(wrapper, 'Próxima').trigger('click')
    await settle()
    expect(adminApi.listAuditLogs).toHaveBeenLastCalledWith(1)
    resetDom()
    vi.mocked(adminApi.listAuditLogs).mockResolvedValue(page([]) as never)
    expect((await mountAt('/admin/auditoria', 'ADMIN')).wrapper.text()).toContain('Nenhuma ação registrada')
  })

  it('shows a failure with retry', async () => {
    vi.mocked(adminApi.listAuditLogs).mockRejectedValueOnce(new ApiError(503, 'SERVICE_UNAVAILABLE', 'Servidor indisponível.')).mockResolvedValue(page([]) as never)
    const { wrapper } = await mountAt('/admin/auditoria', 'ADMIN')
    expect(wrapper.get('[role="alert"]').text()).toContain('indisponível')
    await btn(wrapper, 'Tentar novamente').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Nenhuma ação registrada')
  })
})

describe('admin jobs and interventions', () => {
  it('browses open jobs through GET /jobs with a real search, linking to the detail', async () => {
    vi.mocked(jobApi.search).mockResolvedValue(page([job()]) as never)
    const { wrapper, router } = await mountAt('/admin/vagas', 'ADMIN')
    expect(wrapper.find('a[href="/admin/vagas/j1"]').exists()).toBe(true)
    await wrapper.get('input[name="busca"]').setValue('java')
    await wrapper.get('form[aria-label="Buscar vagas"]').trigger('submit')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ busca: 'java' })
    expect(jobApi.search).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'java' }), 0)
  })

  it('closes with a reason and reflects the backend answer; reopening is offered for a closed job', async () => {
    vi.mocked(jobApi.get).mockResolvedValue(job('OPEN') as never)
    vi.mocked(adminApi.closeJob).mockResolvedValue(job('CLOSED') as never)
    vi.mocked(adminApi.reopenJob).mockResolvedValue(job('OPEN') as never)
    const { wrapper } = await mountAt('/admin/vagas/j1', 'ADMIN')
    await btn(wrapper, 'Encerrar vaga').trigger('click')
    await btn(wrapper, 'Confirmar encerramento').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da intervenção.')
    expect(adminApi.closeJob).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Vaga duplicada')
    await btn(wrapper, 'Confirmar encerramento').trigger('click')
    await settle()
    expect(adminApi.closeJob).toHaveBeenCalledWith('j1', 'Vaga duplicada')
    expect(wrapper.text()).toContain('Encerrada')
    expect(btn(wrapper, 'Reabrir vaga')).toBeDefined()
    expect(wrapper.text()).toContain('Vaga encerrada por intervenção administrativa')
    await btn(wrapper, 'Reabrir vaga').trigger('click')
    await wrapper.get('textarea[name="reason"]').setValue('Revisão concluída')
    await btn(wrapper, 'Confirmar reabertura').trigger('click')
    await settle()
    expect(adminApi.reopenJob).toHaveBeenCalledWith('j1', 'Revisão concluída')
  })

  it('deleting asks for confirmation and a reason, then goes back to the list with a notice', async () => {
    vi.mocked(jobApi.get).mockResolvedValue(job() as never)
    vi.mocked(jobApi.search).mockResolvedValue(page([]) as never)
    vi.mocked(adminApi.deleteJob).mockResolvedValue(undefined)
    const { wrapper, router } = await mountAt('/admin/vagas/j1', 'ADMIN')
    await btn(wrapper, 'Excluir vaga').trigger('click')
    expect(wrapper.text()).toContain('candidaturas já recebidas são mantidas')
    await wrapper.get('textarea[name="reason"]').setValue('Conteúdo indevido')
    await btn(wrapper, 'Confirmar exclusão').trigger('click')
    await settle()
    expect(adminApi.deleteJob).toHaveBeenCalledWith('j1', 'Conteúdo indevido')
    expect(router.currentRoute.value.name).toBe('admin-jobs')
    expect(router.currentRoute.value.query.aviso).toBe('excluida')
  })

  it('links to the company profile and the applications; 404 is explained', async () => {
    vi.mocked(jobApi.get).mockResolvedValue(job() as never)
    const { wrapper } = await mountAt('/admin/vagas/j1', 'ADMIN')
    expect(wrapper.find('a[href="/admin/empresas/c1"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/admin/vagas/j1/candidaturas"]').exists()).toBe(true)
    resetDom()
    vi.mocked(jobApi.get).mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', messageFor('JOB_NOT_FOUND')))
    const missing = await mountAt('/admin/vagas/zz', 'ADMIN')
    expect(missing.wrapper.get('[role="alert"]').text()).toContain('Vaga não encontrada ou excluída')
  })

  it('applications of a job: reads through the admin route and decides with the same rules and the admin endpoints', async () => {
    vi.mocked(jobApi.get).mockResolvedValue(job() as never)
    const application = (id: string, status: string) => ({ id, status, resumeType: 'PLATFORM', appliedAt: '2026-11-12T10:00:00Z', updatedAt: '', matchScore: null, student: { id: `s${id}`, fullName: `Estudante ${id}`, course: 'Sistemas', location: 'Garanhuns - PE', summary: null, skills: [], interestAreas: [] } })
    vi.mocked(adminApi.listJobApplications).mockResolvedValue(page([application('1', 'SUBMITTED'), application('2', 'SUSPENDED')]) as never)
    vi.mocked(adminApi.changeApplicationStatus).mockResolvedValue({ id: '1', previousStatus: 'SUBMITTED', status: 'IN_REVIEW', reason: null, updatedAt: '' })
    vi.mocked(adminApi.reactivateApplication).mockResolvedValue({ id: '2', previousStatus: 'SUSPENDED', status: 'IN_REVIEW', updatedAt: '' })
    const { wrapper } = await mountAt('/admin/vagas/j1/candidaturas', 'ADMIN')
    expect(adminApi.listJobApplications).toHaveBeenCalledWith('j1', '', 0)
    expect(wrapper.findAll('article')[0]!.text()).toContain('Sem dados suficientes')
    const labels = (i: number) => wrapper.findAll('article')[i]!.findAll('button').map((b) => b.text()).filter((t) => !t.includes('currículo'))
    expect(labels(0)).toEqual(['Iniciar análise', 'Aprovar', 'Rejeitar'])
    expect(labels(1)).toEqual(['Reativar candidatura'])
    await wrapper.findAll('article')[0]!.findAll('button').find((b) => b.text() === 'Iniciar análise')!.trigger('click')
    await settle()
    expect(adminApi.changeApplicationStatus).toHaveBeenCalledWith('1', 'IN_REVIEW', '')
    await wrapper.findAll('article')[1]!.findAll('button').find((b) => b.text() === 'Reativar candidatura')!.trigger('click')
    await btn(wrapper, 'Confirmar reativação').trigger('click')
    await settle()
    expect(adminApi.reactivateApplication).toHaveBeenCalledWith('2')
  })

  it('company intervention requires a reason, sends profile and reason, and confirms', async () => {
    vi.mocked(adminApi.getCompany).mockResolvedValue({ id: 'c1', name: 'Oficina Digital', sector: 'Software', description: null, location: 'Garanhuns - PE' })
    vi.mocked(adminApi.updateCompany).mockResolvedValue({ id: 'c1', name: 'Oficina Nova', sector: 'Software', description: null, location: 'Garanhuns - PE' })
    const { wrapper } = await mountAt('/admin/empresas/c1', 'ADMIN')
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe('Oficina Digital')
    await wrapper.get('input[name="name"]').setValue('Oficina Nova')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da intervenção.')
    expect(adminApi.updateCompany).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Nome corrigido a pedido da empresa')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(adminApi.updateCompany).toHaveBeenCalledWith('c1', { profile: { name: 'Oficina Nova', sector: 'Software', location: 'Garanhuns - PE' }, reason: 'Nome corrigido a pedido da empresa' })
    expect(wrapper.text()).toContain('registrado na auditoria')
  })
})
