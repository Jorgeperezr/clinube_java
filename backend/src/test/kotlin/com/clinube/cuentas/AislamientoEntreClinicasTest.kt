package com.clinube.cuentas

import com.clinube.PruebaDeIntegracion
import com.clinube.comun.jwt.Jwt
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La regla de aislamiento: **la clínica de la petición es la del usuario en
 * la base, no la del token**.
 *
 * Esta prueba existe porque la alternativa es tentadora y está mal. El
 * token ya trae `tenant_id` y leerlo ahorra una consulta; el problema es
 * que entonces la clínica la elige quien presenta el token. Aquí se firma
 * un token con la clínica de OTRA y se comprueba que el servidor sigue
 * dando la del usuario.
 *
 * Los tokens se firman con la misma clave que usa el servidor: es el caso
 * peor, el de una clave filtrada. Incluso así, el aislamiento tiene que
 * aguantar.
 */
class AislamientoEntreClinicasTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val secreto = "dev-only-insecure-key-change-me-never-use-in-production"

    private fun tokenFirmado(usuarioId: String, rol: String, clinicaId: String?): String {
        val ahora = Instant.now().epochSecond
        return Jwt.firmar(
            linkedMapOf(
                "token_type" to "access",
                "exp" to ahora + 1800,
                "iat" to ahora,
                "jti" to java.util.UUID.randomUUID().toString().replace("-", ""),
                "user_id" to usuarioId,
                "role" to rol,
                "tenant_id" to (clinicaId ?: "None"),
            ),
            secreto,
        )
    }

    private fun perfil(token: String): Pair<Int, com.fasterxml.jackson.databind.JsonNode> {
        val cabeceras = HttpHeaders().apply { set("Authorization", "Bearer $token") }
        val r = rest.exchange(
            "/api/v1/auth/me/", HttpMethod.GET, HttpEntity<Void>(cabeceras), String::class.java,
        )
        return r.statusCode.value() to json.readTree(r.body ?: "{}")
    }

    @Test
    fun `un token que dice otra clinica no cambia la clinica de la peticion`() {
        // La clínica demo es de pruebas; la vecina no. Si el servidor se
        // creyera el token, `clinica_de_pruebas` saldría `false`.
        val (estado, datos) = perfil(tokenFirmado(ID_ADMIN, "admin", ID_CLINICA_VECINA))
        assertEquals(200, estado)
        assertTrue(
            datos["clinica_de_pruebas"].asBoolean(),
            "la clínica tendría que ser la del usuario (de pruebas), no la del token",
        )
        // Y las funcionalidades son las de SU clínica: las funciones en
        // fase de prueba solo existen en una clínica de pruebas.
        assertTrue(datos["funcionalidades"]["finanzas"].asBoolean())
        assertTrue(datos["funcionalidades"]["temas_personales"].asBoolean())
    }

    @Test
    fun `el administrador de la clinica vecina ve lo suyo, y no es de pruebas`() {
        val (estado, datos) = perfil(
            tokenFirmado("22222222-2222-2222-2222-222222222222", "admin", ID_CLINICA_VECINA),
        )
        assertEquals(200, estado)
        assertFalse(datos["clinica_de_pruebas"].asBoolean())
        assertFalse(
            datos["funcionalidades"]["finanzas"].asBoolean(),
            "finanzas está en fase de prueba: en una clínica normal todavía no existe",
        )
        assertFalse(datos["funcionalidades"]["temas_personales"].asBoolean())
    }

    @Test
    fun `un token que se sube el rol no sube el rol`() {
        // El rol viaja en el token, pero el que manda es el de la base. Si
        // no, bastaría con firmar `role: admin` para tener todas las
        // funciones de gestión.
        val (estado, datos) = perfil(
            tokenFirmado("cf2adf14-2921-4f3f-86a2-ae4e1c681284", "admin", ID_CLINICA),
        )
        // Esa cuenta tiene contraseña temporal, así que `me` pasa pero el
        // rol que sale es el suyo.
        assertEquals(200, estado)
        assertEquals("doctor", datos["role"].asText())
        assertFalse(datos["funciones"]["finanzas"].asBoolean(), "un doctor no tiene finanzas por heredar")
    }

    @Test
    fun `un token de un usuario que ya no existe no entra`() {
        val (estado, datos) = perfil(
            tokenFirmado("99999999-9999-9999-9999-999999999999", "admin", ID_CLINICA),
        )
        assertEquals(401, estado)
        assertEquals("Usuario no encontrado", datos["error"]["message"].asText())
    }

    @Test
    fun `un token de una cuenta dada de baja no entra`() {
        val (estado, datos) = perfil(
            tokenFirmado("8d494297-cc5a-44fd-852e-7e7498b7ce55", "doctor", ID_CLINICA),
        )
        assertEquals(401, estado)
        assertEquals("El usuario está inactivo", datos["error"]["message"].asText())
    }

    @Test
    fun `un token sin user_id no entra`() {
        val ahora = Instant.now().epochSecond
        val sinUsuario = Jwt.firmar(
            linkedMapOf<String, Any?>(
                "token_type" to "access", "exp" to ahora + 1800, "iat" to ahora,
                "jti" to "0".repeat(32), "role" to "admin", "tenant_id" to ID_CLINICA,
            ),
            secreto,
        )
        val (estado, datos) = perfil(sinUsuario)
        assertEquals(401, estado)
        assertEquals("token_not_valid", datos["error"]["code"].asText())
        assertEquals(
            "El token no contenía identificación de usuario reconocible",
            datos["error"]["message"].asText(),
        )
    }
}
