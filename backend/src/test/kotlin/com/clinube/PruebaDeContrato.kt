package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Base de las pruebas que comparan las respuestas de este servidor con las
 * de Django, capturadas en `src/test/resources/contrato/` por
 * `scripts/capturar-contrato.sh` y `scripts/capturar-condiciones.sh`.
 *
 * No son respuestas escritas a mano: salieron de un Django 5.2.17 real con
 * la clínica demo. Cuando Django cambie, se recapturan y el diff dice qué
 * hay que portar.
 */
abstract class PruebaDeContrato : PruebaDeIntegracion() {

    protected val json = ObjectMapper()

    /** Lo que cambia en cada respuesta y no se compara por valor, solo por tipo. */
    protected open val volatiles: Set<String> = setOf("access", "refresh", "jti", "created_at")

    protected fun deDjango(nombre: String): Pair<Int, ObjectNode?> {
        val fixture = json.readTree(javaClass.getResource("/contrato/$nombre.json")!!)
        val cuerpo = fixture["cuerpo"]
        return fixture["estado"].asInt() to (if (cuerpo.isNull) null else cuerpo as ObjectNode)
    }

    protected fun pedir(
        metodo: HttpMethod,
        ruta: String,
        cuerpo: Any? = null,
        token: String? = null,
        cabeceraCruda: String? = null,
    ): Pair<Int, String> {
        val cabeceras = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            token?.let { set("Authorization", "Bearer $it") }
            cabeceraCruda?.let { set("Authorization", it) }
        }
        val respuesta = rest.exchange(
            ruta, metodo, HttpEntity(cuerpo?.let { json.writeValueAsString(it) }, cabeceras),
            String::class.java,
        )
        return respuesta.statusCode.value() to (respuesta.body ?: "")
    }

    /** Una petición con el cuerpo EN CRUDO, tal como la mandó curl al capturar. */
    protected fun pedirCrudo(
        ruta: String,
        cuerpo: String?,
        tipo: String = "application/json",
        autorizacion: String? = null,
        metodo: HttpMethod = HttpMethod.POST,
    ): Pair<Int, String> {
        val cabeceras = HttpHeaders().apply {
            if (cuerpo != null) set("Content-Type", tipo)
            autorizacion?.let { set("Authorization", it) }
        }
        val r = rest.exchange(ruta, metodo, HttpEntity(cuerpo, cabeceras), String::class.java)
        return r.statusCode.value() to (r.body ?: "")
    }

    /** Pide lo mismo que la captura de Django y comprueba código y cuerpo. */
    protected fun comoDjango(fixture: String, respuesta: Pair<Int, String>) {
        val (esperado, cuerpoDjango) = deDjango(fixture)
        assertEquals(esperado, respuesta.first, "$fixture: código HTTP distinto. Kotlin respondió ${respuesta.second}")
        igual(cuerpoDjango, respuesta.second, fixture)
    }

    /** Compara dos JSON: claves, orden, tipos y valores, salvo los volátiles. */
    protected fun igual(django: JsonNode?, kotlin: String, donde: String) {
        if (django == null) return
        val diferencias = mutableListOf<String>()
        comparar(django, json.readTree(kotlin), "", diferencias)
        assertTrue(diferencias.isEmpty(), "$donde no responde como Django:\n  " + diferencias.joinToString("\n  "))
    }

    private fun comparar(a: JsonNode, b: JsonNode, camino: String, dif: MutableList<String>) {
        if (a.isObject && b.isObject) {
            val clavesA = a.fieldNames().asSequence().toList()
            val clavesB = b.fieldNames().asSequence().toList()
            if (clavesA != clavesB) {
                dif += "${camino.ifEmpty { "(raíz)" }}: claves distintas o en otro orden\n" +
                    "    Django: $clavesA\n    Kotlin: $clavesB"
                return
            }
            clavesA.forEach { comparar(a[it], b[it], if (camino.isEmpty()) it else "$camino.$it", dif) }
        } else if (a.isArray && b.isArray) {
            if (a.size() != b.size()) {
                dif += "$camino: Django da ${a.size()} elementos y Kotlin ${b.size()}"
                return
            }
            a.forEachIndexed { i, x -> comparar(x, b[i], "$camino[$i]", dif) }
        } else {
            val hoja = camino.substringAfterLast('.').substringBefore('[')
            if (hoja in volatiles) {
                if (a.nodeType != b.nodeType) dif += "$camino: tipo distinto (${a.nodeType} / ${b.nodeType})"
                return
            }
            if (a != b) dif += "$camino:\n    Django: $a\n    Kotlin: $b"
        }
    }

    protected fun entrar(correo: String = CORREO_ADMIN, clave: String = CLAVE): String {
        val (estado, cuerpo) = pedir(
            HttpMethod.POST, "/api/v1/auth/login/", mapOf("email" to correo, "password" to clave),
        )
        assertEquals(200, estado, "no se pudo entrar: $cuerpo")
        return json.readTree(cuerpo)["access"].asText()
    }
}
