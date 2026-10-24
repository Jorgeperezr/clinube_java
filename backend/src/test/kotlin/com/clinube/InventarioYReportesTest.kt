package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Inventario, la pantalla de Reportes y las tarifas de los doctores. La
 * paridad de cada respuesta la comprueba el comparador; aquí, las reglas.
 */
class InventarioYReportesTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA
    private val hoy = LocalDate.of(2026, 10, 15)

    private data class Respuesta(val estado: Int, val cuerpo: JsonNode, val bytes: ByteArray, val cabeceras: HttpHeaders)

    @BeforeEach
    fun fijarElDia() = reloj.fijarEnGuayaquil(hoy)

    private fun token(correo: String = CORREO_ADMIN): String {
        val r = rest.postForEntity("/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java)
        return json.readTree(r.body)["access"].asText()
    }

    private fun pedir(metodo: HttpMethod, ruta: String, cuerpo: Any? = null, token: String = token()): Respuesta {
        val cabeceras = HttpHeaders().apply { setBearerAuth(token); if (cuerpo != null) contentType = MediaType.APPLICATION_JSON }
        val r = rest.exchange(URI("http://localhost:$puerto$ruta"), metodo, HttpEntity(cuerpo?.let { json.writeValueAsString(it) }, cabeceras),
            ByteArray::class.java)
        val bytes = r.body ?: ByteArray(0)
        val esJson = r.headers.contentType?.includes(MediaType.APPLICATION_JSON) == true
        return Respuesta(r.statusCode.value(), if (esJson) json.readTree(bytes) else json.nullNode(), bytes, r.headers)
    }

    @Test
    fun `un lote con su costo deja la entrada y el egreso de la compra`() {
        val producto = sembrar.producto(demo, "Anestesia", "5")
        sembrar.usuario("aux@inventario.ec", "auxiliary", demo)
        val r = pedir(HttpMethod.POST, "/api/v1/products/$producto/batches/", mapOf(
            "product" to producto.toString(), "batch_number" to "L1", "quantity" to "4", "expiration_date" to "2026-10-25",
            "costo_total" to "25.50", "proveedor" to "Dental Quito", "proveedor_ruc" to "1790011122001", "forma_pago" to "tarjeta",
        ), token("aux@inventario.ec"))
        assertEquals(201, r.estado)
        assertEquals(listOf("4.00", "2026-10-15"), listOf("quantity", "received_at").map { r.cuerpo[it].asText() })

        val movimiento = jdbc.queryForMap("select movement_type, reason, quantity from inventory_inventorymovement")
        assertEquals(listOf("in", "purchase", BigDecimal("4.00")), listOf(movimiento["movement_type"], movimiento["reason"], movimiento["quantity"]))
        val egreso = jdbc.queryForMap("select concepto, monto, forma_pago, origen, proveedor_ruc from finanzas_egreso")
        assertEquals("Compra: Anestesia — lote L1 (4.00 unidad)", egreso["concepto"])
        assertEquals(listOf(BigDecimal("25.50"), "tarjeta", "inventario", "1790011122001"),
            listOf(egreso["monto"], egreso["forma_pago"], egreso["origen"], egreso["proveedor_ruc"]))

        // Vence en 10 días: sale con el plazo por defecto (30); un plazo raro también es 30.
        val alerta = pedir(HttpMethod.GET, "/api/v1/inventory/alerts/expiring/?days=abc").cuerpo["expiring_batches"].single()
        assertEquals(10, alerta["days_to_expiry"].asInt())
        assertEquals(0, pedir(HttpMethod.GET, "/api/v1/inventory/alerts/expiring/?days=5").cuerpo["expiring_batches"].size())

        val errores = pedir(HttpMethod.GET, "/api/v1/inventory/movements/?movement_type=x&product=abc").cuerpo["error"]["details"]
        assertEquals(listOf("product", "movement_type"), errores.fieldNames().asSequence().toList())
    }

    @Test
    fun `el stock de un producto sin lotes se escribe distinto en cada sitio`() {
        val vacio = sembrar.producto(demo, "Guantes", "10")
        val lleno = sembrar.producto(demo, "Gasa", "5")
        sembrar.lote(demo, lleno, "16.5")
        // En la lista de productos lo escribe el `DecimalField` del serializador…
        val lista = pedir(HttpMethod.GET, "/api/v1/products/").cuerpo["results"]
        assertEquals(listOf("Gasa 16.50 false", "Guantes 0.00 true"),
            lista.map { "${it["name"].asText()} ${it["current_stock"].asText()} ${it["is_low_stock"].asBoolean()}" })
        // …y en el reporte es `str()` de la suma de Python, que sin lotes es el entero 0.
        val reporte = pedir(HttpMethod.GET, "/api/v1/reports/inventory/").cuerpo["products"]
        assertEquals(listOf("16.50", "0"), reporte.map { it["current_stock"].asText() })

        val excel = pedir(HttpMethod.GET, "/api/v1/reports/inventory/?export=excel")
        assertEquals("attachment; filename=\"inventario.xlsx\"", excel.cabeceras.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        val hoja = XSSFWorkbook(excel.bytes.inputStream()).getSheet("Inventario")
        assertEquals(listOf("Guantes", "unidad", "0", "10.00", "SÍ"), (0..4).map { hoja.getRow(2).getCell(it).stringCellValue })
        // El filtro de activos de django-filter no se queja de un valor raro: no filtra.
        jdbc.update("update inventory_product set is_active = false where id = ?", vacio)
        assertEquals(2, pedir(HttpMethod.GET, "/api/v1/products/?is_active=quizas").cuerpo["count"].asInt())
        assertEquals(1, pedir(HttpMethod.GET, "/api/v1/products/?is_active=FALSE").cuerpo["count"].asInt())
    }

    @Test
    fun `el resumen de citas redondea como Python y los pacientes nuevos llevan el dia en UTC`() {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "1111111111",
            creado = hoy.atTime(LocalTime.of(21, 0)).atZone(RelojDePrueba.GUAYAQUIL).toInstant())
        val doctor = sembrar.doctor(sembrar.usuario("doc@reportes.ec", "doctor", demo, "Dra. Ruiz"), demo)
        val a_las = { h: Int -> hoy.atTime(h, 0).atZone(RelojDePrueba.GUAYAQUIL).toInstant() }
        listOf("completed", "completed", "no_show", "cancelled").forEachIndexed { i, estado -> sembrar.cita(demo, paciente, doctor, a_las(8 + i), estado) }

        val citas = pedir(HttpMethod.GET, "/api/v1/reports/appointments-summary/?date_from=2026-10-15&date_to=2026-10-15").cuerpo
        assertEquals(4, citas["total"].asInt())
        assertEquals(66.7, citas["attendance_rate"].asDouble())
        assertEquals(25.0, citas["cancellation_rate"].asDouble())
        assertNull(pedir(HttpMethod.GET, "/api/v1/reports/appointments-summary/?date_from=2020-01-01&date_to=2020-01-01").cuerpo["attendance_rate"]
            .takeUnless { it.isNull })

        // Registrada el 15 a las 21:00 en Guayaquil: entra en el día 15 de la clínica, pero se escribe el 16 (UTC).
        val nuevos = pedir(HttpMethod.GET, "/api/v1/reports/new-patients/?date_from=2026-10-15&date_to=2026-10-15").cuerpo
        assertEquals(1, nuevos["count"].asInt())
        assertEquals("2026-10-16", nuevos["patients"][0]["registered"].asText())

        val produccion = pedir(HttpMethod.GET, "/api/v1/reports/production-by-doctor/", token = token("doc@reportes.ec")).cuerpo["doctors"]
        assertEquals("Dra. Ruiz 2", produccion.single().let { "${it["doctor_name"].asText()} ${it["completed_appointments"].asInt()}" })
    }

    @Test
    fun `la tarifa es del doctor de la ruta y no admite lo de otra clinica`() {
        val doctor = sembrar.doctor(sembrar.usuario("doc@tarifas.ec", "doctor", demo), demo)
        val ajeno = sembrar.doctor(sembrar.usuario("ajeno@tarifas.ec", "doctor", ID_CLINICA_VECINA), ID_CLINICA_VECINA)
        val creada = pedir(HttpMethod.POST, "/api/v1/doctors/$doctor/fees/", mapOf("doctor" to ajeno.toString(), "fee_type" to "fixed", "value" to "12.5"))
        assertEquals(201, creada.estado)
        assertEquals(listOf(doctor.toString(), "Monto fijo", "12.50"), listOf("doctor", "fee_type_display", "value").map { creada.cuerpo[it].asText() })
        assertEquals(1, pedir(HttpMethod.GET, "/api/v1/doctors/$doctor/fees/").cuerpo["count"].asInt())

        val deOtra = pedir(HttpMethod.POST, "/api/v1/doctors/$ajeno/fees/", mapOf("doctor" to ajeno.toString(), "fee_type" to "fixed", "value" to "1"))
        assertEquals(404, deOtra.estado)
        assertEquals(404, pedir(HttpMethod.POST, "/api/v1/doctors/${UUID.randomUUID()}/fees/",
            mapOf("doctor" to doctor.toString(), "fee_type" to "fixed", "value" to "1")).estado)
    }
}
