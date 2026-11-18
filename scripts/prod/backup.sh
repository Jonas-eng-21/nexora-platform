#!/usr/bin/env bash
# Backup consistente: dump do PostgreSQL + arquivo dos PDFs, no MESMO instante lógico.
# Para a escrita de candidaturas por uma janela curta (backend parado) para que registros e arquivos não divirjam;
# o site continua servindo a UI estática (o proxy responde 503 JSON à API durante o backup).
# Saída: $BACKUP_DIR/nexora-AAAAMMDD-HHMMSS/{db.dump,resumes.tar.gz,SHA256SUMS}. Cópia FORA da VPS: veja o guia.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/nexora}"
KEEP="${KEEP:-14}"            # quantos backups locais manter
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/nexora-$STAMP"
umask 077
mkdir -p "$OUT"
restart_backend() { dc start backend >/dev/null; }
trap restart_backend EXIT
echo ">> Parando o backend (janela curta de consistência)..."
dc stop backend >/dev/null
echo ">> Dump do PostgreSQL..."
dc exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$OUT/db.dump"
echo ">> Arquivo dos PDFs..."
VOL="$(docker volume ls -q --filter label=com.docker.compose.project=nexora-prod --filter label=com.docker.compose.volume=resumes_data)"
[ -n "$VOL" ] || { echo "Volume resumes_data não encontrado." >&2; exit 1; }
# O tar roda como root no container (lê arquivos do usuário da API); o resultado volta para o seu usuário, modo 600.
docker run --rm -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" -v "$VOL":/data:ro -v "$OUT":/out alpine:3 \
  sh -c 'tar -C /data -czf /out/resumes.tar.gz . && chown "$HOST_UID:$HOST_GID" /out/resumes.tar.gz && chmod 600 /out/resumes.tar.gz'
restart_backend; trap - EXIT
( cd "$OUT" && sha256sum db.dump resumes.tar.gz > SHA256SUMS )
# Retenção local (as cópias fora da VPS têm a própria retenção).
ls -1dt "$BACKUP_DIR"/nexora-* | tail -n +"$((KEEP + 1))" | while read -r old; do rm -rf -- "$old"; done
echo ">> Backup concluído em $OUT (copie para FORA da VPS e criptografe antes)."
