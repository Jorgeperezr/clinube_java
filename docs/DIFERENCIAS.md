# Diferencias encontradas

Lo que no cuadra entre el plan, `clinube` y lo que hay aquí. Se escribe
para que no se olvide, no para justificarlo.

## 1. `clinube` no tiene `docs/contrato/` ni `scripts/exportar-contrato.sh`

El plan manda leer, por este orden, `docs/contrato/LEEME.md`,
`docs/contrato/RUTAS.md` y `docs/contrato/MODELO_DE_DATOS.md` de
`clinube`, y regenerarlos con `bash ../clinube/scripts/exportar-contrato.sh`.

**Ninguno de los cuatro existe** en `clinube` (commit `f6ef2f0`): no hay
carpeta `docs/` y `scripts/` no tiene ese guion.

Qué se hizo: generarlos aquí, desde el código y desde la base, con dos
guiones propios:

| Documento | De dónde sale | Guion |
|---|---|---|
| `docs/contrato/RUTAS.md` | el resolvedor de URL de Django, en marcha | `scripts/exportar-rutas.py` |
| `docs/contrato/MODELO_DE_DATOS.md` | el esquema real de una base migrada | `scripts/exportar-modelo-de-datos.py` |

Se recorre el resolvedor y se lee la base, en vez de leer los `urls.py` y
los `models.py`, porque así no se escapa nada: `/auth/token/refresh/` la
pone SimpleJWT, no `clinube`, y no aparecería leyendo solo el código del
proyecto.

El reemplazo de `LEEME.md` son `CLAUDE.md` (las reglas transversales) y
`docs/DECISIONES.md`.

**Esto hay que revisarlo** si algún día `clinube` publica su carpeta
`docs/contrato/`: entonces la fuente es la suya y estos guiones sirven
para comparar.

## 2. Una ruta desconocida: Django responde HTML, Kotlin responde JSON

`GET /api/v1/no-existe/` en Django devuelve **404 con una página HTML**
(la de Django, no una de DRF), porque la petición no llega a ninguna vista
de DRF. Aquí devuelve 404 con `{"error": {...}}`.

No se ha igualado porque igualarlo significaría servir una página de error
de Django en HTML, que no aporta nada: ni el panel ni la app piden rutas
que no existen. Queda anotado para que no parezca un descuido.

## 3. El límite de peticiones anónimas sale al capturar

Django limita a **20 peticiones por minuto** las que llegan sin sesión
(`anon`), y casi todas las de los casos de error llegan así. Al capturar
el contrato se llega al límite y el servidor responde:

```json
{"error":{"code":"throttled","message":"Solicitud fue regulada (throttled). Se espera que esté disponible en 11 segundos.","details":{}}}
```

con **429**. El mensaje lleva dentro un número de segundos que cambia, así
que no se puede comparar por igualdad.

`scripts/capturar-contrato.sh` pide el token primero —las peticiones
autenticadas no gastan ese cupo— y, si recibe un 429, espera lo que el
propio servidor dice y repite.

**Pendiente:** los límites todavía no están implementados en Kotlin
(600/min con sesión, 20/min sin sesión por IP, 5/min para pedir códigos,
10/hora para las solicitudes públicas). Van en Redis, compartidos entre
hilos e instancias. Mientras no estén, el comparador no puede comprobar
las respuestas 429.

## 4. `last_login` no se actualiza, y así se queda

`UPDATE_LAST_LOGIN` de SimpleJWT viene apagado y `clinube` no lo cambia:
`accounts_user.last_login` se queda **nulo** aunque la persona entre
muchas veces. Comprobado en la base después de varios inicios de sesión.

Aquí tampoco se escribe. Parece un error de Clinube y puede que lo sea,
pero arreglarlo sería escribir en una columna que el otro servidor deja
en paz.

## 5. Revisión a mano pendiente de aplicar (sin compilar todavía)

Repasando el código sin JVM salieron estos fallos. Los cinco primeros ya
están corregidos; los demás quedan para la próxima sesión, **antes** de dar
nada por terminado:

- [x] El filtro JWT se registraba dos veces (como @Component y en la cadena).
- [x] `GET /auth/login/` sin token daba 401; Django da 405.
- [x] `Boolean::class.java` es el primitivo: la comprobación de producción no convertía.
- [x] `PostgreSQLContainer` sin subclase no compila en Kotlin.
- [x] Se quitó `.anonymous(...)` (referencia a método dudosa en Kotlin).
- [ ] **Paginación:** DRF ORDENA los parámetros de `next`/`previous`
      (`urlencode(sorted(...))`) y vuelve a codificar (espacio → `+`). Aquí se
      conserva el orden original. Corregir y ajustar `PaginacionTest`.
- [ ] **Rutas sin autenticación en Django** (`authentication_classes = ()`):
      login, refresco, health, ready, legal/privacidad, legal/condiciones,
      otp/canal, google/config, google/, publico/solicitudes. El filtro no debe
      leer el token en ellas (un token caducado en /health/ hoy daría 401).
- [ ] `estaEnListaNegra`: `select count(n) > 0` puede no valer en HQL; devolver
      `Long` y comparar en el servicio.
- [ ] Jackson: registrar los serializadores con `serializerByType` en el
      customizer, para que ganen siempre a los de JavaTimeModule.
- [ ] `NoResourceFoundException` (ruta desconocida en Spring Boot 3.2+) cae en
      el manejador genérico y daría 500: mapearla a 404, y respetar el estado
      de las excepciones `ErrorResponse` de Spring.
- [ ] El filtro y `ContextoDeLaPeticion` consultan la clínica dos veces por
      petición: que el filtro la deje en un atributo y el contexto la reutilice.
- [ ] JSON malformado: DRF responde `JSON parse error - <texto de Python>`;
      no se puede copiar exacto. Documentar o aproximar.
