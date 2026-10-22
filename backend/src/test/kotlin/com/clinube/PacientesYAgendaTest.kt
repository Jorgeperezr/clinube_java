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
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Las reglas de la ficha del paciente y de la agenda, portadas de
 * apps/patients/tests.py, apps/agenda/tests.py y tests_morosidad.py. La
 * paridad de las respuestas y del estado de la base la comprueba el
 * comparador de contrato; aquí, que las reglas se cumplen.
 */
class PacientesYAgendaTest : PruebaDeIntegracion() {

    private val json = ObjectMapper()
    private val demo = ID_CLINICA
    private val hoy = LocalDate.of(2026, 10, 21)

    private data class Respuesta(val estado: Int, val cuerpo: JsonNode, val texto: String, val tipo: String?)

    private fun token(correo: String = CORREO_ADMIN): String {
        val r = rest.postForEntity("/api/v1/auth/login/", mapOf("email" to correo, "password" to CLAVE), String::class.java)
        return json.readTree(r.body)["access"].asText()
    }

    private fun pedir(metodo: HttpMethod, ruta: String, token: String? = token(), cuerpo: Any? = null): Respuesta {
        val cabeceras = HttpHeaders().apply {
            token?.let { setBearerAuth(it) }
            if (cuerpo != null) contentType = MediaType.APPLICATION_JSON
        }
        val r = rest.exchange(
            URI("http://localhost:$puerto$ruta"), metodo,
            HttpEntity(cuerpo?.let { json.writeValueAsString(it) }, cabeceras), String::class.java,
        )
        val texto = r.body ?: ""
        val nodo = runCatching { json.readTree(texto.ifEmpty { "null" }) }.getOrElse { json.nullNode() }
        return Respuesta(r.statusCode.value(), nodo, texto, r.headers.contentType?.toString())
    }

    private fun en(dia: LocalDate, hora: Int, minuto: Int = 0) =
        dia.atTime(LocalTime.of(hora, minuto)).atZone(RelojDePrueba.GUAYAQUIL).toInstant()

    private fun iso(dia: LocalDate, hora: Int, minuto: Int = 0) = en(dia, hora, minuto).toString()

    private fun doctor(correo: String, clinica: String = demo, nombre: String = "Dr. $correo"): Pair<UUID, UUID> {
        val usuario = sembrar.usuario(correo, "doctor", clinica, nombre)
        return usuario to sembrar.doctor(usuario, clinica)
    }

    // ── Pacientes (apps/patients/tests.py) ───────────────────────────

    @Test
    fun `recepcion da de alta, la cedula no se repite y el doctor no puede`() {
        val recepcion = token("recepcion@clinube.local")
        val alta = pedir(HttpMethod.POST, "/api/v1/patients/", recepcion,
            mapOf("first_name" to " Ana ", "last_name" to "Ruiz", "national_id" to " 1818181818 "))
        assertEquals(201, alta.estado, alta.texto)
        assertEquals("Ana", alta.cuerpo["first_name"].asText(), "se recortan los espacios")
        assertEquals("1818181818", alta.cuerpo["national_id"].asText())
        assertTrue(alta.cuerpo["agreement_name"].isNull)
        assertEquals("", jdbc.queryForObject("select photo from patients_patient where national_id = '1818181818'", String::class.java),
            "la foto, vacía y no nula, como la deja Django")

        val repetida = pedir(HttpMethod.POST, "/api/v1/patients/", recepcion,
            mapOf("first_name" to "Otra", "last_name" to "Ruiz", "national_id" to "1818181818"))
        assertEquals(400, repetida.estado)
        assertEquals("Ya existe un paciente activo con esta identificación.",
            repetida.cuerpo["error"]["details"]["national_id"][0].asText())

        doctor("doctora@clinube.local")
        val delDoctor = pedir(HttpMethod.POST, "/api/v1/patients/", token("doctora@clinube.local"),
            mapOf("first_name" to "X", "last_name" to "Y", "national_id" to "0999999999"))
        assertEquals(403, delDoctor.estado)
    }

