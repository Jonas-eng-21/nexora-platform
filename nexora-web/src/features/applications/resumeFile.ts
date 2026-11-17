import { MAX_PDF_BYTES, formatMiB } from '@/shared/format'

/** Pré-validação para dar retorno imediato; o backend valida de novo (extensão, tipo e assinatura %PDF-). */
export function validatePdf(file: File | null): string | null {
  if (!file) return 'Selecione um arquivo PDF.'
  if (!file.name.toLowerCase().endsWith('.pdf')) return 'O arquivo precisa ter a extensão .pdf.'
  if (file.type !== 'application/pdf') return 'O arquivo precisa ser um PDF.'
  if (file.size === 0) return 'O arquivo está vazio.'
  // Em bytes: um arquivo 1 byte acima do limite apareceria como "5 MiB" nos dois lados.
  if (file.size > MAX_PDF_BYTES)
    return `O arquivo tem ${file.size.toLocaleString('pt-BR')} bytes e o limite é ${MAX_PDF_BYTES.toLocaleString('pt-BR')} bytes (${formatMiB(MAX_PDF_BYTES)}).`
  return null
}
