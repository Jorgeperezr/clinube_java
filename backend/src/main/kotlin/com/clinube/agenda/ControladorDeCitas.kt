package com.clinube.agenda

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.cobros.ControladorDeReportes
import com.clinube.cuentas.dominio.Rol
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Las citas y los doctores (`apps/agenda`): lista, alta, ficha, edición,
 * las acciones del flujo de atención y el calendario de cada doctor.
 *
 * Quién puede qué, como en Django: VER la agenda, todo el personal;
 * gestionarla (crear, mover, confirmar, cancelar, marcar la llegada,
 * iniciar la atención), quien tenga la FUNCIÓN «agenda»; finalizar,
 * administración, recepción y doctores.
 */
@RestController
class ControladorDeCitas(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val citas: RepresentacionDeCitas,
    private val json: com.fasterxml.jackson.databind.ObjectMapper,
) {

    companion object {
        const val ID = "{id:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}"
        val GESTIONAN = Permisos.funcion("agenda")

        const val CRUZADA = "El doctor ya tiene una cita en ese horario."
        const val FIN_ANTES = "La hora de fin debe ser posterior a la de inicio."
        const val EN_EL_PASADO = "No se puede agendar una cita en el pasado."

        private val FORMATO_ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }

    private val clinica get() = contexto.clinica?.id

    // ── Doctores ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/doctors/ — los doctores activos, paginados y ordenados por
     * nombre y correo. El orden no es cosmético: sin un orden estable,
     * paginar es quedarse a merced de lo que devuelva la base.
     */
    @GetMapping("/api/v1/doctors/")
    fun doctores(@RequestParam(required = false) page: String?, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(Permisos.PERSONAL)
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        val total = jdbc.queryForObject(
            "select count(*) from agenda_doctor where tenant_id = :clinica and is_active", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(page, total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        // Las especialidades van en la misma consulta (json_agg), no una
        // consulta por doctor.
        val doctores = jdbc.query(
            """select d.id, d.user_id, d.calendar_color, d.is_active, u.email, u.full_name,
                      coalesce((select json_agg(s.specialty_id::text) from agenda_doctor_specialties s
                                where s.doctor_id = d.id), '[]'::json)::text as especialidades
               from agenda_doctor d join accounts_user u on u.id = d.user_id
               where d.tenant_id = :clinica and d.is_active
               order by u.full_name, u.email limit :cuantos offset :desde""",
            parametros,
        ) { f, _ ->
            linkedMapOf<String, Any?>(
                "id" to f.uuid("id").toString(),
                "user" to f.uuid("user_id").toString(),
                "email" to f.getString("email"),
                "full_name" to RepresentacionDeCitas.nombreDelDoctor(f.getString("full_name"), f.getString("email"), f.uuid("user_id")),
                "specialties" to json.readTree(f.getString("especialidades")),
                "calendar_color" to f.getString("calendar_color"),
                "is_active" to f.getBoolean("is_active"),
            )
        }
        return Pagina.de(total, doctores, pagina, peticion)
    }

    // ── Citas: lista y alta ──────────────────────────────────────────

    /**
     * GET /api/v1/appointments/ — RF-AGN-03. Filtros de `django-filter`
     * (`?doctor=`, `?status=`, `?patient=`), con sus mensajes de error, y
     * por fecha (`?date_from=`, `?date_to=`, días de la clínica). Un doctor
     * solo ve su agenda.
     */
    @GetMapping("/api/v1/appointments/")
    fun lista(
        @RequestParam(required = false) doctor: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) patient: String?,
        @RequestParam(name = "date_from", required = false) desde: String?,
        @RequestParam(name = "date_to", required = false) hasta: String?,
        @RequestParam(required = false) page: String?,
        peticion: HttpServletRequest,
    ): Pagina<Map<String, Any?>> {
        val usuario = permisos.exigir(Permisos.PERSONAL)
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        var filtro = "a.tenant_id = :clinica"

        // Los filtros se validan todos y los errores salen juntos, en el
        // orden de `filterset_fields` (doctor, status, patient). El doctor
        // y el paciente valen de CUALQUIER clínica para django-filter (su
        // queryset no filtra), pero el filtro por clínica de arriba hace
        // que uno ajeno no devuelva nada.
        val errores = LinkedHashMap<String, List<String>>()
        fun filtroPorClave(nombre: String, valor: String?, tabla: String, columna: String) {
            if (valor.isNullOrEmpty()) return
            val id = CamposDrf.uuidDePython(valor)
            when {
                id == null -> errores[nombre] = listOf(MensajesDeDjango.uuidInvalido(valor))
                jdbc.queryForObject("select count(*) from $tabla where id = :id", mapOf("id" to id), Long::class.java)!! == 0L ->
                    errores[nombre] = listOf(MensajesDeDjango.OPCION_INEXISTENTE)
                else -> { parametros[nombre] = id; filtro += " and a.$columna = :$nombre" }
            }
        }
        filtroPorClave("doctor", doctor, "agenda_doctor", "doctor_id")
        if (!status.isNullOrEmpty()) {
            if (status !in RepresentacionDeCitas.ESTADOS) errores["status"] = listOf(MensajesDeDjango.opcionNoValida(status))
            else { parametros["status"] = status; filtro += " and a.status = :status" }
        }
        filtroPorClave("patient", patient, "patients_patient", "patient_id")
        if (errores.isNotEmpty()) throw ExcepcionApi.validacion(errores)

        if (usuario.rol == Rol.DOCTOR) { parametros["usuario"] = usuario.id; filtro += " and d.user_id = :usuario" }
        if (!desde.isNullOrEmpty()) {
            parametros["desdeDia"] = Timestamp.from(zona.inicioDelDia(ControladorDeReportes.fechaDeDjango(desde)))
            filtro += " and a.scheduled_start >= :desdeDia"
        }
        if (!hasta.isNullOrEmpty()) {
            parametros["hastaDia"] = Timestamp.from(zona.inicioDelDia(ControladorDeReportes.fechaDeDjango(hasta).plusDays(1)))
            filtro += " and a.scheduled_start < :hastaDia"
        }
        val total = jdbc.queryForObject(
            "select count(*) from agenda_appointment a join agenda_doctor d on d.id = a.doctor_id where $filtro",
            parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(page, total)
        parametros["desdeFila"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            "${RepresentacionDeCitas.SELECT} where $filtro order by a.scheduled_start limit :cuantos offset :desdeFila",
            parametros,
        ) { f, _ -> citas.deFila(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/appointments/ — crea la cita. Después de validar, el
     * bloqueo por morosidad (RF-AGN-07): un paciente con cuotas vencidas
     * más allá del umbral no se agenda, salvo `override=true`. La regla vive
     * en el servidor para que valga igual desde la web o la app.
     */
    @PostMapping("/api/v1/appointments/")
    @Transactional
    fun alta(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        val usuario = permisos.exigir(GESTIONAN)
        val datos = CuerpoDrf.objeto(cuerpo)
        val cita = leerCita(datos, parcial = false, actual = null)
        val paciente = cita.getValue("patient_id") as UUID

        val forzar = datos.get("override")?.let { v ->
            val texto = when {
                v.isBoolean -> if (v.booleanValue()) "true" else "false"
                v.isNull -> "none"
                else -> v.asText()
            }
            texto.lowercase() in setOf("true", "1", "yes")
        } ?: false
        if (!forzar && esMoroso(paciente)) {
            // Forma propia de esta vista: sin `details`.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf("error" to linkedMapOf(
                    "code" to "patient_delinquent",
                    "message" to "El paciente tiene cuotas vencidas. Regularice el pago o use override=true para agendar de todas formas.",
                )),
            )
        }
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into agenda_appointment (id, created_at, updated_at, scheduled_start, scheduled_end, status, notes,
               google_calendar_event_id, patient_id, tenant_id, doctor_id, treatment_id, created_by_id)
               values (:id, :ahora, :ahora, :scheduled_start, :scheduled_end, 'pending', :notes, '', :patient_id,
                       :clinica, :doctor_id, :treatment_id, :usuario)""",
            cita + mapOf("id" to id, "ahora" to ahora, "clinica" to clinica, "usuario" to usuario.id,
                "notes" to (cita["notes"] ?: ""), "treatment_id" to cita["treatment_id"]),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(citas.una(id))
    }

    /** `is_patient_delinquent`: alguna cuota sin pagar vencida antes de hoy menos el umbral. */
    private fun esMoroso(paciente: UUID): Boolean {
        val umbral = jdbc.queryForList(
            "select value from configuration_systemparameter where tenant_id = :clinica and key = 'dias_morosidad' order by id limit 1",
            mapOf("clinica" to clinica), String::class.java,
        ).firstOrNull()?.trim()?.toIntOrNull() ?: 30
        return jdbc.queryForObject(
            """select count(*) from billing_installment where patient_id = :paciente and tenant_id = :clinica
               and due_date < :limite and status <> 'paid'""",
            mapOf("paciente" to paciente, "clinica" to clinica, "limite" to zona.hoy().minusDays(umbral.toLong())),
            Long::class.java,
        )!! > 0
    }

    /**
     * Los campos del `AppointmentSerializer`, en su orden, y su `validate()`.
     * Devuelve las columnas a guardar.
     *
     * Una diferencia DELIBERADA con Django: allí el doctor y el tratamiento
     * se buscan en TODAS las clínicas, así que se podía agendar con el
     * doctor de otra clínica y la respuesta traía su nombre. Aquí solo
     * valen los de esta clínica; uno ajeno es «objeto no existe», igual que
     * uno inventado. Ver docs/DIFERENCIAS.md.
     */
    private fun leerCita(datos: ObjectNode, parcial: Boolean, actual: UUID?): Map<String, Any?> {
        val campos = CamposDrf(datos, parcial)
        val guardar = LinkedHashMap<String, Any?>()
        val paciente = campos.clave("patient") { existe("patients_patient", it, cualquierClinica = true) }
        if (paciente is Leido.Valor) {
            val deEsta = existe("patients_patient", paciente.valor!!)
            if (!deEsta) campos.error("patient", "El paciente no pertenece a esta clínica.") else guardar["patient_id"] = paciente.valor
        }
        val doctor = campos.clave("doctor") { existe("agenda_doctor", it) }
        if (doctor is Leido.Valor) guardar["doctor_id"] = doctor.valor
        val tratamiento = campos.clave("treatment", requerido = false, nulo = true) { existe("configuration_treatment", it) }
        if (tratamiento is Leido.Valor) guardar["treatment_id"] = tratamiento.valor
        val inicio = campos.fechaHora("scheduled_start", zona.zona)
        if (inicio is Leido.Valor) guardar["scheduled_start"] = Timestamp.from(inicio.valor!!.toInstant())
        val fin = campos.fechaHora("scheduled_end", zona.zona)
        if (fin is Leido.Valor) guardar["scheduled_end"] = Timestamp.from(fin.valor!!.toInstant())
        val notas = campos.texto("notes", requerido = false, enBlanco = true)
        if (notas is Leido.Valor) guardar["notes"] = notas.valor
        campos.validar()

        // `validate()`: solo si el inicio y el fin llegaron JUNTOS. Un PATCH
        // que cambia solo el inicio no se comprueba —así es en Django—.
        val i = (inicio as? Leido.Valor)?.valor?.toInstant()
        val f = (fin as? Leido.Valor)?.valor?.toInstant()
        if (i != null && f != null) {
            if (!f.isAfter(i)) throw ExcepcionApi.validacionGeneral(FIN_ANTES)
            if (actual == null && i.isBefore(zona.ahora())) throw ExcepcionApi.validacionGeneral(EN_EL_PASADO)
            val doctorDeLaCita = (doctor as? Leido.Valor)?.valor ?: actual?.let { doctorDe(it) }
            if (doctorDeLaCita != null && seCruza(doctorDeLaCita, i, f, actual)) throw ExcepcionApi.validacionGeneral(CRUZADA)
        }
        return guardar
    }

    private fun existe(tabla: String, id: UUID, cualquierClinica: Boolean = false): Boolean =
        jdbc.queryForObject(
            "select count(*) from $tabla where id = :id" + if (cualquierClinica) "" else " and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0

    private fun doctorDe(cita: UUID): UUID =
        jdbc.queryForObject("select doctor_id from agenda_appointment where id = :id", mapOf("id" to cita), UUID::class.java)!!

    /** `overlaps_with_existing`: otra cita del mismo doctor, no cancelada, que se cruce. */
    private fun seCruza(doctor: UUID, inicio: Instant, fin: Instant, excepto: UUID?): Boolean =
        jdbc.queryForObject(
            """select count(*) from agenda_appointment where doctor_id = :doctor and scheduled_start < :fin
               and scheduled_end > :inicio and status <> 'cancelled' and (cast(:excepto as uuid) is null or id <> :excepto)""",
            mapOf("doctor" to doctor, "inicio" to Timestamp.from(inicio), "fin" to Timestamp.from(fin), "excepto" to excepto),
            Long::class.java,
        )!! > 0

    // ── Ficha y edición de la cita ───────────────────────────────────

    @GetMapping("/api/v1/appointments/$ID/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(Permisos.PERSONAL)
        citaDeLaClinica(id)
        return citas.una(id)
    }

    @PutMapping("/api/v1/appointments/$ID/")
    @Transactional
    fun reemplazar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, false)

    @PatchMapping("/api/v1/appointments/$ID/")
    @Transactional
    fun modificar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, true)

    /** El estado no se cambia por aquí (es de solo lectura): para eso están las acciones. */
    private fun editar(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(GESTIONAN)
        citaDeLaClinica(id)
        val datos = leerCita(CuerpoDrf.objeto(cuerpo), parcial, actual = id)
        val asignaciones = (datos.keys.map { "$it = :$it" } + "updated_at = :ahora").joinToString(", ")
        jdbc.update("update agenda_appointment set $asignaciones where id = :id",
            datos + mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())))
        return citas.una(id)
    }

    private fun citaDeLaClinica(id: UUID) {
        if (!existe("agenda_appointment", id)) throw ExcepcionApi.http404("Appointment")
    }

    // ── Acciones del flujo de atención ───────────────────────────────
    //
    // Guardan SOLO los campos que cambian (`save(update_fields=…)` en
    // Django), así que `updated_at` no se toca: el registro de cuándo se
    // editó la cita no cambia por confirmarla o marcar la llegada.

    /** POST /appointments/{id}/confirm/ — RF-AGN-04. */
    @PostMapping("/api/v1/appointments/$ID/confirm/")
    @Transactional
    fun confirmar(@PathVariable id: UUID) = cambiarEstado(id, "confirmed")

    /** POST /appointments/{id}/cancel/ — RF-AGN-04. */
    @PostMapping("/api/v1/appointments/$ID/cancel/")
    @Transactional
    fun cancelar(@PathVariable id: UUID) = cambiarEstado(id, "cancelled")

    private fun cambiarEstado(id: UUID, estado: String): Map<String, Any?> {
        permisos.exigir(GESTIONAN)
        citaDeLaClinica(id)
        jdbc.update("update agenda_appointment set status = :estado where id = :id", mapOf("id" to id, "estado" to estado))
        return citas.una(id)
    }

    /**
     * POST /appointments/{id}/reschedule/ — RF-AGN-04. Los errores de
     * formato van con la forma de los errores; el fin antes del inicio y el
     * cruce con otra cita, con `{"detail": …}` (400 y 409), porque así los
     * construye la vista en Django.
     */
    @PostMapping("/api/v1/appointments/$ID/reschedule/")
    @Transactional
    fun reagendar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(GESTIONAN)
        citaDeLaClinica(id)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val inicio = campos.fechaHora("new_start", zona.zona)
        val fin = campos.fechaHora("new_end", zona.zona)
        campos.validar()
        val i = (inicio as Leido.Valor).valor!!.toInstant()
        val f = (fin as Leido.Valor).valor!!.toInstant()
        if (!f.isAfter(i)) return ResponseEntity.badRequest().body(mapOf("detail" to FIN_ANTES))
        if (seCruza(doctorDe(id), i, f, id)) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("detail" to CRUZADA))
        jdbc.update(
            "update agenda_appointment set scheduled_start = :i, scheduled_end = :f, status = 'rescheduled' where id = :id",
            mapOf("id" to id, "i" to Timestamp.from(i), "f" to Timestamp.from(f)),
        )
        return ResponseEntity.ok(citas.una(id))
    }

    /**
     * POST /appointments/{id}/checkin/ — RF-AGN-05, el paciente llegó.
     *
     * En Django esto encola además el aviso por WhatsApp al doctor
     * (RF-WSP-05). El módulo de WhatsApp es la fase 13: hasta entonces no se
     * avisa, y el registro de llegada, que es lo crítico, funciona igual.
     */
    @PostMapping("/api/v1/appointments/$ID/checkin/")
    @Transactional
    fun llegada(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(GESTIONAN)
        citaDeLaClinica(id)
        jdbc.update("update agenda_appointment set checkin_at = :ahora where id = :id",
            mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())))
        return citas.una(id)
    }

    /**
     * POST /appointments/{id}/start/ — empieza la atención. Requiere la
     * llegada antes, y no se inicia dos veces. Esta vista tiene sus propios
     * mensajes, también el 404.
     */
    @PostMapping("/api/v1/appointments/$ID/start/")
    @Transactional
    fun iniciar(@PathVariable id: UUID): ResponseEntity<Any> {
        permisos.exigir(GESTIONAN)
        val fila = jdbc.queryForList(
            "select checkin_at, attention_started_at from agenda_appointment where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica),
        ).firstOrNull() ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("detail" to "Cita no encontrada."))
        if (fila["checkin_at"] == null) {
            return ResponseEntity.badRequest().body(mapOf("detail" to "El paciente aún no hizo check-in (botón Llegó)."))
        }
        if (fila["attention_started_at"] != null) {
            return ResponseEntity.badRequest().body(mapOf("detail" to "La atención ya fue iniciada."))
        }
        jdbc.update("update agenda_appointment set attention_started_at = :ahora where id = :id",
            mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())))
        return ResponseEntity.ok(citas.una(id))
    }

    /** POST /appointments/{id}/checkout/ — RF-AGN-05, la atención terminó. */
    @PostMapping("/api/v1/appointments/$ID/checkout/")
    @Transactional
    fun finalizar(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(Permisos.rol("admin", "reception", "doctor"))
        citaDeLaClinica(id)
        jdbc.update("update agenda_appointment set checkout_at = :ahora, status = 'completed' where id = :id",
            mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())))
        return citas.una(id)
    }

    // ── Calendario de cada doctor ────────────────────────────────────

    /**
     * GET /api/v1/doctors/{id}/calendar-url/ — la dirección SECRETA del feed
     * iCalendar, para suscribirse desde Google, Apple u Outlook. Solo la
     * administración o el propio doctor.
     */
    @GetMapping("/api/v1/doctors/$ID/calendar-url/")
    fun direccionDelCalendario(@PathVariable id: UUID): ResponseEntity<Any> {
        val usuario = permisos.exigir(Permisos.rol("admin", "doctor"))
        val doctor = jdbc.queryForList(
            "select user_id, calendar_token from agenda_doctor where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica),
        ).firstOrNull() ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("detail" to "Doctor no encontrado."))
        if (usuario.rol == Rol.DOCTOR && doctor["user_id"] != usuario.id) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("detail" to "Solo puedes ver la URL de tu propio calendario."))
        }
        val ruta = "/api/v1/calendar/${doctor["calendar_token"]}.ics"
        return ResponseEntity.ok(
            linkedMapOf(
                "url_path" to ruta,
                "url" to ServletUriComponentsBuilder.fromCurrentContextPath().path(ruta).build().toUriString(),
                "instructions" to "En Google Calendar: Otros calendarios → + → Desde URL → pegar la URL. " +
                    "Google actualiza las suscripciones cada varias horas.",
            ),
        )
    }

    /**
     * GET /api/v1/calendar/{token}.ics — el feed iCalendar del doctor. Sin
     * sesión: el token ES el secreto. Las 500 citas más tempranas del
     * doctor; las canceladas van como CANCELLED, que es como el estándar
     * hace desaparecer un evento.
     */
    @GetMapping("/api/v1/calendar/{token:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}.ics")
    fun calendario(@PathVariable token: UUID): ResponseEntity<String> {
        val doctor = jdbc.queryForList(
            """select d.id, d.user_id, u.full_name, u.email from agenda_doctor d join accounts_user u on u.id = d.user_id
               where d.calendar_token = :token""",
            mapOf("token" to token),
        ).firstOrNull() ?: throw ExcepcionApi.noEncontrado()
        val nombre = RepresentacionDeCitas.nombreDelDoctor(
            doctor["full_name"] as String?, doctor["email"] as String?, doctor["user_id"] as UUID,
        )
        val lineas = mutableListOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Clinube//Agenda//ES", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
            "X-WR-CALNAME:Citas — ${escaparIcs(nombre)}", "X-WR-TIMEZONE:America/Guayaquil",
        )
        jdbc.query(
            """select a.id, a.updated_at, a.scheduled_start, a.scheduled_end, a.status, a.notes, p.first_name, p.last_name
               from agenda_appointment a join patients_patient p on p.id = a.patient_id
               where a.doctor_id = :doctor order by a.scheduled_start limit 500""",
            mapOf("doctor" to doctor["id"]),
        ) { f ->
            val estado = f.getString("status")
            val notas = f.getString("notes")
            lineas += listOf(
                "BEGIN:VEVENT",
                "UID:${f.uuid("id")}@clinube",
                "DTSTAMP:${FORMATO_ICS.format(f.instante("updated_at"))}",
                "DTSTART:${FORMATO_ICS.format(f.instante("scheduled_start"))}",
                "DTEND:${FORMATO_ICS.format(f.instante("scheduled_end"))}",
                "SUMMARY:${escaparIcs("Cita: ${f.getString("first_name")} ${f.getString("last_name")}")}",
                "STATUS:" + when (estado) { "cancelled" -> "CANCELLED"; "confirmed" -> "CONFIRMED"; else -> "TENTATIVE" },
                "DESCRIPTION:${escaparIcs("Estado: " + (RepresentacionDeCitas.ESTADOS[estado] ?: estado) +
                    if (notas.isNotEmpty()) " | $notas" else "")}",
                "END:VEVENT",
            )
        }
        lineas += "END:VCALENDAR"
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
            .body(lineas.joinToString("\r\n") + "\r\n")
    }

    private fun escaparIcs(texto: String) =
        texto.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