    @Test
    fun `los errores de validacion salen todos, en el orden del serializador`() {
        val r = pedir(HttpMethod.POST, "/api/v1/patients/", cuerpo = mapOf(
            "first_name" to "x".repeat(101), "last_name" to "", "email" to "no-es-correo",
            "birth_date" to "", "address" to null,
        ))
        assertEquals(400, r.estado)
        assertEquals(listOf("first_name", "last_name", "national_id", "birth_date", "email", "address"),
            r.cuerpo["error"]["details"].fieldNames().asSequence().toList())
        assertEquals("Asegúrese de que este campo no tenga más de 100 caracteres.",
            r.cuerpo["error"]["details"]["first_name"][0].asText())
    }

    @Test
    fun `un PATCH que deja sin convenio no devuelve agreement_name, un GET si`() {
        val convenio = UUID.randomUUID()
        jdbc.update("""insert into configuration_agreement (id, created_at, updated_at, name, discount_percentage, is_active, tenant_id)
                       values (?, now(), now(), 'Seguro', 10, true, ?::uuid)""", convenio, demo)
        val id = sembrar.paciente(demo, "Ana", "López", "1700000001")
        jdbc.update("update patients_patient set agreement_id = ? where id = ?", convenio, id)

        val parche = pedir(HttpMethod.PATCH, "/api/v1/patients/$id/", cuerpo = mapOf("agreement" to null))
        assertEquals(200, parche.estado)
        assertFalse(parche.cuerpo.has("agreement_name"), "en un PATCH, DRF salta el valor por defecto")
        val ficha = pedir(HttpMethod.GET, "/api/v1/patients/$id/")
        assertTrue(ficha.cuerpo.has("agreement_name") && ficha.cuerpo["agreement_name"].isNull)
    }

    @Test
    fun `la baja es logica, no cambia updated_at y la ficha deja de verse`() {
        val id = sembrar.paciente(demo, "Ana", "López", "1700000001")
        val antes = jdbc.queryForObject("select updated_at from patients_patient where id = ?", java.sql.Timestamp::class.java, id)
        val baja = pedir(HttpMethod.DELETE, "/api/v1/patients/$id/", token("recepcion@clinube.local"))
        assertEquals(204, baja.estado)
        val fila = jdbc.queryForMap("select is_active, deleted_at, updated_at from patients_patient where id = ?", id)
        assertEquals(false, fila["is_active"])
        assertTrue(fila["deleted_at"] != null)
        assertEquals(antes, fila["updated_at"])
        val ficha = pedir(HttpMethod.GET, "/api/v1/patients/$id/")
        assertEquals(404, ficha.estado)
        assertEquals("http404", ficha.cuerpo["error"]["code"].asText())
        assertEquals("No Patient matches the given query.", ficha.cuerpo["error"]["message"].asText())
    }

    @Test
    fun `la ficha de otra clinica no existe`() {
        val ajeno = sembrar.paciente(ID_CLINICA_VECINA, "Luis", "Mora", "1")
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/patients/$ajeno/").estado)
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/patients/$ajeno/medical-background/").estado)
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/patients/$ajeno/app-access/").estado)
    }

    @Test
    fun `antecedentes, recepcion no y cada acceso queda auditado aunque los datos esten mal`() {
        val id = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        assertEquals(403, pedir(HttpMethod.GET, "/api/v1/patients/$id/medical-background/", token("recepcion@clinube.local")).estado)

        doctor("doctora@clinube.local")
        val doctora = token("doctora@clinube.local")
        val leido = pedir(HttpMethod.GET, "/api/v1/patients/$id/medical-background/", doctora)
        assertEquals(200, leido.estado)
        assertTrue(leido.cuerpo["is_pregnant"].isNull)

        val malo = pedir(HttpMethod.PUT, "/api/v1/patients/$id/medical-background/", doctora, mapOf("is_pregnant" to "quizá"))
        assertEquals(400, malo.estado)
        assertEquals("Debe ser un booleano válido.", malo.cuerpo["error"]["details"]["is_pregnant"][0].asText())

        val bueno = pedir(HttpMethod.PATCH, "/api/v1/patients/$id/medical-background/", doctora,
            mapOf("allergies" to "Penicilina", "is_pregnant" to "y"))
        assertEquals("Penicilina", bueno.cuerpo["allergies"].asText())
        assertTrue(bueno.cuerpo["is_pregnant"].asBoolean())

        // La regresión que cazó la comparación del estado: el PUT con datos
        // malos también deja su fila, como en Django.
        val acciones = jdbc.queryForList(
            "select action from accounts_auditlog where entity_type = 'MedicalBackground' order by created_at", String::class.java,
        )
        assertEquals(listOf("view_medical_background", "update_medical_background", "update_medical_background"), acciones)
    }

