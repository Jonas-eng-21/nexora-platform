import { ApiError } from '@/shared/api/apiError'

// Alguns códigos são redigidos para o estudante em `apiError.ts`; na área da empresa o sentido muda.
const COMPANY_MESSAGES: Record<string, string> = {
  JOB_NOT_FOUND: 'Vaga não encontrada, excluída ou de outra empresa.',
  STUDENT_NOT_VALIDATED: 'O estudante não tem, no momento, vínculo acadêmico aprovado e vigente. Aguarde a revalidação.',
}

/** Reescreve a mensagem de erros cujo texto padrão é voltado ao estudante. */
export function forCompany(error: ApiError | null): ApiError | null {
  if (!error || !(error.code in COMPANY_MESSAGES)) return error
  return new ApiError(error.status, error.code, COMPANY_MESSAGES[error.code]!, error.fieldErrors)
}
