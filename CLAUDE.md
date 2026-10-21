# Convenciones de clinube_java

Este repositorio reimplementa en **Kotlin con Spring Boot** el servidor de
[clinube](https://github.com/Jorgeperezr/clinube), que hoy funciona en
producción con Django. El objetivo es **las mismas funcionalidades y la
misma apariencia**, no una versión mejorada.

El desarrollo es **local**. Nada se despliega a ningún servidor.

## La regla que decide todo

> El panel web y la app del paciente no se tocan. Si para que algo
> funcione hubiera que cambiarlos, el fallo está en el servidor.

`frontend/` y `movil/` son copias exactas de `clinube` y llegan con
`scripts/sincronizar-clientes.sh`. Son el testigo: si el panel entra y
funciona contra este servidor, el contrato se cumple.

**Archivos que no se tocan nunca**, ni al copiarlos:

- `frontend/lib/odontogram/meshProvider.js`, `contract.js`, `registry.js`,
  `Odontogram3D.js`
- `frontend/lib/ClinicalTabs.js`
- `frontend/lib/Odontogram.js`
- `frontend/lib/periodontal/PeriodontalMatrix.js`

## Idioma

Código, comentarios, nombres de dominio, mensajes de commit y
documentación **en castellano**, como en clinube.

Los textos de la interfaz y de los errores **son los de clinube,
copiados**. No se redactan de nuevo ni se corrigen: están capturados de un
servidor Django real en
`backend/src/main/kotlin/com/clinube/comun/errores/MensajesDeDjango.kt`,
con sus rarezas incluidas (falta una tilde en «no es valido para ningun
tipo de token»; falta el punto final en «no tiene una cuenta activa»).
Arreglarlas sería dejar de responder igual que Django.

Las clases y los campos van en castellano; los **nombres de tabla y de
columna son los de Django**, porque son el contrato con la base de datos:

```kotlin
@Entity @Table(name = "common_tenant")
class Clinica(
    @Column(name = "is_active") var activa: Boolean = true,
)
```

## De dónde sale la verdad

Por orden:

1. **El código de Django**, en `../clinube/django-api/apps/`.
2. **Las pruebas de cada app** (`apps/*/tests*.py`). Dicen las reglas de
   negocio mejor que cualquier documento.
3. **Las respuestas capturadas** de un Django en marcha, en
   `docs/contrato/`. Cuando algo del formato no está claro —el formato de
   las fechas, el orden de las claves, el texto exacto de un error—, no se
   deduce: se captura.

`clinube` no tiene (todavía) `docs/contrato/` ni
`scripts/exportar-contrato.sh`; ver `docs/DIFERENCIAS.md`.

**No se hacen commits ni push en `../clinube`.** Se lee y se actualiza con
`git pull`.

## Paridad: lo que no se negocia

- **Rutas idénticas:** `/api/v1/...` **con barra final**, mismos métodos,
  mismos códigos HTTP, mismas claves JSON **en el mismo orden** y en el
  mismo formato.
- **Paginación** `{count, next, previous, results}`, 20 por página,
  `?page=N`, donde Django pagina; arreglo plano donde no. `next` y
  `previous` son direcciones absolutas y conservan los demás filtros.
- **Errores en las dos formas que usa Django:**
  `{"error": {"code", "message", "details"}}` (lo que sale de una
  excepción) y `{"detail": "..."}` (lo que una vista construye a mano).
- **Fechas:** se guardan en UTC; se escriben **en la zona de la clínica,
  con microsegundos y con desplazamiento**:
  `2026-10-20T23:53:46.378048-05:00`. Nunca `Z`, nunca milisegundos.
- **Decimales como texto:** `"125.50"`, no `125.5`.
- **Clínica por usuario:** la clínica de la petición es la del usuario **en
  la base**, no la del token, y toda consulta se filtra por ella.
- **Contraseñas compatibles:** se lee y se genera
  `pbkdf2_sha256$<iteraciones>$<sal>$<hash>`, con las iteraciones de cada
  hash.
- **Tokens compatibles:** HS256 con `DJANGO_SECRET_KEY`, los claims de
  SimpleJWT y las mismas tablas `token_blacklist_*`. Un token sirve en los
  dos servidores.
- **Base de datos idéntica:** mismas tablas, columnas, tipos y
  restricciones. La línea base de Flyway es un `pg_dump` del esquema de
  Django, no una traducción a mano.

## Antes de cada commit

```bash
./gradlew test
```

```bash
./scripts/probar.sh
```

El segundo pasa la suite **dos veces**, la segunda en
`Pacific/Kiritimati` (UTC+14). Y para el módulo que se haya tocado:

```bash
./scripts/comparar-contrato
```

## Rendimiento: lo que ya se aprendió en clinube

- **Sin N+1.** `@EntityGraph` o `JOIN FETCH` en las listas. La auditoría y
  la lista de pacientes tienen que hacer las mismas consultas con 3 filas
  que con 30.
- Atenciones y próxima cita de la lista de pacientes: **subconsultas por
  fila después de paginar**, no un JOIN con GROUP BY sobre todas las citas.
- Los informes **agrupan en la base**, no en memoria.
- Los índices compuestos del esquema se crean igual (ya están en la línea
  base).

## Dependencias

Ninguna que no haga falta, y **ninguna con licencia AGPL o GPL
incompatible**. En particular: **iText 7 no se usa** (es AGPL); para PDF,
**OpenPDF**.

## Seguridad

- Nunca pedir ni aceptar en el chat claves privadas, contraseñas ni
  secretos de producción. Solo claves públicas.
- Si hace falta una salida de terminal, avisar de que se quite antes
  cualquier línea con secretos.
- **No conectarse al servidor de producción ni a su base.**
- Datos de prueba: la clínica demo, o un `pg_dump` de una base local de
  Django. **Nunca datos reales de pacientes.**
- Con el perfil de producción, el servidor **no arranca** con claves de
  ejemplo ni con la depuración encendida.

## Autoría de los commits

```bash
git config user.name "Jorgeperezr"
```

```bash
git config user.email "148003448+Jorgeperezr@users.noreply.github.com"
```

## Honestidad en el avance

`docs/PROGRESO.md` dice la verdad: si una ruta no responde igual que
Django, lo dice, **aunque las pruebas propias pasen**. Los tres estados
son *pendiente*, *hecha* (implementada) e *igual a Django* (comprobada con
el comparador).
