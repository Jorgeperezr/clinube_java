#!/usr/bin/env python3
"""
Calcula con el `alertas.py` DE DJANGO las alertas y los choques de miles
de antecedentes y recetas, y los guarda para que `ReglasDeAlertasTest`
compruebe que Kotlin da exactamente lo mismo.

Las alertas son seguridad del paciente: una negación mal leída, una tilde o
un espacio raro no pueden cambiar el resultado. Por eso, además de los casos
de tests_alertas.py, se generan textos al azar (con semilla fija, así el
fichero no cambia entre ejecuciones) con lo que más fácil rompe una
traducción: negaciones al principio y en medio, tildes compuestas y
descompuestas, mayúsculas, espacios de Unicode, cifras y signos pegados a la
palabra, y casillas del formulario 033 con valores de todos los tipos.

`alertas.py` solo usa `re` y `unicodedata`: se importa sin Django.

    CLINUBE=../clinube python3 -I scripts/casos-de-alertas.py
"""
import importlib.util
import json
import os
import random
import sys
from types import SimpleNamespace

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLINUBE = os.path.abspath(os.environ.get("CLINUBE", os.path.join(RAIZ, "..", "clinube")))
FUENTE = os.path.join(CLINUBE, "django-api", "apps", "clinical", "alertas.py")
DESTINO = os.path.join(RAIZ, "backend", "src", "test", "resources", "alertas", "casos.json")

spec = importlib.util.spec_from_file_location("alertas_de_django", FUENTE)
alertas = importlib.util.module_from_spec(spec)
spec.loader.exec_module(alertas)

azar = random.Random(2026)

PALABRAS = [
    "penicilina", "Penicilina", "PENICILINA", "amoxicilina", "Amoxicilina 500 mg", "Augmentin", "ampicilina",
    "betalactámicos", "dicloxacilina", "cefalexina", "Ceftriaxona", "clindamicina", "azitromicina",
    "doxiciclina", "tetraciclina", "ibuprofeno", "Ibuprofeno 400", "aspirina", "ácido acetilsalicílico",
    "acido acetilsalicilico", "diclofenaco", "ketorolaco", "AINE", "aines", "lidocaína", "articaína",
    "anestesia local", "anestésicos", "látex", "latex", "LÁTEX", "warfarina", "Sintrom 4 mg", "Rivaroxabán",
    "Xarelto", "apixabán", "clopidogrel", "heparina", "anticoagulantes", "antiagregante", "alendronato",
    "Ácido alendrónico", "Zoledronato", "denosumab", "Prolia", "Fosamax", "metformina 850", "insulina",
    "diabetes tipo 2", "Diabética", "hipertensión", "Hipertensa", "cardiopatía", "infarto en 2019", "arritmia",
    "angina de pecho", "insuficiencia cardíaca", "enf. cardíaca", "enfermedad cardíaca", "endocarditis",
    "prótesis valvular aórtica", "válvula protésica", "válvula artificial", "cardiopatía congénita",
    "hemofilia A", "hemorragias", "coagulopatía", "von Willebrand", "Von Willebrand", "trombocitopenia",
    "paracetamol", "omeprazol", "losartán", "control", "en la infancia", "dudosa", "urticaria",
    "no recuerda cuál", "toma", "refiere", "alérgica a la", "Alergia a la", "nadar", "sinusitis",
    "noviembre", "ningún problema", "nada", "negativo", "descarta", "niega",
]
# Lo que más rompe una traducción: delante, detrás y entre las palabras.
DELANTE = ["", "", "", "", "no ", "No ", "sin ", "Sin ", "niega ", "Niega ", "ninguna ", "Ningún ", "nada de ",
           "descarta ", "negativo a ", "sino ", "nosotros ", "sinusitis y ", "no-", " no ", " Sin ",
           "\x1cno ", "　", "\t", "  ", "Ño ", "ño ", "toma ", "refiere ", "alergia a "]
PEGADO = ["", "", "", "", "_", "2", "²", "º", "‿", "Ⅻ", "٣", "ß", "é", "é", "😀", "-", "'", "ª", "ﬁ"]
DETRAS = ["", "", "", "s", "a", "o", "²", "_x", "1", "‿x", "😀", " 500 mg", "́", "ina", "nos"]
SEPARA = [". ", "; ", ", ", "\n", " y ", "\r\n", ".", ",,", " ", " — ", " / ", "; ", " ", "\x85"]
RARAS = ["İ", "Σ", "ẞ", "Ǆ", "ﬀ", "́", "​", "﻿", "Å", "Å", "ñ", "ŉ"]


def frase():
    texto = azar.choice(DELANTE) + azar.choice(PEGADO) + azar.choice(PALABRAS) + azar.choice(DETRAS)
    if azar.random() < 0.15:
        i = azar.randrange(len(texto) + 1)
        texto = texto[:i] + azar.choice(RARAS) + texto[i:]
    return texto


def campo():
    if azar.random() < 0.3:
        return ""
    partes = [frase() for _ in range(azar.randint(1, 4))]
    return "".join(p + azar.choice(SEPARA) for p in partes[:-1]) + partes[-1]


CASILLAS = ["Alergia antibiótico", "Alergia anestesia", "Otro", "Hemorragias", "Diabetes",
            "Hipertensión arterial", "Enf. cardíaca", "VIH/SIDA", "Tuberculosis", "Asma",
            "Alergia antibiótico"]
