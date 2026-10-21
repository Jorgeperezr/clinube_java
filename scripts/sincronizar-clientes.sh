#!/usr/bin/env bash
# Trae frontend/ y movil/ desde clinube, tal cual.
#
# El panel y la app Flutter NO se modifican aquí: llegan de clinube y son
# el testigo de que el servidor cumple el contrato. Si para que algo
# funcione hubiera que tocarlos, el fallo está en el servidor.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
origen="${1:-$raiz/../clinube}"

if [ ! -d "$origen/frontend" ]; then
  echo "No encuentro clinube en $origen" >&2
  echo "Clónalo al lado:  git clone https://github.com/Jorgeperezr/clinube.git $origen" >&2
  exit 1
fi

for parte in frontend movil; do
  echo "→ $parte"
  rsync -a --delete \
    --exclude 'node_modules/' --exclude '.next/' \
    --exclude 'build/' --exclude '.dart_tool/' \
    --exclude '.DS_Store' \
    "$origen/$parte/" "$raiz/$parte/"
done

echo
echo "Copiados desde $origen (commit $(git -C "$origen" rev-parse --short HEAD))."
echo "Recuerda: estas dos carpetas no se editan a mano."
