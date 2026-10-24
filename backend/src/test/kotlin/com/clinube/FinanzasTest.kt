package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finanzas: las cuentas del rol y de las liquidaciones, el comprobante de
 * cobro, los egresos con su factura y la caja. La paridad de cada respuesta
 * con Django la comprueba el comparador de contrato; aquí, las reglas.
 */
class FinanzasTest : PruebaDeIntegracion() {

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
        val cabeceras = HttpHeaders().apply {
            setBearerAuth(token)
            if (cuerpo != null && cuerpo !is LinkedMultiValueMap<*, *>) contentType = MediaType.APPLICATION_JSON
            if (cuerpo is LinkedMultiValueMap<*, *>) contentType = MediaType.MULTIPART_FORM_DATA
        }
        val entidad = HttpEntity(if (cuerpo is LinkedMultiValueMap<*, *>) cuerpo else cuerpo?.let { json.writeValueAsString(it) }, cabeceras)
        val r = rest.exchange(URI("http://localhost:$puerto$ruta"), metodo, entidad, ByteArray::class.java)
        val bytes = r.body ?: ByteArray(0)
        val esJson = r.headers.contentType?.includes(MediaType.APPLICATION_JSON) == true
        return Respuesta(r.statusCode.value(), if (esJson) json.readTree(bytes) else json.nullNode(), bytes, r.headers)
    }

    private fun texto(pdf: ByteArray): String {
        val lector = PdfReader(pdf)
        return (1..lector.numberOfPages).joinToString("\n") { PdfTextExtractor(lector).getTextFromPage(it) }
    }

    private fun profesional(correo: String, nombre: String, perfil: Map<String, Any?>): UUID {
        val id = sembrar.usuario(correo, "doctor", demo, nombre)
        assertEquals(200, pedir(HttpMethod.PATCH, "/api/v1/finanzas/equipo/$id/", perfil).estado)
        return id
    }

    private fun cobroDe(profesional: UUID?, monto: String, metodo: String = "cash", fecha: LocalDate = hoy): UUID {
        val paciente = sembrar.paciente(demo, "Ana", "Ruiz", "17${(10000000..99999999).random()}")
        val pago = sembrar.pago(demo, paciente, monto, metodo, fecha)
        jdbc.update("update billing_payment set profesional_id = ? where id = ?", profesional, pago)
        return pago
    }

    @Test
    fun `el rol de pagos sale como lo calcula un contador`() {
        val empleada = sembrar.usuario("laura@finanzas.ec", "reception", demo, "Laura Paz")
        val perfil = pedir(HttpMethod.PATCH, "/api/v1/finanzas/equipo/$empleada/", mapOf(
            "modalidad" to "dependencia", "sueldo" to "600", "fecha_ingreso" to "2023-01-10", "decimo_cuarto_mensual" to true,
        ))
        assertEquals("600.00", perfil.cuerpo["sueldo"].asText())
        assertEquals("Recepción", perfil.cuerpo["rol"].asText())

        val rol = pedir(HttpMethod.GET, "/api/v1/finanzas/roles/previa/?empleado=$empleada&mes=2026-10&horas_suplementarias=10").cuerpo
        // Valor hora 600/240 = 2,50; 10 h al 50 % = 37,50; lo que aporta, 637,50.
        assertEquals(
            listOf("Sueldo 600.00", "Horas suplementarias (10 h al 50 %) 37.50", "Décimo cuarto sueldo 40.17", "Fondos de reserva 53.10"),
            rol["ingresos"].map { "${it["concepto"].asText()} ${it["valor"].asText()}" },
        )
        // 637,50 / 12 = 53,125: «a la mitad hacia arriba», 53,13 (a la par serían 53,12).
        assertEquals(listOf("Décimo tercer sueldo 53.13", "Vacaciones 26.56"), rol["provisiones"].map { "${it["concepto"].asText()} ${it["valor"].asText()}" })
        assertEquals("Aporte personal IESS 9,45 % 60.24", rol["descuentos"][0].let { "${it["concepto"].asText()} ${it["valor"].asText()}" })
        assertEquals(listOf("730.77", "60.24", "670.53", "77.46", "12.15", "887.92"),
            listOf("total_ingresos", "total_descuentos", "neto", "aporte_patronal", "aporte_patronal_porcentaje", "costo_empleador").map { rol[it].asText() })
        assertTrue(rol["fondos_reserva_desde"].isNull)
        assertEquals("53.13", rol["acumula"]["decimo_tercero"].asText())

        // Los valores negativos se miran campo a campo y antes que los 30 días.
        val malo = pedir(HttpMethod.GET, "/api/v1/finanzas/roles/previa/?empleado=$empleada&dias_trabajados=31&anticipos=-1")
        assertEquals(400, malo.estado)
        assertEquals("Los valores del rol son números positivos.", malo.cuerpo["error"]["message"].asText())
    }

    @Test
    fun `la liquidacion descuenta, retiene y no se solapa`() {
        val doctora = profesional("mora@finanzas.ec", "Dra. Mora", mapOf("modalidad" to "honorarios", "porcentaje" to "40"))
        cobroDe(doctora, "100.00")
        val segundo = cobroDe(doctora, "55.55", "card", hoy.minusDays(3))
        val categorias = pedir(HttpMethod.GET, "/api/v1/finanzas/categorias/").cuerpo
        val laboratorio = categorias.first { it["clave"].asText() == "laboratorio" }["id"].asText()
        assertEquals(201, pedir(HttpMethod.POST, "/api/v1/finanzas/egresos/", mapOf(
            "fecha" to hoy.toString(), "categoria" to laboratorio, "concepto" to "Corona", "monto" to "20",
            "profesional" to doctora.toString(), "descontar_al_profesional" to true,
        )).estado)

        val cuerpo = mapOf("profesional" to doctora.toString(), "desde" to "2026-10-01", "hasta" to "2026-10-31", "ajustes" to "10")
        val liq = pedir(HttpMethod.POST, "/api/v1/finanzas/liquidaciones/", cuerpo)
        assertEquals(201, liq.estado)
        // 40 % de 155,55 = 62,22; retención del 10 % sobre 62,22 + el bono = 7,22; 62,22 − 20 + 10 − 7,22.
        assertEquals(listOf(1L, "155.55", "62.22", "20.00", "10.00", "7.22", "45.00"),
            listOf(liq.cuerpo["numero"].asLong()) + listOf("base", "honorario", "descuentos", "ajustes", "retencion", "a_pagar").map { liq.cuerpo[it].asText() })
        assertEquals(listOf("modalidad", "cobros", "descuentos", "cobros_ids", "descuentos_ids"), liq.cuerpo["detalle"].fieldNames().asSequence().toList())

        val otra = pedir(HttpMethod.POST, "/api/v1/finanzas/liquidaciones/", cuerpo)
        assertEquals(400, otra.estado)
        assertContains(otra.cuerpo["detail"].asText(), "(N.º 00001)")

        // Un cobro ya liquidado no cambia de profesional.
        val reasignar = pedir(HttpMethod.PATCH, "/api/v1/finanzas/cobros/$segundo/", mapOf("profesional" to null))
        assertEquals("Ese cobro ya entró en la liquidación N.º 00001. Anúlala antes de cambiarlo.", reasignar.cuerpo["detail"].asText())

        val id = liq.cuerpo["id"].asText()
        assertEquals("pagada", pedir(HttpMethod.POST, "/api/v1/finanzas/liquidaciones/$id/pagar/", mapOf("fecha" to hoy.toString())).cuerpo["estado"].asText())
        val sri = pedir(HttpMethod.GET, "/api/v1/finanzas/obligaciones/?mes=2026-10").cuerpo["obligaciones"].single()
        assertEquals(listOf("sri", "7.22", "2026-11-28"), listOf("tipo", "monto", "vence").map { sri[it].asText() })
        assertEquals("Dra. Mora — liquidación N.º 00001", sri["detalle"][0]["concepto"].asText())

        val pdf = pedir(HttpMethod.GET, "/api/v1/finanzas/liquidaciones/$id/pdf/")
        assertEquals("inline; filename=\"liquidacion-00001.pdf\"", pdf.cabeceras.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        val enElPapel = texto(pdf.bytes)
        assertContains(enElPapel, "LIQUIDACIÓN DE HONORARIOS")
        assertContains(enElPapel, "Honorario (40,00 % de $155,55)")
        assertContains(enElPapel, "Cuarenta y cinco con 00/100 dólares")
    }

    @Test
    fun `el comprobante se numera una vez y lo puede sacar recepcion`() {
        val pago = cobroDe(null, "121.50")
        sembrar.usuario("caja@finanzas.ec", "reception", demo, "Caja")
        val primero = pedir(HttpMethod.GET, "/api/v1/finanzas/cobros/$pago/comprobante/", token = token("caja@finanzas.ec"))
        assertEquals(200, primero.estado)
        assertEquals(MediaType.APPLICATION_PDF, primero.cabeceras.contentType)
        assertEquals("inline; filename=\"comprobante-000001.pdf\"", primero.cabeceras.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        val papel = texto(primero.bytes)
        assertContains(papel, "COMPROBANTE DE COBRO")
        assertContains(papel, "000001")
        assertContains(papel, "Ciento veintiún con 50/100 dólares")
        assertContains(papel, "Pago por servicios odontológicos")

        // Reimprimirlo da el mismo número; el contador de la clínica ya va por el 2.
        val otra = pedir(HttpMethod.GET, "/api/v1/finanzas/cobros/$pago/comprobante/")
        assertEquals("inline; filename=\"comprobante-000001.pdf\"", otra.cabeceras.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        assertEquals(2, jdbc.queryForObject("select siguiente_comprobante from finanzas_configuracionfinanzas", Int::class.java))
        assertEquals(1, jdbc.queryForObject("select numero_comprobante from billing_payment where id = ?", Int::class.java, pago))

        sembrar.usuario("doc@finanzas.ec", "doctor", demo)
        val sinCobros = pedir(HttpMethod.GET, "/api/v1/finanzas/cobros/$pago/comprobante/", token = token("doc@finanzas.ec"))
        assertEquals(403, sinCobros.estado)
        assertEquals("No tienes asignada esta función en la clínica.", sinCobros.cuerpo["error"]["message"].asText())
    }

    @Test
    fun `un egreso desde el formulario guarda su factura con el nombre de Django`() {
        val categoria = pedir(HttpMethod.GET, "/api/v1/finanzas/categorias/").cuerpo.first { it["clave"].asText() == "insumos" }["id"].asText()
        fun formulario() = LinkedMultiValueMap<String, Any>().apply {
            mapOf("fecha" to hoy.toString(), "categoria" to categoria, "concepto" to "Guantes", "monto" to "42.10", "forma_pago" to "efectivo",
                "estado" to "por_pagar", "vence" to "", "proveedor" to "", "proveedor_ruc" to "", "numero_factura" to "", "profesional" to "")
                .forEach { (k, v) -> add(k, v) }
            add("comprobante", object : ByteArrayResource("%PDF factura".toByteArray()) { override fun getFilename() = "factura 1.pdf" })
        }
        val creado = pedir(HttpMethod.POST, "/api/v1/finanzas/egresos/", formulario())
        assertEquals(201, creado.estado)
        // En un formulario, un vencimiento vacío es nulo (y una cuenta por pagar vence el día de la factura) y
        // una casilla que no llega es «no».
        assertEquals(listOf(hoy.toString(), "false", "true", "null"),
            listOf("vence", "descontar_al_profesional", "tiene_comprobante", "profesional").map { creado.cuerpo[it].asText() })
        val id = creado.cuerpo["id"].asText()
        assertEquals("%PDF factura", String(pedir(HttpMethod.GET, "/api/v1/finanzas/egresos/$id/comprobante/").bytes))
        assertEquals("finanzas/comprobantes/factura_1.pdf", jdbc.queryForObject("select comprobante from finanzas_egreso where id = ?::uuid", String::class.java, id))

        // La segunda factura con el mismo nombre lleva el sufijo al azar de Django.
        val segundo = pedir(HttpMethod.POST, "/api/v1/finanzas/egresos/", formulario()).cuerpo["id"].asText()
        val nombre = jdbc.queryForObject("select comprobante from finanzas_egreso where id = ?::uuid", String::class.java, segundo)
        assertTrue(Regex("""finanzas/comprobantes/factura_1_[A-Za-z0-9]{7}\.pdf""").matches(nombre!!), nombre)

        val mala = LinkedMultiValueMap<String, Any>().apply {
            add("concepto", "x")
            add("comprobante", object : ByteArrayResource("MZ".toByteArray()) { override fun getFilename() = "virus.exe" })
        }
        val errores = pedir(HttpMethod.POST, "/api/v1/finanzas/egresos/", mala).cuerpo["error"]["details"]
        assertEquals(listOf("fecha", "categoria", "monto", "comprobante"), errores.fieldNames().asSequence().toList())
        assertEquals("Sube una foto, un PDF o el XML de la factura.", errores["comprobante"][0].asText())
    }

    @Test
    fun `la caja arrastra lo que quedo del cierre anterior`() {
        assertEquals(201, pedir(HttpMethod.POST, "/api/v1/finanzas/caja/", mapOf("fecha" to hoy.minusDays(1).toString(), "contado" to "100", "retirado" to "20")).estado)
        cobroDe(null, "50.00")
        cobroDe(null, "30.00", "card")
        val caja = pedir(HttpMethod.GET, "/api/v1/finanzas/caja/").cuerpo
        assertEquals(listOf("80.00", "50.00", "0.00", "130.00", "30.00"),
            listOf("saldo_inicial", "cobros_efectivo", "egresos_efectivo", "esperado", "otros_cobros").map { caja[it].asText() })
        assertEquals(hoy.minusDays(1).toString(), caja["viene_del_cierre"].asText())

        val cierre = pedir(HttpMethod.POST, "/api/v1/finanzas/caja/", mapOf("contado" to "130.555")).cuerpo
        // La respuesta redondea lo que llegó a la par, como DRF; la base, a la mitad hacia arriba.
        assertEquals(listOf("130.56", "0.56"), listOf("contado", "diferencia").map { cierre[it].asText() })
        assertEquals(BigDecimal("130.56"), jdbc.queryForObject("select contado from finanzas_cierrecaja where fecha = ?", BigDecimal::class.java, hoy))
        assertEquals(false, pedir(HttpMethod.GET, "/api/v1/finanzas/caja/").cuerpo["desactualizado"].asBoolean())
        cobroDe(null, "5.00")
        assertEquals(true, pedir(HttpMethod.GET, "/api/v1/finanzas/caja/").cuerpo["desactualizado"].asBoolean())

        val futuro = pedir(HttpMethod.POST, "/api/v1/finanzas/caja/", mapOf("fecha" to hoy.plusDays(1).toString(), "contado" to 1))
        assertEquals(400, futuro.estado)
        assertEquals("No se cierra un día que aún no llega.", futuro.cuerpo["detail"].asText())
    }

    @Test
    fun `los errores de las vistas salen como en Django`() {
        // `raise _error(...)`: un `ValidationError({"detail": …})`, con el texto en `message`.
        val doctora = profesional("x@finanzas.ec", "Dra. X", emptyMap())
        val r = pedir(HttpMethod.POST, "/api/v1/finanzas/liquidaciones/", mapOf("profesional" to doctora.toString(), "desde" to "2026-10-31", "hasta" to "2026-10-01"))
        assertEquals(400, r.estado)
        assertEquals("""{"code":"invalid","message":"Indica el período (desde y hasta) y, si hay, un ajuste en dólares.","details":{}}""",
            r.cuerpo["error"].toString())
        // Un id que Django no entiende como UUID es el 404 a secas; uno que no está, el de User.
        assertEquals("No encontrado.", pedir(HttpMethod.GET, "/api/v1/finanzas/liquidaciones/previa/?profesional=xyz").cuerpo["error"]["message"].asText())
        assertEquals("No User matches the given query.",
            pedir(HttpMethod.GET, "/api/v1/finanzas/liquidaciones/previa/?profesional=${UUID.randomUUID()}").cuerpo["error"]["message"].asText())
        // `Response({"detail": …})` va sin el sobre.
        assertEquals("""{"detail":"El mes va como 2026-10."}""", pedir(HttpMethod.GET, "/api/v1/finanzas/resumen/?mes=2026-13").cuerpo.toString())

        sembrar.usuario("sin@finanzas.ec", "doctor", demo)
        val sin = pedir(HttpMethod.GET, "/api/v1/finanzas/resumen/", token = token("sin@finanzas.ec"))
        assertEquals(403, sin.estado)
    }
}
