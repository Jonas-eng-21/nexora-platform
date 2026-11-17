import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { affiliationApi } from '../api/affiliationApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/affiliationApi', () => ({ affiliationApi: { listUniversities: vi.fn(), request: vi.fn(), listOwn: vi.fn(), eligibility: vi.fn() } }))

const uni = { id: 'u1', name: 'Universidade de Garanhuns', acronym: 'UG', location: 'Garanhuns - PE', website: null }
const page = { content: [uni], page: 0, size: 100, totalElements: 1, totalPages: 1 }
const item = (status: string, extra = {}) => ({ id: 'a1', university: uni, status, requestedAt: '2026-11-10T10:00:00Z', decidedAt: null, decisionReason: null, revokedAt: null, revocationReason: null, ...extra })

describe('AffiliationView', () => {
  beforeEach(() => {
    resetDom()
    vi.mocked(affiliationApi.listUniversities).mockResolvedValue(page)
  })

  it('states clearly that the student is not validated and lets them request a link', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: false })
    vi.mocked(affiliationApi.listOwn).mockResolvedValue([])
    vi.mocked(affiliationApi.request).mockResolvedValue(item('PENDING') as never)
    const { wrapper } = await mountAt('/estudante/vinculo')
    expect(wrapper.text()).toContain('Você ainda não está validado.')
    expect(wrapper.text()).toContain('Nenhum pedido ainda')

    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.text()).toContain('Escolha a universidade.')
    expect(affiliationApi.request).not.toHaveBeenCalled()

    await wrapper.get('select').setValue('u1')
    vi.mocked(affiliationApi.listOwn).mockResolvedValue([item('PENDING')] as never)
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(affiliationApi.request).toHaveBeenCalledWith('u1')
    expect(wrapper.text()).toContain('Aguardando análise')
  })

  it('shows the validated state and the whole history with the decision reasons', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: true })
    vi.mocked(affiliationApi.listOwn).mockResolvedValue([
      item('APPROVED', { decidedAt: '2026-11-11T10:00:00Z' }),
      item('REJECTED', { id: 'a0', decisionReason: 'Matrícula não localizada' }),
      item('REVOKED', { id: 'a00', revocationReason: 'Trancamento do curso', revokedAt: '2026-11-12T10:00:00Z' }),
    ] as never)
    const { wrapper } = await mountAt('/estudante/vinculo')
    expect(wrapper.text()).toContain('Você está validado.')
    expect(wrapper.text()).toContain('Aprovado')
    expect(wrapper.text()).toContain('Matrícula não localizada')
    expect(wrapper.text()).toContain('Trancamento do curso')
    expect(wrapper.find('select').exists()).toBe(false) // já aprovado: sem novo pedido
  })

  it('does not offer a second request while one is pending', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: false })
    vi.mocked(affiliationApi.listOwn).mockResolvedValue([item('PENDING')] as never)
    const { wrapper } = await mountAt('/estudante/vinculo')
    expect(wrapper.text()).toContain('aguardando análise')
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('translates the real conflict code when the API refuses the request', async () => {
    vi.mocked(affiliationApi.eligibility).mockResolvedValue({ eligible: false })
    vi.mocked(affiliationApi.listOwn).mockResolvedValue([])
    vi.mocked(affiliationApi.request).mockRejectedValue(new ApiError(409, 'UNIVERSITY_NOT_ACTIVE', messageFor('UNIVERSITY_NOT_ACTIVE')))
    const { wrapper } = await mountAt('/estudante/vinculo')
    await wrapper.get('select').setValue('u1')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('não está ativa')
  })
})
