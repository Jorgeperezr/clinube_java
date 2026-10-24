# Decisiones

Lo que se decidió y **por qué**, para no volver a discutirlo y para poder
cambiarlo a sabiendas. Las decisiones de partida están en el plan; aquí
están las que se tomaron al empezar a escribir.

## 1. La línea base de Flyway conserva TODAS las tablas de Django

Incluidas `django_migrations`, `django_content_type`, `auth_permission`,
`auth_group*`, `django_session` y `django_admin_log`.

El plan decía «sin las tablas internas de Django que no se usan», y aquí
se hizo lo contrario. Dos razones:

1. **No se pueden quitar sin romper el esquema.** El `User` de Django
   hereda de `PermissionsMixin`, así que existen
   `accounts_user_groups` y `accounts_user_user_permissions`, con claves
   foráneas a `auth_group` y `auth_permission`, que a su vez apunta a
   `django_content_type`.
2. **Se necesitan para lo que el propio plan pide.** El objetivo es poder
   cambiar un servidor por el otro sin migrar datos, y el comparador de
   contrato arranca los DOS servidores contra copias de la misma base. Sin
   `django_migrations`, Django no vuelve a arrancar contra ella.

Se verificó: aplicar la línea base a una base vacía produce un esquema
**idéntico** al de Django (`pg_dump` de las dos y `diff` sin diferencias).

## 2. Los JWT se firman a mano, sin biblioteca

Hay bibliotecas buenas (nimbus-jose-jwt viene con Spring Security). Se
firman a mano, en unas sesenta líneas, porque la paridad exige controlar
tres cosas que una biblioteca decide por su cuenta:

- la cabecera tiene que ser **exactamente** `{"alg":"HS256","typ":"JWT"}`,
  en ese orden y sin espacios, porque forma parte de lo que se firma;
- los claims, en el orden de SimpleJWT;
- hay que distinguir «caducado» de «inválido» **con los textos de
  SimpleJWT**, porque el panel y la app los reciben y los enseñan.

## 3. PBKDF2 se calcula con `Mac`, no con `SecretKeyFactory`

`SecretKeyFactory` convierte los caracteres por su cuenta, y eso es una
diferencia de comportamiento frente a `hashlib.pbkdf2_hmac`, que recibe
los bytes UTF-8 tal cual. Con `Mac` el resultado es el mismo byte a byte.
El coste es el mismo: las dos implementaciones son Java puro.

La contraseña vacía necesita un detalle más: `SecretKeySpec` tampoco
admite una clave vacía («Empty key»). Como HMAC rellena la clave con ceros
hasta 64 bytes, la clave vacía y la de un solo byte cero dan el mismo
resultado, y eso es lo que se usa. Lo comprueba una prueba con un hash de
contraseña vacía calculado con `hashlib`.

## 4. `Usuario` no tiene una relación perezosa con `Clinica`

Tiene `clinicaId: UUID?`, y la clínica se resuelve una vez por petición en
`ContextoDeLaPeticion`, que la memoriza.

Con una relación perezosa, cada vista que tocara `usuario.clinica`
lanzaría su propia consulta sin que se vea en el código. Eso es
exactamente de donde salen los N+1 que el plan pide no repetir. Así, una
vista que lea la clínica diez veces hace **una** consulta.

## 5. El servidor de Kotlin no va en un contenedor

`docker-compose.yml` levanta solo PostgreSQL, Redis y Mailpit. El servidor
se arranca con Gradle (`scripts/start-local.sh`), para que el reinicio en
caliente y el depurador funcionen sin más vueltas. En producción se
empaquetará; en local no aporta nada.

## 6. Las respuestas de Django se capturan, no se deducen

`scripts/capturar-contrato.sh` guarda en
`backend/src/test/resources/contrato/` las respuestas de un Django real, y
las pruebas de contrato las comparan con las de Kotlin.

Esto no es comodidad: varias diferencias que más cuestan de ver solo
aparecen así.

- DRF **no** escribe las fechas en UTC con `Z`. Las escribe en la zona de
  la clínica, con desplazamiento y **microsegundos**:
  `2026-10-20T23:53:46.378048-05:00`. Jackson, por su cuenta, habría
  escrito `2026-10-21T04:53:46.378Z`. Es la misma hora y no es la misma
  respuesta.
- Los textos traducidos tienen rarezas que hay que copiar: falta una
  tilde en «no es valido para ningun tipo de token», falta el punto final
  en «no tiene una cuenta activa».
- Dentro de `details.messages`, los mensajes de SimpleJWT **no** están
  traducidos («Token is expired»), mientras que el de fuera sí.
