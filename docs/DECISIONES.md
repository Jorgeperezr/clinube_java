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

El proveedor de la JVM rechaza la contraseña vacía y convierte los
caracteres por su cuenta. Las dos cosas son diferencias de comportamiento
frente a `hashlib.pbkdf2_hmac`, que recibe los bytes UTF-8 tal cual. Con
`Mac` el resultado es el mismo byte a byte, también en los casos raros.
El coste es el mismo: las dos implementaciones son Java puro.

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
