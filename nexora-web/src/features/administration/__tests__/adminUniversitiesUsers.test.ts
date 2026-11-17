import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { adminApi } from '../api/adminApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/adminApi', () => ({
  adminApi: {
    listUniversities: vi.fn(), approveUniversity: vi.fn(), rejectUniversity: vi.fn(), listUsers: vi.fn(), getUser: vi.fn(), suspendUser: vi.fn(),
    reactivateUser: vi.fn(), listAffiliations: vi.fn(),
  },
}))

const uni = (id: string, approvalStatus: string, extra: Record<string, unknown> = {}) => ({
  id, userId: `u-${id}`, email: `uni${id}@teste.nexora.dev`, accountStatus: approvalStatus === 'APPROVED' ? 'ACTIVE' : approvalStatus === 'REJECTED' ? 'REJECTED' : 'PENDING_APPROVAL',
  name: `Faculdade ${id}`, acronym: 'FT', location: 'Garanhuns - PE', website: 'https://ft.edu.br', description: 'Instituição de teste.', approvalStatus,
  approvalReason: null, approvalDecidedBy: null, approvalDecidedAt: null, createdAt: '2026-11-10T10:00:00Z', ...extra,
})
const user = (id: string, role: string, status: string) => ({ id, email: `${id}@teste.nexora.dev`, role, status, createdAt: '2026-11-10T10:00:00Z' })
const page = (content: unknown[], totalPages = 1, total = content.length) => ({ content, page: 0, size: 10, totalElements: total, totalPages })
type W = { findAll: (s: string) => { text: () => string; trigger: (e: string) => Promise<void> }[] }
const btn = (w: unknown, text: string) => (w as W).findAll('button').find((b) => b.text() === text)!

beforeEach(() => resetDom())

