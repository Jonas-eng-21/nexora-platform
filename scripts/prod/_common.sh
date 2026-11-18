# Funções comuns dos scripts de produção (use com "source"). Não imprime segredos.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.prod}"
[ -f "$ENV_FILE" ] || { echo "Arquivo $ENV_FILE não encontrado (copie de .env.prod.example)." >&2; exit 2; }
dc() { docker compose -f "$ROOT/compose.prod.yaml" --env-file "$ENV_FILE" "$@"; }
# Lê UMA variável do env file sem exportar o arquivo inteiro (evita vazar segredos para subprocessos).
envval() { grep -E "^$1=" "$ENV_FILE" | head -n1 | cut -d= -f2-; }
