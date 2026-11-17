import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { resetAreasCache } from '@/features/students/composables/useAreas'
import { studentApi } from '@/features/students/api/studentApi'
import { jobManagementApi } from '@/features/jobs/api/jobManagementApi'
import { companyApplicationApi } from '@/features/applications/api/companyApplicationApi'
import { companyApi } from '../api/companyApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/companyApi', () => ({ companyApi: { getProfile: vi.fn(), saveProfile: vi.fn() } }))
vi.mock('@/features/students/api/studentApi', () => ({ studentApi: { listAreas: vi.fn(), searchCatalog: vi.fn(), addCatalogSkill: vi.fn() } }))
vi.mock('@/features/jobs/api/jobManagementApi', () => ({ jobManagementApi: { listMine: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), close: vi.fn(), reopen: vi.fn(), remove: vi.fn() } }))
vi.mock('@/features/applications/api/companyApplicationApi', () => ({ companyApplicationApi: { listForJob: vi.fn(), changeStatus: vi.fn(), reactivate: vi.fn() } }))

const skill = (id: string, name: string) => ({ id, name })
const catalog = { content: [skill('s1', 'Java'), skill('s2', 'Docker'), skill('s3', 'Redis')], page: 0, size: 100, totalElements: 3, totalPages: 1 }
const job = (id: string, title: string, status = 'OPEN') => ({
  id, companyId: 'c1', companyName: 'Oficina', title, description: 'Descrição da vaga.', area: 'BACKEND', location: 'Garanhuns - PE', workModel: 'HYBRID',
  level: 'INTERNSHIP', status, requiredSkills: [skill('s1', 'Java')], desiredSkills: [skill('s2', 'Docker')], createdAt: '2026-11-10T10:00:00Z', updatedAt: '2026-11-10T10:00:00Z',
})
const page = <T>(content: T[], totalPages = 1) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages })
const profile = { id: 'c1', name: 'Oficina Digital', sector: 'Software', description: null, location: 'Garanhuns - PE' }

beforeEach(() => {
  resetDom()
  resetAreasCache()
  vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }, { code: 'DATA', label: 'Ciência e Análise de Dados' }])
  vi.mocked(studentApi.searchCatalog).mockResolvedValue(catalog)
})

