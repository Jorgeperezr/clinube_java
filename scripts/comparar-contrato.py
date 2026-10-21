#!/usr/bin/env python3
"""
Comparador de contrato: lanza la MISMA lista de peticiones contra Django y
contra Kotlin y señala en qué se diferencian.

Compara el código HTTP y, del JSON, las claves, su orden, sus tipos y sus
valores. Lo que cambia solo —identificadores nuevos, fechas de creación,
tokens— se compara por tipo y no por valor; esas claves se declaran en
`volatiles` de cada petición en `peticiones.json`.

Qué hace falta:
  · Django de ../clinube escuchando en el 8010;
  · el servidor de Kotlin en el 8000;
  · cada uno con su copia de la MISMA base.

`scripts/comparar-contrato` levanta los dos y llama a esto.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

VERDE, ROJO, AMARILLO, GRIS, FIN = "\033[32m", "\033[31m", "\033[33m", "\033[90m", "\033[0m"


def pedir(base, peticion, token):
    url = base + peticion["ruta"]
    datos = None
    cabeceras = {"Accept": "application/json"}
    if "cuerpo" in peticion:
        datos = json.dumps(peticion["cuerpo"]).encode()
        cabeceras["Content-Type"] = "application/json"
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    cabeceras.update(peticion.get("cabeceras", {}))

    sol = urllib.request.Request(url, data=datos, headers=cabeceras, method=peticion["metodo"])
    try:
        with urllib.request.urlopen(sol, timeout=20) as r:
            cuerpo, estado = r.read(), r.status
    except urllib.error.HTTPError as e:
        cuerpo, estado = e.read(), e.code
    except Exception as e:                                    # servidor caído
        return None, f"no respondió: {e}"
    try:
        return estado, json.loads(cuerpo) if cuerpo else None
    except json.JSONDecodeError:
        return estado, cuerpo.decode("utf-8", "replace")[:200]


def entrar(base, credenciales):
    estado, cuerpo = pedir(base, {"metodo": "POST", "ruta": "/api/v1/auth/login/",
                                  "cuerpo": credenciales}, None)
    if estado == 200 and isinstance(cuerpo, dict):
        return cuerpo.get("access")
    return None


def comparar(a, b, volatiles, camino=""):
    """Las diferencias entre dos respuestas, como lista de textos."""
    dif = []
    if type(a) is not type(b):
        # Un entero donde el otro da un decimal no es la misma respuesta,
        # pero int/float con el mismo valor sí se tolera.
        if not (isinstance(a, (int, float)) and isinstance(b, (int, float)) and a == b):
            return [f"{camino or '(raíz)'}: Django da {tipo(a)} y Kotlin {tipo(b)}"]
    if isinstance(a, dict):
        clavesA, clavesB = list(a), list(b)
        if set(clavesA) != set(clavesB):
            faltan = sorted(set(clavesA) - set(clavesB))
            sobran = sorted(set(clavesB) - set(clavesA))
            if faltan:
                dif.append(f"{camino or '(raíz)'}: a Kotlin le faltan {faltan}")
            if sobran:
                dif.append(f"{camino or '(raíz)'}: Kotlin añade {sobran}")
        elif clavesA != clavesB:
            dif.append(f"{camino or '(raíz)'}: mismas claves en otro orden\n"
                       f"      Django: {clavesA}\n      Kotlin: {clavesB}")
        for clave in clavesA:
            if clave in clavesB:
                dif += comparar(a[clave], b[clave], volatiles, f"{camino}.{clave}" if camino else clave)
    elif isinstance(a, list):
        if len(a) != len(b):
            dif.append(f"{camino}: Django da {len(a)} elementos y Kotlin {len(b)}")
        for i, (x, y) in enumerate(zip(a, b)):
            dif += comparar(x, y, volatiles, f"{camino}[{i}]")
    else:
        hoja = camino.split(".")[-1].split("[")[0]
        if hoja in volatiles:
            return dif                                        # cambia solo: basta el tipo
        if a != b:
            dif.append(f"{camino}:\n      Django: {a!r}\n      Kotlin: {b!r}")
    return dif


def tipo(v):
    return {dict: "objeto", list: "lista", str: "texto", bool: "booleano",
            int: "entero", float: "decimal", type(None): "nulo"}.get(type(v), type(v).__name__)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--django", default="http://127.0.0.1:8010")
    p.add_argument("--kotlin", default="http://127.0.0.1:8000")
    p.add_argument("--peticiones", default=None)
    p.add_argument("--markdown", default=None, help="escribe el informe para docs/PROGRESO.md")
    args = p.parse_args()

    ruta = args.peticiones or __file__.rsplit("/", 1)[0] + "/peticiones.json"
    with open(ruta, encoding="utf-8") as f:
        config = json.load(f)

    tokens = {}
    for nombre, credenciales in config.get("usuarios", {}).items():
        tokens[nombre] = {
            "django": entrar(args.django, credenciales),
            "kotlin": entrar(args.kotlin, credenciales),
        }
        for servidor in ("django", "kotlin"):
            if tokens[nombre][servidor] is None:
                print(f"{AMARILLO}! no se pudo entrar como «{nombre}» en {servidor}{FIN}")

    iguales, distintas, filas = 0, 0, []
    for peticion in config["peticiones"]:
        volatiles = set(peticion.get("volatiles", [])) | {"access", "refresh", "jti"}
        usuario = peticion.get("usuario")
        eD, cD = pedir(args.django, peticion, tokens.get(usuario, {}).get("django") if usuario else None)
        eK, cK = pedir(args.kotlin, peticion, tokens.get(usuario, {}).get("kotlin") if usuario else None)

        problemas = []
        if eD != eK:
            problemas.append(f"código HTTP: Django {eD} y Kotlin {eK}")
        problemas += comparar(cD, cK, volatiles)

        if problemas:
            distintas += 1
            print(f"{ROJO}✘ {peticion['nombre']}{FIN}  {GRIS}{peticion['metodo']} {peticion['ruta']}{FIN}")
            for d in problemas:
                print(f"    {d}")
            filas.append((peticion, "distinta", problemas))
        else:
            iguales += 1
            print(f"{VERDE}✔ {peticion['nombre']}{FIN}  {GRIS}{eD}{FIN}")
            filas.append((peticion, "igual", []))

    print(f"\n{iguales} iguales, {distintas} distintas, de {iguales + distintas}.")

    if args.markdown:
        with open(args.markdown, "w", encoding="utf-8") as f:
            f.write(f"| Petición | Ruta | Resultado |\n|---|---|---|\n")
            for peticion, estado, problemas in filas:
                marca = "igual a Django" if estado == "igual" else "**distinta**"
                f.write(f"| {peticion['nombre']} | `{peticion['metodo']} {peticion['ruta']}` | {marca} |\n")
        print(f"Informe en {args.markdown}")

    return 1 if distintas else 0


if __name__ == "__main__":
    sys.exit(main())
