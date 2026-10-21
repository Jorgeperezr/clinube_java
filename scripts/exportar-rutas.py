#!/usr/bin/env python3
"""
Saca la lista de rutas del Django de ../clinube recorriendo su resolvedor
de URL, y escribe `docs/contrato/RUTAS.md`.

Se recorre el resolvedor en vez de leer los `urls.py` porque así no se
escapa ninguna ruta incluida indirectamente ni ninguna que venga de una
biblioteca (el refresco de token lo pone SimpleJWT, no clinube).

Uso:
    CLINUBE=../clinube python3 scripts/exportar-rutas.py
"""
import inspect
import json
import os
import re
import sys
from collections import OrderedDict

RAIZ_REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLINUBE = os.path.abspath(os.environ.get("CLINUBE", os.path.join(RAIZ_REPO, "..", "clinube")))

sys.path.insert(0, os.path.join(CLINUBE, "django-api"))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
os.environ.setdefault("POSTGRES_HOST", "localhost")
os.environ.setdefault("POSTGRES_DB", "clinube_ref")
os.environ.setdefault("POSTGRES_USER", "clinica")
os.environ.setdefault("POSTGRES_PASSWORD", "clinica")
os.environ.setdefault("DJANGO_DEBUG", "True")

import django                                                  # noqa: E402
django.setup()
from django.urls import get_resolver                           # noqa: E402
from django.urls.resolvers import URLPattern, URLResolver      # noqa: E402

FASES = OrderedDict([
    ("common", (0, "Cimientos y Plataforma")),
    ("accounts", (1, "Cuentas")),
    ("configuration", (2, "Configuración y catálogos")),
    ("specialties", (2, "Configuración y catálogos")),
    ("patients", (3, "Pacientes")),
    ("agenda", (4, "Agenda")),
    ("clinical", (5, "Historia clínica")),
    ("billing", (7, "Cobros")),
    ("inventory", (8, "Inventario y reportes")),
    ("finanzas", (9, "Finanzas")),
    ("app_paciente", (10, "App del paciente")),
    ("logros", (10, "App del paciente")),
    ("whatsapp", (13, "WhatsApp")),
])
ORDEN_VERBOS = ["GET", "POST", "PUT", "PATCH", "DELETE"]


def metodos_de(cls):
    if cls is None:
        return ["?"]
    ms = [m.upper() for m in ("get", "post", "put", "patch", "delete") if hasattr(cls, m)]
    for attr, verbo in (("list", "GET"), ("create", "POST"), ("retrieve", "GET"),
                        ("update", "PUT"), ("partial_update", "PATCH"), ("destroy", "DELETE")):
        if hasattr(cls, attr) and verbo not in ms:
            ms.append(verbo)
    return sorted(set(ms), key=lambda x: ORDEN_VERBOS.index(x) if x in ORDEN_VERBOS else 9) or ["?"]


def recoger():
    filas = []

    def recorrer(res, prefijo=""):
        for p in res.url_patterns:
            if isinstance(p, URLResolver):
                recorrer(p, prefijo + str(p.pattern))
            elif isinstance(p, URLPattern):
                cb = p.callback
                cls = getattr(cb, "cls", None) or getattr(cb, "view_class", None)
                objetivo = cls or cb
                try:
                    archivo = (inspect.getsourcefile(objetivo) or "").replace(CLINUBE + "/", "")
                    linea = inspect.getsourcelines(objetivo)[1]
                except Exception:
                    archivo, linea = "", 0
                filas.append({
                    "ruta": "/" + prefijo + str(p.pattern),
                    "vista": cls.__name__ if cls else getattr(cb, "__name__", "?"),
                    "modulo": objetivo.__module__ or "",
                    "archivo": archivo, "linea": linea,
                    "metodos": metodos_de(cls), "nombre": p.name or "",
                })

    recorrer(get_resolver())
    return filas


def limpia(ruta):
    return re.sub(r"\(\?P<(\w+)>[^)]*\)", r"<\1>", ruta)


def app_de(fila):
    return fila["modulo"].split(".")[1] if fila["modulo"].startswith("apps.") else "terceros"


def main():
    filas = recoger()
    api = [f for f in filas if f["ruta"].startswith("/api/v1/")]
    json.dump(api, open(os.path.join(RAIZ_REPO, "docs/contrato/rutas.json"), "w"),
              ensure_ascii=False, indent=1)

    por_app = OrderedDict()
    for f in api:
        por_app.setdefault(app_de(f), []).append(f)

    destino = os.path.join(RAIZ_REPO, "docs/contrato/RUTAS.md")
    with open(destino, "w", encoding="utf-8") as salida:
        salida.write(
            f"# Rutas de la API de Django\n\n"
            f"Las **{len(api)} rutas** bajo `/api/v1/` que expone el servidor de `clinube`, con\n"
            f"la vista que las implementa y el archivo y la línea donde está.\n\n"
            f"Generado con `scripts/exportar-rutas.py` recorriendo el resolvedor de URL\n"
            f"de Django, no leyendo los `urls.py` a mano: así no se escapa ninguna ruta\n"
            f"incluida indirectamente. Se regenera cuando `clinube` avance.\n\n"
            f"Todas llevan **barra final**. Los `<uuid:pk>` son los conversores de\n"
            f"Django tal como los declara.\n\n",
        )
        for app in list(dict.fromkeys(list(FASES) + list(por_app))):
            if app not in por_app:
                continue
            fase, titulo = FASES.get(app, ("—", "Terceros"))
            salida.write(f"## `{app}` — fase {fase}: {titulo}\n\n")
            salida.write("| Métodos | Ruta | Vista | Archivo |\n|---|---|---|---|\n")
            for f in sorted(por_app[app], key=lambda x: x["ruta"]):
                arch = f"`{f['archivo']}:{f['linea']}`" if f["archivo"] else "—"
                salida.write(f"| {' '.join(f['metodos'])} | `{limpia(f['ruta'])}` | `{f['vista']}` | {arch} |\n")
            salida.write("\n")
    print(f"{len(api)} rutas en {destino}")


if __name__ == "__main__":
    main()
