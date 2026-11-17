// Único ponto que toca o storage do token. sessionStorage: some ao fechar a aba.
// Decisão temporária do MVP; um XSS conseguiria ler o token (ver andamento.md).
const KEY = 'nexora.accessToken'

export const tokenStorage = {
  get(): string | null {
    try {
      return sessionStorage.getItem(KEY)
    } catch {
      return null
    }
  },
  set(token: string): void {
    try {
      sessionStorage.setItem(KEY, token)
    } catch {
      // Storage indisponível: a sessão vale só até recarregar a página.
    }
  },
  clear(): void {
    try {
      sessionStorage.removeItem(KEY)
    } catch {
      // nada a limpar
    }
  },
}
