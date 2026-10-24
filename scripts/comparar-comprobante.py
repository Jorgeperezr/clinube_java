#!/usr/bin/env python3
"""
El comprobante de cobro de Django no se puede pedir por la API: su vista
bloquea el pago con `select_for_update()` y a la vez se trae con
`select_related` el profesional y la cuota, que pueden ser nulos, y
PostgreSQL responde «FOR UPDATE cannot be applied to the nullable side of
an outer join». Siempre da 500 (ver docs/DIFERENCIAS.md).

Así que aquí Django DIBUJA el comprobante con su propio código
(`documentos.comprobante_cobro`) y con el número que le asigna Kotlin, y se
compara lo que dicen los dos papeles, palabra por palabra.

Lo llama `scripts/comparar-contrato` al final, con los dos servidores en
marcha y con la copia de Django:

    BASE=clinube_cmp_django CLINUBE=../clinube ../clinube/.venv/bin/python scripts/comparar-comprobante.py
"""
import collections
import glob
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import uuid

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLINUBE = os.path.abspath(os.environ.get("CLINUBE", os.path.join(RAIZ, "..", "clinube")))
BASE = os.environ.get("BASE")
KOTLIN = os.environ.get("KOTLIN", "http://127.0.0.1:8000")
if not BASE or BASE == "clinube_ref":
    sys.exit("Indica BASE=<copia desechable>. Nunca la base de referencia.")
sys.path.insert(0, os.path.join(CLINUBE, "django-api"))
for clave, valor in {"POSTGRES_HOST": "localhost", "POSTGRES_DB": BASE, "POSTGRES_USER": "clinica",
                     "POSTGRES_PASSWORD": "clinica", "DJANGO_DEBUG": "True", "DJANGO_LOG_LEVEL": "WARNING"}.items():
    os.environ[clave] = valor
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

import django  # noqa: E402
django.setup()
from apps.billing.models import Payment  # noqa: E402
from apps.finanzas import documentos  # noqa: E402

ESPACIO = uuid.UUID("6f1c2a5e-0000-4000-8000-c1b0be000000")
# Uno ya numerado, uno de una cuota y uno con profesional, en este orden:
# Kotlin numera los dos últimos con su contador, 120 y 121.
COBROS = ["cobro-doctora-2", "pago-1700000008-1", "cobro-doctor2"]


def palabras(pdf):
    jar = glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/com.github.librepdf/openpdf/*/*/openpdf-*.jar"))[0]
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(pdf)
        f.flush()
        salida = subprocess.run(["java", "-cp", jar, os.path.join(RAIZ, "scripts", "TextoDePdf.java"), f.name],
                                capture_output=True, text=True, check=True)
    return collections.Counter(salida.stdout.split())


def pedir(ruta, token=None, cuerpo=None):
    cabeceras = {"Content-Type": "application/json"}
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    sol = urllib.request.Request(KOTLIN + ruta, data=json.dumps(cuerpo).encode() if cuerpo else None, headers=cabeceras)
    with urllib.request.urlopen(sol, timeout=30) as r:
        return r.read(), r.headers


def main():
    token = json.loads(pedir("/api/v1/auth/login/", cuerpo={"email": "admin@clinube.local", "password": "OdontoDemo2026"})[0])["access"]
    distintos = 0
    for nombre in COBROS:
        pago = Payment.objects.select_related("tenant").get(id=uuid.uuid5(ESPACIO, nombre))
        pdf_k, cabeceras = pedir(f"/api/v1/finanzas/cobros/{pago.id}/comprobante/", token)
        numero = int(cabeceras["Content-Disposition"].split("comprobante-")[1].split(".")[0])
        pdf_d = documentos.comprobante_cobro(pago.tenant, pago, numero)
        d, k = palabras(pdf_d), palabras(pdf_k)
        if d == k:
            print(f"\033[32m✔ comprobante {numero:06d} ({nombre})\033[0m  {sum(d.values())} palabras")
            continue
        distintos += 1
        print(f"\033[31m✘ comprobante {numero:06d} ({nombre})\033[0m")
        print(f"    a Kotlin le faltan {sorted((d - k).elements())}")
        print(f"    Kotlin añade {sorted((k - d).elements())}")
    return 1 if distintos else 0


if __name__ == "__main__":
    sys.exit(main())