    @Test
    fun `aviso de privacidad, 201 la primera vez y 200 despues, sin duplicar`() {
        val id = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        assertFalse(pedir(HttpMethod.GET, "/api/v1/patients/$id/aviso-privacidad/").cuerpo["al_dia"].asBoolean())
        assertEquals(400, pedir(HttpMethod.POST, "/api/v1/patients/$id/aviso-privacidad/", cuerpo = mapOf("medio" to "paloma")).estado)
        val primera = pedir(HttpMethod.POST, "/api/v1/patients/$id/aviso-privacidad/", cuerpo = emptyMap<String, Any>())
        assertEquals(201, primera.estado)
        assertTrue(primera.cuerpo["entregas"][0]["fecha"].asText().endsWith("Z"), "la fecha va en UTC con Z")
        assertEquals(200, pedir(HttpMethod.POST, "/api/v1/patients/$id/aviso-privacidad/", cuerpo = emptyMap<String, Any>()).estado)
        assertEquals(1, jdbc.queryForObject("select count(*) from patients_avisoprivacidadentregado", Int::class.java))
    }

    @Test
    fun `el acceso a la app normaliza el telefono de la ficha`() {
        val id = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818", telefono = "099 123 4567")
        val acceso = pedir(HttpMethod.GET, "/api/v1/patients/$id/app-access/").cuerpo
        assertFalse(acceso["activo"].asBoolean())
        assertEquals("+593991234567", acceso["telefono_ficha"].asText())
        assertEquals("correo", acceso["canal"].asText())
    }

    // ── Agenda (apps/agenda/tests.py) ────────────────────────────────

    @Test
    fun `recepcion agenda, no se cruzan citas ni se agenda en el pasado`() {
        reloj.fijarEnGuayaquil(hoy, 12, 0)
        val (_, doc) = doctor("doctora@clinube.local")
        val p = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        val recepcion = token("recepcion@clinube.local")
        val dia = hoy.plusDays(4)

        val cita = pedir(HttpMethod.POST, "/api/v1/appointments/", recepcion, mapOf(
            "patient" to p, "doctor" to doc, "scheduled_start" to iso(dia, 10), "scheduled_end" to iso(dia, 10, 30)))
        assertEquals(201, cita.estado, cita.texto)
        assertEquals("pending", cita.cuerpo["status"].asText())

        val cruzada = pedir(HttpMethod.POST, "/api/v1/appointments/", recepcion, mapOf(
            "patient" to p, "doctor" to doc, "scheduled_start" to iso(dia, 10, 15), "scheduled_end" to iso(dia, 10, 45)))
        assertEquals("El doctor ya tiene una cita en ese horario.", cruzada.cuerpo["error"]["details"]["non_field_errors"][0].asText())

        val pasada = pedir(HttpMethod.POST, "/api/v1/appointments/", recepcion, mapOf(
            "patient" to p, "doctor" to doc, "scheduled_start" to iso(hoy, 9), "scheduled_end" to iso(hoy, 9, 30)))
        assertEquals("No se puede agendar una cita en el pasado.", pasada.cuerpo["error"]["details"]["non_field_errors"][0].asText())

        // Sin zona, la hora es de la clínica.
        val local = pedir(HttpMethod.POST, "/api/v1/appointments/", recepcion, mapOf(
            "patient" to p, "doctor" to doc, "scheduled_start" to "${dia}T16:00", "scheduled_end" to "$dia 16:30:00.1234567"))
        assertEquals("${dia}T16:00:00-05:00", local.cuerpo["scheduled_start"].asText())
        assertEquals("${dia}T16:30:00.123456-05:00", local.cuerpo["scheduled_end"].asText(), "decimales truncados a seis")
    }

    @Test
    fun `un doctor de otra clinica no vale, aunque Django lo acepte`() {
        // Diferencia DELIBERADA con Django (docs/DIFERENCIAS.md): allí la cita
        // se crea con el doctor de otra clínica y la respuesta trae su nombre.
        reloj.fijarEnGuayaquil(hoy)
        val (_, ajeno) = doctor("vecino@clinube.local", ID_CLINICA_VECINA)
        val p = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        val r = pedir(HttpMethod.POST, "/api/v1/appointments/", cuerpo = mapOf(
            "patient" to p, "doctor" to ajeno, "scheduled_start" to iso(hoy.plusDays(3), 10), "scheduled_end" to iso(hoy.plusDays(3), 11)))
        assertEquals(400, r.estado)
        assertEquals("Clave primaria \"$ajeno\" inválida - objeto no existe.", r.cuerpo["error"]["details"]["doctor"][0].asText())
    }

