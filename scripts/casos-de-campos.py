#!/usr/bin/env python3
"""
Pasa miles de valores por los campos de DRF que usan las rutas portadas
(texto, decimal, entero, booleano, JSON, opción y fecha) y guarda lo que
devuelve cada uno —el valor como lo escribiría la respuesta, o los mensajes
de error en el idioma de clinube—, para que `CamposDrfTest` compruebe que
`CamposDrf` de Kotlin hace exactamente lo mismo.

Hace falta porque estas reglas son de Python y no de Java: `"36.50"` como
texto tiene dos decimales y no vale, pero `36.50` como número es el float
36.5 y sí; `Decimal("1__0")` es 10; `int("٣")` es 3 y `int("\\x1c7")` no;
el float 1e16 se escribe «1e+16»… Una prueba escrita a mano no encuentra
eso. La semilla es fija: el fichero no cambia entre ejecuciones.

    CLINUBE=../clinube ../clinube/.venv/bin/python -I scripts/casos-de-campos.py
"""
import json
import os
import random
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLINUBE = os.path.abspath(os.environ.get("CLINUBE", os.path.join(RAIZ, "..", "clinube")))
DESTINO = os.path.join(RAIZ, "backend", "src", "test", "resources", "campos", "casos.json")

sys.path.insert(0, os.path.join(CLINUBE, "django-api"))
# Sin base de datos: los campos no la tocan. Solo hacen falta los ajustes
# (el idioma de los mensajes).
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
os.environ.setdefault("DJANGO_DEBUG", "True")
os.environ.setdefault("DJANGO_LOG_LEVEL", "WARNING")
import django  # noqa: E402

django.setup()
from django.conf import settings  # noqa: E402
from django.utils import translation  # noqa: E402
from rest_framework import serializers  # noqa: E402
from rest_framework.fields import empty  # noqa: E402

translation.activate(settings.LANGUAGE_CODE)

# Los campos, con la configuración que tienen en los serializadores de
# clinube. El nombre lo usa la prueba de Kotlin para saber qué leer.
CAMPOS = {
    "texto": lambda: serializers.CharField(max_length=15, allow_blank=True, required=False),
    "texto_obligatorio": lambda: serializers.CharField(),
    "texto_corto": lambda: serializers.CharField(max_length=2, allow_blank=True, required=False),
    "correo": lambda: serializers.EmailField(max_length=254, allow_blank=True, required=False),
    "decimal": lambda: serializers.DecimalField(max_digits=4, decimal_places=1, allow_null=True, required=False),
    "entero": lambda: serializers.IntegerField(min_value=0, max_value=32767, allow_null=True, required=False),
    "booleano_nulo": lambda: serializers.BooleanField(allow_null=True, required=False),
    "json": lambda: serializers.JSONField(required=False),
    "opcion": lambda: serializers.ChoiceField(choices=[("pre", "Presuntivo"), ("def", "Definitivo")], required=False),
    "fecha": lambda: serializers.DateField(),
}

azar = random.Random(2026)

