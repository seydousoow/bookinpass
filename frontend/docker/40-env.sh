#!/bin/sh
# Lancé par l'entrypoint nginx avant le démarrage : écrit la configuration d'exécution lue par le front (/env.js),
# et le relais `/api/` si API_UPSTREAM est défini.
set -eu

escaped=$(printf '%s' "${API_BASE_URL:-/api}" | sed 's/\\/\\\\/g; s/"/\\"/g')
printf 'window.__env = { apiBaseUrl: "%s" };\n' "$escaped" > /usr/share/nginx/html/env.js

if [ -n "${API_UPSTREAM:-}" ]; then
  printf 'location /api/ {\n  proxy_pass %s;\n  proxy_set_header Host $host;\n  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n  proxy_set_header X-Forwarded-Proto $scheme;\n}\n' \
    "$API_UPSTREAM" > /etc/nginx/conf.d/api.locations
else
  : > /etc/nginx/conf.d/api.locations
fi