    @Test
    fun `el flujo de atencion y que las acciones no tocan updated_at`() {
        reloj.fijarEnGuayaquil(hoy, 9, 0)
        val (_, doc) = doctor("doctora@clinube.local")
        val p = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        val cita = sembrar.cita(demo, p, doc, en(hoy, 10))
        jdbc.update("update agenda_appointment set updated_at = '2026-01-01 00:00:00+00' where id = ?", cita)

        assertEquals("El paciente aún no hizo check-in (botón Llegó).",
            pedir(HttpMethod.POST, "/api/v1/appointments/$cita/start/").cuerpo["detail"].asText())
        assertEquals("confirmed", pedir(HttpMethod.POST, "/api/v1/appointments/$cita/confirm/").cuerpo["status"].asText())
        assertFalse(pedir(HttpMethod.POST, "/api/v1/appointments/$cita/checkin/").cuerpo["checkin_at"].isNull)
        assertFalse(pedir(HttpMethod.POST, "/api/v1/appointments/$cita/start/").cuerpo["attention_started_at"].isNull)
        assertEquals("La atención ya fue iniciada.", pedir(HttpMethod.POST, "/api/v1/appointments/$cita/start/").cuerpo["detail"].asText())
        sembrar.usuario("auxiliar@clinube.local", "auxiliary", demo)
        assertEquals(403, pedir(HttpMethod.POST, "/api/v1/appointments/$cita/checkout/", token("auxiliar@clinube.local")).estado)
        val fin = pedir(HttpMethod.POST, "/api/v1/appointments/$cita/checkout/", token("recepcion@clinube.local"))
        assertEquals("completed", fin.cuerpo["status"].asText())

        assertEquals(
            java.time.Instant.parse("2026-01-01T00:00:00Z"),
            jdbc.queryForObject("select updated_at from agenda_appointment where id = ?", java.sql.Timestamp::class.java, cita)!!.toInstant(),
            "las acciones guardan solo lo que cambian",
        )
        assertEquals("Cita no encontrada.",
            pedir(HttpMethod.POST, "/api/v1/appointments/${UUID.randomUUID()}/start/").cuerpo["detail"].asText())
    }

    @Test
    fun `reagendar encima de otra cita es un 409 con detail`() {
        reloj.fijarEnGuayaquil(hoy)
        val (_, doc) = doctor("doctora@clinube.local")
        val p = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        val dia = hoy.plusDays(2)
        sembrar.cita(demo, p, doc, en(dia, 10))
        val otra = sembrar.cita(demo, p, doc, en(dia, 13))
        val r = pedir(HttpMethod.POST, "/api/v1/appointments/$otra/reschedule/", cuerpo = mapOf(
            "new_start" to iso(dia, 10), "new_end" to iso(dia, 10, 30)))
        assertEquals(409, r.estado)
        assertEquals("El doctor ya tiene una cita en ese horario.", r.cuerpo["detail"].asText())
        val bien = pedir(HttpMethod.POST, "/api/v1/appointments/$otra/reschedule/", cuerpo = mapOf(
            "new_start" to iso(dia, 15), "new_end" to iso(dia, 15, 30)))
        assertEquals("rescheduled", bien.cuerpo["status"].asText())
    }

    @Test
    fun `el doctor solo ve su agenda en la lista de citas`() {
        val (_, mio) = doctor("doctora@clinube.local")
        val (_, otro) = doctor("otro@clinube.local")
        val p = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        sembrar.cita(demo, p, mio, en(hoy, 10))
        sembrar.cita(demo, p, otro, en(hoy, 12))
        assertEquals(1, pedir(HttpMethod.GET, "/api/v1/appointments/", token("doctora@clinube.local")).cuerpo["count"].asInt())
        assertEquals(2, pedir(HttpMethod.GET, "/api/v1/appointments/").cuerpo["count"].asInt())
        val filtros = pedir(HttpMethod.GET, "/api/v1/appointments/?doctor=abc&status=raro").cuerpo["error"]["details"]
        assertEquals("“abc” no es un UUID válido.", filtros["doctor"][0].asText())
        assertEquals("Escoja una opción válida. raro no es una de las opciones disponibles.", filtros["status"][0].asText())
    }

