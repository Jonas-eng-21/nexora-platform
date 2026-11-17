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
  STUDENT_PROFILE_NOT_FOUND: 'Você ainda não preencheu seu perfil. Preencha os dados do perfil para continuar.',
  STUDENT_NOT_VALIDATED: 'Você só pode se candidatar com um vínculo acadêmico aprovado e vigente. Solicite o vínculo e aguarde a aprovação.',
  APPLICATION_ALREADY_EXISTS: 'Você já se candidatou a esta vaga.',
  JOB_NOT_FOUND: 'Esta vaga não está mais disponível.',
  JOB_NOT_ACCEPTING_APPLICATIONS: 'Esta vaga não está recebendo candidaturas.',
  APPLICATION_NOT_FOUND: 'Candidatura não encontrada.',
  RESUME_FILE_REQUIRED: 'Selecione o arquivo PDF para enviar.',
  RESUME_FILE_NOT_ALLOWED: 'Escolha apenas uma opção: currículo do Nexora ou PDF, não os dois.',
  INVALID_RESUME_FILE: 'O arquivo não é um PDF válido. Envie um PDF de até 5 MiB.',
  RESUME_FILE_TOO_LARGE: 'O PDF ultrapassa o limite de 5 MiB. Envie um arquivo menor.',
  UNIVERSITY_NOT_ACTIVE: 'Esta universidade não está ativa para receber pedidos de vínculo.',
  AFFILIATION_ALREADY_PENDING: 'Você já tem um pedido de vínculo aguardando análise.',
  AFFILIATION_ALREADY_APPROVED: 'Você já tem um vínculo acadêmico aprovado.',
  SKILL_NOT_FOUND: 'Uma das competências escolhidas não existe mais no catálogo. Recarregue a lista.',
  COMPANY_PROFILE_NOT_FOUND: 'Preencha o perfil da empresa antes de publicar vagas.',
  JOB_SKILL_CONFLICT: 'A mesma competência não pode ser obrigatória e desejável ao mesmo tempo.',
  APPLICATION_SUSPENDED: 'Esta candidatura está suspensa e não pode avançar. Reative-a quando a validação do estudante for restabelecida.',
  INVALID_APPLICATION_STATUS_TRANSITION: 'Esta mudança de situação não é permitida a partir do estado atual.',
  REJECTION_REASON_REQUIRED: 'Informe o motivo da rejeição.',
  APPLICATION_NOT_SUSPENDED: 'Esta candidatura não está suspensa.',
  INVALID_SUSPENSION_STATE: 'Não foi possível restaurar o estado anterior desta candidatura.',
  STUDENT_ACCOUNT_NOT_ACTIVE: 'A conta do estudante não está ativa.',
  JOB_NOT_AVAILABLE: 'A vaga precisa estar aberta para reativar candidaturas.',
  AFFILIATION_ALREADY_DECIDED: 'Esta solicitação já foi analisada, talvez por outra sessão. Atualize a lista para ver a decisão atual.',
  AFFILIATION_NOT_APPROVED: 'Só é possível revogar um vínculo aprovado. Atualize a lista para ver a situação atual.',
  AFFILIATION_NOT_FOUND: 'Solicitação de vínculo não encontrada para a sua universidade.',
  UNIVERSITY_PROFILE_NOT_FOUND: 'O perfil institucional não foi encontrado para esta conta.',
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
