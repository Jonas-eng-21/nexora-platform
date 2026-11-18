#!/usr/bin/env bash
# Renova os certificados que estão perto de vencer (o Certbot só age a menos de 30 dias do vencimento) e recarrega o
# Nginx quando houve renovação. Agende 2x ao dia (veja o guia: systemd timer ou cron). Seguro de rodar sempre.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
dc run --rm certbot renew --non-interactive
# Recarregar é barato e idempotente: garante que o Nginx leia o arquivo atual mesmo se o hook rodou antes.
dc exec -T web nexora-tls-reload
