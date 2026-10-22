#!/usr/bin/env bash
# La suite, DOS veces: una en UTC y otra en Pacific/Kiritimati (UTC+14).
#
# La segunda pasada no es paranoia. Clinube guarda en UTC y presenta en la
# zona de la clínica (America/Guayaquil, UTC-5). Una prueba que corra en
# una zona parecida pasa aunque el código confunda las dos cosas, porque
# los valores coinciden por casualidad. En UTC+14 no hay casualidad
# posible: todo lo que confunda «instante» con «día de la clínica» falla.
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"

# El wrapper es lo normal; mientras no exista (se crea una vez con
# `gradle wrapper`), se usa el gradle del sistema.
if [ -x ./gradlew ]; then GRADLE=./gradlew
elif command -v gradle >/dev/null 2>&1; then GRADLE=gradle
else
  echo "No hay ni ./gradlew ni gradle. Mira: ./scripts/comprobar-entorno.sh" >&2
  exit 1
fi

echo "══ Pasada 1: UTC"
"$GRADLE" test -PzonaPruebas=UTC "$@"

echo
echo "══ Pasada 2: Pacific/Kiritimati (UTC+14)"
"$GRADLE" test -PzonaPruebas=Pacific/Kiritimati "$@"

echo
echo "Las dos pasadas en verde."
