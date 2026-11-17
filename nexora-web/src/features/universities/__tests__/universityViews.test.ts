import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { affiliationDecisionApi } from '../api/affiliationDecisionApi'
import { universityApi } from '../api/universityApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/universityApi', () => ({ universityApi: { getProfile: vi.fn(), saveProfile: vi.fn() } }))
vi.mock('../api/affiliationDecisionApi', () => ({ affiliationDecisionApi: { listReceived: vi.fn(), approve: vi.fn(), reject: vi.fn(), revoke: vi.fn() } }))

const uni = { id: 'u1', name: 'Universidade de Garanhuns', acronym: 'UG', location: 'Garanhuns - PE', website: null }
const req = (id: string, status: string, extra: Record<string, unknown> = {}) => ({
  id, status, university: uni, requestedAt: '2026-11-10T10:00:00Z', decidedAt: null, decisionReason: null, revokedAt: null, revocationReason: null,
  student: { id: `s-${id}`, fullName: `Estudante ${id}`, course: 'Sistemas de Informação', institution: 'UFAPE' }, ...extra,
})
const page = (content: unknown[], totalPages = 1, total = content.length) => ({ content, page: 0, size: 10, totalElements: total, totalPages })
const profile = { id: 'u1', name: 'Universidade de Garanhuns', acronym: 'UG', location: 'Garanhuns - PE', website: 'https://ug.edu.br', description: null }
const open = (list: unknown[], path = '/universidade/vinculos') => {
  vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page(list) as never)
  return mountAt(path, 'UNIVERSITY')
}
type W = { findAll: (s: string) => { text: () => string; trigger: (e: string) => Promise<void> }[] }
const btn = (w: unknown, text: string) => (w as W).findAll('button').find((b) => b.text() === text)!

beforeEach(() => resetDom())

describe('institutional profile', () => {
  it('shows the current data, blocks invalid values and saves trimmed ones', async () => {
    vi.mocked(universityApi.getProfile).mockResolvedValue(profile)
    vi.mocked(universityApi.saveProfile).mockResolvedValue({ ...profile, name: 'UG Nova' })
    const { wrapper } = await mountAt('/universidade/perfil', 'UNIVERSITY')
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe('Universidade de Garanhuns')
    await wrapper.get('input[name="name"]').setValue(' ')
    await wrapper.get('input[name="website"]').setValue('ug.edu.br')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o nome da instituição.')
    expect(wrapper.text()).toContain('começando com http:// ou https://')
    expect(universityApi.saveProfile).not.toHaveBeenCalled()
    await wrapper.get('input[name="name"]').setValue('  UG Nova ')
    await wrapper.get('input[name="website"]').setValue('https://ug.edu.br')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(universityApi.saveProfile).toHaveBeenCalledWith({ name: 'UG Nova', acronym: 'UG', location: 'Garanhuns - PE', website: 'https://ug.edu.br' })
    expect(wrapper.text()).toContain('Perfil salvo.')
  })

  it('maps server field errors, and shows a failure with retry when the API is unavailable', async () => {
    vi.mocked(universityApi.getProfile).mockResolvedValue(profile)
    vi.mocked(universityApi.saveProfile).mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'Revise.', { acronym: 'tamanho inválido' }))
    const ok = await mountAt('/universidade/perfil', 'UNIVERSITY')
    await ok.wrapper.get('form').trigger('submit')
    await settle()
    expect(ok.wrapper.get('input[name="acronym"]').attributes('aria-invalid')).toBe('true')
    resetDom()
    vi.mocked(universityApi.getProfile).mockRejectedValueOnce(new ApiError(503, 'SERVICE_UNAVAILABLE', 'Servidor indisponível.')).mockResolvedValue(profile)
    const down = await mountAt('/universidade/perfil', 'UNIVERSITY')
    expect(down.wrapper.get('[role="alert"]').text()).toContain('indisponível')
    await btn(down.wrapper, 'Tentar novamente').trigger('click')
    await flushPromises()
    expect(down.wrapper.find('input[name="name"]').exists()).toBe(true)
  })
})