MARCAS = [True, True, True, False, None, 1, 0, 0.0, "si", "", [], [1], {}, {"x": 1}]


def seccion_033():
    r = azar.random()
    if r < 0.05:
        return None
    if r < 0.1:
        return []
    salida = {}
    for nombre in azar.sample(CASILLAS, azar.randint(0, 5)):
        valor = {}
        if azar.random() < 0.9:
            valor["checked"] = azar.choice(MARCAS)
        d = azar.random()
        if d < 0.6:
            valor["detail"] = campo()
        elif d < 0.7:
            valor["detail"] = None
        salida[nombre] = valor if azar.random() < 0.95 else azar.choice(["x", 1, True, None])
    return salida


def receta():
    return "\n".join(frase() for _ in range(azar.randint(1, 4)))


def caso(fondo, form033, recetas):
    ns_fondo = None if fondo is None else SimpleNamespace(
        allergies=fondo["alergias"], medications=fondo["medicacion"],
        conditions=fondo["condiciones"], is_pregnant=fondo["embarazada"])
    ns_033 = None if form033 is None else SimpleNamespace(
        antecedentes_personales=form033["antecedentes_personales"], embarazada=form033["embarazada"])
    activas = alertas.alertas_de_antecedentes(ns_fondo, ns_033)
    return {"fondo": fondo, "form033": form033, "alertas": activas,
            "recetas": [{"texto": t, "choques": alertas.revisar_receta(t, activas)} for t in recetas]}


def fondo(alergias="", medicacion="", condiciones="", embarazada=None):
    return {"alergias": alergias, "medicacion": medicacion, "condiciones": condiciones, "embarazada": embarazada}


def f033(seccion, embarazada=None):
    return {"antecedentes_personales": seccion, "embarazada": embarazada}


# Los casos de tests_alertas.py, tal cual.
casos = [
    caso(fondo(alergias="Alergia a la Penicilina en la infancia"), None, []),
    caso(fondo(alergias="amoxicilina"), None, []),
    *[caso(fondo(alergias=t), None, []) for t in ("Niega alergias", "Sin alergia a la penicilina",
                                                  "No refiere alergia a penicilina", "Ninguna", "Descarta alergia a AINE")],
    caso(fondo(medicacion="No toma anticoagulantes"), None, []),
    caso(fondo(alergias="Niega alergia al látex. Alérgica al ibuprofeno"), None, []),
    caso(fondo(alergias="LÁTEX"), None, []),
    caso(fondo(medicacion="Sintrom 4 mg"), None, []),
    caso(fondo(medicacion="Ácido alendrónico semanal; metformina 850",
               condiciones="Hipertensión controlada. Prótesis valvular aórtica"), None, []),
    caso(fondo(embarazada=True), None, ["Doxiciclina 100 mg"]),
    caso(None, None, ["Amoxicilina 500 mg"]),
    caso(fondo(), f033({"Alergia antibiótico": {"checked": True, "detail": ""},
                        "Alergia anestesia": {"checked": False, "detail": ""},
                        "Hemorragias": {"checked": True, "detail": ""},
                        "Diabetes": {"checked": True, "detail": ""},
                        "Enf. cardíaca": {"checked": True, "detail": ""}}, True), []),
    caso(fondo(alergias="penicilina"), f033({"Alergia antibiótico": {"checked": True}}), []),
    caso(fondo(alergias="penicilina"), None, ["Amoxicilina 500 mg c/8h por 7 días", "Cefalexina 500 mg",
                                              "Paracetamol 1 g c/8h. Clindamicina 300 mg"]),
    caso(fondo(medicacion="Warfarina"), None, ["Ibuprofeno 400 mg cada 8 horas"]),
    caso(fondo(alergias="penicilina", medicacion="clopidogrel"), None, ["Amoxicilina 500 mg\nKetorolaco 10 mg"]),
    caso(None, f033({"Alergia antibiótico": {"checked": True, "detail": "Amoxicilina, urticaria"}}), ["Amoxicilina 875 mg"]),
    caso(None, f033({"Alergia antibiótico": {"checked": False, "detail": "penicilina"}}), []),
    caso(None, f033({"Otro": {"checked": True, "detail": "Toma Xarelto. Zoledronato por osteoporosis"}}), []),
    caso(None, f033({"Alergia antibiótico": {"checked": True, "detail": "no recuerda cuál"}}), ["Azitromicina"]),
]

descartados = 0
while len(casos) < 3000:
    datos_fondo = None if azar.random() < 0.1 else fondo(campo(), campo(), campo(), azar.choice([None, None, False, True]))
    datos_033 = None if azar.random() < 0.4 else f033(seccion_033(), azar.choice([None, None, False, True]))
    try:
        casos.append(caso(datos_fondo, datos_033, [receta() for _ in range(azar.randint(0, 2))]))
    except (TypeError, AttributeError):
        # Django da un 500 con un detalle que no es texto: no es un caso que comparar.
        descartados += 1

os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
with open(DESTINO, "w", encoding="utf-8") as f:
    json.dump(casos, f, ensure_ascii=False, separators=(",", ":"))
    f.write("\n")
con_alertas = sum(1 for c in casos if c["alertas"])
choques = sum(1 for c in casos for r in c["recetas"] if r["choques"])
print(f"{len(casos)} casos en {os.path.relpath(DESTINO, RAIZ)}: {con_alertas} con alertas, "
      f"{choques} recetas con choques; {descartados} descartados (500 en Django).", file=sys.stderr)
