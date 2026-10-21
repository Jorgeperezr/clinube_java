# clinube_java

El servidor de [Clinube](https://github.com/Jorgeperezr/clinube) —gestión
clínica odontológica multi-clínica para Ecuador— reescrito en **Kotlin con
Spring Boot**.

El objetivo no es una versión nueva: es **el mismo servidor**. Responde
exactamente como el de Django, sobre la misma base de datos, para que el
panel web y la app del paciente no noten el cambio. Las reglas están en
[`CLAUDE.md`](CLAUDE.md) y el avance, ruta por ruta, en
[`docs/PROGRESO.md`](docs/PROGRESO.md).

## Qué hay

| Carpeta | Qué es |
|---|---|
| `backend/` | El servidor: Kotlin 2.1, Spring Boot 3.4, Java 21, PostgreSQL 16 |
| `frontend/` | El panel web (Next.js), **copia exacta** de clinube. No se edita |
| `movil/` | La app del paciente (Flutter), **copia exacta** de clinube. Es el testigo del contrato |
| `docs/` | Progreso, decisiones, diferencias, y el contrato de Django (rutas y modelo de datos) |
| `scripts/` | Entorno, arranque, captura y comparación del contrato |

## Arrancar en local

Primero, ver qué falta:

```bash
./scripts/comprobar-entorno.sh
```

Luego, todo con un comando (PostgreSQL, Redis y Mailpit en Docker; el
servidor en `http://localhost:8000`, el mismo puerto que usaba Django):

```bash
./scripts/start-local.sh
```

El panel, en otra terminal:

```bash
cd frontend && NEXT_PUBLIC_API_URL=http://localhost:8000 npm run dev
```

Datos de prueba: la clínica demo de clinube. Su usuario de administración
y su contraseña están en `backend/src/test/resources/semilla.sql`. Nunca
datos reales de pacientes.

## Comprobar la paridad

```bash
./scripts/probar.sh
```

pasa la suite dos veces, la segunda con la JVM en `Pacific/Kiritimati`
(UTC+14), y

```bash
./scripts/comparar-contrato
```

arranca Django (8010) y Kotlin (8000) contra copias de la misma base y
compara sus respuestas: código HTTP, claves, orden y valores.

## Traer los cambios de clinube

```bash
./scripts/sincronizar-clientes.sh
```

copia `frontend/` y `movil/`. Si cambia el esquema de Django:

```bash
./scripts/regenerar-linea-base.sh
```
