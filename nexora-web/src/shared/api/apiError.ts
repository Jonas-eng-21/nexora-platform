import { isAxiosError } from 'axios'

/** Corpo `application/problem+json` devolvido pelo backend. */
interface ProblemBody {
  status?: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
  }

  /** Falha de rede ou API ainda iniciando: vale tentar de novo. */
  get isUnavailable(): boolean {
    return this.code === 'NETWORK_ERROR' || this.code === 'SERVICE_UNAVAILABLE'
  }
}

/** Mensagens por `code` estável. Sem entrada aqui, vale o `detail` que a API enviou. */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'E-mail ou senha incorretos. Confira os dados e tente de novo.',
  ACCOUNT_PENDING_APPROVAL:
    'Esta conta de universidade ainda aguarda aprovação do administrador. Você poderá entrar depois da aprovação.',
  ACCOUNT_SUSPENDED: 'Esta conta está suspensa. Fale com o administrador da plataforma.',
  EMAIL_ALREADY_REGISTERED: 'Já existe uma conta com este e-mail. Use outro e-mail ou entre na sua conta.',
  PASSWORD_TOO_LONG: 'A senha é longa demais. Use no máximo 72 bytes (acentos contam mais de um).',
  UNAUTHENTICATED: 'Sua sessão expirou. Entre novamente.',
  ACCESS_DENIED: 'Você não tem permissão para realizar esta ação.',
  VALIDATION_ERROR: 'Revise os campos destacados e tente de novo.',
  INTERNAL_ERROR: 'O servidor encontrou um erro inesperado. Tente novamente em instantes.',
  NETWORK_ERROR: 'Não foi possível falar com o servidor. Verifique sua conexão e tente novamente.',
  SERVICE_UNAVAILABLE:
    'O servidor está indisponível ou ainda iniciando. Aguarde alguns segundos e tente novamente.',
}

export function messageFor(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? 'Algo deu errado. Tente novamente.'
}

/** Converte qualquer falha de requisição em ApiError, sem expor corpo bruto nem dados sensíveis. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (!isAxiosError(error)) return new ApiError(0, 'UNKNOWN', messageFor('UNKNOWN'))

  const response = error.response
  if (!response) return new ApiError(0, 'NETWORK_ERROR', messageFor('NETWORK_ERROR'))

  const body = (typeof response.data === 'object' && response.data !== null ? response.data : {}) as ProblemBody
  if (!body.code) {
    // Sem `code` não veio do backend (ex.: proxy sem upstream, HTML de erro).
    const code = response.status >= 500 ? 'SERVICE_UNAVAILABLE' : 'UNKNOWN'
    return new ApiError(response.status, code, messageFor(code))
  }

  const fieldErrors: Record<string, string> = {}
  for (const item of body.errors ?? []) {
    if (!(item.field in fieldErrors)) fieldErrors[item.field] = item.message
  }
  return new ApiError(response.status, body.code, messageFor(body.code, body.detail), fieldErrors)
}
