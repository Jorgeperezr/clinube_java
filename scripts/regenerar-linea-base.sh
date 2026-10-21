#!/usr/bin/env bash
# Regenera la línea base de Flyway desde el esquema de Django.
#
# Aplica TODAS las migraciones de clinube sobre una base vacía y vuelca el
# esquema. Así la línea base es lo que Django produce de verdad, y no una
# traducción a mano de las entidades.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
clinube="${CLINUBE:-$raiz/../clinube}"
base="${BASE:-clinube_ref}"
destino="$raiz/backend/src/main/resources/db/migration/V1__linea_base_django.sql"

[ -d "$clinube/django-api" ] || { echo "No encuentro clinube en $clinube" >&2; exit 1; }
[ -x "$clinube/.venv/bin/python" ] || { echo "clinube no tiene su .venv preparado" >&2; exit 1; }

echo "→ Base limpia: $base"
psql -h localhost -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='$base'" | grep -q 1 \
  && psql -h localhost -d postgres -qc "DROP DATABASE $base"
psql -h localhost -d postgres -qc "CREATE DATABASE $base OWNER clinica"

echo "→ Migraciones de Django"
cd "$clinube/django-api"
POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_DB="$base" \
POSTGRES_USER=clinica POSTGRES_PASSWORD=clinica DJANGO_DEBUG=True \
  "$clinube/.venv/bin/python" manage.py migrate --noinput >/dev/null

echo "→ Volcado del esquema"
cabecera=$(sed -n '1,/^$/p' "$destino" | head -40)
tmp=$(mktemp)
pg_dump -h localhost -U clinica -d "$base" --schema-only --no-owner --no-privileges --no-tablespaces \
  | grep -v '^\\restrict' | grep -v '^\\unrestrict' \
  | grep -v "^SET \(statement_timeout\|lock_timeout\|idle_in_transaction_session_timeout\|client_encoding\|standard_conforming_strings\|check_function_bodies\|xmloption\|client_min_messages\|row_security\|default_table_access_method\)" \
  | grep -v "^SELECT pg_catalog.set_config" \
  | grep -v "^-- Dumped \(from\|by\)" \
  | cat -s > "$tmp"

# Se conserva la cabecera explicativa del archivo y se cambia solo el DDL.
{ sed -n '1,/^$/p' "$destino"; cat "$tmp"; } > "$destino.nuevo"
mv "$destino.nuevo" "$destino"
rm -f "$tmp"

echo "Hecho: $(grep -c '^CREATE TABLE' "$destino") tablas en $destino"
echo "Mira el diff: ahí está lo que hay que portar."
