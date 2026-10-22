package com.clinube.cuentas

import com.clinube.PruebaDeContrato
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Entrar, renovar y el perfil, comparados con las respuestas capturadas de Django. */
class ContratoDeAutenticacionTest : PruebaDeContrato() {

    // ── Entrar ───────────────────────────────────────────────────────

    @Test
    fun `entra un usuario creado por Django`() {
        // La prueba del punto 1 del plan: «el panel tiene que dejar entrar
        // con un usuario creado por Django». Su hash está en semilla.sql
        // tal como lo escribió Django.
        val (esperado, cuerpoDjango) = deDjango("login-correcto")
        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to CORREO_ADMIN, "password" to CLAVE),
        )
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "login correcto")

        val datos = json.readTree(cuerpo)
        assertEquals("admin", datos["role"].asText())
        assertEquals(false, datos["must_change_password"].asBoolean())
        assertTrue(datos["access"].asText().startsWith("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."))
    }

    @Test
    fun `la contraseña equivocada responde igual que Django`() {
        val (esperado, cuerpoDjango) = deDjango("login-contrasena-mala")
        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to CORREO_ADMIN, "password" to "mala"),
        )
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "contraseña mala")
    }

    @Test
    fun `un correo que no existe da el mismo error que una contraseña mala`() {
        // A propósito: decir «esa cuenta no existe» le confirma a quien
        // prueba correos cuáles son válidos.
        val (_, cuerpoDjango) = deDjango("login-contrasena-mala")
        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to "nadie@ningunaparte.com", "password" to CLAVE),
        )
        assertEquals(401, estado)
        igual(cuerpoDjango, cuerpo, "correo inexistente")
    }

    @Test
    fun `sin campos y con campos vacios son errores distintos`() {
        val (eSin, djangoSin) = deDjango("login-sin-campos")
        val (estadoSin, cuerpoSin) = pedir(HttpMethod.POST, "/api/v1/auth/login/", emptyMap<String, String>())
        assertEquals(eSin, estadoSin)
        igual(djangoSin, cuerpoSin, "login sin campos")

        val (eVacio, djangoVacio) = deDjango("login-campos-vacios")
        val (estadoVacio, cuerpoVacio) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/", mapOf("email" to "", "password" to ""),
        )
        assertEquals(eVacio, estadoVacio)
        igual(djangoVacio, cuerpoVacio, "login con campos vacíos")
    }

    @Test
    fun `un paciente no entra por el panel`() {
        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to "paciente@clinube.local", "password" to CLAVE),
        )
        assertEquals(400, estado)
        val detalles = json.readTree(cuerpo)["error"]["details"]["non_field_errors"]
        assertEquals(
            "Los pacientes deben ingresar con el código OTP enviado por WhatsApp.",
            detalles[0].asText(),
        )
    }

    @Test
    fun `una cuenta dada de baja no entra`() {
        val (estado, _) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to "inactivo@clinube.local", "password" to CLAVE),
        )
        assertEquals(401, estado)
    }

    @Test
    fun `con la clinica dada de baja el login funciona y el resto no`() {
        // Comprobado contra Django: el login devuelve 200 con sus tokens y
        // es la siguiente petición la que corta. No se «arregla».
        val (estadoLogin, cuerpoLogin) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to "apagada@clinube.local", "password" to CLAVE),
        )
        assertEquals(200, estadoLogin)

        val token = json.readTree(cuerpoLogin)["access"].asText()
        val (estadoPerfil, cuerpoPerfil) = pedir(HttpMethod.GET, "/api/v1/auth/me/", token = token)
        assertEquals(401, estadoPerfil)
        assertEquals(
            "La clínica de este usuario está desactivada.",
            json.readTree(cuerpoPerfil)["error"]["message"].asText(),
        )
    }

    @Test
    fun `el metodo GET en login no esta permitido`() {
        val (esperado, cuerpoDjango) = deDjango("login-metodo-get")
        val (estado, cuerpo) = pedir(HttpMethod.GET, "/api/v1/auth/login/")
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "login con GET")
    }

    // ── El perfil ────────────────────────────────────────────────────

    @Test
    fun `el perfil es igual al de Django, clave por clave y en el mismo orden`() {
        val (esperado, cuerpoDjango) = deDjango("perfil")
        val (estado, cuerpo) = pedir(HttpMethod.GET, "/api/v1/auth/me/", token = entrar())
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "perfil")
    }

    @Test
    fun `sin token, el perfil responde lo que responde Django`() {
        val (esperado, cuerpoDjango) = deDjango("perfil-sin-token")
        val (estado, cuerpo) = pedir(HttpMethod.GET, "/api/v1/auth/me/")
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "perfil sin token")
    }

    @Test
    fun `un token que no es un token`() {
        val (esperado, cuerpoDjango) = deDjango("perfil-token-basura")
        val (estado, cuerpo) = pedir(HttpMethod.GET, "/api/v1/auth/me/", token = "basura")
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "token basura")
    }

    @Test
    fun `una cabecera Authorization mal formada`() {
        val (esperado, cuerpoDjango) = deDjango("cabecera-mal-formada")
        val (estado, cuerpo) = pedir(HttpMethod.GET, "/api/v1/auth/me/", cabeceraCruda = "Bearer")
        assertEquals(esperado, estado)
        igual(cuerpoDjango, cuerpo, "cabecera mal formada")
    }

    @Test
    fun `otro esquema de autorizacion se ignora, no es un error`() {
        // Django devuelve «no se proveyeron las credenciales», porque el
        // autenticador ni se da por aludido.
        val (estado, cuerpo) = pedir(
            HttpMethod.GET, "/api/v1/auth/me/", cabeceraCruda = "Token ${entrar()}",
        )
        assertEquals(401, estado)
        assertEquals("not_authenticated", json.readTree(cuerpo)["error"]["code"].asText())
    }

    // ── Renovar ──────────────────────────────────────────────────────

    @Test
    fun `renovar devuelve una pareja nueva`() {
        val (_, cuerpoLogin) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to CORREO_ADMIN, "password" to CLAVE),
        )
        val refresco = json.readTree(cuerpoLogin)["refresh"].asText()

        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/token/refresh/", mapOf("refresh" to refresco),
        )
        assertEquals(200, estado)
        val datos = json.readTree(cuerpo)
        // El orden importa: al renovar, Django pone `access` primero.
        assertEquals(listOf("access", "refresh"), datos.fieldNames().asSequence().toList())
        assertNotNull(datos["access"])
        assert(datos["refresh"].asText() != refresco) { "la rotación tendría que dar un refresco nuevo" }
    }

    @Test
    fun `un refresco usado dos veces falla la segunda`() {
        // Es `ROTATE_REFRESH_TOKENS` + `BLACKLIST_AFTER_ROTATION`. Si no
        // fallara, un refresco robado serviría para siempre.
        val (_, cuerpoLogin) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to CORREO_ADMIN, "password" to CLAVE),
        )
        val refresco = json.readTree(cuerpoLogin)["refresh"].asText()

        assertEquals(200, pedir(HttpMethod.POST, "/api/v1/auth/token/refresh/", mapOf("refresh" to refresco)).first)

        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/token/refresh/", mapOf("refresh" to refresco),
        )
        assertEquals(401, estado)
        assertEquals("El token está en lista negra", json.readTree(cuerpo)["error"]["message"].asText())
    }

    @Test
    fun `el refresco emitido queda apuntado, como en SimpleJWT`() {
        pedir(HttpMethod.POST, "/api/v1/auth/login/", mapOf("email" to CORREO_ADMIN, "password" to CLAVE))
        val vivos = jdbc.queryForObject(
            "select count(*) from token_blacklist_outstandingtoken where user_id = ?::uuid",
            Long::class.java, ID_ADMIN,
        )
        assertEquals(1L, vivos)
    }

    @Test
    fun `un token de acceso no sirve para renovar`() {
        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/token/refresh/", mapOf("refresh" to entrar()),
        )
        assertEquals(401, estado)
        assertEquals("El token tiene un tipo incorrecto", json.readTree(cuerpo)["error"]["message"].asText())
    }

    @Test
    fun `renovar sin campo y con basura responden como Django`() {
        val (eSin, djangoSin) = deDjango("refresco-sin-campo")
        val (estadoSin, cuerpoSin) = pedir(
            HttpMethod.POST, "/api/v1/auth/token/refresh/", emptyMap<String, String>(),
        )
        assertEquals(eSin, estadoSin)
        igual(djangoSin, cuerpoSin, "refresco sin campo")

        val (eBasura, djangoBasura) = deDjango("refresco-basura")
        val (estadoBasura, cuerpoBasura) = pedir(
            HttpMethod.POST, "/api/v1/auth/token/refresh/", mapOf("refresh" to "basura"),
        )
        assertEquals(eBasura, estadoBasura)
        igual(djangoBasura, cuerpoBasura, "refresco con basura")
    }

    // ── Contraseña puesta por otra persona ───────────────────────────

    @Test
    fun `con contrasena temporal solo se puede ver el perfil y renovar`() {
        val (estadoLogin, cuerpoLogin) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to "temporal@clinube.local", "password" to CLAVE),
        )
        assertEquals(200, estadoLogin)
        val datos = json.readTree(cuerpoLogin)
        assertEquals(true, datos["must_change_password"].asBoolean())

        val token = datos["access"].asText()
        assertEquals(200, pedir(HttpMethod.GET, "/api/v1/auth/me/", token = token).first)

        val (estado, cuerpo) = pedir(HttpMethod.GET, "/api/v1/users/", token = token)
        // 403 con un código propio para que el panel sepa a dónde llevar
        // a la persona, en vez de un «no tienes permiso» que no explica nada.
        assertEquals(403, estado)
        assertEquals("password_change_required", json.readTree(cuerpo)["error"]["code"].asText())
    }

    // ── Cómo se lee el cuerpo y quién mira el token ──────────────────

    /**
     * Cada fila es una respuesta capturada de Django con
     * `scripts/capturar-contrato.sh`, y la misma petición contra Kotlin.
     */
    @Test
    fun `el cuerpo y la cabecera se leen como en Django, caso por caso`() {
        data class Caso(
            val fixture: String,
            val ruta: String,
            val cuerpo: String?,
            val tipo: String = "application/json",
            val autorizacion: String? = null,
            val metodo: HttpMethod = HttpMethod.POST,
        )
        val login = "/api/v1/auth/login/"
        val casos = listOf(
            Caso("login-cuerpo-null", login, "null"),
            Caso("login-cuerpo-texto", login, "\"hola\""),
            Caso("login-cuerpo-lista", login, "[1,2]"),
            Caso("login-campos-nulos", login, """{"email":null,"password":null}"""),
            Caso("login-campos-no-texto", login, """{"email":{"a":1},"password":true}"""),
            Caso("login-solo-espacios", login, """{"email":"   ","password":"   "}"""),
            Caso("login-texto-plano", login, "hola", tipo = "text/plain"),
            Caso("refresco-nulo", "/api/v1/auth/token/refresh/", """{"refresh":null}"""),
            // Django no autentica en estas dos: el token se ignora.
            Caso("salud-con-token-basura", "/api/v1/health/", null,
                 autorizacion = "Bearer basura", metodo = HttpMethod.GET),
            Caso("login-con-cabecera-mal-formada", login, "{}", autorizacion = "Bearer"),
            // Abierta, pero SÍ autentica: un token que no vale es un 401.
            Caso("otp-con-token-basura", "/api/v1/auth/otp/request/", "{}", autorizacion = "Bearer basura"),
        )
        val fallos = mutableListOf<String>()
        casos.forEach { caso ->
            val (esperado, cuerpoDjango) = deDjango(caso.fixture)
            val (estado, cuerpo) = pedirCrudo(caso.ruta, caso.cuerpo, caso.tipo, caso.autorizacion, caso.metodo)
            if (estado != esperado) {
                fallos += "${caso.fixture}: Django $esperado, Kotlin $estado ($cuerpo)"
                return@forEach
            }
            try {
                igual(cuerpoDjango, cuerpo, caso.fixture)
            } catch (e: AssertionError) {
                fallos += e.message ?: caso.fixture
            }
        }
        assertTrue(fallos.isEmpty(), "Casos distintos de Django:\n" + fallos.joinToString("\n"))
    }

    @Test
    fun `el correo y la contrasena se recortan, como en DRF`() {
        // `CharField` de DRF recorta por defecto, y `PasswordField` de
        // SimpleJWT no lo desactiva: comprobado contra Django, este login
        // entra.
        val (estado, _) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/",
            mapOf("email" to "  $CORREO_ADMIN  ", "password" to " $CLAVE "),
        )
        assertEquals(200, estado)
    }

    // ── Salud ────────────────────────────────────────────────────────

    @Test
    fun `las sondas de salud responden como las de Django`() {
        val (eSalud, djangoSalud) = deDjango("salud")
        val (estadoSalud, cuerpoSalud) = pedir(HttpMethod.GET, "/api/v1/health/")
        assertEquals(eSalud, estadoSalud)
        igual(djangoSalud, cuerpoSalud, "health")

        val (eListo, djangoListo) = deDjango("disponibilidad")
        val (estadoListo, cuerpoListo) = pedir(HttpMethod.GET, "/api/v1/ready/")
        assertEquals(eListo, estadoListo)
        igual(djangoListo, cuerpoListo, "ready")
    }
}