    @Test
    fun `el calendario de cada doctor, sin sesion por su token y solo para el`() {
        val (_, mio) = doctor("doctora@clinube.local", nombre = "Dra. Mora, Elena")
        val (_, otro) = doctor("otro@clinube.local")
        val p = sembrar.paciente(demo, "Ana", "Ruiz", "1818181818")
        sembrar.cita(demo, p, mio, en(hoy, 10), "cancelled")
        val token = jdbc.queryForObject("select calendar_token from agenda_doctor where id = ?", UUID::class.java, mio)

        val ics = pedir(HttpMethod.GET, "/api/v1/calendar/$token.ics", token = null)
        assertEquals(200, ics.estado)
        assertTrue(ics.tipo!!.startsWith("text/calendar"))
        assertTrue("X-WR-CALNAME:Citas — Dra. Mora\\, Elena\r\n" in ics.texto, "las comas se escapan")
        assertTrue("STATUS:CANCELLED" in ics.texto)
        assertTrue("SUMMARY:Cita: Ana Ruiz" in ics.texto)
        assertEquals(404, pedir(HttpMethod.GET, "/api/v1/calendar/${UUID.randomUUID()}.ics", token = null).estado)

        val doctora = token("doctora@clinube.local")
        assertEquals(200, pedir(HttpMethod.GET, "/api/v1/doctors/$mio/calendar-url/", doctora).estado)
        val ajena = pedir(HttpMethod.GET, "/api/v1/doctors/$otro/calendar-url/", doctora)
        assertEquals(403, ajena.estado)
        assertEquals("Solo puedes ver la URL de tu propio calendario.", ajena.cuerpo["detail"].asText())
    }

    // ── Morosidad (apps/agenda/tests_morosidad.py) ───────────────────

    @Test
    fun `el moroso no se agenda salvo con override, y una deuda reciente o pagada no bloquea`() {
        reloj.fijarEnGuayaquil(hoy)
        val (_, doc) = doctor("doctora@clinube.local")
        val moroso = sembrar.paciente(demo, "Pedro", "Moroso", "1")
        val reciente = sembrar.paciente(demo, "Rosa", "Reciente", "2")
        val pagado = sembrar.paciente(demo, "Luis", "Pagado", "3")
        sembrar.cuotas(demo, moroso, Triple(hoy.minusDays(45), "100.00", "pending"))
        sembrar.cuotas(demo, reciente, Triple(hoy.minusDays(10), "100.00", "overdue"))
        sembrar.cuotas(demo, pagado, Triple(hoy.minusDays(60), "100.00", "paid"))
        val dia = hoy.plusDays(5)
        fun agendar(p: UUID, hora: Int, extra: Map<String, Any> = emptyMap()) = pedir(HttpMethod.POST, "/api/v1/appointments/",
            cuerpo = mapOf("patient" to p, "doctor" to doc, "scheduled_start" to iso(dia, hora), "scheduled_end" to iso(dia, hora, 30)) + extra)

        val bloqueada = agendar(moroso, 9)
        assertEquals(409, bloqueada.estado)
        assertEquals("patient_delinquent", bloqueada.cuerpo["error"]["code"].asText())
        assertFalse(bloqueada.cuerpo["error"].has("details"), "esta vista arma su propio error, sin details")
        assertEquals(201, agendar(moroso, 10, mapOf("override" to true)).estado)
        assertEquals(201, agendar(reciente, 11).estado)
        assertEquals(201, agendar(pagado, 12).estado)
    }

    @Test
    fun `doctores activos, ordenados, con el correo si no tienen nombre`() {
        doctor("zeta@clinube.local", nombre = "Zeta")
        doctor("sin@clinube.local", nombre = "")
        val (_, baja) = doctor("baja@clinube.local", nombre = "Alfa")
        jdbc.update("update agenda_doctor set is_active = false where id = ?", baja)
        val lista = pedir(HttpMethod.GET, "/api/v1/doctors/").cuerpo["results"]
        assertEquals(listOf("sin@clinube.local", "Zeta"), lista.map { it["full_name"].asText() })
        assertNull(lista[0]["specialties"].firstOrNull())
    }
}
