#!/usr/bin/env bash
# Captura de Django las respuestas de los textos legales, de aceptar las
# condiciones y de la configuración de Google, y el estado que dejan en la
# base (filas de auditoría, constancia de aceptación, configuración).
#
# CAMBIA LA BASE: crea la fila de configuración de plataforma, acepta las
# condiciones y rellena los datos del titular. Ejecútalo SOLO contra una
# copia desechable, nunca contra la de referencia:
#
#   psql -h localhost -d postgres -c "CREATE DATABASE clinube_captura WITH TEMPLATE clinube_ref OWNER clinica"
#   (arrancar Django en el 8010 contra clinube_captura, con una cuenta de
#    recepción recepcion@clinube.local y el admin con nombre «Marta Ruiz»)
#   ./scripts/capturar-condiciones.sh http://127.0.0.1:8010 clinube_captura
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
base="${1:-http://127.0.0.1:8010}"
bd="${2:-clinube_captura}"
destino="$raiz/backend/src/test/resources/contrato"
clave="${CLAVE_DEMO:-OdontoDemo2026}"
J='Content-Type: application/json'

guardar() {
  local nombre="$1"; shift
  local tmp estado; tmp=$(mktemp)
  estado=$(curl -s -o "$tmp" -w '%{http_code}' -m 15 "$@")
  CUERPO="$tmp" ESTADO="$estado" DESTINO="$destino/$nombre.json" python3 "$raiz/scripts/_fixture.py"
  rm -f "$tmp"; echo "  $nombre ($estado)"
}
token() {
  curl -s -m 15 -X POST "$base/api/v1/auth/login/" -H "$J" -d "{\"email\":\"$1\",\"password\":\"$clave\"}" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["access"])'
}
sql() { psql -h localhost -U clinica -d "$bd" -tAc "$1"; }

echo "Capturando de $base (base $bd)"
[ "$(sql 'select count(*) from common_platformconfiguration')" = "0" ] || {
  echo "La base ya tiene configuración de plataforma: usa una copia recién hecha." >&2; exit 1; }

guardar google-config "$base/api/v1/auth/google/config/"
echo "  filas de configuración antes de leer los textos: $(sql 'select count(*) from common_platformconfiguration')"
guardar legal-privacidad "$base/api/v1/legal/privacidad/"
echo "  filas de configuración después: $(sql 'select count(*) from common_platformconfiguration')"
guardar legal-condiciones "$base/api/v1/legal/condiciones/"

A=$(token admin@clinube.local); R=$(token recepcion@clinube.local)
U="$base/api/v1/legal/condiciones/aceptar/"
guardar aceptar-sin-token -X POST "$U" -H "$J" -d '{"version":"2026.10"}'
guardar aceptar-recepcion -X POST "$U" -H "$J" -H "Authorization: Bearer $R" -d '{"version":"2026.10"}'
guardar aceptar-version-vieja -X POST "$U" -H "$J" -H "Authorization: Bearer $A" -d '{"version":"1999.01"}'
guardar aceptar-sin-cuerpo -X POST "$U" -H "Authorization: Bearer $A"
guardar aceptar-version-numero -X POST "$U" -H "$J" -H "Authorization: Bearer $A" -d '{"version":2026.10}'
guardar aceptar-metodo-get "$U" -H "Authorization: Bearer $A"
guardar aceptar-primera -X POST "$U" -H "$J" -H "Authorization: Bearer $A" -d '{"version":"2026.10"}'
guardar aceptar-segunda -X POST "$U" -H "$J" -H "Authorization: Bearer $A" -d '{"version":"2026.10"}'
guardar perfil-tras-aceptar "$base/api/v1/auth/me/" -H "Authorization: Bearer $A"

# El rastro que deja aceptar en la base: la constancia y la auditoría.
sql "select json_build_object(
  'aceptaciones', (select json_agg(json_build_object('version', version, 'nombre', nombre, 'correo', correo,
                     'ip', host(ip), 'aceptada_por', aceptada_por_id::text, 'tenant', tenant_id::text)) from common_aceptacioncondiciones),
  'auditoria', (select json_agg(json_build_object('action', action, 'entity_type', entity_type, 'entity_id_vacio', entity_id = '',
                     'ip', host(ip_address), 'metadata', metadata, 'user', user_id::text, 'tenant', tenant_id::text) order by created_at)
                from accounts_auditlog),
  'configuracion', (select row_to_json(c)::jsonb - 'id' - 'updated_at' from common_platformconfiguration c)
)" | python3 -c 'import sys,json; json.dump(json.loads(sys.stdin.read()), open(sys.argv[1],"w",encoding="utf-8"), ensure_ascii=False, indent=1)' "$destino/estado-tras-aceptar.json"
echo "  estado-tras-aceptar (base)"

# Con los datos del titular, el canal por WhatsApp y Google configurado.
sql "update common_platformconfiguration set titular_nombre='Servicios Clínicos Pérez S.A.S.', titular_ruc='1790000000001',
     titular_correo='privacidad@clinube.com', proveedor_correo='Brevo', canal_codigo_paciente='whatsapp',
     google_client_id='  123-abc.apps.googleusercontent.com  '" >/dev/null
guardar legal-privacidad-con-titular "$base/api/v1/legal/privacidad/"
guardar legal-condiciones-con-titular "$base/api/v1/legal/condiciones/"
guardar google-config-con-id "$base/api/v1/auth/google/config/"
echo "Hecho."
