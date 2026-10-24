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

## 10. Una cita con el doctor de otra clínica: aquí no (diferencia DELIBERADA)

En Django, `POST /appointments/` (y `PUT`/`PATCH /appointments/{id}/`) acepta
el doctor y el tratamiento de **cualquier** clínica: el serializador los busca
en `Doctor.objects.all()` y `Treatment.objects.all()` sin filtrar. Comprobado
contra Django: la cita se crea (201) y la respuesta trae el nombre del doctor
ajeno («Dr. Vecino»).

Eso choca con la regla de aislamiento del plan, que no se negocia («un
usuario nunca ve datos de otra clínica»). Aquí el doctor y el tratamiento se
buscan solo en la clínica de la petición, y uno ajeno da el mismo error que
uno inventado: «Clave primaria "…" inválida - objeto no existe.». Lo comprueba
`PacientesYAgendaTest`, y el comparador no incluye ese caso.

**Conviene corregirlo también en Django** (filtrar los dos `queryset` por
`request.tenant`): es una fuga de datos entre clínicas.

## 11. Pequeñas diferencias de las pantallas de Pacientes y Agenda

- **Un id que no es un UUID en la dirección** (`/patients/abc/`): Django
  responde con su 404 en HTML porque la ruta ni coincide; aquí, 404 en JSON.
  Lo mismo con un token de calendario que no existe (`/calendar/<token>.ics`).
- **Fechas en formato ISO «básico»** (`20300102T1000`, sin guiones): las
  acepta `datetime.fromisoformat` de Python y aquí no. El panel y la app
  mandan siempre el formato extendido; todos los demás casos raros (cualquier
  separador, `+0500`, siete decimales, solo la fecha) se aceptan igual.
- **La llegada no avisa al doctor por WhatsApp.** En Django,
  `POST /appointments/{id}/checkin/` encola ese aviso; el módulo de WhatsApp es
  la fase 13. La llegada se registra igual, que es lo crítico.
- **El 500 de Django no se copia** cuando un `PATCH` de la ficha llega con un
  cuerpo que no es un objeto en el aviso de privacidad, o cuando el Super
  Administrador sin clínica da de alta un paciente: aquí son un 400 y un error
  de la base, respectivamente.

## 12. Alertas clínicas: los 500 de Django no se copian

Las reglas dan exactamente lo mismo que el `alertas.py` de Django (3000
casos comparados en `ReglasDeAlertasTest`), y las respuestas de
`/patients/{id}/alertas/` y `/alertas/revisar/` son iguales en el
comparador. Solo cambian tres casos en los que Django se cae con un 500:

- **Revisar una receta con un cuerpo que no es un objeto** (`["…"]`,
  `"texto"`, `null`): Django llama a `.get()` sobre él y falla; aquí es el
  400 de siempre, «Falta el texto de la receta.».
- **Un formulario 033 cuya sección D no es un objeto** (una lista o un texto
  no vacíos): Django falla al recorrerla; aquí no cuenta ninguna casilla.
- **Una casilla marcada con un detalle que no es texto** (un número, una
  lista) en «Alergia antibiótico», «Alergia anestesia» u «Otro»: Django
  falla al leerlo; aquí cuenta como detalle vacío.

El panel no puede producir ninguno de los tres: siempre manda texto.

## 13. Formulario 033 y diagnósticos

Las respuestas y el estado de la base son iguales a Django en el comparador
(listas, altas, ediciones, errores de validación, CIE-10). Lo que cambia:

- **Dar de alta un 033 de un paciente que no existe** (o de otra clínica):
  Django da un 500, porque busca al paciente con `Patient.objects.get` y no
  captura el `DoesNotExist`. Aquí es el 404 «No Patient matches the given
  query.». Como en Django, sí se admite un paciente dado de baja (los
  diagnósticos, en cambio, exigen que esté activo: así lo hace Django).
- **Un número infinito en un `JSONField`** (`1e400` en el cuerpo): los dos
  responden «El valor debe ser JSON válido.». Los tokens `NaN` o `Infinity`,
  que no son JSON, no se compararon.

## 14. El escape de los caracteres de control en el JSON

DRF escribe `\u001f` y Jackson `\u001F`: el mismo carácter para cualquier
cliente que lea JSON, pero no los mismos bytes. Solo afecta a textos con
caracteres de control (del 0x00 al 0x1F que no tienen escape corto), que el
panel no produce. Viene de antes de esta ronda y vale para todas las rutas.

## 15. Renovar la sesión

