#!/usr/bin/env python3
"""
Escribe `docs/contrato/MODELO_DE_DATOS.md` leyendo el esquema REAL de una
base con las migraciones de Django aplicadas.

Se lee la base y no los `models.py` a propósito: lo que tiene que cumplir
Hibernate en modo `validate` es la tabla, con sus tipos, sus restricciones
y sus índices, no lo que el modelo diga.

Uso:
    BASE=clinube_ref python3 scripts/exportar-modelo-de-datos.py
"""
import json
import os
import subprocess
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.environ.get("BASE", "clinube_ref")
HOST = os.environ.get("PGHOST", "localhost")
USUARIO = os.environ.get("PGUSER", "clinica")


def consultar(sql):
    """
    Las filas, como listas de texto.

    Se piden en JSON y no separadas por un carácter porque algún valor por
    omisión del esquema lleva saltos de línea dentro, y entonces una fila
    se partía en dos y el recuento de columnas no cuadraba.
    """
    envuelto = f"select json_agg(t) from ({sql}) t"
    salida = subprocess.run(
        ["psql", "-h", HOST, "-U", USUARIO, "-d", BASE, "-tA", "-c", envuelto],
        capture_output=True, text=True,
    )
    if salida.returncode:
        sys.exit(f"psql falló: {salida.stderr.strip()}")
    filas = json.loads(salida.stdout.strip() or "[]") or []
    return [["" if v is None else str(v) for v in fila.values()] for fila in filas]


COLUMNAS = """
select c.table_name as tabla, c.column_name as columna, c.data_type as tipo,
       coalesce(c.character_maximum_length::text,
                c.numeric_precision::text || ',' || c.numeric_scale::text, '') as largo,
       c.is_nullable as nulable, coalesce(c.column_default, '') as defecto
from information_schema.columns c
join pg_tables t on t.tablename = c.table_name and t.schemaname = 'public'
where c.table_schema = 'public'
order by c.table_name, c.ordinal_position
"""

INDICES = """
select tablename as tabla, indexname as nombre, indexdef as definicion
from pg_indexes where schemaname = 'public'
order by tablename, indexname
"""

FORANEAS = """
select tc.table_name as tabla, kcu.column_name as columna,
       ccu.table_name as destino, ccu.column_name as columna_destino
from information_schema.table_constraints tc
join information_schema.key_column_usage kcu
  on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
join information_schema.constraint_column_usage ccu
  on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema
where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public'
order by tc.table_name, kcu.column_name
"""

TIPOS = {
    "character varying": "varchar", "timestamp with time zone": "timestamptz",
    "double precision": "float8", "character": "char",
}


def main():
    columnas, indices, foraneas = consultar(COLUMNAS), consultar(INDICES), consultar(FORANEAS)
    tablas = sorted({c[0] for c in columnas})

    por_tabla = {}
    for t, col, tipo, largo, nulable, defecto in columnas:
        por_tabla.setdefault(t, []).append((col, TIPOS.get(tipo, tipo), largo, nulable, defecto))
    idx_tabla = {}
    for t, nombre, definicion in indices:
        idx_tabla.setdefault(t, []).append((nombre, definicion))
    fk_tabla = {}
    for t, col, destino, col_destino in foraneas:
        fk_tabla.setdefault(t, []).append((col, destino, col_destino))

    destino = os.path.join(RAIZ, "docs/contrato/MODELO_DE_DATOS.md")
    with open(destino, "w", encoding="utf-8") as f:
        f.write(f"""# Modelo de datos

Las **{len(tablas)} tablas** del esquema de Django, leídas de una base con
todas sus migraciones aplicadas. La línea base de Flyway
(`backend/src/main/resources/db/migration/V1__linea_base_django.sql`) crea
exactamente esto, y Hibernate corre en modo `validate` contra ello: si una
entidad no cuadra con su tabla, el servidor no arranca.

Se lee la base y no los `models.py` a propósito: lo que hay que cumplir es
la tabla. Se regenera con `scripts/exportar-modelo-de-datos.py`.

Las tablas `django_*` y `auth_*` se conservan; el motivo está en la
cabecera de la línea base.

## Convenciones

- Las claves primarias de las tablas del dominio son `uuid`, no enteros:
  un identificador que no revela cuántos pacientes tiene una clínica ni en
  qué orden se crearon.
- Toda tabla del dominio lleva `tenant_id`: es por donde se filtra el
  aislamiento entre clínicas.
- Las marcas de tiempo son `timestamptz` y se guardan en UTC.
- Las tablas clínicas y financieras llevan borrado lógico (`is_active`,
  `deleted_at`): nunca se hace `DELETE` físico sobre datos de salud o de
  pagos.
- Los `JSONField` de Django son `jsonb`; en Kotlin se mapean con
  `@JdbcTypeCode(SqlTypes.JSON)`.

""")
        for t in tablas:
            f.write(f"## `{t}`\n\n| Columna | Tipo | Nulable | Por omisión |\n|---|---|---|---|\n")
            for col, tipo, largo, nulable, defecto in por_tabla[t]:
                tt = f"{tipo}({largo})" if largo else tipo
                nn = "sí" if nulable == "YES" else "no"
                dd = f"`{defecto}`" if defecto else "—"
                f.write(f"| `{col}` | {tt} | {nn} | {dd} |\n")
            if t in fk_tabla:
                f.write("\nClaves foráneas: ")
                f.write(", ".join(f"`{c}` → `{d}.{cd}`" for c, d, cd in fk_tabla[t]))
                f.write("\n")
            propios = [(n, d) for n, d in idx_tabla.get(t, []) if not n.endswith("_pkey")]
            if propios:
                f.write(f"\nÍndices ({len(propios)}):\n\n")
                for nombre, definicion in propios:
                    corto = definicion.split(" USING ")[-1] if " USING " in definicion else definicion
                    unico = "ÚNICO " if "UNIQUE" in definicion else ""
                    f.write(f"- `{nombre}`: {unico}{corto}\n")
            f.write("\n")
    print(f"{len(tablas)} tablas en {destino}")


if __name__ == "__main__":
    main()
