#!/usr/bin/env bash
# Emite o certificado Let's Encrypt (desafio HTTP-01 via webroot) e o grava em TLS_CERT_DIR.
# Pré-requisitos: DNS do DOMAIN apontando para esta VPS, portas 80/443 liberadas e a stack no ar (o Nginx
# responde o desafio). Rode UMA vez; a renovação é o scripts/prod/tls-renew.sh.
# Ao usar este script você aceita os termos do Let's Encrypt (--agree-tos): leia-os antes.
# Teste antes com certificado de homologação (sem limite de emissão, mas não confiável): STAGING=1 scripts/prod/tls-issue.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
DOMAIN="$(envval DOMAIN)"; EMAIL="$(envval LETSENCRYPT_EMAIL)"
[ -n "$DOMAIN" ] && [ -n "$EMAIL" ] || { echo "Defina DOMAIN e LETSENCRYPT_EMAIL no .env.prod." >&2; exit 2; }
STAGING_FLAG=(); [ "${STAGING:-0}" = 1 ] && STAGING_FLAG=(--staging)
# O deploy-hook copia o certificado para /tls (TLS_CERT_DIR) e fica gravado na configuração de renovação.
HOOK='cp -L "$RENEWED_LINEAGE/fullchain.pem" /tls/fullchain.pem && cp -L "$RENEWED_LINEAGE/privkey.pem" /tls/privkey.pem && chmod 644 /tls/fullchain.pem && chmod 600 /tls/privkey.pem'
dc run --rm certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" \
  --email "$EMAIL" --agree-tos --no-eff-email --non-interactive "${STAGING_FLAG[@]}" --deploy-hook "$HOOK"
dc exec web nexora-tls-reload
echo "Certificado instalado e Nginx recarregado. Confira: curl -vI https://$DOMAIN/ (não use -k)."
