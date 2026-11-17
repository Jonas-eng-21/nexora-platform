import { describe, expect, it } from 'vitest'
import { validatePdf } from '../resumeFile'
import { MAX_PDF_BYTES } from '@/shared/format'

const file = (name: string, type: string, size: number) => {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

describe('validatePdf', () => {
  it('asks for a file when none is selected', () => {
    expect(validatePdf(null)).toMatch(/Selecione/)
  })
  it('accepts a PDF up to exactly 5 MiB (5.242.880 bytes)', () => {
    expect(validatePdf(file('cv.pdf', 'application/pdf', MAX_PDF_BYTES))).toBeNull()
  })
  it('rejects one byte over the limit and says the limit', () => {
    expect(validatePdf(file('cv.pdf', 'application/pdf', MAX_PDF_BYTES + 1))).toMatch(/5\.242\.881 bytes e o limite é 5\.242\.880 bytes \(5 MiB\)/)
  })
  it('rejects a wrong extension even when the type says PDF', () => {
    expect(validatePdf(file('cv.docx', 'application/pdf', 100))).toMatch(/\.pdf/)
  })
  it('rejects a non-PDF content type even with .pdf extension', () => {
    expect(validatePdf(file('cv.pdf', 'image/png', 100))).toMatch(/PDF/)
  })
  it('rejects an empty file', () => {
    expect(validatePdf(file('cv.pdf', 'application/pdf', 0))).toMatch(/vazio/)
  })
  it('is case-insensitive on the extension', () => {
    expect(validatePdf(file('CV.PDF', 'application/pdf', 10))).toBeNull()
  })
})
