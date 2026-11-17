import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ApiError } from '@/shared/api/apiError'
import { mountAt, resetDom, settle } from '@/test/mountApp'
import { resetAreasCache } from '../composables/useAreas'
import { studentApi } from '../api/studentApi'

vi.mock('@/features/authentication/api/authApi', () => ({ authApi: { login: vi.fn(), register: vi.fn(), me: vi.fn() } }))
vi.mock('../api/studentApi', () => ({
  studentApi: {
    getProfile: vi.fn(), saveProfile: vi.fn(), getResume: vi.fn(), saveResume: vi.fn(), getSkills: vi.fn(), saveSkills: vi.fn(),
    searchCatalog: vi.fn(), addCatalogSkill: vi.fn(), listAreas: vi.fn(),
  },
}))

const notFound = () => new ApiError(404, 'STUDENT_PROFILE_NOT_FOUND', 'x')
const profile = { id: 'p1', fullName: 'Ana Souza', course: 'Sistemas', institution: 'UFAPE', location: 'Garanhuns - PE', summary: null }
const emptyResume = { education: [], experiences: [], projects: [], courses: [], softSkills: [], interests: [], links: {}, interestAreas: [], preferredWorkModels: [] }
const catalog = { content: [{ id: 's1', name: 'Java' }, { id: 's2', name: 'Vue' }], page: 0, size: 100, totalElements: 2, totalPages: 1 }

describe('student profile and skills', () => {
  beforeEach(() => {
    resetDom()
    resetAreasCache()
    vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }, { code: 'FRONTEND', label: 'Frontend' }])
    vi.mocked(studentApi.searchCatalog).mockResolvedValue(catalog)
    vi.mocked(studentApi.getSkills).mockResolvedValue([])
  })

  it('shows an empty first-access form when the profile does not exist yet, hiding the skills until it is saved', async () => {
    vi.mocked(studentApi.getProfile).mockRejectedValue(notFound())
    vi.mocked(studentApi.getSkills).mockRejectedValue(notFound()) // o backend também responde 404 aqui sem perfil
    const { wrapper } = await mountAt('/estudante/perfil')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Tentar novamente')
    expect(wrapper.text()).toContain('Você ainda não preencheu seu perfil')
    expect(wrapper.text()).toContain('Salve os dados do perfil acima')
    expect(wrapper.find('input[name="skill-search"]').exists()).toBe(false)
  })

  it('after saving the very first profile the skills card appears ready to use, without the old 404 error', async () => {
    vi.mocked(studentApi.getProfile).mockRejectedValue(notFound())
    vi.mocked(studentApi.getSkills).mockRejectedValue(notFound())
    vi.mocked(studentApi.saveProfile).mockResolvedValue(profile)
    const { wrapper } = await mountAt('/estudante/perfil')
    await wrapper.get('input[name="fullName"]').setValue('Ana Souza')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.text()).toContain('Perfil salvo.')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('input[name="skill-search"]').exists()).toBe(true)
  })

  it('loads the profile, blocks an empty name and saves trimmed data', async () => {
    vi.mocked(studentApi.getProfile).mockResolvedValue(profile)
    vi.mocked(studentApi.saveProfile).mockResolvedValue({ ...profile, fullName: 'Ana Maria' })
    const { wrapper } = await mountAt('/estudante/perfil')
    const name = wrapper.get('input[name="fullName"]')
    expect((name.element as HTMLInputElement).value).toBe('Ana Souza')

    await name.setValue('  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Informe seu nome completo.')
    expect(studentApi.saveProfile).not.toHaveBeenCalled()

    await name.setValue('  Ana Maria  ')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(studentApi.saveProfile).toHaveBeenCalledWith({ fullName: 'Ana Maria', course: 'Sistemas', institution: 'UFAPE', location: 'Garanhuns - PE' })
    expect(wrapper.text()).toContain('Perfil salvo.')
  })

  it('shows the API validation error on a field', async () => {
    vi.mocked(studentApi.getProfile).mockResolvedValue(profile)
    vi.mocked(studentApi.saveProfile).mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'Revise.', { course: 'tamanho inválido' }))
    const { wrapper } = await mountAt('/estudante/perfil')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.get('input[name="course"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('tamanho inválido')
  })

  it('selects catalog skills and persists only their ids', async () => {
    vi.mocked(studentApi.getProfile).mockResolvedValue(profile)
    vi.mocked(studentApi.getSkills).mockResolvedValue([{ id: 's1', name: 'Java' }])
    vi.mocked(studentApi.saveSkills).mockResolvedValue([{ id: 's1', name: 'Java' }, { id: 's2', name: 'Vue' }])
    const { wrapper } = await mountAt('/estudante/perfil')
    const boxes = wrapper.findAll('[role="group"] input[type="checkbox"]')
    expect((boxes[0]!.element as HTMLInputElement).checked).toBe(true)
    await boxes[1]!.setValue(true)
    const save = wrapper.findAll('button').find((b) => b.text().includes('Salvar competências'))!
    await save.trigger('click')
    await settle()
    expect(studentApi.saveSkills).toHaveBeenCalledWith(['s1', 's2'])
    expect(wrapper.text()).toContain('Competências salvas.')
  })

  it('offers to add a missing skill to the catalog and selects it', async () => {
    vi.mocked(studentApi.getProfile).mockResolvedValue(profile)
    vi.mocked(studentApi.addCatalogSkill).mockResolvedValue({ id: 's9', name: 'Rust' })
    const { wrapper } = await mountAt('/estudante/perfil')
    vi.mocked(studentApi.searchCatalog).mockResolvedValue({ ...catalog, content: [], totalElements: 0 })
    await wrapper.get('input[name="skill-search"]').setValue('Rust')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Adicionar “Rust” ao catálogo'))
    await wrapper.findAll('button').find((b) => b.text().includes('Adicionar “Rust”'))!.trigger('click')
    await settle()
    expect(studentApi.addCatalogSkill).toHaveBeenCalledWith('Rust')
    expect(wrapper.text()).toContain('Rust')
  })
})

