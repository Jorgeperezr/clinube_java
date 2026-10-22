package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El odontograma y los índices CPO/ceo. La paridad de cada respuesta y del
 * estado de la base la comprueba el comparador de contrato; aquí, las reglas.
 */
class OdontogramaTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA
    private val dia = LocalDate.of(2026, 10, 1)

    private data class Respuesta(val estado: Int, val cuerpo: JsonNode, val texto: String)

    private fun token(correo: String = CORREO_ADMIN): String {
        val r = rest.postForEntity("/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java)
        return json.readTree(r.body)["access"].asText()
    }

    private fun pedir(metodo: HttpMethod, ruta: String, token: String = token(), cuerpo: Any? = null): Respuesta {
        val cabeceras = HttpHeaders().apply {
            setBearerAuth(token)
            if (cuerpo != null) contentType = MediaType.APPLICATION_JSON
        }
        val r = rest.exchange(URI("http://localhost:$puerto$ruta"), metodo,
            HttpEntity(cuerpo?.let { json.writeValueAsString(it) }, cabeceras), String::class.java)
        val texto = r.body ?: ""
        return Respuesta(r.statusCode.value(), json.readTree(texto.ifEmpty { "null" }), texto)
    }

    private fun momento(segundos: Long) = Instant.parse("2026-10-01T12:00:00Z").plusSeconds(segundos)

    private fun auditadas(accion: String) =
        jdbc.queryForObject("select count(*) from accounts_auditlog where action = ?", Long::class.java, accion)!!

    @Test
    fun `el catalogo es el activo de la clinica, por orden`() {
        sembrar.estado(demo, "CARIES", 2)
        sembrar.estado(demo, "SANO", 1)
        sembrar.estado(demo, "OBSOLETO", 0, activo = false)
        sembrar.estado(ID_CLINICA_VECINA, "AJENO", 0)
        val r = pedir(HttpMethod.GET, "/api/v1/odontogram-states/")
        assertEquals(listOf("SANO", "CARIES"), r.cuerpo.map { it["code"].asText() })
        assertEquals(listOf("id", "code", "label", "color", "order", "is_active"), r.cuerpo[0].fieldNames().asSequence().toList())
        assertEquals(403, pedir(HttpMethod.GET, "/api/v1/odontogram-states/", token("recepcion@clinube.local")).estado)
    }

    @Test
    fun `el estado actual es el ultimo registro de cada pieza y superficie`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val caries = sembrar.estado(demo, "CARIES", 2)
        val sano = sembrar.estado(demo, "SANO", 1)
        sembrar.diente(demo, paciente, "16", caries, dia, creado = momento(1))
        sembrar.diente(demo, paciente, "16", sano, dia, creado = momento(2))
        sembrar.diente(demo, paciente, "16", caries, dia, "occlusal", creado = momento(3))
        sembrar.diente(demo, paciente, "21", sano, dia, creado = momento(4))
        val r = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/odontogram/current/")
        assertEquals(paciente.toString(), r.cuerpo["patient_id"].asText())
        // Del más reciente al más antiguo, uno por (pieza, superficie).
        assertEquals(listOf("21 whole SANO", "16 occlusal CARIES", "16 whole SANO"),
            r.cuerpo["teeth"].map { "${it["tooth_fdi_code"].asText()} ${it["surface"].asText()} ${it["state_code"].asText()}" })
        assertEquals("Oclusal", r.cuerpo["teeth"][1]["surface_display"].asText())
        assertEquals(1L, auditadas("view_odontogram"))
    }

    @Test
    fun `el CPO usa la fecha mas reciente de cada pieza y separa permanentes de temporales`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val e = listOf("CARIES", "OBTURADO", "CORONA", "PERDIDA_CARIES", "INDICADA_EXTRACCION", "SANO")
            .mapIndexed { i, c -> c to sembrar.estado(demo, c, i) }.toMap()
        // La 21: el último CREADO es SANO, pero el de fecha más reciente es CARIES.
        sembrar.diente(demo, paciente, "21", e.getValue("CARIES"), dia, creado = momento(1))
        sembrar.diente(demo, paciente, "21", e.getValue("SANO"), dia.minusDays(5), creado = momento(2))
        sembrar.diente(demo, paciente, "36", e.getValue("CORONA"), dia)
        sembrar.diente(demo, paciente, "46", e.getValue("INDICADA_EXTRACCION"), dia)
        sembrar.diente(demo, paciente, "55", e.getValue("CARIES"), dia)
        sembrar.diente(demo, paciente, "64", e.getValue("PERDIDA_CARIES"), dia)
        sembrar.diente(demo, paciente, "75", e.getValue("OBTURADO"), dia, "occlusal")
        val r = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/cpo-ceo/", token("recepcion@clinube.local"))
        assertEquals(200, r.estado)
        assertEquals("""{"cpo":{"C":1,"P":1,"O":1,"total":3},"ceo":{"c":1,"e":1,"o":1,"total":3},"teeth_evaluated":6}""", r.texto)

        val baja = sembrar.paciente(demo, "Dado", "De Baja", "2222222222", activo = false)
        assertEquals(200, pedir(HttpMethod.GET, "/api/v1/patients/$baja/cpo-ceo/").estado)
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/patients/${UUID.randomUUID()}/cpo-ceo/").estado)
    }

    @Test
    fun `registrar valida, exige un paciente activo y un estado de esta clinica`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val sano = sembrar.estado(demo, "SANO", 1)
        val ajeno = sembrar.estado(ID_CLINICA_VECINA, "SANO", 1)
        val usuario = sembrar.usuario("doc@odonto.ec", "doctor", demo)
        val doctor = sembrar.doctor(usuario, demo)
        val doctora = token("doc@odonto.ec")
        val ruta = "/api/v1/patients/$paciente/tooth-records/"

        val malo = pedir(HttpMethod.POST, ruta, doctora, mapOf("surface" to "arriba", "mobility" to -1))
        assertEquals(400, malo.estado)
        assertEquals(listOf("tooth_fdi_code", "surface", "state", "mobility", "date"),
            malo.cuerpo["error"]["details"].fieldNames().asSequence().toList())

        // DIFERENCIA DELIBERADA: Django aceptaría el estado de otra clínica.
        val deOtra = pedir(HttpMethod.POST, ruta, doctora, mapOf("tooth_fdi_code" to "16", "state" to ajeno, "date" to "2026-10-01"))
        assertEquals(400, deOtra.estado)
        assertEquals("Clave primaria \"$ajeno\" inválida - objeto no existe.", deOtra.cuerpo["error"]["details"]["state"][0].asText())

        val bueno = pedir(HttpMethod.POST, ruta, doctora,
            mapOf("tooth_fdi_code" to "16", "state" to sano, "mobility" to "2", "date" to "2026-10-01", "notes" to "  ok  "))
        assertEquals(201, bueno.estado, bueno.texto)
        assertEquals("whole", bueno.cuerpo["surface"].asText())
        assertEquals(doctor.toString(), bueno.cuerpo["doctor"].asText())
        assertEquals(2, bueno.cuerpo["mobility"].asInt())
        assertEquals("ok", bueno.cuerpo["notes"].asText())
        assertEquals(1L, auditadas("create_tooth_record"))

        val baja = sembrar.paciente(demo, "Dado", "De Baja", "2222222222", activo = false)
        assertEquals(404, pedir(HttpMethod.POST, "/api/v1/patients/$baja/tooth-records/", doctora,
            mapOf("tooth_fdi_code" to "16", "state" to sano, "date" to "2026-10-01")).estado)
    }

    @Test
    fun `el historial se filtra como django-filter`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val sano = sembrar.estado(demo, "SANO", 1)
        sembrar.diente(demo, paciente, "16", sano, dia, creado = momento(1))
        sembrar.diente(demo, paciente, "16", sano, dia, "mesial", creado = momento(2))
        sembrar.diente(demo, paciente, "21", sano, dia, creado = momento(3))
        val ruta = "/api/v1/patients/$paciente/tooth-records/"
        assertEquals(3, pedir(HttpMethod.GET, ruta).cuerpo["count"].asInt())
        assertEquals(2, pedir(HttpMethod.GET, "$ruta?tooth_fdi_code=+16+").cuerpo["count"].asInt())
        assertEquals(1, pedir(HttpMethod.GET, "$ruta?tooth_fdi_code=16&surface=mesial").cuerpo["count"].asInt())
        val malo = pedir(HttpMethod.GET, "$ruta?surface=arriba")
        assertEquals(400, malo.estado)
        assertEquals("Escoja una opción válida. arriba no es una de las opciones disponibles.",
            malo.cuerpo["error"]["details"]["surface"][0].asText())
    }

    @Test
    fun `borrar un registro queda auditado con su contexto`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val caries = sembrar.estado(demo, "CARIES", 2)
        val registro = sembrar.diente(demo, paciente, "26", caries, dia)
        val ajeno = sembrar.diente(ID_CLINICA_VECINA, sembrar.paciente(ID_CLINICA_VECINA, "X", "Y", "3"), "16",
            sembrar.estado(ID_CLINICA_VECINA, "CARIES", 1), dia)

        assertEquals(204, pedir(HttpMethod.DELETE, "/api/v1/tooth-records/$registro/").estado)
        val metadatos = json.readTree(jdbc.queryForObject(
            "select metadata::text from accounts_auditlog where action = 'delete_tooth_record'", String::class.java,
        ))
        assertEquals(json.readTree("""{"patient_id": "$paciente", "tooth_fdi_code": "26", "state": "CARIES", "date": "2026-10-01"}"""), metadatos)
        assertEquals(0L, jdbc.queryForObject("select count(*) from clinical_toothrecord where id = ?", Long::class.java, registro))
        assertEquals(404, pedir(HttpMethod.DELETE, "/api/v1/tooth-records/$registro/").estado)
        assertEquals(404, pedir(HttpMethod.DELETE, "/api/v1/tooth-records/$ajeno/").estado)
        assertTrue(jdbc.queryForObject("select count(*) from clinical_toothrecord where id = ?", Long::class.java, ajeno)!! == 1L)
    }
}