describe('home', () => {
  it('opens on the pending queue (oldest first from the API) with the count and a link to the history', async () => {
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([req('1', 'PENDING'), req('2', 'PENDING')], 1, 7) as never)
    const { wrapper } = await mountAt('/universidade', 'UNIVERSITY')
    expect(affiliationDecisionApi.listReceived).toHaveBeenCalledWith('PENDING', 0, 5)
    const text = wrapper.get('main').text()
    expect(text).toContain('7 solicitações aguardando análise')
    expect(text).toContain('Mostrando 2 de 7')
    expect(text).not.toMatch(/bem-vind/i)
    expect(wrapper.find('a[href="/universidade/vinculos"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/universidade/perfil"]').exists()).toBe(true)
  })

  it('has an empty state when nothing is waiting', async () => {
    const { wrapper } = await open([], '/universidade')
    expect(wrapper.text()).toContain('Nenhuma solicitação aguardando análise')
  })
})

describe('received requests', () => {
  it('lists the authorized fields, dates and reasons; never an e-mail', async () => {
    const { wrapper } = await open([
      req('1', 'PENDING'),
      req('2', 'REJECTED', { decidedAt: '2026-11-11T10:00:00Z', decisionReason: 'Matrícula não localizada' }),
      req('3', 'REVOKED', { decidedAt: '2026-11-11T10:00:00Z', revokedAt: '2026-11-12T10:00:00Z', revocationReason: 'Trancamento' }),
    ], '/universidade/vinculos?status=ALL')
    const text = wrapper.get('main').text()
    for (const t of ['Estudante 1', 'Sistemas de Informação', 'Instituição informada pelo estudante: UFAPE', 'Aguardando análise', 'Rejeitado', 'Revogado', 'Solicitado em 10 de novembro de 2026', 'Matrícula não localizada', 'Trancamento', 'revogado em 12 de novembro de 2026']) expect(text).toContain(t)
    expect(text).not.toContain('@')
  })

  it('defaults to the pending queue; the status filter really filters through the API and the URL', async () => {
    const { wrapper, router } = await open([req('1', 'PENDING')])
    expect(affiliationDecisionApi.listReceived).toHaveBeenLastCalledWith('PENDING', 0)
    await wrapper.get('select[name="status"]').setValue('APPROVED')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ status: 'APPROVED' })
    expect(affiliationDecisionApi.listReceived).toHaveBeenLastCalledWith('APPROVED', 0)
    await wrapper.get('select[name="status"]').setValue('ALL')
    await settle()
    expect(affiliationDecisionApi.listReceived).toHaveBeenLastCalledWith('', 0)
    await wrapper.get('select[name="status"]').setValue('PENDING')
    await settle()
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('has distinct empty states and paginates with the API page', async () => {
    const empty = await open([])
    expect(empty.wrapper.text()).toContain('Nenhuma solicitação aguardando análise')
    resetDom()
    const filtered = await open([], '/universidade/vinculos?status=REVOKED')
    expect(filtered.wrapper.text()).toContain('Nenhuma solicitação com situação “revogado”')
    resetDom()
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([req('1', 'PENDING')], 3) as never)
    const { wrapper } = await mountAt('/universidade/vinculos', 'UNIVERSITY')
    await btn(wrapper, 'Próxima').trigger('click')
    await settle()
    expect(affiliationDecisionApi.listReceived).toHaveBeenLastCalledWith('PENDING', 1)
  })

  it('offers only valid actions per status: history entries have none', async () => {
    const { wrapper } = await open([req('1', 'PENDING'), req('2', 'APPROVED'), req('3', 'REJECTED'), req('4', 'REVOKED')], '/universidade/vinculos?status=ALL')
    const labels = (i: number) => wrapper.findAll('article')[i]!.findAll('button').map((b) => b.text())
    expect(labels(0)).toEqual(['Aprovar vínculo', 'Rejeitar solicitação'])
    expect(labels(1)).toEqual(['Revogar vínculo'])
    expect(labels(2)).toEqual([])
    expect(labels(3)).toEqual([])
    expect(wrapper.findAll('article')[2]!.text()).toContain('Registro mantido no histórico')
  })
})

