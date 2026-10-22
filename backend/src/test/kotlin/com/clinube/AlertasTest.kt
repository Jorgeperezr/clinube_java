package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los endpoints de las alertas clínicas, portados de la clase
 * EndpointsDeAlertas de apps/clinical/tests_alertas.py. Las reglas en sí
 * las compara con Django `ReglasDeAlertasTest`.
 */
class AlertasTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA

    private data class Respuesta(val estado: Int, val cuerpo: JsonNode)

    private fun token(correo: String): String {
        val r = rest.postForEntity("/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java)
        return json.readTree(r.body)["access"].asText()
    }

    private fun pedir(metodo: HttpMethod, ruta: String, token: String, cuerpo: Any? = null): Respuesta {
        val cabeceras = HttpHeaders().apply {
            setBearerAuth(token)
            if (cuerpo != null) contentType = MediaType.APPLICATION_JSON
        }
        val r = rest.exchange(URI("http://localhost:$puerto$ruta"), metodo,
            HttpEntity(cuerpo?.let { json.writeValueAsString(it) }, cabeceras), String::class.java)
        return Respuesta(r.statusCode.value(), json.readTree(r.body ?: "null"))
    }

    private fun claves(nodo: JsonNode) = nodo.map { it["clave"].asText() }.toSet()

    private fun pacienteConAntecedentes(): UUID {
        val id = sembrar.paciente(demo, "Ana", "Pérez", "0102030405")
        jdbc.update(
            """insert into patients_medicalbackground (allergies, medications, conditions, is_pregnant, updated_at, patient_id)
               values ('Penicilina', 'Rivaroxabán 20 mg', '', null, now(), ?)""", id,
        )
        return id
    }

    private fun doctora(): String {
        sembrar.doctor(sembrar.usuario("doc@alertas.ec", "doctor", demo), demo)
        return token("doc@alertas.ec")
    }

    private fun auditadas(accion: String) =
        jdbc.queryForObject("select count(*) from accounts_auditlog where action = ?", Long::class.java, accion)!!

    @Test
    fun `el doctor ve las alertas y queda auditado`() {
        val id = pacienteConAntecedentes()
        val r = pedir(HttpMethod.GET, "/api/v1/patients/$id/alertas/", doctora())
        assertEquals(200, r.estado)
        assertEquals(setOf("alergia_penicilina", "anticoagulado"), claves(r.cuerpo["alertas"]))
        assertEquals("Medicación: «Rivaroxabán 20 mg»", r.cuerpo["alertas"][1]["fuente"].asText())
        assertEquals(listOf("clave", "nivel", "titulo", "detalle", "fuente"), r.cuerpo["alertas"][0].fieldNames().asSequence().toList())
        val fila = jdbc.queryForMap("select entity_type, entity_id from accounts_auditlog where action = 'view_clinical_alerts'")
        assertEquals("MedicalBackground", fila["entity_type"])
        assertEquals(id.toString(), fila["entity_id"])
    }

    @Test
    fun `usa el ultimo formulario 033`() {
        val id = pacienteConAntecedentes()
        sembrar.form033(demo, id, LocalDate.of(2025, 1, 1), """{"Diabetes": {"checked": true}}""")
        sembrar.form033(demo, id, LocalDate.of(2026, 1, 1), embarazada = true)
        val alertas = claves(pedir(HttpMethod.GET, "/api/v1/patients/$id/alertas/", doctora()).cuerpo["alertas"])
        assertTrue("embarazo" in alertas)
        assertTrue("diabetes" !in alertas)
    }

    @Test
    fun `sin antecedentes ni formulario no hay alertas`() {
        val id = sembrar.paciente(demo, "Luis", "Mora", "0202020202")
        val r = pedir(HttpMethod.GET, "/api/v1/patients/$id/alertas/", doctora())
        assertEquals(200, r.estado)
        assertEquals(0, r.cuerpo["alertas"].size())
    }

    @Test
    fun `recepcion no tiene acceso`() {
        val id = pacienteConAntecedentes()
        val recepcion = token("recepcion@clinube.local")
        assertEquals(403, pedir(HttpMethod.GET, "/api/v1/patients/$id/alertas/", recepcion).estado)
        assertEquals(403, pedir(HttpMethod.POST, "/api/v1/patients/$id/alertas/revisar/", recepcion, mapOf("texto" to "Amoxicilina")).estado)
        assertEquals(0L, jdbc.queryForObject("select count(*) from accounts_auditlog", Long::class.java))
    }

    @Test
    fun `el paciente de otra clinica o dado de baja no existe`() {
        val ajeno = sembrar.paciente(ID_CLINICA_VECINA, "X", "Y", "1")
        val baja = sembrar.paciente(demo, "Dado", "De Baja", "2", activo = false)
        val doctora = doctora()
        for (id in listOf(ajeno, baja)) {
            val r = pedir(HttpMethod.GET, "/api/v1/patients/$id/alertas/", doctora)
            assertEquals(404, r.estado)
            assertEquals("No Patient matches the given query.", r.cuerpo["error"]["message"].asText())
        }
        assertEquals(0L, auditadas("view_clinical_alerts"))
    }

    @Test
    fun `revisar una receta`() {
        val id = pacienteConAntecedentes()
        val r = pedir(HttpMethod.POST, "/api/v1/patients/$id/alertas/revisar/", doctora(),
            mapOf("texto" to "Amoxicilina 500 mg\nIbuprofeno 400 mg"))
        assertEquals(200, r.estado)
        assertEquals(listOf("choques", "alertas"), r.cuerpo.fieldNames().asSequence().toList())
        assertEquals(setOf("alergia_penicilina", "anticoagulado"), claves(r.cuerpo["choques"]))
        assertEquals(1L, auditadas("check_prescription_alerts"))
        // Y la fila del middleware, como cualquier POST que sale bien.
        assertEquals(1L, auditadas("post"))
    }

    @Test
    fun `revisar sin texto o con uno demasiado largo es un 400, antes de buscar al paciente`() {
        val doctora = doctora()
        val inexistente = UUID.randomUUID()
        for (cuerpo in listOf(mapOf("texto" to "  "), mapOf("texto" to 5), emptyMap<String, Any>(), listOf("x"))) {
            val r = pedir(HttpMethod.POST, "/api/v1/patients/$inexistente/alertas/revisar/", doctora, cuerpo)
            assertEquals(400, r.estado)
            assertEquals("Falta el texto de la receta.", r.cuerpo["detail"].asText())
        }
        val largo = pedir(HttpMethod.POST, "/api/v1/patients/$inexistente/alertas/revisar/", doctora, mapOf("texto" to "x".repeat(5001)))
        assertEquals("La receta es demasiado larga para revisarla.", largo.cuerpo["detail"].asText())
        // 5000 caracteres, aunque en UTF-16 sean 10000: vale (y entonces sí falta el paciente).
        val emojis = pedir(HttpMethod.POST, "/api/v1/patients/$inexistente/alertas/revisar/", doctora, mapOf("texto" to "😀".repeat(5000)))
        assertEquals(404, emojis.estado)
        assertEquals(0L, jdbc.queryForObject("select count(*) from accounts_auditlog", Long::class.java))
    }
}