describe('admin universities', () => {
  const open = (list: unknown[], path = '/admin/universidades') => {
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page(list) as never)
    return mountAt(path, 'ADMIN')
  }

  it('defaults to the approval queue and shows the institutional data the API authorizes', async () => {
    const { wrapper } = await open([uni('1', 'PENDING')])
    expect(adminApi.listUniversities).toHaveBeenLastCalledWith('PENDING', 0)
    const text = wrapper.get('article').text()
    for (const t of ['Faculdade 1 (FT)', 'Garanhuns - PE', 'uni1@teste.nexora.dev', 'https://ft.edu.br', 'Instituição de teste.', 'Aguardando aprovação', 'Cadastrada em: 10 de novembro de 2026']) expect(text).toContain(t)
  })

  it('the filter really filters through the API and the URL; empty states differ', async () => {
    const { wrapper, router } = await open([uni('1', 'PENDING')])
    await wrapper.get('select[name="status"]').setValue('REJECTED')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ status: 'REJECTED' })
    expect(adminApi.listUniversities).toHaveBeenLastCalledWith('REJECTED', 0)
    await wrapper.get('select[name="status"]').setValue('ALL')
    await settle()
    expect(adminApi.listUniversities).toHaveBeenLastCalledWith('', 0)
    resetDom()
    expect((await open([])).wrapper.text()).toContain('Nenhum cadastro aguardando aprovação')
    resetDom()
    expect((await open([], '/admin/universidades?status=APPROVED')).wrapper.text()).toContain('Nenhuma universidade com situação “aprovadas”')
  })

  it('only a pending registration offers decisions; decided ones are history without actions', async () => {
    const { wrapper } = await open([uni('1', 'PENDING'), uni('2', 'APPROVED', { approvalDecidedAt: '2026-11-11T10:00:00Z', approvalReason: 'ok' }), uni('3', 'REJECTED', { approvalDecidedAt: '2026-11-11T10:00:00Z', approvalReason: 'Documentos inválidos' })], '/admin/universidades?status=ALL')
    const labels = (i: number) => wrapper.findAll('article')[i]!.findAll('button').map((b) => b.text())
    expect(labels(0)).toEqual(['Aprovar universidade', 'Rejeitar cadastro'])
    expect(labels(1)).toEqual([])
    expect(labels(2)).toEqual([])
    expect(wrapper.findAll('article')[2]!.text()).toContain('Motivo: Documentos inválidos')
    expect(wrapper.findAll('article')[1]!.text()).toContain('Cadastro já analisado')
  })

  it('approving asks for confirmation (cancel changes nothing), then reloads from the backend', async () => {
    vi.mocked(adminApi.approveUniversity).mockResolvedValue(uni('1', 'APPROVED') as never)
    const { wrapper } = await open([uni('1', 'PENDING')])
    await btn(wrapper, 'Aprovar universidade').trigger('click')
    expect(wrapper.text()).toContain('Aprovar Faculdade 1?')
    expect(wrapper.text()).toContain('poderá entrar na plataforma')
    await btn(wrapper, 'Cancelar').trigger('click')
    expect(adminApi.approveUniversity).not.toHaveBeenCalled()
    await btn(wrapper, 'Aprovar universidade').trigger('click')
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(adminApi.approveUniversity).toHaveBeenCalledWith('1', '')
    expect(adminApi.listUniversities).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="status"]').text()).toContain('Faculdade 1 aprovada')
    expect(wrapper.find('article').exists()).toBe(false)
  })

  it('rejecting requires a reason (focus on the field, nothing sent) and then sends it', async () => {
    vi.mocked(adminApi.rejectUniversity).mockResolvedValue(uni('1', 'REJECTED') as never)
    const { wrapper } = await open([uni('1', 'PENDING')])
    await btn(wrapper, 'Rejeitar cadastro').trigger('click')
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da intervenção.')
    expect(document.activeElement).toBe(wrapper.get('textarea[name="reason"]').element)
    expect(adminApi.rejectUniversity).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Documentos inválidos')
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await settle()
    expect(adminApi.rejectUniversity).toHaveBeenCalledWith('1', 'Documentos inválidos')
    expect(wrapper.get('[role="status"]').text()).toContain('Cadastro de Faculdade 1 rejeitado')
  })

  it('a concurrent decision (409) is explained and the list can be refreshed', async () => {
    vi.mocked(adminApi.approveUniversity).mockRejectedValue(new ApiError(409, 'UNIVERSITY_ALREADY_DECIDED', messageFor('UNIVERSITY_ALREADY_DECIDED')))
    const { wrapper } = await open([uni('1', 'PENDING')])
    await btn(wrapper, 'Aprovar universidade').trigger('click')
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('já foi analisado')
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page([uni('1', 'APPROVED')]) as never)
    await btn(wrapper, 'Atualizar lista').trigger('click')
    await settle()
    expect(wrapper.get('article').text()).toContain('Aprovada')
  })

  it.each([['ACCESS_DENIED', 403, 'não tem permissão'], ['UNIVERSITY_NOT_FOUND', 404, 'Universidade não encontrada'], ['ACCOUNT_SUSPENDED', 403, 'conta está suspensa']])('shows %s in Portuguese', async (code, status, text) => {
    vi.mocked(adminApi.rejectUniversity).mockRejectedValue(new ApiError(status as number, code as string, messageFor(code as string)))
    const { wrapper } = await open([uni('1', 'PENDING')])
    await btn(wrapper, 'Rejeitar cadastro').trigger('click')
    await wrapper.get('textarea[name="reason"]').setValue('Motivo')
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain(text)
  })
})

