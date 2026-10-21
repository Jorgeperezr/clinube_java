#!/usr/bin/env bash
# Comprueba que en esta Mac está todo lo que hace falta y dice qué falta
# y cómo instalarlo, con los comandos de uno en uno.
set -uo pipefail

faltan=0
ok()    { printf '  \033[32m✔\033[0m %s\n' "$1"; }
mal()   { printf '  \033[31m✘\033[0m %s\n' "$1"; faltan=$((faltan+1)); }
aviso() { printf '  \033[33m!\033[0m %s\n' "$1"; }

echo
echo "Entorno de clinube_java"
echo "───────────────────────"

# ── Java 21 ──────────────────────────────────────────────────────────
if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE '"(21|2[2-9])'; then
  ok "Java $(java -version 2>&1 | head -1 | sed 's/.*version "\([^"]*\)".*/\1/')"
else
  mal "Java 21 (no está, o es anterior)"
  echo "      brew install --cask temurin@21"
fi

# ── Gradle: el wrapper basta; el gradle del sistema solo para crearlo ─
if [ -x "$(dirname "$0")/../gradlew" ]; then
  ok "Wrapper de Gradle (./gradlew)"
elif command -v gradle >/dev/null 2>&1; then
  aviso "No hay wrapper todavía; créalo con: gradle wrapper --gradle-version 8.12"
else
  mal "Gradle (solo hace falta una vez, para crear el wrapper)"
  echo "      brew install gradle"
fi

# ── Docker ───────────────────────────────────────────────────────────
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    ok "Docker $(docker --version | sed 's/Docker version //;s/,.*//') y el demonio en marcha"
  else
    mal "Docker está instalado pero el demonio no responde: abre Docker Desktop"
  fi
else
  mal "Docker"
  echo "      brew install --cask docker"
fi

# ── Node 20 o posterior (el panel) ───────────────────────────────────
if command -v node >/dev/null 2>&1; then
  mayor=$(node --version | sed 's/v\([0-9]*\).*/\1/')
  if [ "$mayor" -ge 20 ]; then ok "Node $(node --version)"; else mal "Node 20 o posterior (hay $(node --version))"; echo "      brew install node"; fi
else
  mal "Node 20 (hace falta para el panel)"
  echo "      brew install node"
fi

# ── Flutter (la app testigo, hasta la fase 14) ───────────────────────
if command -v flutter >/dev/null 2>&1; then
  ok "Flutter $(flutter --version 2>/dev/null | head -1 | awk '{print $2}')"
else
  aviso "Flutter no está. Solo hace falta para probar la app testigo (movil/)."
  echo "      brew install --cask flutter"
fi

# ── PostgreSQL cliente (psql, para los volcados y el comparador) ─────
if command -v psql >/dev/null 2>&1; then
  ok "psql $(psql --version | awk '{print $3}')"
else
  aviso "psql no está. Hace falta para regenerar la línea base y para el comparador."
  echo "      brew install postgresql@16"
fi

echo
if [ "$faltan" -eq 0 ]; then
  echo "Todo listo."
else
  echo "Faltan $faltan cosas. Los comandos van de uno en uno y sin comentarios"
  echo "detrás, porque en zsh un # puede llegar como argumento."
fi
exit "$faltan"
