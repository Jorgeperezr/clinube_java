#!/usr/bin/env python3
"""Guarda una respuesta capturada como fixture. Lo usa capturar-contrato.sh."""
import json
import os

cuerpo = open(os.environ["CUERPO"], encoding="utf-8", errors="replace").read()
try:
    datos = json.loads(cuerpo) if cuerpo.strip() else None
except json.JSONDecodeError:
    # Una ruta desconocida o un 404 de Django devuelven HTML, no JSON. Se
    # guarda un recorte para que la prueba pueda comprobar que tampoco
    # Kotlin manda JSON ahí.
    datos = {"_no_json": cuerpo[:400]}

with open(os.environ["DESTINO"], "w", encoding="utf-8") as f:
    json.dump({"estado": int(os.environ["ESTADO"]), "cuerpo": datos}, f,
              ensure_ascii=False, indent=1)