`POST /auth/token/refresh/` hace lo mismo que la `TokenRefreshView` de
SimpleJWT 5.5.1: rota el refresco, invalida el presentado, apunta el nuevo
en `OutstandingToken` y exige que el usuario siga activo (si no, 401 «No
active account found for the given token.», sin invalidar el token). Varias
renovaciones a la vez con el mismo token tampoco fallan: las que llegan
después de invalidarlo reciben el 401 de token en lista negra, como en Django.

Dos cosas distintas:

- **Un refresco de un usuario que ya no existe:** Django da un 500
  (`User.DoesNotExist` sin capturar); aquí, el mismo 401 que para una cuenta
  desactivada.
- **El login de un paciente por el panel** (rechazado con 400 en los dos):
  Django deja apuntado en `OutstandingToken` un refresco válido que nunca
  entrega, porque lo crea antes de mirar el rol. Aquí no se crea: es un
  token vivo guardado sin motivo. Por eso la comparación del estado de las
  bases cuenta los tokens del personal y no los de los pacientes.

## 16. Odontograma: el estado de otra clínica, aquí no (diferencia DELIBERADA)

En Django, `POST /patients/{id}/tooth-records/` busca el estado y el ítem del
plan de tratamiento en TODAS las clínicas (`objects.all()`), así que acepta
el estado de otra clínica y la respuesta trae su nombre y su color. Es el
mismo fallo de aislamiento que el doctor de otra clínica en las citas (§10). Comprobado contra Django: 201 con «Caries ajena» y el color de la vecina.
Aquí el estado y el ítem tienen que ser de la clínica de la petición; uno
ajeno da «Clave primaria "…" inválida - objeto no existe.». Un estado
INACTIVO de la propia clínica sí se acepta, como en Django.

**Conviene corregirlo también en Django** (filtrar los dos `queryset` por
`request.tenant`).

Además, como en el formulario 033: `GET /patients/{id}/cpo-ceo/` de un
paciente que no existe o es de otra clínica da un 500 en Django
(`Patient.DoesNotExist`) y aquí el 404 «No Patient matches the given query.».
Con un paciente dado de baja responde en los dos.

## 17. Evoluciones y planes: lo ajeno, aquí no (diferencia DELIBERADA)

El mismo fallo de aislamiento que en las citas (§10) y el odontograma (§16):

- **Una línea de plan con el tratamiento de otra clínica**: Django la acepta
  (`Treatment.objects.all()`); comprobado contra Django: 201 con «Tratamiento
  ajeno» en `treatment_name`. Aquí, «Clave primaria "…" inválida - objeto no
  existe.».
- **Una evolución con la cita de otra clínica**: el serializador de Django
  usa `Appointment.objects.all()`. Aquí la cita tiene que ser de la clínica.

**Conviene corregirlo también en Django.**

## 18. El inventario se descuenta otra vez al editar una línea ya realizada (fallo de Django, COPIADO)

Al guardar una línea de plan que queda «realizada», Django descuenta del
inventario los insumos del tratamiento. Lo hace cada vez que se guarda y la
línea ESTÁ realizada, no solo cuando PASA a realizada: cambiar el precio o la
pieza de una línea ya realizada vuelve a descontar. Y al crear una línea ya
realizada no descuenta nada.

Aquí se hace exactamente igual, a propósito: mientras los dos servidores
compartan la base, el stock no puede depender de cuál atendió la petición.
`EvolucionesYPlanesTest` lo deja escrito. **Conviene corregirlo en los dos a
la vez** (descontar solo en el paso a «realizada», también al crear).

## 19. Plantillas de plan: los 500 de Django no se copian

Las respuestas y el estado de la base son iguales a Django en el comparador
(plantillas, aplicarlas con el precio del convenio, presupuesto desde el
plan). Cambian los casos en que Django se cae con un 500:

- **Crear una plantilla con líneas mal formadas** (una línea que no es un
  objeto, un `treatment` que no es un UUID, un `order` que no es un número o
  es negativo, `items` que no es una lista): Django deja la plantilla creada
  a medias y responde 500. Aquí se valida antes: 400 con
  `{"items": ["Ítems de plantilla inválidos."]}` y no se crea nada. Las
  líneas que Django ignora sin error (un tratamiento de otra clínica o que no
  existe, `null`, un número) también se ignoran aquí.
- **Crear una plantilla con un nombre repetido**: los dos fallan con 500 (la
  restricción única de la base), pero el de Django es una página HTML.
