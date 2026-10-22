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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El formulario 033, los diagnósticos y el catálogo CIE-10 de la ficha
 * (`apps/clinical/form033_views.py` y `views.py`). La paridad de cada
 * respuesta y del estado de la base la comprueba el comparador de contrato;
 * aquí, las reglas.
 */
class HistoriaClinicaTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA

    private data class Respuesta(val estado: Int, val cuerpo: JsonNode, val texto: String)

    private fun token(correo: String): String {
        val r = rest.postForEntity("/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java)
        return json.readTree(r.body)["access"].asText()
    }

    /** El cuerpo va como texto: así se puede mandar JSON que Jackson no escribiría igual (`1e-5`, claves desordenadas). */
    private fun pedir(metodo: HttpMethod, ruta: String, token: String, cuerpo: String? = null): Respuesta {
        val cabeceras = HttpHeaders().apply {
            setBearerAuth(token)
            if (cuerpo != null) contentType = MediaType.APPLICATION_JSON
        }
        val r = rest.exchange(URI("http://localhost:$puerto$ruta"), metodo, HttpEntity(cuerpo, cabeceras), String::class.java)
        val texto = r.body ?: ""
        return Respuesta(r.statusCode.value(), json.readTree(texto.ifEmpty { "null" }), texto)
    }

    private fun doctora(licencia: String = "MSP-77", firma: String = "firmas/x.png"): Pair<UUID, String> {
        val usuario = sembrar.usuario("doc@historia.ec", "doctor", demo, "Dra. Historia")
        val doctor = sembrar.doctor(usuario, demo)
        jdbc.update("update agenda_doctor set license_number = ?, signature_image = ? where id = ?", licencia, firma, doctor)
        return doctor to token("doc@historia.ec")
    }

    // ── Formulario 033 ───────────────────────────────────────────────

    @Test
    fun `el alta guarda la constancia del profesional y responde como Python`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val (doctor, doctora) = doctora()
        val r = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/form033/", doctora,
            """{"date": "20261020", "temperatura": "-0", "pulso": "12.0", "examen_estomatognatico": {"zz": 1e-5, "a": [1.0, 1e16]}}""")
        assertEquals(201, r.estado, r.texto)
        assertEquals("2026-10-20", r.cuerpo["date"].asText())
        // El «-0.0» de Python y los floats con `repr()`, en el orden en que llegaron.
        assertTrue(""""temperatura":"-0.0"""" in r.texto, r.texto)
        assertTrue(""""examen_estomatognatico":{"zz":1e-05,"a":[1.0,1e+16]}""" in r.texto, r.texto)
        assertEquals(12, r.cuerpo["pulso"].asInt())
        assertEquals("Dra. Historia · MSP-77", r.cuerpo["registered_by"].asText())
        assertEquals(json.readTree("{}"), r.cuerpo["antecedentes_personales"])

        val fila = jdbc.queryForMap("select doctor_id, created_by_id, professional_snapshot::text as o, temperatura::text as t from clinical_form033record")
        assertEquals(doctor, fila["doctor_id"])
        assertEquals("0.0", fila["t"])
        val constancia = json.readTree(fila["o"] as String)
        assertEquals(true, constancia["has_signature"].asBoolean())
        assertEquals("doctor", constancia["role"].asText())
        assertTrue(constancia["recorded_at"].asText().endsWith("+00:00"))

        // Leída de la base: el orden de `jsonb`, el 0.0 sin signo y el 1e16
        // que `jsonb` guarda como entero (y Python lee como `int`).
        val lista = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/form033/", doctora)
        assertTrue(""""temperatura":"0.0"""" in lista.texto, lista.texto)
        assertTrue(""""examen_estomatognatico":{"a":[1.0,10000000000000000],"zz":1e-05}""" in lista.texto, lista.texto)
    }

    @Test
    fun `los errores salen todos y en el orden del serializador`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val (_, doctora) = doctora()
        val r = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/form033/", doctora,
            """{"presion_arterial": "1234567890123456", "pulso": -1, "temperatura": "36.55", "embarazada": "quizá"}""")
        assertEquals(400, r.estado)
        assertEquals(listOf("date", "embarazada", "temperatura", "pulso", "presion_arterial"),
            r.cuerpo["error"]["details"].fieldNames().asSequence().toList())
        assertEquals("Asegúrese de que no haya más de 1 decimales.", r.cuerpo["error"]["details"]["temperatura"][0].asText())
        assertEquals(0L, jdbc.queryForObject("select count(*) from clinical_form033record", Long::class.java))
    }

    @Test
    fun `recepcion ve la lista pero no crea ni abre uno`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val id = sembrar.form033(demo, paciente, LocalDate.of(2026, 10, 1))
        val recepcion = token("recepcion@clinube.local")
        assertEquals(1, pedir(HttpMethod.GET, "/api/v1/patients/$paciente/form033/", recepcion).cuerpo.size())
        assertEquals(403, pedir(HttpMethod.POST, "/api/v1/patients/$paciente/form033/", recepcion, """{"date": "2026-10-20"}""").estado)
        assertEquals(403, pedir(HttpMethod.GET, "/api/v1/form033/$id/", recepcion).estado)
    }

    @Test
    fun `un paciente que no existe es un 404 y uno de baja se admite`() {
        val (_, doctora) = doctora()
        val nadie = pedir(HttpMethod.POST, "/api/v1/patients/${UUID.randomUUID()}/form033/", doctora, """{"date": "2026-10-20"}""")
        assertEquals(404, nadie.estado)
        val baja = sembrar.paciente(demo, "Dado", "De Baja", "2222222222", activo = false)
        assertEquals(201, pedir(HttpMethod.POST, "/api/v1/patients/$baja/form033/", doctora, """{"date": "2026-10-20"}""").estado)
    }

    @Test
    fun `editar renueva la constancia con quien guarda pero no cambia el doctor`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val (doctor, doctora) = doctora()
        val alta = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/form033/", doctora, """{"date": "2026-10-20", "motivo_consulta": "Dolor"}""")
        val id = alta.cuerpo["id"].asText()
        val admin = token(CORREO_ADMIN)
        val r = pedir(HttpMethod.PATCH, "/api/v1/form033/$id/", admin, """{"pulso": "80"}""")
        assertEquals(200, r.estado, r.texto)
        assertEquals(80, r.cuerpo["pulso"].asInt())
        assertEquals("Dolor", r.cuerpo["motivo_consulta"].asText())
        assertEquals(CORREO_ADMIN, r.cuerpo["registered_by"].asText())
        assertEquals(doctor, jdbc.queryForObject("select doctor_id from clinical_form033record", UUID::class.java))
        assertEquals(400, pedir(HttpMethod.PUT, "/api/v1/form033/$id/", admin, """{"pulso": 1}""").estado)
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/form033/${UUID.randomUUID()}/", admin).estado)
    }

    @Test
    fun `sin constancia no hay registered_by`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val id = sembrar.form033(demo, paciente, LocalDate.of(2026, 10, 1))
        assertNull(pedir(HttpMethod.GET, "/api/v1/form033/$id/", token(CORREO_ADMIN)).cuerpo["registered_by"].textValue())
    }

    // ── Diagnósticos ─────────────────────────────────────────────────

    @Test
    fun `diagnosticos paginados, con el doctor de quien los registra`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val (doctor, doctora) = doctora()
        for (i in 1..21) {
            assertEquals(201, pedir(HttpMethod.POST, "/api/v1/patients/$paciente/diagnoses/", doctora,
                """{"description": "Caries $i", "date": "2026-10-${"%02d".format(i)}"}""").estado)
        }
        val admin = token(CORREO_ADMIN)
        val r = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/diagnoses/", admin,
            """{"description": "  General ", "diagnosis_kind": "def", "tooth_fdi_code": "26", "date": "2026-10-25"}""")
        assertEquals(201, r.estado, r.texto)
        assertTrue(r.cuerpo["doctor"].isNull)
        assertEquals("General", r.cuerpo["description"].asText())
        assertEquals("Definitivo", r.cuerpo["kind_display"].asText())

        val pagina1 = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/diagnoses/", doctora)
        assertEquals(22, pagina1.cuerpo["count"].asInt())
        assertEquals("2026-10-25", pagina1.cuerpo["results"][0]["date"].asText())
        assertEquals(doctor.toString(), pagina1.cuerpo["results"][1]["doctor"].asText())
        assertEquals("Presuntivo", pagina1.cuerpo["results"][1]["kind_display"].asText())
        assertEquals(2, pedir(HttpMethod.GET, "/api/v1/patients/$paciente/diagnoses/?page=2", doctora).cuerpo["results"].size())
        assertEquals(403, pedir(HttpMethod.GET, "/api/v1/patients/$paciente/diagnoses/", token("recepcion@clinube.local")).estado)
    }

    @Test
    fun `un diagnostico valida antes de buscar al paciente, que tiene que estar activo`() {
        val (_, doctora) = doctora()
        val nadie = "/api/v1/patients/${UUID.randomUUID()}/diagnoses/"
        val malo = pedir(HttpMethod.POST, nadie, doctora, """{"diagnosis_kind": "PRE"}""")
        assertEquals(400, malo.estado)
        assertEquals("\"PRE\" no es una elección válida.", malo.cuerpo["error"]["details"]["diagnosis_kind"][0].asText())
        assertEquals(404, pedir(HttpMethod.POST, nadie, doctora, """{"description": "x", "date": "2026-10-20"}""").estado)
        val baja = sembrar.paciente(demo, "Dado", "De Baja", "2222222222", activo = false)
        assertEquals(404, pedir(HttpMethod.POST, "/api/v1/patients/$baja/diagnoses/", doctora,
            """{"description": "x", "date": "2026-10-20"}""").estado)
    }

    // ── CIE-10 ───────────────────────────────────────────────────────

    @Test
    fun `el catalogo CIE-10 viene con la base y se busca como en Django`() {
        assertEquals(89L, jdbc.queryForObject("select count(*) from clinical_cie10", Long::class.java))
        val recepcion = token("recepcion@clinube.local")
        fun buscar(consulta: String) = pedir(HttpMethod.GET, "/api/v1/cie10/$consulta", recepcion).cuerpo["results"]
        assertEquals(0, buscar("?q=k").size())
        assertEquals(0, buscar("").size())
        assertEquals(listOf("K02", "K02.0", "K02.1"), buscar("?q=+k02+").take(3).map { it["code"].asText() })
        assertTrue(buscar("?q=CARIES").all { "aries" in it["description"].asText() })
        assertEquals(20, buscar("?q=de").size())
        assertEquals(0, buscar("?q=%25%25").size())
        assertEquals("K04.0", buscar("?q=xx&q=pulpitis")[0]["code"].asText())
    }
}
