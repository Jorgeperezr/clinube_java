#!/usr/bin/env bash
# Captura las respuestas REALES de un Django en marcha y las guarda como
# fixtures de las pruebas.
#
# Para qué: cuando algo del formato no está claro —el formato de las
# fechas, el orden de las claves, el texto exacto de un error—, no se
# deduce leyendo el código: se pregunta al servidor. Varias de las
# diferencias que más cuestan de ver salieron así (DRF no escribe las
# fechas en UTC con «Z», sino en la zona de la clínica y con
# microsegundos).
#
# Uso:  ./scripts/capturar-contrato.sh [http://127.0.0.1:8010]
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
base="${1:-http://127.0.0.1:8010}"
destino="$raiz/backend/src/test/resources/contrato"
mkdir -p "$destino"

usuario="${USUARIO_DEMO:-admin@clinube.local}"
clave="${CLAVE_DEMO:-OdontoDemo2026}"

# Una sola petición por fixture: el cuerpo a un archivo y el código con
# -w. Con dos peticiones, cada login emitía dos parejas de tokens y se
# apuntaban filas de más en token_blacklist_outstandingtoken.
guardar() {  # guardar <nombre> <curl args...>
  local nombre="$1"; shift
  local tmp estado
  tmp=$(mktemp)
  estado=$(curl -s -o "$tmp" -w '%{http_code}' -m 15 "$@")
  # Django limita a 20 peticiones por minuto las que llegan sin sesión, y
  # casi todas estas llegan así. Un 429 no es la respuesta que se quiere
  # capturar: se espera lo que el propio servidor dice y se repite.
  if [ "$estado" = "429" ]; then
    local espera
    espera=$(sed -n 's/.*disponible en \([0-9]*\) segundos.*/\1/p' "$tmp")
    echo "    (límite de peticiones: esperando ${espera:-61}s)"
    sleep "$((${espera:-60} + 1))"
    estado=$(curl -s -o "$tmp" -w '%{http_code}' -m 15 "$@")
  fi
  CUERPO="$tmp" ESTADO="$estado" DESTINO="$destino/$nombre.json" python3 "$raiz/scripts/_fixture.py"
  rm -f "$tmp"
  echo "  $nombre ($estado)"
}

echo "Capturando de $base"
J='Content-Type: application/json'

# El token, lo primero: las peticiones autenticadas no gastan el cupo de
# anónimo, y los casos de error que vienen después sí.
acceso=$(curl -s -m 15 -X POST "$base/api/v1/auth/login/" -H "$J" \
  -d "{\"email\":\"$usuario\",\"password\":\"$clave\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access",""))')
if [ -z "$acceso" ]; then
  echo "No se pudo entrar como $usuario. ¿Está la clínica demo en la base de Django?" >&2
  exit 1
fi

guardar perfil "$base/api/v1/auth/me/" -H "Authorization: Bearer $acceso"
guardar usuarios "$base/api/v1/users/" -H "Authorization: Bearer $acceso"

guardar salud "$base/api/v1/health/"
guardar disponibilidad "$base/api/v1/ready/"
guardar login-correcto -X POST "$base/api/v1/auth/login/" -H "$J" \
  -d "{\"email\":\"$usuario\",\"password\":\"$clave\"}"
guardar login-contrasena-mala -X POST "$base/api/v1/auth/login/" -H "$J" \
  -d "{\"email\":\"$usuario\",\"password\":\"mala\"}"
guardar login-sin-campos -X POST "$base/api/v1/auth/login/" -H "$J" -d '{}'
guardar login-campos-vacios -X POST "$base/api/v1/auth/login/" -H "$J" \
  -d '{"email":"","password":""}'
guardar login-metodo-get "$base/api/v1/auth/login/"
guardar refresco-sin-campo -X POST "$base/api/v1/auth/token/refresh/" -H "$J" -d '{}'
guardar refresco-basura -X POST "$base/api/v1/auth/token/refresh/" -H "$J" \
  -d '{"refresh":"basura"}'
guardar perfil-sin-token "$base/api/v1/auth/me/"
guardar perfil-token-basura "$base/api/v1/auth/me/" -H 'Authorization: Bearer basura'
guardar cabecera-mal-formada "$base/api/v1/auth/me/" -H 'Authorization: Bearer'

echo
echo "Fixtures en $destino"
echo "Las pruebas de contrato las leen de ahí: si Django cambia, se recapturan"
echo "y el diff dice qué hay que portar."