TEXTOS = [
    "", " ", "  \t", " ", "\x1c", "　x　", "abc", " abc ", "120/80", "x" * 15, "x" * 16, "😀" * 15,
    "😀" * 16, "ab", "abc", "a\x00b", "\x00", "pre", "def", "PRE", " pre", "None", "null", "true", "True", "1",
    "0", "y", "on", "nope", "36.5", "36.50", "36.55", "999.9", "1000", "-99.9", "-0", "-0.0", "0.00", "0e5",
    "1e2", "1E+2", "1e-1", "1e1_0", "1_0", "1__0", "_10", "10_", "1_.5", "1 _", "+_1", "٣", "٣.٥", "１２",
    "²", "1 0", ".5", "5.", ".", "-.5e-0", "Inf", "-infinity", "NaN", "nAn12", "sNaN", "1e", "e1", "0x10",
    " 7 ", "\x1c7", "7\x1c", " 7", "12.0", "12.00 ", "12.0\x1c", "12.", "12.0.0", "1e3", "007", "+5",
    "32767", "32768", "-1", "99999999999999999999", "1" * 1001, " " * 1001 + "1", "2026-01-01", "2026-1-1",
    "20260101", "2026-W01-1", "2026-02-30", "2026-01-01T10:00", " 2026-01-01", "2026-01-01 ", "٢٠٢٦-٠١-٠١",
    "+2026-01-01", "0000-01-01", "a@b.ec", "A@B.EC", " a@b.ec ", "no-es-correo", "a@b", "a@localhost",
    "1e99999999999999999999", "1e-99999999999999999999", "0.0000", "-000.10", "00036.5",
    "1e999999999999999999", "1e1000000000000000000", "1e-1000000000000000000", "1e9223372036854775807",
    "2026W0112", "2026W011", "2026-W01", "2026W01", "2026-W011", "2026-W53-1", "2025-W53-1", "2026-W00-1",
    "2026-W01-8", "2026W011xy", "2026-W01-1x", "2026-01-01\n", "2026-01-01\n\n", "2026-1-1\n", "9999-W52-7",
    "0000-W01-1", "2004-W53-7", "2026-01-1x", "2026-13-01", "2024-02-29", "2023-02-29", "'pre'", "x'y", "a\"b",
    "\x7f", "\xad", "\u2028", "\U0001f600x",
]
NUMEROS = [0, 1, -1, 7, 12, 32767, 32768, 10 ** 20, -(10 ** 20), 0.0, -0.0, 0.5, 36.5, 36.55, 999.9, 1000.0,
           12.0, 12.7, 1e2, 1e15, 1e16, 1e-5, 1e-4, 0.1, 1.5e300, 123456789.123, 1e400, -1e400]
OTROS = [True, False, None, [], [1], ["pre"], {}, {"a": 1}, {"a": [1.5, 1e-7, {"b": None}]}, [1e400]]


def valores():
    """Los valores fijos y, después, combinaciones al azar."""
    yield from TEXTOS
    yield from NUMEROS
    yield from OTROS
    for _ in range(400):
        trozos = ["", " ", "-", "+", "_", ".", "0", "1", "5", "9", "٣", "e", "E", " ", "\x1c", "x", "😀", ","]
        yield "".join(azar.choice(trozos) for _ in range(azar.randint(1, 6)))
    for _ in range(150):
        yield azar.choice([azar.uniform(-2000, 2000), round(azar.uniform(-1000, 1000), 1),
                           azar.randint(-40000, 40000), azar.uniform(0, 1) * 10 ** azar.randint(-8, 20)])


def caso(nombre, entrada):
    campo = CAMPOS[nombre]()
    campo.bind(nombre, serializers.Serializer())
    try:
        valor = campo.run_validation(entrada)
    except serializers.ValidationError as e:
        return {"errores": [str(m) for m in e.detail]}
    except serializers.SkipField:
        return {"ausente": True}
    representado = None if valor is None else campo.to_representation(valor)
    return {"salida": json.dumps(representado, ensure_ascii=False, separators=(",", ":"))}


casos = []
for nombre in CAMPOS:
    casos.append({"campo": nombre, **caso(nombre, empty)})
    for valor in valores():
        texto = json.dumps(valor, ensure_ascii=False)  # 1e400 sale «Infinity»: lo que no llega por JSON se salta
        if "Infinity" in texto or "NaN" in texto and not isinstance(valor, str):
            texto = texto.replace("-Infinity", "-1e400").replace("Infinity", "1e400")
        entrada = json.loads(texto)  # lo que DRF recibiría de ese cuerpo
        casos.append({"campo": nombre, "entrada": texto, **caso(nombre, entrada)})

os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
with open(DESTINO, "w", encoding="utf-8") as f:
    json.dump(casos, f, ensure_ascii=False, separators=(",", ":"))
    f.write("\n")
errores = sum(1 for c in casos if "errores" in c)
print(f"{len(casos)} casos en {os.path.relpath(DESTINO, RAIZ)} ({errores} con error).", file=sys.stderr)
