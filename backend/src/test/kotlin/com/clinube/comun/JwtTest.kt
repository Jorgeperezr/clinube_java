package com.clinube.comun

import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.jwt.ErrorDeToken
import com.clinube.comun.jwt.Jwt
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtTest {

    private val secreto = "dev-only-insecure-key-change-me-never-use-in-production"

    private fun claims(tipo: String = "access", exp: Long = Instant.now().epochSecond + 1800) =
        linkedMapOf<String, Any?>(
            "token_type" to tipo,
            "exp" to exp,
            "iat" to Instant.now().epochSecond,
            "jti" to "0f6f7b3e71c749cd8b1aa7ebb3f37e9d",
            "user_id" to "a35f7a96-7c8b-4954-8f61-0596bd5118ad",
            "role" to "admin",
            "tenant_id" to "88a4cbeb-8604-4c10-b8fd-5bb844cd9362",
        )

    @Test
    fun `la cabecera es la de SimpleJWT, byte a byte`() {
        // `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9` es {"alg":"HS256","typ":"JWT"}
        // y es lo que emite Django. Forma parte de lo que se firma, así
        // que un espacio de más cambiaría la firma.
        val token = Jwt.firmar(claims(), secreto)
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", token.split(".")[0])
    }

    @Test
    fun `los claims van en el orden de SimpleJWT`() {
        val cuerpo = Jwt.firmar(claims(), secreto).split(".")[1]
        val texto = String(java.util.Base64.getUrlDecoder().decode(cuerpo))
        assertTrue(
            texto.startsWith("""{"token_type":"access","exp":"""),
            "el cuerpo tendría que empezar por token_type y exp, y empieza por: ${texto.take(60)}",
        )
        assertEquals(
            listOf("token_type", "exp", "iat", "jti", "user_id", "role", "tenant_id"),
            Regex("\"(\\w+)\":").findAll(texto).map { it.groupValues[1] }.toList(),
        )
    }

    @Test
    fun `lee lo que firma`() {
        val leido = Jwt.leer(Jwt.firmar(claims(), secreto), secreto)
        assertEquals("access", leido["token_type"])
        assertEquals("admin", leido["role"])
        assertEquals("a35f7a96-7c8b-4954-8f61-0596bd5118ad", leido["user_id"])
    }

    @Test
    fun `un token caducado dice caducado, no invalido`() {
        // La diferencia llega al panel y a la app, así que importa cuál
        // de los dos textos sale.
        val caducado = Jwt.firmar(claims(exp = Instant.now().epochSecond - 1), secreto)
        val e = assertFailsWith<ErrorDeToken> { Jwt.leer(caducado, secreto) }
        assertEquals(MensajesDeDjango.TOKEN_CADUCADO, e.motivo)
    }

    @Test
    fun `una firma cambiada no pasa`() {
        val token = Jwt.firmar(claims(), secreto)
        val tocado = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
        val e = assertFailsWith<ErrorDeToken> { Jwt.leer(tocado, secreto) }
        assertEquals(MensajesDeDjango.TOKEN_INVALIDO, e.motivo)
    }

    @Test
    fun `otra clave no vale`() {
        val token = Jwt.firmar(claims(), secreto)
        assertFailsWith<ErrorDeToken> { Jwt.leer(token, "otra-clave-que-no-es-la-de-django-nunca") }
    }

    @Test
    fun `no revienta con basura`() {
        listOf("", "basura", "a.b", "a.b.c.d", "a.b.c").forEach { malo ->
            val e = assertFailsWith<ErrorDeToken>("debería fallar con «$malo»") { Jwt.leer(malo, secreto) }
            assertEquals(MensajesDeDjango.TOKEN_INVALIDO, e.motivo)
        }
    }

    @Test
    fun `sin margen de gracia en la caducidad`() {
        // SimpleJWT no tiene `leeway` por defecto: un token que expira
        // en este mismo segundo ya no vale.
        val ahora = Instant.now()
        val token = Jwt.firmar(claims(exp = ahora.epochSecond), secreto)
        assertFailsWith<ErrorDeToken> { Jwt.leer(token, secreto, ahora) }
    }
}
