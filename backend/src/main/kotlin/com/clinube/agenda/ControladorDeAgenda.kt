package com.clinube.agenda

import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.instanteONulo
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.cuentas.dominio.Rol
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** La agenda (`apps/agenda`): de momento, la vista consolidada y la sala de espera. */
@RestController
class ControladorDeAgenda(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        /** Las etiquetas de `Appointment.Status`, para `status_display`. */
        val ESTADOS = mapOf(
            "pending" to "Pendiente",
            "confirmed" to "Confirmada",
            "cancelled" to "Cancelada",
            "rescheduled" to "Reagendada",
            "completed" to "Completada",
            "no_show" to "No asistió",
        )

        /** Lo que `parse_date` de Django acepta: AAAA-M-D, con meses y días de una o dos cifras. */
        private val FECHA = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    }

    /**
     * GET /api/v1/agenda/view/?mode=daily|weekly|monthly&date=&doctor_id= —
     * RF-AGN-01, 02. Las citas del día, de la semana (de lunes a domingo) o
     * del mes que contiene la fecha ancla; sin fecha, hoy en la clínica.
     *
     * Paginada de 20 en 20, como en Django: es un `ListAPIView`. Un doctor
     * solo ve las suyas.
     *
     * El filtro va por intervalo de instantes y no por `fecha::date`, que es
     * lo que hace Django (convierte cada fila a la zona de la clínica). El
     * resultado es el mismo y se usa el índice (tenant, scheduled_start).
     */
    @GetMapping("/api/v1/agenda/view/")
    fun vista(
        @RequestParam(required = false) mode: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(name = "doctor_id", required = false) doctorId: String?,
        @RequestParam(required = false) page: String?,
        peticion: HttpServletRequest,
    ): Pagina<Map<String, Any?>> {
        val usuario = permisos.exigir(Permisos.PERSONAL)
        val ancla = if (date.isNullOrEmpty()) zona.hoy() else fechaDeDjango(date)

        val (inicio, fin) = when (mode ?: "daily") {
            "weekly" -> ancla.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
            "monthly" -> ancla.withDayOfMonth(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }
            else -> ancla to ancla
        }
        val intervalo = zona.entre(inicio, fin)

        val parametros = mutableMapOf<String, Any?>(
            "clinica" to contexto.clinica?.id,
            "desde" to Timestamp.from(intervalo.desde),
            "hasta" to Timestamp.from(intervalo.hasta),
        )
        var filtro = "a.tenant_id = :clinica and a.scheduled_start >= :desde and a.scheduled_start < :hasta"
        if (!doctorId.isNullOrEmpty()) {
            parametros["doctor"] = UUID.fromString(doctorId)
            filtro += " and a.doctor_id = :doctor"
        }
        if (usuario.rol == Rol.DOCTOR) {
            parametros["usuario"] = usuario.id
            filtro += " and d.user_id = :usuario"
        }

        val desde = "from agenda_appointment a join agenda_doctor d on d.id = a.doctor_id where $filtro"
        val total = jdbc.queryForObject("select count(*) $desde", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(page, total)
        parametros["desdeFila"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA

        // Todo en una consulta, con sus JOIN: el `select_related` de Django.
        val filas = jdbc.query(
            """
            select a.*, p.first_name, p.last_name, u.full_name as doctor_full_name, u.email as doctor_email,
                   d.user_id as doctor_user_id
            from agenda_appointment a
            join agenda_doctor d on d.id = a.doctor_id
            join patients_patient p on p.id = a.patient_id
            join accounts_user u on u.id = d.user_id
            where $filtro
            order by a.scheduled_start
            limit :cuantos offset :desdeFila
            """,
            parametros,
        ) { fila, _ -> cita(fila) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * `AppointmentSerializer` de Django. El nombre del doctor es su nombre
     * completo, y si no tiene, su correo, y si tampoco, el id de su usuario
     * (`Doctor.full_name`).
     */
    private fun cita(fila: ResultSet): Map<String, Any?> {
        fun fechaDrf(columna: String) = fila.instanteONulo(columna)?.let { FormatoDeDrf.deDrf(it, zona.zona) }
        val nombreDoctor = fila.getString("doctor_full_name").ifEmpty { null }
            ?: fila.getString("doctor_email")?.ifEmpty { null }
            ?: fila.uuid("doctor_user_id").toString()
        val estado = fila.getString("status")
        return linkedMapOf(
            "id" to fila.uuid("id").toString(),
            "patient" to fila.uuid("patient_id").toString(),
            "patient_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
            "doctor" to fila.uuid("doctor_id").toString(),
            "doctor_name" to nombreDoctor,
            "treatment" to fila.uuidONulo("treatment_id")?.toString(),
            "scheduled_start" to fechaDrf("scheduled_start"),
            "scheduled_end" to fechaDrf("scheduled_end"),
            "status" to estado,
            "status_display" to (ESTADOS[estado] ?: estado),
            "notes" to fila.getString("notes"),
            "checkin_at" to fechaDrf("checkin_at"),
            "attention_started_at" to fechaDrf("attention_started_at"),
            "reminder_sent_at" to fechaDrf("reminder_sent_at"),
            "checkout_at" to fechaDrf("checkout_at"),
            "created_at" to fechaDrf("created_at"),
        )
    }

    /**
     * GET /api/v1/appointments/waiting/ — quién ya llegó y aún no está siendo
     * atendido, hoy. El doctor ve solo los suyos; el resto del personal, los
     * de toda la clínica (para saber a quién avisar).
     *
     * Es un arreglo plano y las horas van con `isoformat()` de Python, en
     * UTC y con «+00:00»: la vista arma el diccionario a mano, sin
     * serializador de DRF.
     */
    @GetMapping("/api/v1/appointments/waiting/")
    fun salaDeEspera(): List<Map<String, Any?>> {
        val usuario = permisos.exigir(Permisos.PERSONAL)
        val hoy = zona.elDia(zona.hoy())
        val ahora = zona.ahora()
        val parametros = mutableMapOf<String, Any?>(
            "clinica" to contexto.clinica?.id,
            "desde" to Timestamp.from(hoy.desde),
            "hasta" to Timestamp.from(hoy.hasta),
        )
        var filtro = """a.tenant_id = :clinica and a.checkin_at is not null and a.attention_started_at is null
                        and a.scheduled_start >= :desde and a.scheduled_start < :hasta
                        and a.status not in ('cancelled', 'no_show')"""
        if (usuario.rol == Rol.DOCTOR) {
            // El primer perfil de doctor de esa persona en la clínica; si no
            // tiene, no ve a nadie.
            val doctor = jdbc.queryForList(
                "select id from agenda_doctor where tenant_id = :clinica and user_id = :usuario order by id limit 1",
                mapOf("clinica" to contexto.clinica?.id, "usuario" to usuario.id), UUID::class.java,
            ).firstOrNull() ?: return emptyList()
            parametros["doctor"] = doctor
            filtro += " and a.doctor_id = :doctor"
        }
        return jdbc.query(
            """
            select a.id, a.patient_id, a.scheduled_start, a.checkin_at, p.first_name, p.last_name,
                   u.full_name as doctor_full_name, u.email as doctor_email
            from agenda_appointment a
            join patients_patient p on p.id = a.patient_id
            join agenda_doctor d on d.id = a.doctor_id
            join accounts_user u on u.id = d.user_id
            where $filtro
            order by a.checkin_at
            """,
            parametros,
        ) { fila, _ ->
            val llegada = fila.instante("checkin_at")
            val microsEsperando = java.time.Duration.between(llegada, ahora).toNanos() / 1_000
            linkedMapOf<String, Any?>(
                "id" to fila.uuid("id").toString(),
                "patient_id" to fila.uuid("patient_id").toString(),
                "patient_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
                "doctor_name" to (fila.getString("doctor_full_name").ifEmpty { null } ?: fila.getString("doctor_email")),
                "scheduled_start" to FormatoDeDrf.isoformatDePython(fila.instante("scheduled_start")),
                "checkin_at" to FormatoDeDrf.isoformatDePython(llegada),
                // `int(total_seconds() // 60)`: división entera hacia abajo,
                // también si la llegada quedó en el futuro.
                "waiting_minutes" to Math.floorDiv(microsEsperando, 60_000_000L),
            )
        }
    }

    /**
     * `parse_date` de Django. Una fecha mal escrita revienta en Django con un
     * 500 (el filtro recibe `None`); aquí también es un error del servidor,
     * pero con el formato de errores de la API.
     */
    private fun fechaDeDjango(texto: String): LocalDate {
        val m = FECHA.matchEntire(texto) ?: throw IllegalArgumentException("Fecha no válida: $texto")
        return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
}
