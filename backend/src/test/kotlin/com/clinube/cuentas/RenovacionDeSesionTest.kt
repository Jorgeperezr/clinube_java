package com.clinube.cuentas

import com.clinube.PruebaDeIntegracion
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * POST /api/v1/auth/token/refresh/ como la `TokenRefreshView` de SimpleJWT
 * 5.5.1 que usa clinube: rota el refresco, invalida el presentado, apunta el
 * nuevo y exige que el usuario siga activo.
 */
class RenovacionDeSesionTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()

    private data class Respuesta(val estado: Int, val cuerpo: JsonNode)

    private fun refrescoDe(correo: String): String {
        val r = rest.postForEntity("/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java)
        return json.readTree(r.body)["refresh"].asText()
    }

    private fun renovar(refresco: String): Respuesta {
        val r = rest.postForEntity("/api/v1/auth/token/refresh/", mapOf("refresh" to refresco), String::class.java)
        return Respuesta(r.statusCode.value(), json.readTree(r.body ?: "null"))
    }

    private fun jti(token: String): String =
        json.readTree(java.util.Base64.getUrlDecoder().decode(token.split(".")[1]))["jti"].asText()

    private fun enListaNegra(token: String) = jdbc.queryForObject(
        """select count(*) from token_blacklist_blacklistedtoken n
           join token_blacklist_outstandingtoken v on v.id = n.token_id where v.jti = ?""",
        Long::class.java, jti(token),
    )!!

    @Test
    fun `varias renovaciones a la vez con el mismo token no dan 500`() {
        // Lo que hace el panel cuando caduca el acceso: cada petición que
        // falla con 401 pide su propia renovación con el mismo refresco.
        // Antes, la que llegaba segunda a la lista negra chocaba con la
        // restricción única y respondía 500.
        val refresco = refrescoDe(CORREO_ADMIN)
        val cuantas = 12
        val salida = CountDownLatch(1)
        val hilos = Executors.newFixedThreadPool(cuantas)
        val respuestas = (1..cuantas).map {
            hilos.submit<Respuesta> {
                salida.await()
                renovar(refresco)
            }
        }
        salida.countDown()
        val estados = respuestas.map { it.get(30, TimeUnit.SECONDS) }
        hilos.shutdown()

        assertTrue(estados.all { it.estado == 200 || it.estado == 401 }, estados.map { it.estado }.toString())
        assertTrue(estados.any { it.estado == 200 })
        // Las que llegan tarde ven el token ya invalidado, como en Django.
        estados.filter { it.estado == 401 }.forEach {
            assertEquals("El token está en lista negra", it.cuerpo["error"]["message"].asText())
        }
        assertEquals(1L, enListaNegra(refresco))
        // Cada refresco nuevo que se entregó quedó apuntado.
        estados.filter { it.estado == 200 }.forEach {
            assertEquals(1L, jdbc.queryForObject(
                "select count(*) from token_blacklist_outstandingtoken where jti = ?", Long::class.java, jti(it.cuerpo["refresh"].asText()),
            ))
        }
    }

    @Test
    fun `si otra renovacion invalida el token mientras tanto, esta termina bien`() {
        // La carrera, sin azar: otra transacción mete el token en la lista
        // negra SIN confirmar. La renovación no la ve (READ COMMITTED), pasa
        // la comprobación y se queda esperando al insertar el suyo. Al
        // confirmar la otra, antes chocaba con la clave única (500); ahora no
        // inserta nada y responde, como `get_or_create` en Django.
        val refresco = refrescoDe(CORREO_ADMIN)
        val otra = jdbc.dataSource!!.connection
        otra.autoCommit = false
        otra.prepareStatement(
            """insert into token_blacklist_blacklistedtoken (blacklisted_at, token_id)
               select now(), id from token_blacklist_outstandingtoken where jti = ?""",
        ).use { it.setString(1, jti(refresco)); it.executeUpdate() }

        val hilo = Executors.newSingleThreadExecutor()
        val respuesta = hilo.submit<Respuesta> { renovar(refresco) }
        // Esperar a que la renovación esté bloqueada en su insert.
        var bloqueada = false
        repeat(100) {
            if (!bloqueada) {
                bloqueada = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%token_blacklist_blacklistedtoken%'",
                    Long::class.java,
                )!! > 0
                if (!bloqueada) Thread.sleep(50)
            }
        }
        assertTrue(bloqueada, "la renovación no llegó a esperar por la otra transacción")
        otra.commit()
        otra.close()

        val r = respuesta.get(30, TimeUnit.SECONDS)
        hilo.shutdown()
        assertEquals(200, r.estado, r.cuerpo.toString())
        assertEquals(1L, enListaNegra(refresco))
    }

    @Test
    fun `el refresco rota, el viejo queda invalidado y el nuevo sirve`() {
        val primero = refrescoDe(CORREO_ADMIN)
        val r = renovar(primero)
        assertEquals(200, r.estado)
        assertEquals(listOf("access", "refresh"), r.cuerpo.fieldNames().asSequence().toList())
        assertEquals(1L, enListaNegra(primero))

        val otraVez = renovar(primero)
        assertEquals(401, otraVez.estado)
        assertEquals("token_not_valid", otraVez.cuerpo["error"]["code"].asText())

        val segundo = r.cuerpo["refresh"].asText()
        assertEquals(0L, enListaNegra(segundo))
        assertEquals(200, renovar(segundo).estado)
    }

    @Test
    fun `una cuenta desactivada ya no renueva, y su refresco no se invalida`() {
        val refresco = refrescoDe(CORREO_ADMIN)
        jdbc.update("update accounts_user set is_active = false where email = ?", CORREO_ADMIN)
        val r = renovar(refresco)
        assertEquals(401, r.estado)
        assertEquals("authentication_failed", r.cuerpo["error"]["code"].asText())
        assertEquals("No active account found for the given token.", r.cuerpo["error"]["message"].asText())
        assertEquals(0, r.cuerpo["error"]["details"].size())
        assertEquals(0L, enListaNegra(refresco))
    }
}
