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

## 5. Revisión a mano del código, antes de poder compilar

Repasando el código sin JVM salieron estos fallos. Todos están corregidos;
los marcados con «capturado» se comprobaron contra un Django en marcha y
tienen su fixture en `backend/src/test/resources/contrato/`.

- [x] El filtro JWT se registraba dos veces (como @Component y en la cadena).
- [x] `GET /auth/login/` sin token daba 401; Django da 405.
- [x] `Boolean::class.java` es el primitivo: la comprobación de producción no convertía.
- [x] `PostgreSQLContainer` sin subclase no compila en Kotlin.
- [x] Se quitó `.anonymous(...)` (referencia a método dudosa en Kotlin).
- [x] **Paginación:** DRF ORDENA los parámetros de `next`/`previous` y los
      vuelve a codificar con `quote_plus`. Ahora igual; los diez casos de la
      prueba salen de ejecutar `replace_query_param` de DRF 3.17.
- [x] **Rutas sin autenticación** (capturado): en las vistas que en Django
      no autentican, el token se ignora (`/health/` con un token basura da
      200). En las abiertas que sí autentican (`otp/request`), un token que
      no vale da 401. Las dos listas están en `RutasPublicas.kt` y las usan
      el filtro y la configuración de seguridad.
- [x] Lista negra: `count(...) > 0` en HQL cambiado por un recuento.
- [x] Jackson: los serializadores van con `serializerByType`, que gana
      siempre a los de JavaTimeModule.
- [x] Ruta desconocida: `NoResourceFoundException` daba 500; ahora 404. Las
      demás excepciones de Spring con estado 4xx lo conservan.
- [x] La clínica se consultaba dos veces por petición; ahora el filtro la
      lee una vez y la deja en la identidad de la petición.
- [x] **Lectura del cuerpo** (capturado): cuerpo `null`, cuerpo que no es un
      objeto, campos nulos, campos que no son texto y campos con solo
      espacios responden cada uno como en DRF. El correo y la contraseña se
      recortan, como hace DRF: `" OdontoDemo2026 "` entra.
- [x] `text/plain` da 415 con el texto de Django (capturado).

## 6. El texto de un JSON ilegible no es el mismo

Con `{` como cuerpo, Django responde 400 `parse_error` con

```
JSON parse error - Expecting property name enclosed in double quotes: line 1 column 2 (char 1)
```

La parte de detrás del guion es el mensaje de la excepción de Python. Aquí
va el de Jackson, que dice lo mismo con otras palabras. El código HTTP, el
`code` y el principio del mensaje coinciden; el resto no se puede copiar sin
reimplementar el analizador de JSON de Python, y ni el panel ni la app
envían JSON roto.

## 7. Aceptar las condiciones: dos casos en los que Django da 500

Django responde **500** en dos casos raros de `POST /legal/condiciones/aceptar/`
y aquí no se copian, porque un 500 no es un contrato:

- **Cuerpo que no es un objeto** (`[1]`, `null`): Django hace
  `request.data.get(...)` sobre una lista y revienta. Aquí se trata como «no
  trae la versión vigente»: 409, igual que un cuerpo vacío.
- **Super Administrador sin clínica**: `HasRole` lo deja pasar por ser
  superusuario, y al guardar la constancia sin clínica la base rechaza el
  nulo. Aquí responde 403 («esta acción se realiza dentro de una clínica»).

## 8. La IP de la auditoría, solo si es una IP

Django guarda en `ip_address` el primer valor de `X-Forwarded-For` tal como
llega; si no es una IP, la base lo rechaza y la petición da 500. Aquí, si no
es una IP literal, se guarda nula: convertir un texto cualquiera en
`InetAddress` haría una consulta DNS con algo que manda el cliente.

## 9. Lo del escritorio que no es igual a propósito, o todavía

- **`GET /reports/financial/?export=excel`** no está portado: responde **501**
  en vez de devolver el JSON como si nada. El escritorio no lo pide; la
  pantalla de Reportes, sí.
- **Una fecha mal escrita** en `agenda/view/?date=` o en los informes da 500
  en los dos servidores (en Django, el filtro recibe `None`), pero con cuerpos
  distintos: HTML en Django, JSON aquí.
- **Empates de orden.** Donde Django no ordena del todo (pacientes con el mismo
  apellido y nombre, cuotas vencidas el mismo día, productos sin `ORDER BY`),
  el orden lo decide PostgreSQL. Se usan las mismas consultas para que salga
  igual, y el comparador lo confirma con los datos sembrados, pero no es un
  orden garantizado en ninguno de los dos servidores.
- **`waiting_minutes`** se compara por tipo y no por valor en el comparador:
  los dos servidores responden con milisegundos de diferencia y el minuto
  puede cambiar entre uno y otro. Lo comprueban las pruebas con el reloj fijo.
