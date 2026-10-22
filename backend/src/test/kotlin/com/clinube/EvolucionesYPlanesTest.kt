package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Evoluciones, planes de tratamiento y el descuento de inventario. La
 * paridad de cada respuesta y del estado de la base la comprueba el
 * comparador de contrato; aquí, las reglas.
 */
class EvolucionesYPlanesTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA

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

    private fun metadatos(accion: String): JsonNode = json.readTree(jdbc.queryForObject(
        "select metadata::text from accounts_auditlog where action = ?", String::class.java, accion,
    ))

    // ── Evoluciones ──────────────────────────────────────────────────

    @Test
    fun `sin doctor no hay doctor_name, y registered_by cae en quien la registro`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val admin = token()
        val r = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/evolutions/", admin, mapOf("date" to "2026-10-20", "notes" to " Nota "))
        assertEquals(201, r.estado, r.texto)
        assertFalse(r.cuerpo.has("doctor_name"))
        assertEquals(listOf("id", "doctor", "registered_by", "appointment", "type", "type_display", "date", "notes",
            "visible_to_patient", "follow_up_date", "created_at"), r.cuerpo.fieldNames().asSequence().toList())
        assertEquals(CORREO_ADMIN, r.cuerpo["registered_by"].asText())
        assertEquals("Nota", r.cuerpo["notes"].asText())
        assertEquals("clinical_note", r.cuerpo["type"].asText())

        val usuario = sembrar.usuario("doc@evol.ec", "doctor", demo, "Dra. Evolución")
        sembrar.doctor(usuario, demo)
        val conDoctor = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/evolutions/", token("doc@evol.ec"),
            mapOf("date" to "2026-10-21", "notes" to "Receta", "type" to "prescription", "visible_to_patient" to "true"))
        assertEquals("Dra. Evolución", conDoctor.cuerpo["doctor_name"].asText())
        assertEquals("Dra. Evolución", conDoctor.cuerpo["registered_by"].asText())
        assertEquals(2L, jdbc.queryForObject("select count(*) from accounts_auditlog where action = 'create_evolution'", Long::class.java))

        val lista = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/evolutions/?visible_to_patient=TRUE", admin)
        assertEquals(1, lista.cuerpo["count"].asInt())
        assertEquals(400, pedir(HttpMethod.GET, "/api/v1/patients/$paciente/evolutions/?type=receta", admin).estado)
    }

    @Test
    fun `editar deja lo anterior en la auditoria y borrar deja la evolucion entera`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val admin = token()
        val id = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/evolutions/", admin,
            mapOf("date" to "2026-10-20", "notes" to "Primera versión")).cuerpo["id"].asText()

        assertEquals(400, pedir(HttpMethod.PATCH, "/api/v1/evolutions/$id/", admin, mapOf("notes" to "")).estado)
        assertEquals(0L, jdbc.queryForObject("select count(*) from accounts_auditlog where action = 'update_evolution'", Long::class.java))

        val r = pedir(HttpMethod.PATCH, "/api/v1/evolutions/$id/", admin, mapOf("notes" to "Segunda", "visible_to_patient" to true))
        assertEquals("Segunda", r.cuerpo["notes"].asText())
        assertEquals(json.readTree("""{"previous_notes": "Primera versión", "previous_type": "clinical_note", "previous_visible_to_patient": false}"""),
            metadatos("update_evolution"))

        assertEquals(204, pedir(HttpMethod.DELETE, "/api/v1/evolutions/$id/", admin).estado)
        assertEquals(json.readTree("""{"patient_id": "$paciente", "type": "clinical_note", "date": "2026-10-20", "notes": "Segunda"}"""),
            metadatos("delete_evolution"))
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/evolutions/$id/", admin).estado)
    }

    // ── Planes e inventario ──────────────────────────────────────────

    private fun tratamiento(clinica: String, nombre: String): UUID {
        val especialidad = UUID.randomUUID()
        jdbc.update(
            """insert into specialties_specialty (id, created_at, updated_at, name, description, is_active, tenant_id)
               values (?, now(), now(), ?, '', true, ?::uuid)""", especialidad, "Especialidad $nombre", clinica,
        )
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into configuration_treatment (id, created_at, updated_at, name, base_price, consumes_inventory, is_active,
               specialty_id, tenant_id) values (?, now(), now(), ?, 45, true, true, ?, ?::uuid)""", id, nombre, especialidad, clinica,
        )
        return id
    }

    private fun lote(producto: UUID, numero: String, cantidad: String, caduca: LocalDate?) {
        jdbc.update(
            """insert into inventory_batch (id, created_at, updated_at, batch_number, quantity, expiration_date, received_at,
               tenant_id, product_id) values (?, now(), now(), ?, ?, ?, current_date, ?::uuid, ?)""",
            UUID.randomUUID(), numero, BigDecimal(cantidad), caduca, demo, producto,
        )
    }

    private fun existencias(numero: String) =
        jdbc.queryForObject("select quantity from inventory_batch where batch_number = ?", BigDecimal::class.java, numero)!!.toPlainString()

    @Test
    fun `una linea realizada descuenta los insumos, primero el lote que caduca antes`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val resina = tratamiento(demo, "Resina")
        val producto = UUID.randomUUID()
        jdbc.update(
            """insert into inventory_product (id, created_at, updated_at, name, unit, min_stock, is_active, tenant_id)
               values (?, now(), now(), 'Anestesia', 'unidad', 1, true, ?::uuid)""", producto, demo,
        )
        lote(producto, "TARDE", "1.50", LocalDate.of(2027, 5, 1))
        lote(producto, "PRONTO", "5", LocalDate.of(2026, 11, 1))
        lote(producto, "SIN-FECHA", "9", null)
        jdbc.update(
            "insert into configuration_treatmentinventoryitem (id, quantity_used, product_id, treatment_id) values (?, 6.25, ?, ?)",
            UUID.randomUUID(), producto, resina,
        )
        val admin = token()
        val plan = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/treatment-plans/", admin, mapOf("notes" to "Plan")).cuerpo["id"].asText()

        // Al crear una línea ya realizada NO se descuenta: Django solo lo hace al editar.
        val linea = pedir(HttpMethod.POST, "/api/v1/treatment-plans/$plan/items/", admin, mapOf("treatment" to resina, "status" to "done"))
        assertEquals(201, linea.estado, linea.texto)
        assertEquals("5.00", existencias("PRONTO"))

        val id = linea.cuerpo["id"].asText()
        assertEquals(200, pedir(HttpMethod.PATCH, "/api/v1/treatment-plan-items/$id/", admin, mapOf("status" to "done")).estado)
        assertEquals("0.00", existencias("PRONTO"))
        assertEquals("0.25", existencias("TARDE"))
        assertEquals("9.00", existencias("SIN-FECHA"))
        // El fallo de Django que se copia: otro PATCH de una línea realizada vuelve a descontar.
        pedir(HttpMethod.PATCH, "/api/v1/treatment-plan-items/$id/", admin, mapOf("tooth_fdi_code" to "36"))
        assertEquals("0.00", existencias("TARDE"))
        assertEquals("3.00", existencias("SIN-FECHA"))
        assertEquals(4L, jdbc.queryForObject("select count(*) from inventory_inventorymovement where reason = 'consumption'", Long::class.java))
    }

    @Test
    fun `el precio escrito por una persona queda como manual, y el avance redondea como Python`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val resina = tratamiento(demo, "Resina")
        val admin = token()
        val plan = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/treatment-plans/", admin, emptyMap<String, Any>()).cuerpo
        assertEquals(json.readTree("""{"done": 0, "total": 0, "percent": 0}"""), plan["progress"])
        val ruta = "/api/v1/treatment-plans/${plan["id"].asText()}/items/"

        val sinPrecio = pedir(HttpMethod.POST, ruta, admin, mapOf("treatment" to resina)).cuerpo
        assertEquals("0.00", sinPrecio["estimated_price"].asText())
        val conPrecio = pedir(HttpMethod.POST, ruta, admin, mapOf("treatment" to resina, "estimated_price" to "-0")).cuerpo
        assertEquals("-0.00", conPrecio["estimated_price"].asText())
        assertEquals(listOf(false, true), jdbc.queryForList(
            "select price_is_manual from clinical_treatmentplanitem order by price_is_manual", Boolean::class.java,
        ))
        // 1 de 8 hechas es 12.5 %: `round()` de Python da 12, no 13.
        repeat(6) { pedir(HttpMethod.POST, ruta, admin, mapOf("treatment" to resina)) }
        pedir(HttpMethod.PATCH, "/api/v1/treatment-plan-items/${sinPrecio["id"].asText()}/", admin, mapOf("status" to "done"))
        val avance = pedir(HttpMethod.GET, "/api/v1/treatment-plans/${plan["id"].asText()}/", admin).cuerpo["progress"]
        assertEquals(json.readTree("""{"done": 1, "total": 8, "percent": 12}"""), avance)
    }

    @Test
    fun `el tratamiento de otra clinica no vale, aunque Django lo acepte`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val ajeno = tratamiento(ID_CLINICA_VECINA, "Ajeno")
        val admin = token()
        val plan = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/treatment-plans/", admin, emptyMap<String, Any>()).cuerpo["id"].asText()
        val r = pedir(HttpMethod.POST, "/api/v1/treatment-plans/$plan/items/", admin, mapOf("treatment" to ajeno))
        assertEquals(400, r.estado)
        assertEquals("Clave primaria \"$ajeno\" inválida - objeto no existe.", r.cuerpo["error"]["details"]["treatment"][0].asText())
        assertTrue(pedir(HttpMethod.GET, "/api/v1/config/treatments/", admin).cuerpo["results"].none { it["id"].asText() == ajeno.toString() })
    }
}
