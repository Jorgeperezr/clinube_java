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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La pantalla de Pagos: presupuesto, aprobación, plan de cuotas, cobro y
 * estado de cuenta. La paridad de cada respuesta y del estado de la base la
 * comprueba el comparador de contrato; aquí, las reglas.
 */
class CobrosTest : PruebaDeIntegracion() {

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

    private fun tratamiento(): UUID {
        val especialidad = UUID.randomUUID()
        jdbc.update(
            """insert into specialties_specialty (id, created_at, updated_at, name, description, is_active, tenant_id)
               values (?, now(), now(), 'General', '', true, ?::uuid)""", especialidad, demo,
        )
        return UUID.randomUUID().also {
            jdbc.update(
                """insert into configuration_treatment (id, created_at, updated_at, name, base_price, consumes_inventory, is_active,
                   specialty_id, tenant_id) values (?, now(), now(), 'Resina', 45, false, true, ?, ?::uuid)""", it, especialidad, demo,
            )
        }
    }

    @Test
    fun `del presupuesto a la cuota pagada`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val resina = tratamiento()
        val recepcion = token("recepcion@clinube.local")

        val presupuesto = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/budgets/", recepcion,
            mapOf("patient" to paciente.toString(), "notes" to "")).cuerpo
        assertEquals("draft", presupuesto["status"].asText())
        assertEquals("0.00", presupuesto["total_amount"].asText())
        val id = presupuesto["id"].asText()

        val linea = pedir(HttpMethod.POST, "/api/v1/budgets/$id/items/", recepcion,
            mapOf("treatment" to resina, "tooth_fdi_code" to "", "quantity" to 3, "unit_price" to "33.337"))
        assertEquals(400, linea.estado)
        assertEquals("Asegúrese de que no haya más de 2 decimales.", linea.cuerpo["error"]["details"]["unit_price"][0].asText())
        pedir(HttpMethod.POST, "/api/v1/budgets/$id/items/", recepcion, mapOf("treatment" to resina, "quantity" to 3, "unit_price" to "33.33"))
        pedir(HttpMethod.POST, "/api/v1/budgets/$id/items/", recepcion, mapOf("treatment" to resina, "unit_price" to "0.02"))
        val conLineas = pedir(HttpMethod.GET, "/api/v1/budgets/$id/", recepcion).cuerpo
        assertEquals("100.01", conLineas["total_amount"].asText())
        assertEquals("99.99", conLineas["items"][0]["subtotal"].asText())

        // Sin aprobar no hay plan; después, sí, y solo uno.
        val plan = mapOf("installment_count" to 3, "first_due_date" to "2026-01-31")
        val sinAprobar = pedir(HttpMethod.POST, "/api/v1/budgets/$id/generate-payment-plan/", recepcion, plan)
        assertEquals(400, sinAprobar.estado)
        assertEquals("El presupuesto debe estar aprobado antes de generar el plan de pago.", sinAprobar.cuerpo["detail"].asText())
        assertEquals("approved", pedir(HttpMethod.POST, "/api/v1/budgets/$id/approve/", recepcion).cuerpo["status"].asText())
        val generado = pedir(HttpMethod.POST, "/api/v1/budgets/$id/generate-payment-plan/", recepcion, plan)
        assertEquals(201, generado.estado, generado.texto)
        // 100.01 / 3 = 33.336… → 33.34, y la última absorbe la diferencia; el mes siguiente a un 31 es su último día.
        assertEquals(listOf("2026-01-31 33.34", "2026-02-28 33.34", "2026-03-31 33.33"),
            generado.cuerpo["installments"].map { "${it["due_date"].asText()} ${it["amount"].asText()}" })
        assertEquals(409, pedir(HttpMethod.POST, "/api/v1/budgets/$id/generate-payment-plan/", recepcion, plan).estado)

        // Cobrar la primera cuota en dos veces.
        val cuota = generado.cuerpo["installments"][0]["id"].asText()
        val parte = pedir(HttpMethod.POST, "/api/v1/installments/$cuota/pay/", recepcion, mapOf("amount" to "20", "method" to "cash")).cuerpo
        assertEquals(listOf("pending", "20.00", "13.34"), listOf(parte["status"].asText(), parte["total_paid"].asText(), parte["balance"].asText()))
        val resto = pedir(HttpMethod.POST, "/api/v1/installments/$cuota/pay/", recepcion, mapOf("amount" to "13.34", "method" to "card")).cuerpo
        assertEquals(listOf("paid", "Pagada", "0.00"), listOf(resto["status"].asText(), resto["status_display"].asText(), resto["balance"].asText()))

        val cuenta = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/account-statement/", recepcion).cuerpo
        assertEquals("100.01", cuenta["total_amount"].asText())
        assertEquals("33.34", cuenta["total_paid"].asText())
        assertEquals("66.67", cuenta["balance"].asText())
        // Las dos cuotas restantes vencieron en febrero y marzo de 2026 (la prueba corre después).
        assertEquals(2, cuenta["overdue_count"].asInt())
        assertEquals("66.67", cuenta["overdue_amount"].asText())
    }

    @Test
    fun `el pago va al profesional de la ultima cita atendida si no se dice otro`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val habitual = sembrar.usuario("habitual@cobros.ec", "doctor", demo, "Dra. Habitual")
        val otro = sembrar.usuario("otro@cobros.ec", "doctor", demo, "Dr. Otro")
        val doctorHabitual = sembrar.doctor(habitual, demo)
        val doctorOtro = sembrar.doctor(otro, demo)
        sembrar.cita(demo, paciente, doctorHabitual, Instant.parse("2026-09-01T15:00:00Z"), estado = "completed")
        sembrar.cita(demo, paciente, doctorOtro, Instant.parse("2026-09-10T15:00:00Z"), estado = "cancelled")
        val recepcion = token("recepcion@clinube.local")
        val ruta = "/api/v1/patients/$paciente/payments/"

        val r = pedir(HttpMethod.POST, ruta, recepcion, mapOf("amount" to "15.5", "method" to "cash", "date" to "2026-10-20", "profesional" to null))
        assertEquals(201, r.estado, r.texto)
        assertEquals(habitual.toString(), r.cuerpo["profesional"].asText())
        assertEquals("Dra. Habitual", r.cuerpo["profesional_nombre"].asText())
        assertEquals("15.50", r.cuerpo["amount"].asText())
        assertNull(r.cuerpo["numero_comprobante"].numberValue())

        val ajeno = sembrar.usuario("ajeno@cobros.ec", "doctor", ID_CLINICA_VECINA)
        val malo = pedir(HttpMethod.POST, ruta, recepcion, mapOf("amount" to "1", "method" to "cash", "date" to "2026-10-20", "profesional" to ajeno))
        assertEquals("Ese profesional no es de esta clínica.", malo.cuerpo["error"]["details"]["profesional"][0].asText())
        val raro = pedir(HttpMethod.POST, ruta, recepcion, mapOf("amount" to "1", "method" to "cash", "date" to "2026-10-20", "profesional" to " abc "))
        assertEquals("“abc” no es un UUID válido.", raro.cuerpo["error"]["details"]["profesional"][0].asText())
    }

    @Test
    fun `quien no tiene la funcion de cobros no toca los presupuestos, pero el doctor ve las cuotas`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        sembrar.usuario("doc@cobros.ec", "doctor", demo)
        val doctora = token("doc@cobros.ec")
        val presupuesto = pedir(HttpMethod.POST, "/api/v1/patients/$paciente/budgets/", doctora,
            mapOf("patient" to paciente.toString())).cuerpo["id"].asText()
        val sinFuncion = pedir(HttpMethod.GET, "/api/v1/budgets/$presupuesto/", doctora)
        assertEquals(403, sinFuncion.estado)
        assertEquals("No tienes asignada esta función en la clínica.", sinFuncion.cuerpo["error"]["message"].asText())
        // `HasRole | TieneFuncion`: el doctor entra por el rol.
        assertEquals(200, pedir(HttpMethod.GET, "/api/v1/payment-plans/${UUID.randomUUID()}/installments/", doctora).estado)
        sembrar.usuario("aux@cobros.ec", "auxiliary", demo)
        val auxiliar = pedir(HttpMethod.GET, "/api/v1/payment-plans/${UUID.randomUUID()}/installments/", token("aux@cobros.ec"))
        assertEquals(403, auxiliar.estado)
        assertEquals("Usted no tiene permiso para realizar esta acción.", auxiliar.cuerpo["error"]["message"].asText())
    }

    @Test
    fun `el estado de cuenta cuenta todos los pagos, tambien los sueltos, y puede quedar a favor`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111")
        val recepcion = token("recepcion@clinube.local")
        pedir(HttpMethod.POST, "/api/v1/patients/$paciente/payments/", recepcion, mapOf("amount" to "50", "method" to "cash", "date" to "2026-10-20"))
        val cuenta = pedir(HttpMethod.GET, "/api/v1/patients/$paciente/account-statement/", recepcion).cuerpo
        assertEquals(listOf("0.00", "50.00", "-50.00", "0", "0.00"), listOf("total_amount", "total_paid", "balance", "overdue_count", "overdue_amount")
            .map { cuenta[it].asText() })
        assertEquals(BigDecimal("50.00"), jdbc.queryForObject("select amount from billing_payment", BigDecimal::class.java))
        assertEquals(LocalDate.of(2026, 10, 20), jdbc.queryForObject("select date from billing_payment", LocalDate::class.java))
    }
}
