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
import collections
import glob
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

VERDE, ROJO, AMARILLO, GRIS, FIN = "\033[32m", "\033[31m", "\033[33m", "\033[90m", "\033[0m"


FRONTERA = "----frontera-del-comparador"


def multipart(formulario):
    """Un cuerpo multipart como el que manda el navegador con `FormData`."""
    partes = []
    for nombre, valor in formulario.get("campos", {}).items():
        partes.append(f'--{FRONTERA}\r\nContent-Disposition: form-data; name="{nombre}"\r\n\r\n{valor}\r\n'.encode())
    for nombre, archivo in formulario.get("archivos", {}).items():
        partes.append(
            f'--{FRONTERA}\r\nContent-Disposition: form-data; name="{nombre}"; filename="{archivo["nombre"]}"\r\n'
            f'Content-Type: {archivo.get("tipo", "application/octet-stream")}\r\n\r\n'.encode()
            + archivo.get("contenido", "").encode() + b"\r\n")
    return b"".join(partes) + f"--{FRONTERA}--\r\n".encode()


BINARIOS = ("application/pdf", "application/vnd.openxmlformats", "image/", "text/xml", "application/octet-stream")


def pedir(base, peticion, token):
    url = base + peticion["ruta"]
    datos = None
    cabeceras = {"Accept": "application/json"}
    if "cuerpo" in peticion:
        datos = json.dumps(peticion["cuerpo"]).encode()
        cabeceras["Content-Type"] = "application/json"
    elif "multipart" in peticion:
        datos = multipart(peticion["multipart"])
        cabeceras["Content-Type"] = f"multipart/form-data; boundary={FRONTERA}"
    elif "crudo" in peticion:
        datos = peticion["crudo"].encode()
        cabeceras["Content-Type"] = peticion.get("tipo", "application/json")
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    cabeceras.update(peticion.get("cabeceras", {}))

    sol = urllib.request.Request(url, data=datos, headers=cabeceras, method=peticion["metodo"])
    try:
        with urllib.request.urlopen(sol, timeout=20) as r:
            cuerpo, estado, recibidas = r.read(), r.status, r.headers
    except urllib.error.HTTPError as e:
        cuerpo, estado, recibidas = e.read(), e.code, e.headers
    except Exception as e:                                    # servidor caído
        return None, f"no respondió: {e}"
    tipo_recibido = recibidas.get("Content-Type", "") if recibidas else ""
    if tipo_recibido.startswith(BINARIOS):
        return estado, binario(cuerpo, tipo_recibido, recibidas.get("Content-Disposition"))
    try:
        return estado, json.loads(cuerpo) if cuerpo else None
    except json.JSONDecodeError:
        texto = cuerpo.decode("utf-8", "replace")
        # Una página de error de Django en HTML no se compara entera.
        return estado, texto[:200] if texto.lstrip().startswith("<") else texto


# ── Archivos: lo que dicen, no sus bytes ───────────────────────────────

def binario(contenido, tipo, disposicion):
    """
    Un PDF o un Excel no puede salir byte a byte igual de dos motores
    distintos: se compara lo que DICE (las palabras del PDF, las celdas del
    Excel) y sus cabeceras. Cualquier otro archivo, por su huella.
    """
    resumen = {"Content-Type": tipo, "Content-Disposition": disposicion}
    if tipo.startswith("application/pdf"):
        resumen.update(texto_de_pdf(contenido))
    elif tipo.startswith("application/vnd.openxmlformats-officedocument.spreadsheetml"):
        resumen["hojas"] = hojas_de_xlsx(contenido)
    else:
        resumen["sha256"] = hashlib.sha256(contenido).hexdigest()
    return resumen


