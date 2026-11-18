#!/bin/sh
# Executado pelo entrypoint oficial do Nginx antes de subir o servidor.
exec /usr/local/bin/nexora-tls-link
