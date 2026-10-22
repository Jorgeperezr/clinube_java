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
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Plantillas de plan, el precio con convenio al aplicarlas y el presupuesto
 * desde el plan. La paridad de cada respuesta y del estado de la base la
 * comprueba el comparador de contrato; aquí, las reglas.
 */
class PlantillasYPresupuestoTest : PruebaDeIntegracion() {

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

    private val especialidad by lazy {
        UUID.randomUUID().also {
            jdbc.update(
                """insert into specialties_specialty (id, created_at, updated_at, name, description, is_active, tenant_id)
                   values (?, now(), now(), 'General', '', true, ?::uuid)""", it, demo,
            )
        }
    }

    private fun tratamiento(nombre: String, base: String) = UUID.randomUUID().also {
        jdbc.update(
            """insert into configuration_treatment (id, created_at, updated_at, name, base_price, consumes_inventory, is_active,
               specialty_id, tenant_id) values (?, now(), now(), ?, ?, false, true, ?, ?::uuid)""",
            it, nombre, BigDecimal(base), especialidad, demo,
        )
    }

    private fun convenio(nombre: String, descuento: String?, activo: Boolean = true) = UUID.randomUUID().also {
        jdbc.update(
            """insert into configuration_agreement (id, created_at, updated_at, name, discount_percentage, is_active, tenant_id)
               values (?, now(), now(), ?, ?, ?, ?::uuid)""", it, nombre, descuento?.let(::BigDecimal), activo, demo,
        )
    }

    private fun tarifa(tratamiento: UUID, convenio: UUID?, precio: String) {
        jdbc.update(
            """insert into configuration_tariff (id, created_at, updated_at, price, agreement_id, tenant_id, treatment_id)
               values (?, now(), now(), ?, ?, ?::uuid, ?)""", UUID.randomUUID(), BigDecimal(precio), convenio, demo, tratamiento,
        )
    }

    private fun pacienteCon(convenio: UUID?, cedula: String) = sembrar.paciente(demo, "Ana", "Ruiz", cedula).also {
        jdbc.update("update patients_patient set agreement_id = ? where id = ?", convenio, it)
    }

    private fun plantillaCon(vararg tratamientos: UUID): String {
        val r = pedir(HttpMethod.POST, "/api/v1/clinical/plan-templates/", cuerpo = mapOf(
            "name" to "Plantilla ${UUID.randomUUID()}",
            "items" to tratamientos.mapIndexed { i, t -> mapOf("treatment" to t, "order" to i + 1) },
        ))
        assertEquals(201, r.estado, r.texto)
        return r.cuerpo["id"].asText()
    }

    private fun precios(paciente: UUID, plantilla: String): List<String> {
        val r = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/apply-plan-template/", cuerpo = mapOf("template_id" to plantilla))
        assertEquals(201, r.estado, r.texto)
        assertEquals("active", r.cuerpo["status"].asText())
        return r.cuerpo["items"].map { it["estimated_price"].asText() }
    }

    @Test
    fun `el precio sigue la precedencia de Django y redondea como el comercio`() {
        val conTarifaDelConvenio = tratamiento("Endodoncia", "180.50")
        val conTarifaGeneral = tratamiento("Resina", "45.00")
        val soloBase = tratamiento("Limpieza", "10.05")
        val seguro = convenio("Seguro", "50")
        tarifa(conTarifaDelConvenio, seguro, "150.00")
        tarifa(conTarifaGeneral, null, "50.00")
        val plantilla = plantillaCon(conTarifaDelConvenio, conTarifaGeneral, soloBase)

        // Particular: base, tarifa general, base.
        assertEquals(listOf("180.50", "50.00", "10.05"), precios(pacienteCon(null, "1111111111"), plantilla))
        // Con convenio al 50 %: la tarifa pactada manda; el descuento va sobre la
        // tarifa general si la hay; 10.05 / 2 = 5.025 sube a 5.03.
        assertEquals(listOf("150.00", "25.00", "5.03"), precios(pacienteCon(seguro, "2222222222"), plantilla))
        // Un convenio inactivo no descuenta, pero su tarifa pactada sigue valiendo.
        jdbc.update("update configuration_agreement set is_active = false where id = ?", seguro)
        assertEquals(listOf("150.00", "50.00", "10.05"), precios(pacienteCon(seguro, "3333333333"), plantilla))
        // Una tarifa negativa (mal cargada) nunca da un precio negativo.
        val roto = convenio("Roto", null)
        tarifa(conTarifaDelConvenio, roto, "-5")
        assertEquals("0.00", precios(pacienteCon(roto, "4444444444"), plantilla)[0])
    }