def texto_de_pdf(contenido):
    jar = (glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/com.github.librepdf/openpdf/*/*/openpdf-*.jar")) or [None])[0]
    if not jar:
        return {"texto": "(no está OpenPDF en la caché de Gradle: compila el servidor una vez)"}
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(contenido)
        f.flush()
        salida = subprocess.run(["java", "-cp", jar, os.path.join(os.path.dirname(os.path.abspath(__file__)), "TextoDePdf.java"),
                                 f.name], capture_output=True, text=True)
    if salida.returncode:
        return {"texto": "ilegible: " + salida.stderr.strip().splitlines()[-1]}
    primera, _, resto = salida.stdout.partition("\n")
    # Las palabras, y cuántas veces sale cada una: el orden en que un motor
    # escribe las líneas en la página no es lo que se compara.
    return {"páginas": primera, "palabras": dict(sorted(collections.Counter(resto.split()).items()))}


def hojas_de_xlsx(contenido):
    """Las hojas de un .xlsx, con el valor de cada celda, leídas sin openpyxl."""
    ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
          "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}
    libro = zipfile.ZipFile(io.BytesIO(contenido))
    compartidos = []
    if "xl/sharedStrings.xml" in libro.namelist():
        for si in ET.fromstring(libro.read("xl/sharedStrings.xml")).findall("m:si", ns):
            compartidos.append("".join(t.text or "" for t in si.iter(f"{{{ns['m']}}}t")))
    relaciones = {r.get("Id"): r.get("Target") for r in ET.fromstring(libro.read("xl/_rels/workbook.xml.rels"))}
    hojas = {}
    for hoja in ET.fromstring(libro.read("xl/workbook.xml")).find("m:sheets", ns):
        destino = relaciones[hoja.get(f"{{{ns['r']}}}id")].lstrip("/")
        destino = destino if destino.startswith("xl/") else "xl/" + destino
        filas = []
        for fila in ET.fromstring(libro.read(destino)).iter(f"{{{ns['m']}}}row"):
            celdas = {}
            for c in fila.findall("m:c", ns):
                col = re.match(r"[A-Z]+", c.get("r")).group()
                t, v = c.get("t"), c.find("m:v", ns)
                if t == "s":
                    valor = compartidos[int(v.text)]
                elif t == "inlineStr":
                    valor = "".join(x.text or "" for x in c.iter(f"{{{ns['m']}}}t"))
                elif v is None:
                    continue
                elif t == "str":
                    valor = v.text or ""
                else:
                    valor = float(v.text)
                if valor != "":
                    celdas[col] = valor
            filas.append(celdas)
        hojas[hoja.get("name")] = filas
    return hojas


def diferencias_de_palabras(a, b):
    """
    Las palabras que tiene un PDF y no el otro (o no tantas veces). Si solo
    cambia cómo se juntan —el extractor pega dos celdas vecinas cuando no
    ve hueco entre ellas— y las letras son las mismas, no es una diferencia.
    """
    pa, pb = collections.Counter(a), collections.Counter(b)
    letras = lambda p: collections.Counter("".join(k * n for k, n in p.items()))
    if pa != pb and letras(pa) == letras(pb):
        return []
    faltan = sorted((pa - pb).elements())
    sobran = sorted((pb - pa).elements())
    return ([f"palabras: a Kotlin le faltan {faltan}"] if faltan else []) + ([f"palabras: Kotlin añade {sobran}"] if sobran else [])


# ── Lo que genera cada servidor ────────────────────────────────────────

GUARDADOS = {"django": {}, "kotlin": {}}   # {servidor: {nombre: valor}}, de las respuestas


def extraer(valor, camino):
    """
    Un valor dentro de una respuesta: `results.0.id`, o `[clave=arriendo].id`
    (el primer elemento de la lista cuyo `clave` es «arriendo»).
    """
    for paso in camino.split("."):
        m = re.fullmatch(r"\[(\w+)=(.*)\]", paso)
        if m:
            valor = next((x for x in valor if str(x.get(m.group(1))) == m.group(2)), None)
        elif isinstance(valor, list):
            valor = valor[int(paso)]
        else:
            valor = valor.get(paso) if isinstance(valor, dict) else None
        if valor is None:
            return None
    return valor


def fechas():
    """Las fechas de hoy en la clínica, para escribir peticiones que valgan cualquier día."""
    from datetime import date, datetime, timedelta
    from zoneinfo import ZoneInfo
    hoy = datetime.now(ZoneInfo(os.environ.get("DJANGO_TIME_ZONE", "America/Guayaquil"))).date()
    primero = hoy.replace(day=1)
    fin_anterior = primero - timedelta(days=1)
    return hoy, {
        "{mes}": f"{hoy:%Y-%m}", "{mes-1}": f"{fin_anterior:%Y-%m}", "{anio}": str(hoy.year),
        "{primero}": primero.isoformat(), "{fin-anterior}": fin_anterior.isoformat(),
        "{inicio-anterior}": fin_anterior.replace(day=1).isoformat(),
    }


def con_fechas(valor):
    """«{hoy}», «{hoy-1}», «{hoy+3}», «{mes}», «{mes-1}», «{primero}», «{anio}»… por su valor de hoy."""
    from datetime import timedelta
    if isinstance(valor, dict):
        return {k: con_fechas(v) for k, v in valor.items()}
    if isinstance(valor, list):
        return [con_fechas(v) for v in valor]
    if isinstance(valor, str) and "{" in valor:
        hoy, fijas = fechas()
        for clave, texto in fijas.items():
            valor = valor.replace(clave, texto)
        return re.sub(r"\{hoy([+-]\d+)?\}", lambda m: (hoy + timedelta(days=int(m.group(1) or 0))).isoformat(), valor)
    return valor


def con_guardados(valor, servidor):
    """Sustituye "{guardado:nombre}" por lo que ESE servidor devolvió antes."""
    if isinstance(valor, dict):
        return {k: con_guardados(v, servidor) for k, v in valor.items()}
    if isinstance(valor, list):
        return [con_guardados(v, servidor) for v in valor]
    if isinstance(valor, str) and "{guardado:" in valor:
        return re.sub(r"\{guardado:([^}]+)\}", lambda m: str(GUARDADOS[servidor].get(m.group(1), m.group(0))), valor)
    return valor


def refresco(base, credenciales):
    """El token de refresco de un login nuevo en ese servidor."""
    estado, cuerpo = pedir(base, {"metodo": "POST", "ruta": "/api/v1/auth/login/",
                                  "cuerpo": credenciales}, None)
    return cuerpo.get("refresh") if estado == 200 and isinstance(cuerpo, dict) else None


def con_refrescos(valor, servidor, base, usuarios, guardados):
    """
    Sustituye "{refresco:usuario#clave}" por un refresco de ESE servidor:
    cada uno firma los suyos, así que no se puede escribir uno fijo. La
    misma clave da el mismo token, para pedir dos veces con el mismo.
    """
    if isinstance(valor, dict):
        return {k: con_refrescos(v, servidor, base, usuarios, guardados) for k, v in valor.items()}
    if isinstance(valor, str) and valor.startswith("{refresco:") and valor.endswith("}"):
        clave = valor[len("{refresco:"):-1]
        if (servidor, clave) not in guardados:
            guardados[(servidor, clave)] = refresco(base, usuarios[clave.split("#")[0]])
        return guardados[(servidor, clave)]
    return valor


ESTADOS = {}   # {código: id} del catálogo del odontograma, leído de Django


def con_estados(valor, base, token):
    """
    Sustituye "{estado:CARIES}" por el id de ese estado del odontograma. Los
    estados vienen de la base de referencia con ids que no se conocen de
    antemano; las dos copias tienen los mismos.
    """
    if isinstance(valor, dict):
        return {k: con_estados(v, base, token) for k, v in valor.items()}
    if isinstance(valor, str) and valor.startswith("{estado:") and valor.endswith("}"):
        if not ESTADOS:
            _, lista = pedir(base, {"metodo": "GET", "ruta": "/api/v1/odontogram-states/"}, token)
            ESTADOS.update({e["code"]: e["id"] for e in lista or []})
        return ESTADOS.get(valor[len("{estado:"):-1], valor)
    return valor


def entrar(base, credenciales):
    estado, cuerpo = pedir(base, {"metodo": "POST", "ruta": "/api/v1/auth/login/",
                                  "cuerpo": credenciales}, None)
    if estado == 200 and isinstance(cuerpo, dict):
        return cuerpo.get("access")
    return None


ORIGENES = {}   # {"django": "http://127.0.0.1:8010", "kotlin": "http://127.0.0.1:8000"}


def sin_origen(valor, servidor):
    """
    Los enlaces absolutos (`next`, `previous`) llevan el host y el puerto de
    cada servidor, que por fuerza son distintos. Se sustituyen por un
    marcador; el resto del enlace sí se compara.
    """
    if isinstance(valor, str) and servidor in ORIGENES:
        return valor.replace(ORIGENES[servidor], "{servidor}")
    return valor


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
        a, b = sin_origen(a, "django"), sin_origen(b, "kotlin")
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

    ORIGENES.update({"django": args.django.rstrip("/"), "kotlin": args.kotlin.rstrip("/")})
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

    refrescos = {}
    iguales, distintas, filas = 0, 0, []
    for peticion in config["peticiones"]:
        volatiles = set(peticion.get("volatiles", [])) | {"access", "refresh", "jti"}
        usuario = peticion.get("usuario")
        enD, enK = peticion, peticion
        if "cuerpo" in peticion:
            peticion = {**peticion, "cuerpo": con_estados(peticion["cuerpo"], args.django, tokens["admin"]["django"])}
            enD = {**peticion, "cuerpo": con_refrescos(peticion["cuerpo"], "django", args.django, config["usuarios"], refrescos)}
            enK = {**peticion, "cuerpo": con_refrescos(peticion["cuerpo"], "kotlin", args.kotlin, config["usuarios"], refrescos)}
        enD = con_fechas(con_guardados(enD, "django"))
        enK = con_fechas(con_guardados(enK, "kotlin"))
        eD, cD = pedir(args.django, enD, tokens.get(usuario, {}).get("django") if usuario else None)
        eK, cK = pedir(args.kotlin, enK, tokens.get(usuario, {}).get("kotlin") if usuario else None)
        for nombre, camino in peticion.get("guardar", {}).items():
            GUARDADOS["django"][nombre] = extraer(cD, camino)
            GUARDADOS["kotlin"][nombre] = extraer(cK, camino)

        problemas = []
        if eD != eK:
            problemas.append(f"código HTTP: Django {eD} y Kotlin {eK}")
        if isinstance(cD, dict) and isinstance(cK, dict) and "palabras" in cD and "palabras" in cK:
            problemas += diferencias_de_palabras(cD.pop("palabras"), cK.pop("palabras"))
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
