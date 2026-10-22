package com.clinube

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que pide el escritorio del panel al entrar. La paridad con Django la
 * demuestra el comparador de contrato, que pregunta a los dos servidores a
 * la vez sobre los mismos datos; estas pruebas comprueban las REGLAS, las
 * mismas que prueban las de Django (apps/billing/tests.py,
 * tests_sprint22.py, inventory/tests.py, agenda/tests.py…), con el reloj
 * fijado donde la fecha importa.
 */
class EscritorioTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA
    private val vecina = ID_CLINICA_VECINA

    private fun en(dia: LocalDate, hora: Int, minuto: Int = 0) =
        dia.atTime(LocalTime.of(hora, minuto)).atZone(RelojDePrueba.GUAYAQUIL).toInstant()

    private fun token(correo: String = CORREO_ADMIN): String {
        val r = rest.postForEntity(
            "/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java,
        )
        assertEquals(200, r.statusCode.value(), "no entra $correo: ${r.body}")
        return json.readTree(r.body)["access"].asText()
    }

    /**
     * Con la dirección como `URI` ya codificada: si se pasa como texto,
     * `TestRestTemplate` la trata como plantilla y vuelve a codificar el
     * `%`, y `?search=50%25` llegaría como `50%2525`.
     */
    private fun get(ruta: String, token: String = token()): Pair<Int, JsonNode> {
        val cabeceras = HttpHeaders().apply { setBearerAuth(token) }
        val r = rest.exchange(
            java.net.URI("http://localhost:$puerto$ruta"), HttpMethod.GET, HttpEntity<Void>(cabeceras), String::class.java,
        )
        return r.statusCode.value() to json.readTree(r.body ?: "null")
    }

    private fun doctora(correo: String = "doctora@clinube.local", nombre: String = "Dra. Mora"): Pair<UUID, UUID> {
        val usuario = sembrar.usuario(correo, "doctor", demo, nombre)
        return usuario to sembrar.doctor(usuario, demo)
    }

    // ── Cumpleaños (apps/billing/tests.py) ───────────────────────────

    @Test
    fun `cumpleanos de hoy y de los proximos dias, el de hoy primero`() {
        val hoy = LocalDate.of(2026, 10, 21)
        reloj.fijarEnGuayaquil(hoy)
        sembrar.paciente(demo, "Hoy", "Cumple", "2020202020", LocalDate.of(1988, 10, 21))
        sembrar.paciente(demo, "Pronto", "Cumple", "2121212121", LocalDate.of(1984, 10, 24))
        sembrar.paciente(demo, "Lejos", "Cumple", "2222222222", LocalDate.of(1980, 1, 1))
        sembrar.paciente(demo, "Baja", "Cumple", "2323232323", LocalDate.of(1980, 10, 21), activo = false)
        sembrar.paciente(vecina, "Otra", "Clinica", "2424242424", LocalDate.of(1980, 10, 21))

        val (estado, filas) = get("/api/v1/patients/birthdays/?days=7")
        assertEquals(200, estado)
        assertEquals(listOf("Hoy Cumple", "Pronto Cumple"), filas.map { it["full_name"].asText() })
        assertTrue(filas[0]["is_today"].asBoolean())
        assertEquals(38, filas[0]["turns"].asInt())
        assertEquals(3, filas[1]["in_days"].asInt())
        assertEquals("1984-10-24", filas[1]["birth_date"].asText())
    }

    @Test
    fun `quien nacio un 29 de febrero cumple el 28 los anos no bisiestos`() {
        sembrar.paciente(demo, "Bisiesto", "Paciente", "2902290229", LocalDate.of(2000, 2, 29))
        reloj.fijarEnGuayaquil(LocalDate.of(2027, 2, 28))
        val fila = get("/api/v1/patients/birthdays/?days=0").second[0]
        assertTrue(fila["is_today"].asBoolean())
        assertEquals(27, fila["turns"].asInt())

        reloj.fijarEnGuayaquil(LocalDate.of(2028, 2, 29))          // 2028 sí es bisiesto
        val bisiesto = get("/api/v1/patients/birthdays/?days=0").second[0]
        assertEquals(0, bisiesto["in_days"].asInt())
        assertEquals(28, bisiesto["turns"].asInt())
    }

    @Test
    fun `al cruzar el fin de ano los anos que cumple son los del ano siguiente`() {
        sembrar.paciente(demo, "Enero", "Paciente", "0201020102", LocalDate.of(2000, 1, 2))
        reloj.fijarEnGuayaquil(LocalDate.of(2026, 12, 30))
        val fila = get("/api/v1/patients/birthdays/?days=7").second[0]
        assertEquals(3, fila["in_days"].asInt())
        assertEquals(27, fila["turns"].asInt())                     // cumple 27 en 2027, no 26
    }

    @Test
    fun `usa la fecha de la clinica y no la del servidor`() {
        // A las 23:30 del 15 de junio en Guayaquil ya es 16 en UTC: el
        // cumpleaños de hoy tiene que seguir siendo el del 15.
        sembrar.paciente(demo, "Hoy", "Local", "1010101010", LocalDate.of(1990, 6, 15))
        reloj.fijarEnGuayaquil(LocalDate.of(2026, 6, 15), 23, 30)
        val fila = get("/api/v1/patients/birthdays/?days=0").second[0]
        assertTrue(fila["is_today"].asBoolean())
        assertEquals(36, fila["turns"].asInt())
    }

    @Test
    fun `la ventana va de 0 a 60 dias y un valor raro es 7`() {
        val hoy = LocalDate.of(2026, 1, 1)
        reloj.fijarEnGuayaquil(hoy)
        sembrar.paciente(demo, "En", "Ocho", "1000000008", LocalDate.of(1990, 1, 9))
        sembrar.paciente(demo, "En", "Sesenta", "1000000060", LocalDate.of(1990, 3, 2))
        sembrar.paciente(demo, "En", "Setenta", "1000000070", LocalDate.of(1990, 3, 12))
        assertEquals(0, get("/api/v1/patients/birthdays/?days=abc").second.size())          // 7: ninguno
        assertEquals(2, get("/api/v1/patients/birthdays/?days=999").second.size())          // 60
        assertEquals(1, get("/api/v1/patients/birthdays/?days=%208%20").second.size())      // int(" 8 ")
        assertEquals(0, get("/api/v1/patients/birthdays/?days=-5").second.size())           // 0
    }

    // ── Sala de espera (apps/billing/tests.py) ───────────────────────

    @Test
    fun `la sala de espera muestra quien llego y no esta en atencion, hoy`() {
        val hoy = LocalDate.of(2026, 10, 21)
        reloj.fijarEnGuayaquil(hoy, 21, 0)
        val (usuarioA, doctorA) = doctora()
        val (_, doctorB) = doctora("otro@clinube.local", "")
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        val ahora = reloj.instant()

        val esperando = sembrar.cita(demo, p, doctorA, en(hoy, 10), llegada = ahora.minusSeconds(25 * 60 + 30))
        sembrar.cita(demo, p, doctorA, en(hoy, 11))                                          // no llegó
        sembrar.cita(demo, p, doctorA, en(hoy, 12), "cancelled", llegada = ahora)            // cancelada
        sembrar.cita(demo, p, doctorA, en(hoy, 13), llegada = ahora, atencion = ahora)        // en atención
        sembrar.cita(demo, p, doctorA, en(hoy.minusDays(1), 23, 59), llegada = ahora)         // ayer
        // 23:30 en Guayaquil es el día siguiente en UTC: es de hoy.
        val tarde = sembrar.cita(demo, p, doctorB, en(hoy, 23, 30), llegada = ahora.minusSeconds(60))

        val (estado, filas) = get("/api/v1/appointments/waiting/")
        assertEquals(200, estado)
        assertEquals(listOf(esperando.toString(), tarde.toString()), filas.map { it["id"].asText() })
        assertEquals(25, filas[0]["waiting_minutes"].asInt())
        assertEquals("Dra. Mora", filas[0]["doctor_name"].asText())
        assertEquals("otro@clinube.local", filas[1]["doctor_name"].asText(), "sin nombre, el correo")
        // `isoformat()` de Python: en UTC y con +00:00, no con Z.
        assertEquals("2026-10-21T15:00:00+00:00", filas[0]["scheduled_start"].asText())

        // La doctora solo ve a los suyos.
        val suyos = get("/api/v1/appointments/waiting/", token("doctora@clinube.local")).second
        assertEquals(listOf(esperando.toString()), suyos.map { it["id"].asText() })
        assertTrue(usuarioA.toString().isNotEmpty())
    }

    // ── Seguimientos (clinical/tests_sprint22.py) ────────────────────

    @Test
    fun `seguimientos vencidos y de hoy, no los futuros, como mucho 50`() {
        val hoy = LocalDate.of(2026, 10, 21)
        reloj.fijarEnGuayaquil(hoy)
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        sembrar.evolucion(demo, p, "Control en 7 días", hoy.minusDays(3))
        sembrar.evolucion(demo, p, "Futuro", hoy.plusDays(30))
        sembrar.evolucion(demo, p, "Sin seguimiento", null)
        val (estado, cuerpo) = get("/api/v1/clinical/follow-ups/")
        assertEquals(200, estado)
        assertEquals(1, cuerpo["results"].size())
        assertEquals(3, cuerpo["results"][0]["days_overdue"].asInt())

        repeat(55) { sembrar.evolucion(demo, p, "Muchos $it", hoy.minusDays(100L + it)) }
        assertEquals(50, get("/api/v1/clinical/follow-ups/").second["results"].size())
    }

    @Test
    fun `las notas se cortan en 120 caracteres contando un emoji como uno`() {
        reloj.fijarEnGuayaquil(LocalDate.of(2026, 10, 21))
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        val notas = "🦷".repeat(130)
        sembrar.evolucion(demo, p, notas, LocalDate.of(2026, 10, 21))
        val cortadas = get("/api/v1/clinical/follow-ups/").second["results"][0]["notes"].asText()
        assertEquals(120, cortadas.codePointCount(0, cortadas.length))
    }

    // ── Inventario (inventory/tests.py) ──────────────────────────────

    @Test
    fun `stock bajo suma los lotes, y un producto sin lotes tiene 0 a secas`() {
        val agujas = sembrar.producto(demo, "Agujas", "50")
        sembrar.lote(demo, agujas, "10")
        val gasa = sembrar.producto(demo, "Gasa", "5")
        sembrar.lote(demo, gasa, "30"); sembrar.lote(demo, gasa, "20")
        sembrar.producto(demo, "Guantes", "10")
        sembrar.producto(demo, "Retirado", "99", activo = false)
        sembrar.producto(vecina, "Ajeno", "1000")

        val bajos = get("/api/v1/inventory/alerts/low-stock/").second["low_stock_products"]
        val porNombre = bajos.associate { it["name"].asText() to it["current_stock"].asText() }
        assertEquals(mapOf("Agujas" to "10.00", "Guantes" to "0"), porNombre)
        assertEquals("50.00", bajos.first { it["name"].asText() == "Agujas" }["min_stock"].asText())
    }

    @Test
    fun `con el inventario apagado en la clinica, 403 con el mensaje de Django`() {
        jdbc.update("update common_tenant set modulos_clinica = '{\"inventario\": false}' where id = ?::uuid", demo)
        val (estado, cuerpo) = get("/api/v1/inventory/alerts/low-stock/")
        assertEquals(403, estado)
        assertEquals(
            "«Inventario» no está activo en esta clínica. Pídeselo al administrador de la plataforma.",
            cuerpo["error"]["message"].asText(),
        )
    }

    // ── Ingresos (billing/tests_reporte_financiero.py) ───────────────

    @Test
    fun `ingresos del periodo, desglosados, con el ultimo dia entero y sin mezclar clinicas`() {
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        val pv = sembrar.paciente(vecina, "Otra", "Clínica", "1700000002")
        sembrar.pago(demo, p, "150.50", "cash", LocalDate.of(2026, 10, 31))
        sembrar.pago(demo, p, "40", "transfer", LocalDate.of(2026, 10, 1))
        sembrar.pago(demo, p, "999.99", "card", LocalDate.of(2026, 9, 30))
        sembrar.pago(vecina, pv, "5000", "cash", LocalDate.of(2026, 10, 15))

        val (estado, cuerpo) = get("/api/v1/reports/financial/?date_from=2026-10-01&date_to=2026-10-31")
        assertEquals(200, estado)
        assertEquals("190.50", cuerpo["total_income"].asText())
        assertEquals("150.50", cuerpo["by_method"]["cash"].asText())
        assertEquals("40.00", cuerpo["by_method"]["transfer"].asText(), "siempre con dos decimales")
        assertEquals(2, cuerpo["payment_count"].asInt())

        val vacio = get("/api/v1/reports/financial/?date_from=2020-01-01&date_to=2020-01-31").second
        assertEquals("0.00", vacio["total_income"].asText(), "cero, no nulo")
        assertEquals(0, vacio["by_method"].size())
    }

    @Test
    fun `sin la funcion de reportes, 403 con el mensaje de la funcion`() {
        // La recepción de la semilla no tiene la función «reportes».
        val (estado, cuerpo) = get("/api/v1/reports/financial/", token("recepcion@clinube.local"))
        assertEquals(403, estado)
        assertEquals("No tienes asignada esta función en la clínica.", cuerpo["error"]["message"].asText())
    }

    // ── Morosidad ────────────────────────────────────────────────────

    @Test
    fun `morosidad agrupa por paciente, descuenta los pagos y marca el bloqueo`() {
        val hoy = LocalDate.of(2026, 10, 21)
        reloj.fijarEnGuayaquil(hoy)
        val pedro = sembrar.paciente(demo, "Pedro", "Ruiz", "1700000001")
        val lucia = sembrar.paciente(demo, "Lucía", "Torres", "1700000002")
        val (vieja, _, _) = sembrar.cuotas(
            demo, pedro,
            Triple(hoy.minusDays(40), "100.00", "pending"),
            Triple(hoy.minusDays(10), "100.00", "overdue"),
            Triple(hoy.plusDays(20), "100.00", "pending"),
        )
        sembrar.pago(demo, pedro, "30.00", "cash", hoy.minusDays(30), vieja)
        sembrar.cuotas(demo, lucia, Triple(hoy.minusDays(5), "50.00", "paid"), Triple(hoy.minusDays(2), "80.00", "pending"))

        val (estado, cuerpo) = get("/api/v1/reports/delinquency/")
        assertEquals(200, estado)
        assertEquals(30, cuerpo["delinquency_threshold_days"].asInt())
        val pacientes = cuerpo["patients"]
        assertEquals(listOf("Pedro Ruiz", "Lucía Torres"), pacientes.map { it["patient_name"].asText() })
        assertEquals("170.00", pacientes[0]["overdue_amount"].asText())
        assertEquals(2, pacientes[0]["installment_count"].asInt())
        assertEquals(40, pacientes[0]["days_overdue"].asInt())
        assertTrue(pacientes[0]["is_blocking"].asBoolean())
        assertFalse(pacientes[1]["is_blocking"].asBoolean())

        // El umbral es un parámetro de la clínica.
        sembrar.parametro(demo, "dias_morosidad", "50")
        assertEquals(50, get("/api/v1/reports/delinquency/").second["delinquency_threshold_days"].asInt())
    }

    // ── Pacientes (patients/tests.py) ────────────────────────────────

    @Test
    fun `la lista busca por cedula, escapa los comodines y no mezcla clinicas`() {
        sembrar.paciente(demo, "Juan", "Gómez", "1717171717")
        sembrar.paciente(demo, "Iván", "50% Descuento", "1818181818")
        sembrar.paciente(demo, "Otro", "Cualquiera", "1919191919")
        sembrar.paciente(demo, "De", "Baja", "1717170000", activo = false)
        sembrar.paciente(vecina, "Juan", "Vecino", "1717179999")

        assertEquals(1, get("/api/v1/patients/?search=1717").second["count"].asInt())
        // «%» busca un «%», no «cualquier cosa».
        assertEquals(1, get("/api/v1/patients/?search=50%25").second["count"].asInt())
        assertEquals(0, get("/api/v1/patients/?search=_").second["count"].asInt())
        assertEquals(1, get("/api/v1/patients/?search=G%C3%93MEZ").second["count"].asInt(), "sin distinguir mayúsculas")
    }

    @Test
    fun `la lista pagina de 20, cuenta atenciones y deja sin proxima cita al final`() {
        reloj.fijarEnGuayaquil(LocalDate.of(2026, 10, 21))
        val (_, doctor) = doctora()
        val ids = (1..22).map { sembrar.paciente(demo, "Nombre$it", "Apellido%02d".format(it), "17%08d".format(it)) }
        sembrar.cita(demo, ids[21], doctor, en(LocalDate.of(2026, 10, 25), 9))
        sembrar.cita(demo, ids[21], doctor, en(LocalDate.of(2026, 9, 1), 9), "completed")
        sembrar.cita(demo, ids[20], doctor, en(LocalDate.of(2026, 10, 22), 9))

        val (_, primera) = get("/api/v1/patients/")
        assertEquals(22, primera["count"].asInt())
        assertEquals(20, primera["results"].size())
        assertTrue(primera["next"].asText().endsWith("/api/v1/patients/?page=2"))
        assertNull(primera["previous"].takeUnless { it.isNull })

        val (_, proximas) = get("/api/v1/patients/?ordering=next_appt_asc")
        val nombres = proximas["results"].map { it["full_name"].asText() }
        assertEquals(listOf("Nombre21 Apellido21", "Nombre22 Apellido22"), nombres.take(2))
        assertEquals(2, proximas["results"][1]["attention_count"].asInt())
        assertTrue(proximas["results"][2]["next_appointment"].isNull)

        assertEquals(404, get("/api/v1/patients/?page=3").first)
    }

    // ── Agenda (agenda/tests.py) ─────────────────────────────────────

    @Test
    fun `la agenda del dia incluye las 23 30 y no la medianoche de ayer ni la de manana`() {
        val hoy = LocalDate.of(2026, 10, 21)
        reloj.fijarEnGuayaquil(hoy, 22, 0)
        val (_, doctor) = doctora()
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        sembrar.cita(demo, p, doctor, en(hoy.minusDays(1), 23, 59))
        val temprano = sembrar.cita(demo, p, doctor, en(hoy, 0, 0))
        val tarde = sembrar.cita(demo, p, doctor, en(hoy, 23, 30))
        sembrar.cita(demo, p, doctor, en(hoy.plusDays(1), 0, 0))

        val (estado, cuerpo) = get("/api/v1/agenda/view/?mode=daily")
        assertEquals(200, estado)
        assertEquals(listOf(temprano.toString(), tarde.toString()), cuerpo["results"].map { it["id"].asText() })
        assertEquals("Confirmada", cuerpo["results"][0]["status_display"].asText())
        assertEquals("2026-10-21T23:30:00-05:00", cuerpo["results"][1]["scheduled_start"].asText())
    }

    @Test
    fun `la semana va de lunes a domingo y la doctora solo ve las suyas`() {
        val (_, mia) = doctora()
        val (_, otra) = doctora("otro@clinube.local", "Dr. Otro")
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        // 2026-10-21 es miércoles: la semana va del lunes 19 al domingo 25.
        val lunes = sembrar.cita(demo, p, mia, en(LocalDate.of(2026, 10, 19), 8))
        val domingo = sembrar.cita(demo, p, mia, en(LocalDate.of(2026, 10, 25), 20))
        sembrar.cita(demo, p, mia, en(LocalDate.of(2026, 10, 26), 8))
        sembrar.cita(demo, p, otra, en(LocalDate.of(2026, 10, 20), 8))

        val todas = get("/api/v1/agenda/view/?mode=weekly&date=2026-10-21").second
        assertEquals(3, todas["count"].asInt())
        val suyas = get("/api/v1/agenda/view/?mode=weekly&date=2026-10-21", token("doctora@clinube.local")).second
        assertEquals(listOf(lunes.toString(), domingo.toString()), suyas["results"].map { it["id"].asText() })
    }

    // ── Marca (configuration/tests.py) y bandeja ─────────────────────

    @Test
    fun `la marca se crea al leerla y cada clinica tiene la suya`() {
        val (estado, marca) = get("/api/v1/config/branding/")
        assertEquals(200, estado)
        assertEquals("default", marca["theme"]["preset"].asText())
        assertEquals("Clínica Demo", marca["nombre_clinica"].asText(), "sin nombre comercial, el del alta")
        assertTrue(marca["logo_url"].isNull)
        assertEquals(1, jdbc.queryForObject("select count(*) from configuration_clinicbranding", Int::class.java))

        jdbc.update("update configuration_clinicbranding set theme = '{\"preset\": \"custom\"}', display_name = ' Sonrisas '")
        val (_, otra) = get("/api/v1/config/branding/", token("vecina@clinube.local"))
        assertEquals("default", otra["theme"]["preset"].asText())
        assertEquals("Sonrisas", get("/api/v1/config/branding/").second["nombre_clinica"].asText())
    }

    @Test
    fun `la bandeja cuenta lo pendiente de la clinica`() {
        val p = sembrar.paciente(demo, "Ana", "López", "1700000001")
        sembrar.solicitud(demo, p, "pendiente")
        sembrar.solicitud(demo, p, "agendada")
        sembrar.mensaje(demo, p, respondido = false)
        sembrar.mensaje(demo, p, respondido = true)
        val (estado, resumen) = get("/api/v1/bandeja-app/resumen/")
        assertEquals(200, estado)
        assertEquals(1, resumen["solicitudes_pendientes"].asInt())
        assertEquals(1, resumen["mensajes_sin_responder"].asInt())
    }

    @Test
    fun `un paciente no ve nada del escritorio`() {
        // El paciente no entra por el panel, pero si tuviera un token de
        // acceso, ninguna de estas rutas es para él.
        val ahora = java.time.Instant.now().epochSecond
        val tokenPaciente = com.clinube.comun.jwt.Jwt.firmar(
            linkedMapOf("token_type" to "access", "exp" to ahora + 600, "iat" to ahora, "jti" to "0".repeat(32),
                        "user_id" to "f34d4a21-4d5b-4cfa-8a9e-9048f36a6e8e", "role" to "patient", "tenant_id" to demo),
            "dev-only-insecure-key-change-me-never-use-in-production",
        )
        listOf("/api/v1/patients/", "/api/v1/agenda/view/", "/api/v1/config/branding/", "/api/v1/appointments/waiting/")
            .forEach { assertEquals(403, get(it, tokenPaciente).first, it) }
    }
}