    @Test
    fun `el presupuesto respeta el precio escrito a mano salvo que sea cero`() {
        val resina = tratamiento("Resina", "45.00")
        val paciente = pacienteCon(null, "1111111111")
        val plan = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/treatment-plans/", cuerpo = emptyMap<String, Any>()).cuerpo["id"].asText()
        val lineas = "/api/v1/treatment-plans/$plan/items/"
        pedir(HttpMethod.POST, lineas, cuerpo = mapOf("treatment" to resina, "estimated_price" to "99.99", "tooth_fdi_code" to "11", "order" to 1))
        pedir(HttpMethod.POST, lineas, cuerpo = mapOf("treatment" to resina, "estimated_price" to "0", "order" to 2))
        pedir(HttpMethod.POST, lineas, cuerpo = mapOf("treatment" to resina, "order" to 3))

        sembrar.usuario("aux@presupuesto.ec", "auxiliary", demo)
        assertEquals(403, pedir(HttpMethod.POST, "/api/v1/treatment-plans/$plan/generate-budget/", token("aux@presupuesto.ec")).estado)
        val r = pedir(HttpMethod.POST, "/api/v1/treatment-plans/$plan/generate-budget/", token("recepcion@clinube.local"))
        assertEquals(201, r.estado, r.texto)
        assertEquals(listOf("99.99", "45.00", "45.00"), r.cuerpo["items"].map { it["unit_price"].asText() })
        assertEquals("189.99", r.cuerpo["total_amount"].asText())
        assertEquals("draft", r.cuerpo["status"].asText())
        assertEquals("189.99", jdbc.queryForObject("select total_amount::text from billing_budget", String::class.java))
        assertEquals(json.readTree("""{"plan_id": "$plan", "total": "189.99", "agreement": null}"""), json.readTree(
            jdbc.queryForObject("select metadata::text from accounts_auditlog where action = 'generate_budget_from_plan'", String::class.java),
        ))

        val vacio = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/treatment-plans/", cuerpo = emptyMap<String, Any>()).cuerpo["id"].asText()
        val sinLineas = pedir(HttpMethod.POST, "/api/v1/treatment-plans/$vacio/generate-budget/")
        assertEquals(400, sinLineas.estado)
        assertEquals("El plan no tiene ítems.", sinLineas.cuerpo["detail"].asText())
    }

    @Test
    fun `las plantillas nacen activas, con sus lineas en orden y su total estimado`() {
        val resina = tratamiento("Resina", "45.00")
        val endo = tratamiento("Endodoncia", "180.50")
        val r = pedir(HttpMethod.POST, "/api/v1/clinical/plan-templates/", cuerpo = mapOf(
            "name" to "Rehabilitación", "is_active" to false,
            "items" to listOf(mapOf("treatment" to resina, "order" to 2), mapOf("treatment" to endo, "order" to "1"),
                mapOf("treatment" to UUID.randomUUID()), mapOf("treatment" to null)),
        ))
        assertEquals(201, r.estado, r.texto)
        assertEquals(true, r.cuerpo["is_active"].asBoolean())
        assertEquals(listOf("Endodoncia", "Resina"), r.cuerpo["items"].map { it["treatment_name"].asText() })
        assertEquals("225.50", r.cuerpo["total_estimated"].asText())

        val vacia = pedir(HttpMethod.POST, "/api/v1/clinical/plan-templates/", cuerpo = mapOf("name" to "Vacía")).cuerpo
        assertEquals("0", vacia["total_estimated"].asText())
        assertEquals(listOf("Rehabilitación", "Vacía"),
            pedir(HttpMethod.GET, "/api/v1/clinical/plan-templates/", token("recepcion@clinube.local")).cuerpo.map { it["name"].asText() })

        // Donde Django da un 500 y deja la plantilla a medias, aquí un 400 y nada creado.
        val mala = pedir(HttpMethod.POST, "/api/v1/clinical/plan-templates/", cuerpo = mapOf("name" to "Mala", "items" to listOf("x")))
        assertEquals(400, mala.estado)
        assertEquals(0L, jdbc.queryForObject("select count(*) from clinical_treatmentplantemplate where name = 'Mala'", Long::class.java))
    }
}