describe('decisions', () => {
  it('approving asks for confirmation first (cancelling changes nothing), then trusts the reloaded list', async () => {
    vi.mocked(affiliationDecisionApi.approve).mockResolvedValue(req('1', 'APPROVED') as never)
    const { wrapper } = await open([req('1', 'PENDING')])
    await btn(wrapper, 'Aprovar vínculo').trigger('click')
    expect(wrapper.text()).toContain('Aprovar o vínculo de Estudante 1?')
    expect(wrapper.text()).toContain('poderá se candidatar')
    expect(wrapper.text().match(/\(opcional\)/g)).toHaveLength(1) // o TextField já acrescenta "(opcional)"
    await btn(wrapper, 'Cancelar').trigger('click')
    expect(affiliationDecisionApi.approve).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Aprovar o vínculo de')

    await btn(wrapper, 'Aprovar vínculo').trigger('click')
    // O backend, não a tela, diz o que mudou: a lista recarregada vem sem a solicitação.
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(affiliationDecisionApi.approve).toHaveBeenCalledWith('1', '')
    expect(affiliationDecisionApi.listReceived).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="status"]').text()).toContain('Vínculo de Estudante 1 aprovado.')
    expect(wrapper.find('article').exists()).toBe(false)
  })

  it('does not assume the decision took effect: the card shows whatever the reloaded list says', async () => {
    vi.mocked(affiliationDecisionApi.approve).mockResolvedValue(req('1', 'APPROVED') as never)
    const { wrapper } = await open([req('1', 'PENDING')], '/universidade/vinculos?status=ALL')
    await btn(wrapper, 'Aprovar vínculo').trigger('click')
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([req('1', 'REJECTED', { decisionReason: 'decidido em outra sessão' })]) as never)
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(wrapper.get('article').text()).toContain('Rejeitado')
    expect(wrapper.get('article').text()).toContain('decidido em outra sessão')
  })

  it('rejecting requires a reason (no request without it), caps it at 500 and sends it', async () => {
    vi.mocked(affiliationDecisionApi.reject).mockResolvedValue(req('1', 'REJECTED') as never)
    const { wrapper } = await open([req('1', 'PENDING')])
    await btn(wrapper, 'Rejeitar solicitação').trigger('click')
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da rejeição.')
    expect(document.activeElement).toBe(wrapper.get('textarea[name="reason"]').element)
    expect(affiliationDecisionApi.reject).not.toHaveBeenCalled()
    expect(wrapper.get('textarea[name="reason"]').attributes('maxlength')).toBe('500')
    await wrapper.get('textarea[name="reason"]').setValue('x'.repeat(501))
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('até 500 caracteres')
    await wrapper.get('textarea[name="reason"]').setValue('Matrícula não localizada')
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await settle()
    expect(affiliationDecisionApi.reject).toHaveBeenCalledWith('1', 'Matrícula não localizada')
    expect(wrapper.get('[role="status"]').text()).toContain('Solicitação de Estudante 1 rejeitada.')
  })

  it('revoking explains the consequences, requires a reason and keeps the history', async () => {
    vi.mocked(affiliationDecisionApi.revoke).mockResolvedValue(req('2', 'REVOKED') as never)
    const { wrapper } = await open([req('2', 'APPROVED')], '/universidade/vinculos?status=APPROVED')
    await btn(wrapper, 'Revogar vínculo').trigger('click')
    const text = wrapper.text()
    expect(text).toContain('elimina a elegibilidade acadêmica')
    expect(text).toContain('suspende automaticamente as candidaturas')
    expect(text).toContain('O histórico é mantido')
    await btn(wrapper, 'Confirmar revogação').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o motivo da revogação.')
    expect(affiliationDecisionApi.revoke).not.toHaveBeenCalled()
    await wrapper.get('textarea[name="reason"]').setValue('Trancamento de matrícula')
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Confirmar revogação').trigger('click')
    await settle()
    expect(affiliationDecisionApi.revoke).toHaveBeenCalledWith('2', 'Trancamento de matrícula')
    expect(wrapper.get('[role="status"]').text()).toContain('candidaturas dele em andamento são suspensas automaticamente')
  })

  it('only the affiliation endpoints are called: the frontend never touches applications', async () => {
    vi.mocked(affiliationDecisionApi.revoke).mockResolvedValue(req('2', 'REVOKED') as never)
    const { wrapper } = await open([req('2', 'APPROVED')])
    const { http } = await import('@/shared/api/http')
    const spies = [vi.spyOn(http, 'get'), vi.spyOn(http, 'post'), vi.spyOn(http, 'patch')]
    await btn(wrapper, 'Revogar vínculo').trigger('click')
    await wrapper.get('textarea[name="reason"]').setValue('Motivo')
    await btn(wrapper, 'Confirmar revogação').trigger('click')
    await settle()
    for (const s of spies) expect(s).not.toHaveBeenCalled() // nenhuma chamada HTTP direta: tudo passa pela camada de afiliações
  })

  it('a concurrent decision (409) is explained and the list can be refreshed', async () => {
    vi.mocked(affiliationDecisionApi.approve).mockRejectedValue(new ApiError(409, 'AFFILIATION_ALREADY_DECIDED', messageFor('AFFILIATION_ALREADY_DECIDED')))
    const { wrapper } = await open([req('1', 'PENDING')])
    await btn(wrapper, 'Aprovar vínculo').trigger('click')
    await btn(wrapper, 'Confirmar aprovação').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('já foi analisada')
    vi.mocked(affiliationDecisionApi.listReceived).mockResolvedValue(page([]) as never)
    await btn(wrapper, 'Atualizar lista').trigger('click')
    await settle()
    expect(affiliationDecisionApi.listReceived).toHaveBeenCalledTimes(2)
    expect(wrapper.find('article').exists()).toBe(false)
  })

  it.each([
    ['AFFILIATION_NOT_APPROVED', 409, 'Só é possível revogar um vínculo aprovado'],
    ['AFFILIATION_NOT_FOUND', 404, 'não encontrada para a sua universidade'],
    ['ACCOUNT_SUSPENDED', 403, 'conta está suspensa'],
    ['ACCESS_DENIED', 403, 'não tem permissão'],
  ])('shows the real API error %s in Portuguese', async (code, status, text) => {
    vi.mocked(affiliationDecisionApi.reject).mockRejectedValue(new ApiError(status, code, messageFor(code)))
    const { wrapper } = await open([req('1', 'PENDING')])
    await btn(wrapper, 'Rejeitar solicitação').trigger('click')
    await wrapper.get('textarea[name="reason"]').setValue('Motivo')
    await btn(wrapper, 'Confirmar rejeição').trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain(text)
  })

  it('cancelling with Escape gives the focus back to the button that opened the panel', async () => {
    const { wrapper } = await open([req('1', 'PENDING')])
    const trigger = btn(wrapper, 'Rejeitar solicitação') as unknown as { element: HTMLElement; trigger: (e: string) => Promise<void> }
    trigger.element.focus()
    await trigger.trigger('click')
    await flushPromises()
    expect(document.activeElement?.textContent?.trim()).toBe('Cancelar')
    await wrapper.get('[role="group"][aria-labelledby]').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(document.activeElement).toBe(trigger.element)
  })
})