describe('resume editor', () => {
  beforeEach(() => {
    resetDom()
    resetAreasCache()
    vi.mocked(studentApi.listAreas).mockResolvedValue([{ code: 'BACKEND', label: 'Backend' }, { code: 'FRONTEND', label: 'Frontend' }])
  })

  it('guides the student to the profile when there is none', async () => {
    vi.mocked(studentApi.getResume).mockRejectedValue(notFound())
    const { wrapper } = await mountAt('/estudante/curriculo')
    expect(wrapper.text()).toContain('Preencha o perfil antes de montar o currículo')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('does not save while a field is invalid, and flags it for assistive tech', async () => {
    vi.mocked(studentApi.getResume).mockResolvedValue(emptyResume)
    const { wrapper } = await mountAt('/estudante/curriculo')
    await wrapper.findAll('button').find((b) => b.text() === 'Adicionar projeto')!.trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(studentApi.saveResume).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Informe o nome do projeto.')
    expect(wrapper.find('[aria-invalid="true"]').exists()).toBe(true)
  })

  it('saves projects, interest areas and work models in the real contract shape', async () => {
    vi.mocked(studentApi.getResume).mockResolvedValue(emptyResume)
    vi.mocked(studentApi.saveResume).mockImplementation(async (r) => r)
    const { wrapper } = await mountAt('/estudante/curriculo')
    await wrapper.findAll('button').find((b) => b.text() === 'Adicionar projeto')!.trigger('click')
    const inputs = wrapper.findAll('fieldset input[type="text"], fieldset textarea')
    await inputs[0]!.setValue('Nexora')
    const checks = wrapper.findAll('input[type="checkbox"]')
    await checks[0]!.setValue(true) // Backend
    await checks.find((c) => c.element.parentElement?.textContent?.includes('Remoto'))!.setValue(true)
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(studentApi.saveResume).toHaveBeenCalledWith(expect.objectContaining({
      projects: [{ name: 'Nexora' }], interestAreas: ['BACKEND'], preferredWorkModels: ['REMOTE'], education: [], links: {},
    }))
    expect(wrapper.text()).toContain('Currículo salvo.')
  })

  it('maps API errors with indexed paths onto the right field', async () => {
    vi.mocked(studentApi.getResume).mockResolvedValue({ ...emptyResume, experiences: [{ company: 'ACME', role: 'Dev', startDate: '2025-03' }] })
    vi.mocked(studentApi.saveResume).mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'Revise.', { 'experiences[0].startDate': 'deve estar no formato AAAA-MM' }))
    const { wrapper } = await mountAt('/estudante/curriculo')
    await wrapper.get('form').trigger('submit')
    await settle()
    expect(wrapper.text()).toContain('deve estar no formato AAAA-MM')
  })
})