describe('company profile', () => {
  it('treats a missing profile as first access, not as an error', async () => {
    vi.mocked(companyApi.getProfile).mockRejectedValue(new ApiError(404, 'COMPANY_PROFILE_NOT_FOUND', 'x'))
    const { wrapper } = await mountAt('/empresa/perfil', 'COMPANY')
    expect(wrapper.text()).toContain('Você ainda não preencheu o perfil')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('requires the name, then saves trimmed data and confirms', async () => {
    vi.mocked(companyApi.getProfile).mockResolvedValue(profile)
    vi.mocked(companyApi.saveProfile).mockResolvedValue({ ...profile, name: 'Oficina Nova' })
    const { wrapper } = await mountAt('/empresa/perfil', 'COMPANY')
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe('Oficina Digital')
    await wrapper.get('input[name="name"]').setValue(' ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe o nome da empresa.')
    expect(companyApi.saveProfile).not.toHaveBeenCalled()
    await wrapper.get('input[name="name"]').setValue('  Oficina Nova ')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(companyApi.saveProfile).toHaveBeenCalledWith({ name: 'Oficina Nova', sector: 'Software', location: 'Garanhuns - PE' })
    expect(wrapper.text()).toContain('Perfil salvo.')
  })
})

describe('company home', () => {
  it('lists open jobs with the number of applications waiting for analysis', async () => {
    vi.mocked(companyApi.getProfile).mockResolvedValue(profile)
    vi.mocked(jobManagementApi.listMine).mockResolvedValue(page([job('j1', 'Estágio Backend'), job('j2', 'Dados Trainee')]) as never)
    vi.mocked(companyApplicationApi.listForJob).mockImplementation(async (jobId) => ({ ...page([]), totalElements: jobId === 'j1' ? 3 : 0 }) as never)
    const { wrapper } = await mountAt('/empresa', 'COMPANY')
    expect(companyApplicationApi.listForJob).toHaveBeenCalledWith('j1', 'SUBMITTED', 0, 1)
    expect(wrapper.text()).toContain('3 candidaturas aguardando análise')
    expect(wrapper.text()).toContain('Nenhuma candidatura aguardando análise')
    expect(wrapper.text()).not.toMatch(/bem-vind/i)
    expect(wrapper.find('a[href="/empresa/vagas/j1/candidaturas?status=SUBMITTED"]').exists()).toBe(true)
  })

  it('warns when the company profile is missing and offers publishing when there are no jobs', async () => {
    vi.mocked(companyApi.getProfile).mockRejectedValue(new ApiError(404, 'COMPANY_PROFILE_NOT_FOUND', 'x'))
    vi.mocked(jobManagementApi.listMine).mockResolvedValue(page([]) as never)
    const { wrapper } = await mountAt('/empresa', 'COMPANY')
    expect(wrapper.text()).toContain('Falta preencher o perfil da empresa')
    expect(wrapper.text()).toContain('Nenhuma vaga aberta')
  })
})

describe('company jobs list', () => {
  beforeEach(() => vi.mocked(jobManagementApi.listMine).mockResolvedValue(page([job('j1', 'Estágio Backend'), job('j2', 'Dados Trainee', 'CLOSED')]) as never))

  it('shows each job with its real status and per-job actions', async () => {
    const { wrapper } = await mountAt('/empresa/vagas', 'COMPANY')
    const cards = wrapper.findAll('article')
    expect(cards).toHaveLength(2)
    expect(cards[0]!.text()).toContain('Aberta')
    expect(cards[1]!.text()).toContain('Encerrada')
    expect(cards[0]!.text()).toContain('Encerrar vaga')
    expect(cards[1]!.text()).toContain('Reabrir vaga')
    expect(cards[0]!.find('a[href="/empresa/vagas/j1/recomendados"]').exists()).toBe(true)
    expect(cards[0]!.find('a[href="/empresa/vagas/j1/editar"]').exists()).toBe(true)
  })

  it('the status filter really filters through the API and the URL', async () => {
    const { wrapper, router } = await mountAt('/empresa/vagas', 'COMPANY')
    expect(jobManagementApi.listMine).toHaveBeenLastCalledWith('', 0)
    await wrapper.get('select[name="status"]').setValue('OPEN')
    await settle()
    expect(router.currentRoute.value.query).toEqual({ status: 'OPEN' })
    expect(jobManagementApi.listMine).toHaveBeenLastCalledWith('OPEN', 0)
  })

  it('closes and reopens a job through the API and tells what changed', async () => {
    vi.mocked(jobManagementApi.close).mockResolvedValue(job('j1', 'Estágio Backend', 'CLOSED') as never)
    vi.mocked(jobManagementApi.reopen).mockResolvedValue(job('j1', 'Estágio Backend', 'OPEN') as never)
    const { wrapper } = await mountAt('/empresa/vagas', 'COMPANY')
    const card = () => wrapper.findAll('article')[0]!
    await card().findAll('button').find((b) => b.text() === 'Encerrar vaga')!.trigger('click')
    await settle()
    expect(jobManagementApi.close).toHaveBeenCalledWith('j1')
    expect(card().text()).toContain('Encerrada')
    expect(card().text()).toContain('não recebe novas candidaturas')
    await card().findAll('button').find((b) => b.text() === 'Reabrir vaga')!.trigger('click')
    await settle()
    expect(jobManagementApi.reopen).toHaveBeenCalledWith('j1')
    expect(card().text()).toContain('Aberta')
  })

  it('asks for confirmation before deleting, can be cancelled, and reloads the list on confirm', async () => {
    vi.mocked(jobManagementApi.remove).mockResolvedValue(undefined)
    const { wrapper } = await mountAt('/empresa/vagas', 'COMPANY')
    const first = () => wrapper.findAll('article')[0]!
    await first().findAll('button').find((b) => b.text() === 'Excluir vaga')!.trigger('click')
    expect(first().text()).toContain('Excluir esta vaga?')
    expect(first().text()).toContain('mantidas no histórico')
    expect(jobManagementApi.remove).not.toHaveBeenCalled()
    await first().findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')
    expect(first().text()).not.toContain('Excluir esta vaga?')
    expect(jobManagementApi.remove).not.toHaveBeenCalled()

    await first().findAll('button').find((b) => b.text() === 'Excluir vaga')!.trigger('click')
    vi.mocked(jobManagementApi.listMine).mockResolvedValue(page([job('j2', 'Dados Trainee', 'CLOSED')]) as never)
    await first().findAll('button').find((b) => b.text() === 'Confirmar exclusão')!.trigger('click')
    await settle()
    expect(jobManagementApi.remove).toHaveBeenCalledWith('j1')
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('Estágio Backend')
  })

  it('opens the confirmation focused on Cancelar and gives the focus back to the trigger on Escape', async () => {
    const { wrapper } = await mountAt('/empresa/vagas', 'COMPANY')
    const trigger = wrapper.findAll('article')[0]!.findAll('button').find((b) => b.text() === 'Excluir vaga')!
    ;(trigger.element as HTMLElement).focus()
    await trigger.trigger('click')
    await flushPromises()
    expect(document.activeElement?.textContent?.trim()).toBe('Cancelar')
    await wrapper.get('[role="group"]').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.find('article [role="group"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
    expect(jobManagementApi.remove).not.toHaveBeenCalled()
  })

  it('translates a conflict from the API when closing fails and keeps the card usable', async () => {
    vi.mocked(jobManagementApi.close).mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', messageFor('JOB_NOT_FOUND')))
    const { wrapper } = await mountAt('/empresa/vagas', 'COMPANY')
    await wrapper.findAll('article')[0]!.findAll('button').find((b) => b.text() === 'Encerrar vaga')!.trigger('click')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('não está mais disponível')
  })

  it('has an empty state with a call to publish, and an API failure with retry', async () => {
    vi.mocked(jobManagementApi.listMine).mockResolvedValueOnce(page([]) as never)
    const empty = await mountAt('/empresa/vagas', 'COMPANY')
    expect(empty.wrapper.text()).toContain('Você ainda não publicou vagas')
    resetDom()
    vi.mocked(jobManagementApi.listMine).mockRejectedValueOnce(new ApiError(503, 'SERVICE_UNAVAILABLE', 'Servidor indisponível.')).mockResolvedValue(page([]) as never)
    const failed = await mountAt('/empresa/vagas', 'COMPANY')
    expect(failed.wrapper.get('[role="alert"]').text()).toContain('indisponível')
    await failed.wrapper.findAll('button').find((b) => b.text() === 'Tentar novamente')!.trigger('click')
    await flushPromises()
    expect(failed.wrapper.text()).toContain('Você ainda não publicou vagas')
  })

  it('shows the success notice after publishing', async () => {
    const { wrapper } = await mountAt('/empresa/vagas?aviso=criada', 'COMPANY')
    expect(wrapper.get('[role="status"]').text()).toContain('Vaga publicada.')
  })
})

describe('job form', () => {
  async function fillBasics(wrapper: Awaited<ReturnType<typeof mountAt>>['wrapper']) {
    await wrapper.get('input[name="title"]').setValue('  Estágio em Backend  ')
    await wrapper.get('textarea[name="description"]').setValue('Atuar com APIs REST.')
    await wrapper.get('select[name="area"]').setValue('BACKEND')
    await wrapper.get('select[name="level"]').setValue('INTERNSHIP')
    await wrapper.get('select[name="workModel"]').setValue('HYBRID')
  }
  const box = (wrapper: Awaited<ReturnType<typeof mountAt>>['wrapper'], group: number, name: string) =>
    wrapper.findAll('[role="group"]')[group]!.findAll('label').find((l) => l.text().startsWith(name))!.get('input')

  it('does not submit an incomplete job and lists what is missing', async () => {
    const { wrapper } = await mountAt('/empresa/vagas/nova', 'COMPANY')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Revise os campos destacados')
    for (const text of ['Informe o título da vaga.', 'Descreva a vaga.', 'Escolha a área.', 'Escolha o nível.', 'Escolha ao menos uma competência obrigatória.']) expect(wrapper.text()).toContain(text)
    expect(jobManagementApi.create).not.toHaveBeenCalled()
  })

  it('creates a job sending skill ids only, then returns to the list with a notice', async () => {
    vi.mocked(jobManagementApi.create).mockResolvedValue(job('j9', 'Estágio em Backend') as never)
    vi.mocked(jobManagementApi.listMine).mockResolvedValue(page([]) as never)
    const { wrapper, router } = await mountAt('/empresa/vagas/nova', 'COMPANY')
    await fillBasics(wrapper)
    await box(wrapper, 0, 'Java').setValue(true)
    await box(wrapper, 1, 'Docker').setValue(true)
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(jobManagementApi.create).toHaveBeenCalledWith({
      title: 'Estágio em Backend', description: 'Atuar com APIs REST.', area: 'BACKEND', workModel: 'HYBRID', level: 'INTERNSHIP',
      requiredSkillIds: ['s1'], desiredSkillIds: ['s2'],
    })
    expect(router.currentRoute.value.name).toBe('company-jobs')
    expect(router.currentRoute.value.query.aviso).toBe('criada')
  })

  it('does not let one skill be both required and desired: it is disabled in the other list', async () => {
    const { wrapper } = await mountAt('/empresa/vagas/nova', 'COMPANY')
    await box(wrapper, 0, 'Java').setValue(true)
    const javaDesired = box(wrapper, 1, 'Java')
    expect((javaDesired.element as HTMLInputElement).disabled).toBe(true)
    expect(wrapper.findAll('[role="group"]')[1]!.text()).toContain('Java — já é obrigatória')
    expect((box(wrapper, 1, 'Redis').element as HTMLInputElement).disabled).toBe(false)
  })

  it('shows the API conflict error on the desired skills if it still happens', async () => {
    vi.mocked(jobManagementApi.create).mockRejectedValue(new ApiError(400, 'JOB_SKILL_CONFLICT', messageFor('JOB_SKILL_CONFLICT')))
    const { wrapper } = await mountAt('/empresa/vagas/nova', 'COMPANY')
    await fillBasics(wrapper)
    await box(wrapper, 0, 'Java').setValue(true)
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.text()).toContain('não pode ser obrigatória e desejável')
  })

  it('points to the profile when the company has none (COMPANY_PROFILE_NOT_FOUND)', async () => {
    vi.mocked(jobManagementApi.create).mockRejectedValue(new ApiError(404, 'COMPANY_PROFILE_NOT_FOUND', messageFor('COMPANY_PROFILE_NOT_FOUND')))
    const { wrapper } = await mountAt('/empresa/vagas/nova', 'COMPANY')
    await fillBasics(wrapper)
    await box(wrapper, 0, 'Java').setValue(true)
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('perfil da empresa')
    expect(wrapper.find('a[href="/empresa/perfil"]').exists()).toBe(true)
  })

  it('edits an existing job prefilled and sends a full PUT', async () => {
    vi.mocked(jobManagementApi.get).mockResolvedValue(job('j1', 'Estágio Backend') as never)
    vi.mocked(jobManagementApi.update).mockResolvedValue(job('j1', 'Estágio Backend Sênior') as never)
    vi.mocked(jobManagementApi.listMine).mockResolvedValue(page([]) as never)
    const { wrapper, router } = await mountAt('/empresa/vagas/j1/editar', 'COMPANY')
    expect((wrapper.get('input[name="title"]').element as HTMLInputElement).value).toBe('Estágio Backend')
    expect(wrapper.text()).toContain('Java')
    await wrapper.get('input[name="title"]').setValue('Estágio Backend Sênior')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(jobManagementApi.update).toHaveBeenCalledWith('j1', expect.objectContaining({ title: 'Estágio Backend Sênior', requiredSkillIds: ['s1'], desiredSkillIds: ['s2'] }))
    expect(router.currentRoute.value.query.aviso).toBe('editada')
  })

  it('does not expose another company\'s job: the 404 says so', async () => {
    vi.mocked(jobManagementApi.get).mockResolvedValue(job('jx', 'Vaga de outra empresa') as never)
    vi.mocked(jobManagementApi.update).mockRejectedValue(new ApiError(404, 'JOB_NOT_FOUND', messageFor('JOB_NOT_FOUND')))
    const { wrapper } = await mountAt('/empresa/vagas/jx/editar', 'COMPANY')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.get('[role="alert"]').text()).toContain('de outra empresa')
  })
})
