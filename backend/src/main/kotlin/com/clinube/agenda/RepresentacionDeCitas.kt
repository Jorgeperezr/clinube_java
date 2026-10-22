package com.clinube.agenda

import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.sql.Filas.instanteONulo
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.util.UUID

/**
 * Una cita como la devuelve el `AppointmentSerializer` de Django. La usan
 * la vista de agenda, la lista, la ficha de la cita y las acciones
 * (confirmar, cancelar…), que devuelven la cita tal como queda.
 */
@Component
class RepresentacionDeCitas(private val jdbc: NamedParameterJdbcTemplate, private val zona: ZonaDeLaClinica) {

    companion object {
        /** Las etiquetas de `Appointment.Status`, para `status_display`. */
        val ESTADOS = linkedMapOf(
            "pending" to "Pendiente",
            "confirmed" to "Confirmada",
            "cancelled" to "Cancelada",
            "rescheduled" to "Reagendada",
            "completed" to "Completada",
            "no_show" to "No asistió",
        )

        /** Todo en una consulta, con sus JOIN: el `select_related` de Django. */
        const val SELECT = """
            select a.*, p.first_name, p.last_name, u.full_name as doctor_full_name, u.email as doctor_email,
                   d.user_id as doctor_user_id
            from agenda_appointment a
            join agenda_doctor d on d.id = a.doctor_id
            join patients_patient p on p.id = a.patient_id
            join accounts_user u on u.id = d.user_id
        """

        /** `Doctor.full_name`: su nombre, o su correo, o el id de su usuario. */
        fun nombreDelDoctor(nombre: String?, correo: String?, usuario: UUID): String =
            nombre?.ifEmpty { null } ?: correo?.ifEmpty { null } ?: usuario.toString()
    }

    fun deFila(fila: ResultSet): Map<String, Any?> {
        fun fechaDrf(columna: String) = fila.instanteONulo(columna)?.let { FormatoDeDrf.deDrf(it, zona.zona) }
        val estado = fila.getString("status")
        return linkedMapOf(
            "id" to fila.uuid("id").toString(),
            "patient" to fila.uuid("patient_id").toString(),
            "patient_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
            "doctor" to fila.uuid("doctor_id").toString(),
            "doctor_name" to nombreDelDoctor(fila.getString("doctor_full_name"), fila.getString("doctor_email"),
                fila.uuid("doctor_user_id")),
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

    /** La cita por su id, ya representada. */
    fun una(id: UUID): Map<String, Any?> =
        jdbc.queryForObject("$SELECT where a.id = :id", mapOf("id" to id)) { f, _ -> deFila(f) }!!
}
