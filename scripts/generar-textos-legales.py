#!/usr/bin/env python3
"""
Genera backend/src/main/kotlin/com/clinube/legal/TextosLegalesGenerados.kt
desde apps/common/legal.py de clinube.

Los textos legales no se copian a mano: son cientos de líneas en castellano
y cualquier errata dejaría de responder igual que Django. Se ejecutan las
funciones de Django con los datos del titular sustituidos por marcadores, y
los marcadores se convierten en plantillas de Kotlin (${d.titular}...). La
lista de proveedores (_encargados) tiene ramas y se escribe a mano en
TextosLegales.kt; la comprueban los fixtures de los dos canales.

Uso (con la base de referencia en marcha):
    CLINUBE=../clinube python3 scripts/generar-textos-legales.py
"""
import sys, os, json
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLINUBE = os.path.abspath(os.environ.get("CLINUBE", os.path.join(RAIZ, "..", "clinube")))
sys.path.insert(0, os.path.join(CLINUBE, "django-api"))
for k, v in {"POSTGRES_HOST": "localhost", "POSTGRES_DB": "clinube_ref", "POSTGRES_USER": "clinica", "POSTGRES_PASSWORD": "clinica", "DJANGO_DEBUG": "True"}.items():
    os.environ.setdefault(k, v)
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
import django; django.setup()
from apps.common import legal

M = {k: f"\x01{k}\x01" for k in ("plataforma", "titular", "ruc", "correo", "proveedor_correo")}
legal._datos_titular = lambda: (dict(M), [])
legal._encargados = lambda d: ["\x02ENCARGADOS\x02"]

def kstr(s):
    # Literal de Kotlin con las plantillas ${d.campo}
    out = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
    for k in M:
        campo = {"plataforma": "plataforma", "titular": "titular", "ruc": "ruc",
                 "correo": "correo", "proveedor_correo": "proveedorCorreo"}[k]
        out = out.replace(f"\x01{k}\x01", "${d." + campo + "}")
    assert "\x01" not in out
    return '"' + out + '"'

def emitir(nombre, doc):
    lineas = [f"    fun {nombre}(d: DatosDelTitular, encargados: List<String>): List<Seccion> = listOf("]
    for s in doc["secciones"]:
        lineas.append(f"        Seccion(")
        lineas.append(f"            {kstr(s['titulo'])},")
        lineas.append("            listOf(")
        for p in s["parrafos"]:
            if p == "\x02ENCARGADOS\x02":
                lineas.append("                *encargados.toTypedArray(),")
            else:
                lineas.append(f"                {kstr(p)},")
        lineas.append("            ),")
        lineas.append("        ),")
    lineas.append("    )")
    return "\n".join(lineas)

aviso = legal.aviso_privacidad(); cond = legal.condiciones()
cuerpo = emitir("seccionesDelAviso", aviso) + "\n\n" + emitir("seccionesDeLasCondiciones", cond)
destino = os.path.join(RAIZ, "backend/src/main/kotlin/com/clinube/legal/TextosLegalesGenerados.kt")
with open(destino, "w", encoding="utf-8") as f:
    f.write("""package com.clinube.legal

// GENERADO por scripts/generar-textos-legales.py desde apps/common/legal.py
// de clinube. NO SE EDITA A MANO: si cambia un texto en Django, se vuelve a
// generar, y se sube la versión en TextosLegales.kt igual que allí.

@Suppress("LongMethod", "MaxLineLength")
internal object TextosLegalesGenerados {
""" + cuerpo + "\n}\n")
print("escrito", destino)