- Un campo ausente y un campo vacío dan errores distintos: «Este campo es
  requerido.» y «Este campo no puede estar en blanco.».
- Las excepciones propias de SimpleJWT heredan de `DetailDictMixin`, que
  mete `{"detail":…, "code":…}`: por eso sus respuestas llevan
  `details.code` y las de DRF a secas lo dejan vacío. Son dos formas y el
  panel recibe las dos.

## 7. Entrar con la clínica desactivada: el login funciona, el resto no

Comprobado contra Django: `POST /auth/login/` de un usuario cuya clínica
está dada de baja devuelve **200 con sus tokens**, y es la siguiente
petición (`/auth/me/`) la que devuelve 401 «La clínica de este usuario
está desactivada.». No se «arregla» rechazándolo en el login: sería
responder distinto que Django.

## 8. Los textos legales se generan desde Django, no se copian

`TextosLegalesGenerados.kt` sale de `scripts/generar-textos-legales.py`, que
ejecuta `apps/common/legal.py` con los datos del titular sustituidos por
marcadores y los convierte en plantillas de Kotlin. Son cientos de líneas en
castellano y una errata dejaría de responder igual que Django; además, la
versión que acepta una clínica tiene que corresponder EXACTAMENTE con el
texto que leyó. Lo único escrito a mano es la lista de proveedores, que
cambia con el canal del código, y la comprueban los fixtures de los dos
canales.

## 9. Cada servidor del comparador trabaja con su propia copia

El comparador manda peticiones que cambian el estado (aceptar las
condiciones). Si Django trabajara sobre la base de referencia, la dejaría
distinta para la siguiente captura; así pasó una vez, y se restauró. Ahora
el comparador copia la referencia dos veces (`clinube_cmp_django` y
`clinube_cmp_kotlin`) y no la toca. Las capturas con estado
(`scripts/capturar-condiciones.sh`) se hacen también sobre una copia
desechable.

## 10. Las rutas que solo leen van en SQL, no con entidades

Los listados e informes del escritorio (`patients/`, `agenda/view/`, los
informes, el stock…) son consultas SQL escritas a mano con
`NamedParameterJdbcTemplate`, no entidades JPA. Tres razones:

1. **El mismo orden que Django.** Donde Django no ordena del todo, el orden lo
   pone PostgreSQL según la consulta. Copiando la consulta (el mismo filtro,
   sin `ORDER BY` donde Django no lo pone) sale el mismo orden; el comparador
   lo confirma.
2. **Agrupar en la base.** Django calcula la morosidad y el stock en Python,
   con una consulta por cuota o por producto. Aquí agrupa la base, como pide
   el plan.
3. **Subconsultas después de paginar.** Las atenciones y la próxima cita de la
   lista de pacientes se calculan solo para las 20 filas de la página, no con
   un JOIN y un GROUP BY sobre todas las citas, que es lo que hace Django.

Las entidades llegarán con las escrituras de cada módulo.

## 11. Los datos del comparador se siembran con Django

`scripts/sembrar-escritorio.py` crea pacientes, citas, cobros, cuotas,
seguimientos, inventario y la bandeja **con el ORM de Django** y con fechas
relativas a hoy. El comparador lo ejecuta en la copia de Django y después
copia esa base para Kotlin: los dos servidores ven exactamente las mismas
filas, con los valores por defecto que pone Django.

Las rutas que dependen de «hoy» no se comparan con respuestas capturadas
(dejarían de valer al día siguiente): su paridad la da el comparador, que
pregunta a los dos a la vez, y sus reglas las comprueban las pruebas con un
reloj fijo (`RelojDePrueba`), como hacen las de Django.

## 12. El comparador compara también el estado de las dos bases

Después de lanzar las mismas peticiones contra Django y contra Kotlin,
`scripts/comparar-estado.py` resume cada tabla que tocan las rutas portadas
—sin identificadores generados ni marcas de tiempo— y compara las dos bases.

Las respuestas iguales no bastan. La primera vez que se usó cazó dos
diferencias que ninguna respuesta enseñaba:

- un PUT de antecedentes con datos malos no dejaba su fila de auditoría en
  Kotlin, porque la transacción se deshacía con el error de validación (en
  Django la petición no es atómica y la fila se queda);
- Django guarda la foto de un paciente nuevo como `''` y no como `NULL`.

## 13. Los ids de la siembra son fijos

`scripts/sembrar-escritorio.py` crea pacientes, doctores, citas y convenios
con `uuid5` de un nombre (`paciente-0`, `cita-2`…). Así `peticiones.json` puede
nombrarlos, y el comparador puede confirmar, reagendar o dar de baja una fila
concreta en los dos servidores, en el mismo orden.

