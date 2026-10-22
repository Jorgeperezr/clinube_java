package com.clinube

import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Filas de prueba, con las columnas obligatorias de cada tabla de Django y
 * sus valores por defecto. Cada función devuelve el id de lo que crea.
 */
class Sembrador(private val jdbc: JdbcTemplate, private val ahora: () -> Instant) {

    private fun t(i: Instant = ahora()) = Timestamp.from(i)

    fun usuario(correo: String, rol: String, clinica: String, nombre: String = ""): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into accounts_user (id, password, is_superuser, email, role, is_active, is_staff, created_at,
               tenant_id, full_name, must_change_password, preferencias, funciones)
               values (?, ?, false, ?, ?, true, false, ?, ?::uuid, ?, false, '{}', '{}')""",
            id, HASH, correo, rol, t(), clinica, nombre,
        )
        return id
    }

    fun doctor(usuario: UUID, clinica: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into agenda_doctor (id, created_at, updated_at, calendar_color, is_active, tenant_id, user_id,
               calendar_token, license_number, signature_image)
               values (?, ?, ?, '#3b82f6', true, ?::uuid, ?, ?, '', '')""",
            id, t(), t(), clinica, usuario, UUID.randomUUID(),
        )
        return id
    }

    fun paciente(
        clinica: String, nombre: String, apellido: String, cedula: String,
        nacimiento: LocalDate? = null, telefono: String = "", activo: Boolean = true, creado: Instant = ahora(),
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into patients_patient (id, created_at, updated_at, is_active, first_name, last_name, national_id,
               phone, email, address, tenant_id, sex, birth_date)
               values (?, ?, ?, ?, ?, ?, ?, ?, '', '', ?::uuid, '', ?)""",
            id, t(creado), t(creado), activo, nombre, apellido, cedula, telefono, clinica, nacimiento,
        )
        return id
    }

    fun cita(
        clinica: String, paciente: UUID, doctor: UUID, inicio: Instant, estado: String = "confirmed",
        llegada: Instant? = null, atencion: Instant? = null,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into agenda_appointment (id, created_at, updated_at, scheduled_start, scheduled_end, status, notes,
               google_calendar_event_id, patient_id, tenant_id, doctor_id, checkin_at, attention_started_at)
               values (?, ?, ?, ?, ?, ?, '', '', ?, ?::uuid, ?, ?, ?)""",
            id, t(), t(), t(inicio), t(inicio.plusSeconds(1800)), estado, paciente, clinica, doctor,
            llegada?.let { t(it) }, atencion?.let { t(it) },
        )
        return id
    }

    fun pago(clinica: String, paciente: UUID, monto: String, metodo: String, fecha: LocalDate, cuota: UUID? = null): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into billing_payment (id, updated_at, amount, method, date, created_at, patient_id, tenant_id, installment_id)
               values (?, ?, ?, ?, ?, ?, ?, ?::uuid, ?)""",
            id, t(), BigDecimal(monto), metodo, fecha, t(), paciente, clinica, cuota,
        )
        return id
    }

    /** Un plan con sus cuotas: (vencimiento, monto, estado). Devuelve los ids de las cuotas. */
    fun cuotas(clinica: String, paciente: UUID, vararg cuotas: Triple<LocalDate, String, String>): List<UUID> {
        val presupuesto = UUID.randomUUID()
        val total = cuotas.sumOf { BigDecimal(it.second) }
        jdbc.update(
            """insert into billing_budget (id, updated_at, status, total_amount, notes, created_at, patient_id, tenant_id)
               values (?, ?, 'approved', ?, '', ?, ?, ?::uuid)""", presupuesto, t(), total, t(), paciente, clinica,
        )
        val plan = UUID.randomUUID()
        jdbc.update(
            """insert into billing_paymentplan (id, updated_at, total_amount, installment_count, created_at, budget_id,
               patient_id, tenant_id) values (?, ?, ?, ?, ?, ?, ?, ?::uuid)""",
            plan, t(), total, cuotas.size, t(), presupuesto, paciente, clinica,
        )
        return cuotas.mapIndexed { n, (vence, monto, estado) ->
            val id = UUID.randomUUID()
            jdbc.update(
                """insert into billing_installment (id, created_at, updated_at, number, due_date, amount, status,
                   patient_id, tenant_id, payment_plan_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?)""",
                id, t(), t(), n + 1, vence, BigDecimal(monto), estado, paciente, clinica, plan,
            )
            id
        }
    }

    fun evolucion(clinica: String, paciente: UUID, notas: String, seguimiento: LocalDate?): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_evolution (id, updated_at, type, date, notes, visible_to_patient, created_at,
               patient_id, tenant_id, follow_up_date) values (?, ?, 'clinical_note', ?, ?, false, ?, ?, ?::uuid, ?)""",
            id, t(), LocalDate.of(2026, 1, 1), notas, t(), paciente, clinica, seguimiento,
        )
        return id
    }

    fun producto(clinica: String, nombre: String, minimo: String, activo: Boolean = true): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into inventory_product (id, created_at, updated_at, name, unit, min_stock, is_active, tenant_id)
               values (?, ?, ?, ?, 'unidad', ?, ?, ?::uuid)""", id, t(), t(), nombre, BigDecimal(minimo), activo, clinica,
        )
        return id
    }

    fun lote(clinica: String, producto: UUID, cantidad: String) {
        jdbc.update(
            """insert into inventory_batch (id, created_at, updated_at, batch_number, quantity, received_at, tenant_id, product_id)
               values (?, ?, ?, 'L', ?, ?, ?::uuid, ?)""",
            UUID.randomUUID(), t(), t(), BigDecimal(cantidad), LocalDate.of(2026, 1, 1), clinica, producto,
        )
    }

    fun solicitud(clinica: String, paciente: UUID, estado: String) {
        jdbc.update(
            """insert into app_paciente_solicitudcita (id, created_at, updated_at, fecha_preferida, franja, motivo, estado,
               respuesta, patient_id, tenant_id) values (?, ?, ?, ?, 'cualquiera', '', ?, '', ?, ?::uuid)""",
            UUID.randomUUID(), t(), t(), LocalDate.of(2026, 1, 1), estado, paciente, clinica,
        )
    }

    fun mensaje(clinica: String, paciente: UUID, respondido: Boolean) {
        jdbc.update(
            """insert into app_paciente_mensajeconsultorio (id, created_at, updated_at, texto, respuesta, patient_id,
               tenant_id, respondido_en) values (?, ?, ?, 'Hola', '', ?, ?::uuid, ?)""",
            UUID.randomUUID(), t(), t(), paciente, clinica, if (respondido) t() else null,
        )
    }

    fun parametro(clinica: String, clave: String, valor: String) {
        jdbc.update(
            """insert into configuration_systemparameter (id, created_at, updated_at, key, value, description, tenant_id)
               values (?, ?, ?, ?, ?, '', ?::uuid)""", UUID.randomUUID(), t(), t(), clave, valor, clinica,
        )
    }

    companion object {
        /** La contraseña de la clínica demo, con el hash que generó Django. */
        const val HASH = "pbkdf2_sha256\$1000000\$5nzb8mbtEDCsQIIMppAK3o\$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns="
    }
}
