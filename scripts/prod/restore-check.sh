#!/usr/bin/env bash
# Verifica um backup num ambiente ISOLADO e descartável (container Postgres temporário próprio, sem rede publicada
# e sem tocar na stack de produção): confere checksums, restaura o dump, conta tabelas/candidaturas e lista os PDFs.
# Uso: scripts/prod/restore-check.sh /var/backups/nexora/nexora-AAAAMMDD-HHMMSS
set -euo pipefail
DIR="${1:?informe a pasta do backup}"
( cd "$DIR" && sha256sum -c SHA256SUMS )
NAME="nexora-restore-check-$$"
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$NAME" -e POSTGRES_PASSWORD=restore-check -e POSTGRES_DB=restore postgres:18 >/dev/null
for _ in $(seq 1 60); do docker exec "$NAME" pg_isready -U postgres -d restore >/dev/null 2>&1 && break; sleep 1; done
docker exec -i "$NAME" pg_restore -U postgres -d restore --no-owner --exit-on-error < "$DIR/db.dump"
echo "Tabelas restauradas:   $(docker exec "$NAME" psql -U postgres -d restore -tAc "select count(*) from information_schema.tables where table_schema='public'")"
echo "Migrações Flyway:      $(docker exec "$NAME" psql -U postgres -d restore -tAc "select count(*) from flyway_schema_history where success")"
echo "Usuários:              $(docker exec "$NAME" psql -U postgres -d restore -tAc "select count(*) from users")"
echo "PDFs no arquivo:       $(tar -tzf "$DIR/resumes.tar.gz" | grep -vc '/$' || true)"
echo "OK: backup restaurável (ambiente descartável removido ao sair)."