describe('admin accounts', () => {
  const open = (list: unknown[], path = '/admin/usuarios') => {
    vi.mocked(adminApi.listUsers).mockResolvedValue(page(list) as never)
    return mountAt(path, 'ADMIN')
  }

  it('lists accounts and the role and status filters really filter through the API and the URL', async () => {
    const { wrapper, router } = await open([user('a', 'STUDENT', 'ACTIVE')])
    expect(adminApi.listUsers).toHaveBeenLastCalledWith('', '', 0)
    await wrapper.get('select[name="papel"]').setValue('UNIVERSITY')
    await settle()
    expect(adminApi.listUsers).toHaveBeenLastCalledWith('UNIVERSITY', '', 0)
    await wrapper.get('select[name="situacao"]').setValue('SUSPENDED')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ papel: 'UNIVERSITY', situacao: 'SUSPENDED' })
    expect(adminApi.listUsers).toHaveBeenLastCalledWith('UNIVERSITY', 'SUSPENDED', 0)
  })

  it('has an empty state with a way to clear the filters', async () => {
    const { wrapper } = await open([], '/admin/usuarios?papel=COMPANY&situacao=REJECTED')
    expect(wrapper.text()).toContain('Nenhuma conta com estes filtros')
    expect(btn(wrapper, 'Limpar filtros')).toBeDefined()
  })

  it('offers only valid actions per status; the admin cannot suspend their own account', async () => {
    const { wrapper } = await open([user('1', 'ADMIN', 'ACTIVE'), user('b', 'STUDENT', 'ACTIVE'), user('c', 'COMPANY', 'SUSPENDED'), user('d', 'UNIVERSITY', 'PENDING_APPROVAL'), user('e', 'UNIVERSITY', 'REJECTED')])
    const cards = wrapper.findAll('article')
    const labels = (i: number) => cards[i]!.findAll('button').map((b) => b.text())
    expect(labels(0)).toEqual([]) // id '1' é a conta logada
    expect(cards[0]!.text()).toContain('Esta é a sua conta')
    expect(labels(1)).toEqual(['Suspender conta'])
    expect(labels(2)).toEqual(['Reativar conta'])
    expect(labels(3)).toEqual([])
    expect(cards[3]!.text()).toContain('Aguardando aprovação do cadastro')
    expect(cards[3]!.find('a[href="/admin/universidades"]').exists()).toBe(true)
    expect(labels(4)).toEqual([])
    expect(cards[4]!.text()).toContain('Cadastro rejeitado')
  })

  it('suspending needs confirmation and a reason, explains the consequence, and reloads from the backend', async () => {
    vi.mocked(adminApi.suspendUser).mockResolvedValue(user('b', 'STUDENT', 'SUSPENDED') as never)
    const { wrapper } = await open([user('b', 'STUDENT', 'ACTIVE')])
    await btn(wrapper, 'Suspender conta').trigger('click')
    expect(wrapper.text()).toContain('bloqueia todas as operações da conta')
    await btn(wrapper, 'Confirmar suspensão').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da intervenção.')
    expect(adminApi.suspendUser).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Uso indevido')
    vi.mocked(adminApi.listUsers).mockResolvedValue(page([user('b', 'STUDENT', 'SUSPENDED')]) as never)
    await btn(wrapper, 'Confirmar suspensão').trigger('click')
    await settle()
    expect(adminApi.suspendUser).toHaveBeenCalledWith('b', 'Uso indevido')
    expect(wrapper.get('[role="status"]').text()).toContain('Conta de b@teste.nexora.dev suspensa')
    expect(wrapper.get('article').text()).toContain('Suspensa')
    expect(wrapper.get('article').findAll('button').map((b) => b.text())).toEqual(['Reativar conta'])
  })

  it('suspending a university explains the cascade on in-progress applications and that reactivating does not restore them', async () => {
    const { wrapper } = await open([user('u', 'UNIVERSITY', 'ACTIVE')])
    await btn(wrapper, 'Suspender conta').trigger('click')
    const text = wrapper.text()
    expect(text).toContain('suspende automaticamente as candidaturas em andamento')
    expect(text).toContain('não reativa essas candidaturas')
  })

  it('reactivating asks for confirmation and a reason', async () => {
    vi.mocked(adminApi.reactivateUser).mockResolvedValue(user('c', 'COMPANY', 'ACTIVE') as never)
    const { wrapper } = await open([user('c', 'COMPANY', 'SUSPENDED')])
    await btn(wrapper, 'Reativar conta').trigger('click')
    await btn(wrapper, 'Confirmar reativação').trigger('click')
    await flushPromises()
    expect(adminApi.reactivateUser).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Revisão concluída')
    vi.mocked(adminApi.listUsers).mockResolvedValue(page([user('c', 'COMPANY', 'ACTIVE')]) as never)
    await btn(wrapper, 'Confirmar reativação').trigger('click')
    await settle()
    expect(adminApi.reactivateUser).toHaveBeenCalledWith('c', 'Revisão concluída')
    expect(wrapper.get('article').text()).toContain('Ativa')
  })

  it('the backend stays the authority: a 409 transition and a 409 self-suspension are explained, with refresh', async () => {
    vi.mocked(adminApi.suspendUser).mockRejectedValue(new ApiError(409, 'INVALID_STATUS_TRANSITION', messageFor('INVALID_STATUS_TRANSITION')))
    const { wrapper } = await open([user('b', 'STUDENT', 'ACTIVE')])
    await btn(wrapper, 'Suspender conta').trigger('click')
    await wrapper.get('textarea[name="reason"]').setValue('Motivo')
    await btn(wrapper, 'Confirmar suspensão').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('não é permitida')
    expect(btn(wrapper, 'Atualizar lista')).toBeDefined()
    resetDom()
    vi.mocked(adminApi.suspendUser).mockRejectedValue(new ApiError(409, 'CANNOT_SUSPEND_SELF', messageFor('CANNOT_SUSPEND_SELF')))
    const self = await open([user('b', 'STUDENT', 'ACTIVE')])
    await btn(self.wrapper, 'Suspender conta').trigger('click')
    await self.wrapper.get('textarea[name="reason"]').setValue('Motivo')
    await btn(self.wrapper, 'Confirmar suspensão').trigger('click')
    await settle()
    expect(self.wrapper.get('[role="alert"]').text()).toContain('própria conta')
  })

  it('shows account detail by id with fresh data, and 404 in Portuguese', async () => {
    vi.mocked(adminApi.getUser).mockResolvedValue(user('b', 'STUDENT', 'ACTIVE') as never)
    const { wrapper } = await mountAt('/admin/usuarios/b', 'ADMIN')
    expect(adminApi.getUser).toHaveBeenCalledWith('b')
    expect(wrapper.get('main').text()).toContain('b@teste.nexora.dev')
    resetDom()
    vi.mocked(adminApi.getUser).mockRejectedValue(new ApiError(404, 'USER_NOT_FOUND', messageFor('USER_NOT_FOUND')))
    const missing = await mountAt('/admin/usuarios/zz', 'ADMIN')
    expect(missing.wrapper.get('[role="alert"]').text()).toContain('Conta não encontrada')
  })
})