- **Añadir una línea a una plantilla** con un `treatment` que no es un UUID:
  Django 500, aquí el 400 «Tratamiento inválido.»; con un `order` que no es
  un número o es negativo, Django 500, aquí 400 «Orden inválido.».
- **Aplicar una plantilla con un `template_id` que no es un UUID**: Django
  500, aquí el 404 «Plantilla no encontrada.».

## 20. Cobros: lo ajeno, aquí no (diferencia DELIBERADA)

- **Una línea de presupuesto con el tratamiento de otra clínica**: Django la
  acepta (`Treatment.objects.all()`); aquí, «objeto no existe».
- **Un pago directo con la cuota de otra clínica**
  (`POST /patients/{id}/payments/`): igual, Django busca la cuota en todas.

**Conviene corregirlo también en Django.**

## 21. Cobros: donde Django da un 500

Las respuestas y el estado de la base son iguales a Django en el comparador
(433 peticiones, 21 tablas). Quedan distintos los casos en que Django se cae:

- **Importes que no caben** en el serializador: un subtotal de línea de más de
  12 cifras, o lo pagado de una cuota de más de 10 (pagar dos veces
  99 999 999,99). Django da 500 —y en el pago deshace lo hecho—; aquí se
  responde y el pago queda guardado.
- **Un plan de cuotas que pasaría del año 9999**: Django 500 (y deshace);
  aquí se crea con fechas del año 10000.
- **El superadministrador sin clínica** en las listas de cobros: Django 500,
  aquí una lista vacía.

## 22. Todas las rutas: el cuerpo de la petición

Dos diferencias de Spring que valen para TODAS las rutas portadas, no solo
para cobros (las encontró la revisión de esta ronda):

- **Un cuerpo JSON mal formado se rechaza antes de mirar los permisos y el
  objeto**: Spring lo lee al entrar; DRF solo al usarlo. Un usuario sin
  permiso que manda JSON roto recibe aquí el 400 de JSON y en Django el 403.
- **Solo se aceptan cuerpos JSON**: Django acepta también formularios
  (`application/x-www-form-urlencoded` y `multipart/form-data`); aquí son un
  415. El panel y la app mandan siempre JSON, salvo en un sitio: **el alta y
  la edición de egresos** (`/finanzas/egresos/`), que van como formulario con
  la factura del proveedor. Esas dos rutas sí leen los tres formatos, con las
  reglas de DRF para los formularios (ver §26).

## 23. Lo que la pantalla de Pagos aún no tiene

- **La tarea diaria que marca las cuotas vencidas** (`mark_overdue_installments`,
  Celery, 00:30): no hay tareas programadas en Kotlin todavía. El estado de
  cuenta no depende de ella (calcula lo vencido con la fecha), pero la columna
  «Estado» de una cuota se queda en «Pendiente» hasta que la tarea exista.

## 24. El comprobante de cobro: en Django no funciona (diferencia DELIBERADA)

`GET /finanzas/cobros/{id}/comprobante/` responde **500 siempre** en Django
con PostgreSQL. `ComprobanteCobroView` bloquea el pago con
`select_for_update()` y en la misma consulta se trae con `select_related` el
profesional y la cuota, que pueden ser nulos (`LEFT OUTER JOIN`), y
PostgreSQL lo rechaza: «FOR UPDATE cannot be applied to the nullable side of
an outer join». Pasa con cualquier cobro, también con el de otra clínica
(la consulta revienta antes del 404). Sus pruebas no lo ven porque corren
con SQLite, que no tiene `FOR UPDATE`.

Aquí el comprobante funciona: bloquea solo el pago y el contador de la
clínica, numera la primera vez (y renumera un 0, como el `if not` de Django),
y el PDF dice lo mismo que el que dibuja `documentos.comprobante_cobro`.
Como por la API no se puede comparar, `scripts/comparar-comprobante.py` le
pide a Django que lo dibuje con su código y su número, y compara los dos
papeles palabra por palabra.

**Conviene corregirlo en Django**: `select_for_update(of=("self",))`.

## 25. Los PDF y el Excel de finanzas: lo que dicen, no sus bytes

El comprobante, la liquidación y el rol de pagos se dibujan con OpenPDF, no
con reportlab, y el Excel con Apache POI, no con openpyxl. No pueden salir
byte a byte iguales; el comparador compara las **palabras** de cada PDF y el
**valor de cada celda** del Excel, y las cabeceras (`Content-Type`,
`Content-Disposition`, el nombre del archivo).

- La letra es la Helvetica estándar sin incrustar, como reportlab, con la
  codificación Windows-1252. El signo menos «−» (U+2212) de «(−) Descuentos»
  no está en ella: reportlab lo escribe como guion, y aquí también.
