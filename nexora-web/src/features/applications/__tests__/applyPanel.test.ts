import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiError, messageFor } from '@/shared/api/apiError'
import { http } from '@/shared/api/http'
import { MAX_PDF_BYTES } from '@/shared/format'
import { applicationApi } from '../api/applicationApi'
import ApplyPanel from '../components/ApplyPanel.vue'

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { render: () => null } }] })
const created = { id: 'ap1', jobId: 'j1', jobTitle: 'Dev', companyName: 'ACME', resumeType: 'PLATFORM', resumeFileName: null, resumeFileSize: null, status: 'SUBMITTED', createdAt: '2026-11-17T10:00:00Z' }

function panel(props: Partial<{ eligible: boolean | null; existing: unknown }> = {}) {
  return mount(ApplyPanel, { props: { jobId: 'j1', eligible: true, existing: null, ...props } as never, global: { plugins: [router] } })
}
function pdf(name: string, type: string, size: number) {
  const f = new File(['%PDF-'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}
async function choosePdf(wrapper: ReturnType<typeof panel>, file: File) {
  await wrapper.get('input[value="PDF"]').setValue(true)
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('ApplyPanel', () => {
  beforeEach(() => { vi.spyOn(applicationApi, 'apply') })

  it('blocks the application for an ineligible student and points to the affiliation page', () => {
    const wrapper = panel({ eligible: false })
    expect(wrapper.text()).toContain('Candidatura bloqueada')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('a').attributes('href')).toBe('/estudante/vinculo')
  })

  it('shows neither form nor block while eligibility is still loading', () => {
    const wrapper = panel({ eligible: null })
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('bloqueada')
  })

  it('shows the existing application instead of the form', () => {
    const wrapper = panel({ existing: { id: 'x', jobId: 'j1', jobTitle: 'Dev', companyName: 'A', status: 'IN_REVIEW', resumeType: 'PDF', appliedAt: '2026-11-10T10:00:00Z', updatedAt: '2026-11-10T10:00:00Z' } })
    expect(wrapper.text()).toContain('Você se candidatou a esta vaga')
    expect(wrapper.text()).toContain('Em análise')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('applies with the platform resume and no file', async () => {
    vi.mocked(applicationApi.apply).mockResolvedValue(created as never)
    const wrapper = panel()
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(applicationApi.apply).toHaveBeenCalledWith('j1', 'PLATFORM', undefined)
    expect(wrapper.text()).toContain('Candidatura enviada.')
    expect(wrapper.emitted('applied')).toHaveLength(1)
  })

  it('applies with a valid PDF and shows the size against the 5 MiB limit', async () => {
    vi.mocked(applicationApi.apply).mockResolvedValue({ ...created, resumeType: 'PDF' } as never)
    const wrapper = panel()
    const file = pdf('cv.pdf', 'application/pdf', 2 * 1024 * 1024)
    await choosePdf(wrapper, file)
    expect(wrapper.text()).toContain('2 MiB de 5 MiB')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe(String(2 * 1024 * 1024))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(applicationApi.apply).toHaveBeenCalledWith('j1', 'PDF', file)
  })

  it('shows small files in KiB instead of rounding to 0 MiB', async () => {
    const wrapper = panel()
    await choosePdf(wrapper, pdf('cv.pdf', 'application/pdf', 150 * 1024))
    expect(wrapper.text()).toContain('150 KiB de 5 MiB')
  })

  it.each([
    ['too big', pdf('cv.pdf', 'application/pdf', MAX_PDF_BYTES + 1), /5\.242\.881 bytes e o limite é 5\.242\.880 bytes/],
    ['wrong extension', pdf('cv.docx', 'application/pdf', 10), /\.pdf/],
    ['wrong type', pdf('cv.pdf', 'image/png', 10), /PDF/],
  ])('rejects an invalid file (%s) before any request', async (_name, file, message) => {
    const wrapper = panel()
    await choosePdf(wrapper, file)
    expect(wrapper.text()).toMatch(message)
    expect(wrapper.get('input[type="file"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(applicationApi.apply).not.toHaveBeenCalled()
  })

  it('requires a file when PDF is chosen and drops it when switching back to the platform resume', async () => {
    const wrapper = panel()
    await wrapper.get('input[value="PDF"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Selecione um arquivo PDF.')
    expect(applicationApi.apply).not.toHaveBeenCalled()
    await choosePdf(wrapper, pdf('cv.pdf', 'application/pdf', 10))
    await wrapper.get('input[value="PLATFORM"]').setValue(true)
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
  })

  it.each([
    ['APPLICATION_ALREADY_EXISTS', 'já se candidatou'],
    ['STUDENT_NOT_VALIDATED', 'vínculo acadêmico aprovado'],
    ['RESUME_FILE_TOO_LARGE', '5 MiB'],
    ['JOB_NOT_ACCEPTING_APPLICATIONS', 'não está recebendo'],
  ])('shows the translated API error %s', async (code, text) => {
    vi.mocked(applicationApi.apply).mockRejectedValue(new ApiError(409, code, messageFor(code)))
    const wrapper = panel()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain(text)
  })
})

describe('applicationApi.apply', () => {
  it('always sends multipart/form-data, with the file only for PDF', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: created })
    await applicationApi.apply('j1', 'PLATFORM')
    const platform = post.mock.calls[0]![1] as FormData
    expect(platform).toBeInstanceOf(FormData)
    expect(platform.get('resumeType')).toBe('PLATFORM')
    expect(platform.has('file')).toBe(false)

    const file = new File(['%PDF-'], 'cv.pdf', { type: 'application/pdf' })
    await applicationApi.apply('j1', 'PDF', file)
    const body = post.mock.calls[1]![1] as FormData
    expect(post.mock.calls[1]![0]).toBe('/jobs/j1/applications')
    expect(body.get('resumeType')).toBe('PDF')
    expect((body.get('file') as File).name).toBe('cv.pdf')
  })
})