## 14. Las alertas clínicas se comparan con Django caso a caso

Las alertas son seguridad del paciente: si Kotlin no avisa de una alergia
que Django sí avisaba, nadie lo nota hasta que pasa algo. Y la traducción
tiene trampas que una prueba escrita a mano no encuentra: `\b`, `\w` y `\s`
no significan lo mismo en Python que en Java («²» es parte de una palabra en
Python y no en Java; «\u001c» es un espacio en Python y no en Java).

Por eso `scripts/casos-de-alertas.py` ejecuta el `alertas.py` de Django
sobre 3000 casos —los de tests_alertas.py y textos al azar con semilla fija:
negaciones, tildes compuestas y descompuestas, espacios de Unicode, signos
pegados a la palabra, casillas del 033 con valores de todos los tipos— y
`ReglasDeAlertasTest` exige que Kotlin dé lo mismo en todos. Quitar un solo
carácter de los espacios de Python hace fallar 45 casos: la prueba no es de
adorno. Si cambian las reglas en Django, se vuelve a generar el fichero.

## 15. Los campos de DRF se comparan con DRF caso a caso

`scripts/casos-de-campos.py` pasa unos 7300 valores por los campos de DRF
que usan las rutas portadas (texto, correo, decimal, entero, booleano, JSON,
opción y fecha) y `CamposDrfTest` exige que `CamposDrf` dé el mismo valor o
los mismos errores. Las reglas son de Python y no de Java, y no se adivinan:

- `"36.50"` como texto tiene dos decimales y no vale en un
  `DecimalField(max_digits=4, decimal_places=1)`; el número `36.50` es el
  float 36.5 y sí; `"-0"` vale y responde «-0.0» (la base guarda 0.0).
- `int()` acepta `"٣"`, `"1_000"` y `" 7 "`, pero no `"\u001c7"`;
  `Decimal()` ignora TODOS los guiones bajos (`"1__0"` es 10).
- El float 1e16 se escribe «1e+16» y 1e-5 «1e-05».

La primera ejecución encontró dos fallos en código ya portado y dado por
bueno: `DateField` solo aceptaba `AAAA-MM-DD`, y Django acepta también
`20261020`, `2026-W43-3`, `2026-1-5` y cifras de otros alfabetos; y un
`EmailField` que recibe una lista responde «Introduzca una dirección de
correo electrónico válida.», no «No es una cadena válida.». Lo común está en
`comun/python/Python.kt`.

## 16. Los datos que Django mete con migraciones

La V1 de Flyway es solo el esquema (`pg_dump --schema-only`). Los datos que
crean las migraciones de Django no están: en una base nueva (las pruebas,
una instalación desde cero) faltarían. El catálogo CIE-10 va ahora en la V2
(`V2__catalogo_cie10.sql`, generada desde `clinical/0008_seed_cie10.py`, con
`on conflict do nothing` para no tocar una base que ya viene de Django).

**Pendiente de revisar** el resto de migraciones con `RunPython`, por si
alguna crea filas que una base nueva necesite: `accounts/0009`, `agenda/0002`,
`billing/0002`, `clinical/0011` y `0013`, `common/0005`, `0007`, `0008`,
`0009` y `0011`.

## 17. Las carreras se prueban forzando el orden, no con hilos

El 500 al renovar la sesión solo salía cuando dos renovaciones con el mismo
token se cruzaban en un momento concreto. Doce hilos a la vez no lo
reprodujeron. `RenovacionDeSesionTest` lo fuerza: otra transacción mete el
token en la lista negra sin confirmar, la renovación pasa la comprobación y
se queda esperando en su `insert`, y la prueba confirma la otra transacción.
Sin la corrección falla siempre con el 500; con ella pasa siempre.

En Django esas altas son `get_or_create`, que no falla si otro proceso crea la
fila a la vez. Aquí, `insert … on conflict do nothing`.

## 18. El UUID de Python, entero

`CamposDrf.uuidDePython` hace lo mismo que `uuid.UUID(hex=…)`, que es lo que
usa el `UUIDField` de Django: quita «urn:» y «uuid:», las llaves de los
extremos y todos los guiones, y lo que queda es un `int(…, 16)` de 32
caracteres (con lo que admite Python: «0x», guiones bajos). Antes solo se
aceptaban las formas habituales; lo encontró la revisión adversarial de la
pantalla de Pagos, junto con el «""» en un campo relacional (DRF lo trata
como nulo) y el «-0.00» de un subtotal (`0 * Decimal("-5.00")`).