- De la apariencia de documentos de la clínica (`DocumentAppearance`) se
  usan la hoja, los márgenes, la tipografía, la paleta, la marca, el
  membrete, las tablas y el pie. Faltan la marca de agua, la alineación
  centrada o a la derecha del membrete y el logotipo cuando Django lo tiene
  en su almacenamiento y no en `MEDIA_ROOT`.
- El extractor de texto junta a veces dos celdas vecinas del PDF de reportlab
  («%)$39,38Anticipos»); si las letras son las mismas, el comparador no lo
  cuenta como diferencia.

## 26. Finanzas: donde Django da un 500

Las vistas de finanzas leen a mano parte de lo que reciben (`_decimal`,
`_fecha`, `mes_de`, `request.data.get`) y no protegen todos los casos. Aquí
responden con el mismo código, 500, pero el cuerpo es el JSON de error de
Kotlin y no la página de Django:

- **Un mes que no se entiende** en `egresos/`, `cobros/` y `roles/` (allí
  `mes_de` no está dentro de un `try`), o **una categoría o un profesional que
  no son un UUID** en los filtros de `egresos/` y `liquidaciones/`.
- **Un día que no existe** («2026-02-30») donde la vista llama a
  `parse_date` a pelo: Django lanza `ValueError`. Y una fecha que no se
  entiende al pagar un egreso, una liquidación, un rol o una obligación: la
  fecha queda nula y la base la rechaza.
- **`NaN` o `Infinity`** en un importe: son `Decimal` válidos en Python, pero
  la primera cuenta o comparación con ellos revienta. Aquí revienta al leerlos.
- **Un cuerpo que no es un objeto** (una lista, `null`) en las vistas que hacen
  `request.data.get(...)`; o un motivo, una nota o unas observaciones que no
  son texto.
- **`GET /finanzas/cobros/{id}/` y `PATCH /finanzas/cobros/`**: la misma vista
  atiende las dos rutas y cada método espera la otra (falta o sobra el `pk`).
- **Una categoría que no existe** cuando se paga algo que crea su egreso (una
  clínica que creó una categoría propia antes de abrir la lista, y así nunca
  recibió las de serie).

## 27. Finanzas: los archivos subidos

- **Dónde se guardan**: en la carpeta `MEDIA_ROOT` (por defecto `media/`
  junto al servidor). En producción Django los guarda en Google Cloud
  Storage; Kotlin, todavía no.
- **El nombre** es el de Django: `get_valid_filename` (espacios a «_», fuera
  lo raro), y si ya existe, «_» y siete letras al azar, recortando la raíz si
  no cabe en los 100 caracteres de la columna. Un egreso sin factura guarda
  `""`, no `NULL`, como Django.
- **El tope**: Spring corta en 50 MB (413) lo que Django dejaría pasar para
  rechazarlo después con su mensaje («El archivo pasa de 8 MB.»). Entre 8 y
  50 MB, el mismo mensaje.
- **El tipo del archivo** al descargarlo sale de una tabla fija (la de Python
  para las extensiones admitidas); Django lo adivina con `mimetypes`, que en
  algunos sistemas mira también las tablas del sistema operativo.
- **Un formulario** se lee como DRF: un campo vacío que admite nulos es nulo
  (un vencimiento vacío, un profesional vacío, la factura como texto vacío,
  que la borra) y una casilla que no llega es «no».

## 28. Tarifas de los doctores: lo ajeno, aquí no (diferencia DELIBERADA)

`POST /doctors/{id}/fees/`:

- **El doctor de la ruta** tiene que ser de la clínica: si no existe, Django
  da 500 (la base rechaza la regla); si es de otra clínica, Django la apunta
  en esta. Aquí, en los dos casos, «No Doctor matches the given query.» (404).
- **El tratamiento** tiene que ser de la clínica: Django lo busca en todas.

El `doctor` del cuerpo sigue siendo obligatorio y vale de cualquier clínica,
como en Django: manda el de la ruta.

## 29. Reportes e inventario: donde Django da un 500

- Un `doctor_id` que no es un UUID en la producción por doctor, o una fecha que
  no se entiende en los reportes de pacientes nuevos y de citas: Django da 500,
  y aquí también (con el cuerpo de error de Kotlin).
- Un plazo enorme en las alertas de vencimiento (`?days=99999999999`): Django
  da 500 al sumar los días; aquí, una fecha fuera de rango, también 500.
