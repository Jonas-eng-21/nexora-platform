const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' })
const monthFormat = new Intl.DateTimeFormat('pt-BR', { month: 'short', year: 'numeric' })

export const formatDate = (iso: string): string => dateFormat.format(new Date(iso))

/** "2025-03" -> "mar. de 2025". */
export function formatYearMonth(value: string): string {
  const [year, month] = value.split('-').map(Number)
  return monthFormat.format(new Date(year!, (month ?? 1) - 1, 1))
}

export const MAX_PDF_BYTES = 5 * 1024 * 1024

/** Tamanho em MiB com vírgula decimal, para o indicador do limite de upload. */
export function formatMiB(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 2, minimumFractionDigits: 0 })} MiB`
}

/** Tamanho de um arquivo: KiB abaixo de 1 MiB (senão um PDF pequeno apareceria como "0 MiB"). */
export function formatFileSize(bytes: number): string {
  return bytes < 1024 * 1024
    ? `${(bytes / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} KiB`
    : formatMiB(bytes)
}

const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
export const formatDateTime = (iso: string): string => dateTimeFormat.format(new Date(iso))