describe('admin home', () => {
  it('opens on the universities waiting for approval, with real counts and shortcuts', async () => {
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page([uni('1', 'PENDING'), uni('2', 'PENDING')], 1, 6) as never)
    vi.mocked(adminApi.listAffiliations).mockResolvedValue(page([], 1, 3) as never)
    vi.mocked(adminApi.listUsers).mockResolvedValue(page([], 1, 1) as never)
    const { wrapper } = await mountAt('/admin', 'ADMIN')
    expect(adminApi.listUniversities).toHaveBeenCalledWith('PENDING', 0, 5)
    expect(adminApi.listAffiliations).toHaveBeenCalledWith('PENDING', 0, 1)
    expect(adminApi.listUsers).toHaveBeenCalledWith('', 'SUSPENDED', 0, 1)
    const text = wrapper.get('main').text()
    for (const t of ['6 cadastros aguardando aprovação', 'Mostrando 2 de 6', '3 solicitações de vínculo aguardando análise', '1 conta suspensa']) expect(text).toContain(t)
    expect(text).not.toMatch(/bem-vind/i)
    for (const href of ['/admin/usuarios', '/admin/universidades', '/admin/vagas', '/admin/auditoria', '/admin/vinculos']) expect(wrapper.find(`a[href="${href}"]`).exists()).toBe(true)
  })

  it('approves from the home and shows the empty state afterwards', async () => {
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page([uni('1', 'PENDING')]) as never)
    vi.mocked(adminApi.listAffiliations).mockResolvedValue(page([]) as never)
    vi.mocked(adminApi.listUsers).mockResolvedValue(page([]) as never)
    vi.mocked(adminApi.approveUniversity).mockResolvedValue(uni('1', 'APPROVED') as never)
    const { wrapper } = await mountAt('/admin', 'ADMIN')
    await btn(wrapper, 'Aprovar universidade').trigger('click')
    vi.mocked(adminApi.listUniversities).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(wrapper.text()).toContain('Nenhum cadastro aguardando aprovação')
    expect(wrapper.get('[role="status"]').text()).toContain('aprovada')
  })
})
