#!/usr/bin/env bash
# Levanta todo el desarrollo local: dependencias de servicio y el servidor.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

# El wrapper es lo normal; mientras no exista (se crea una vez con
# `gradle wrapper`), se usa el gradle del sistema.
if [ -x ./gradlew ]; then GRADLE=./gradlew
elif command -v gradle >/dev/null 2>&1; then GRADLE=gradle
else
  echo "No hay ni ./gradlew ni gradle. Mira: ./scripts/comprobar-entorno.sh" >&2
  exit 1
fi

echo "→ Dependencias de servicio (PostgreSQL, Redis, Mailpit)"
docker compose up -d

echo "→ Esperando a que PostgreSQL acepte conexiones"
for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -q 2>/dev/null; then break; fi
  sleep 1
done

echo "→ Servidor en http://localhost:8000"
echo "  (el panel: NEXT_PUBLIC_API_URL=http://localhost:8000 npm run dev, en frontend/)"
echo "  (el correo de prueba: http://localhost:8025)"
exec "$GRADLE" :backend:bootRun
