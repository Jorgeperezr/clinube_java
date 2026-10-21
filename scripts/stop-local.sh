#!/usr/bin/env bash
# Para las dependencias de servicio. Los datos se quedan en ./data.
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"
docker compose down
echo "Parado. Los datos siguen en ./data/postgres; para borrarlos: rm -rf data/postgres"
