package com.clinube.cobros

import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

/**
 * Lo que comparten las rutas de cobros: cómo se escriben un presupuesto, un
 * plan, una cuota y un pago (los serializadores de `apps/billing`), y las
 * reglas del cobro (el profesional habitual, el profesional de la clínica).
 */
@Component
class Cobros(
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        val ESTADOS_DEL_PRESUPUESTO = linkedMapOf("draft" to "Borrador", "approved" to "Aprobado", "rejected" to "Rechazado")
        val ESTADOS_DE_LA_CUOTA = linkedMapOf("pending" to "Pendiente", "paid" to "Pagada", "overdue" to "Vencida")
        val METODOS = linkedMapOf("cash" to "Efectivo", "transfer" to "Transferencia", "card" to "Tarjeta")

        /** Un `DecimalField(decimal_places=2)` de DRF: el valor redondeado a céntimos. */
        fun dinero(valor: BigDecimal): String = valor.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()
    }

    // ── Presupuestos ─────────────────────────────────────────────────

    fun presupuesto(id: UUID): Map<String, Any?> =
        jdbc.query("select * from billing_budget where id = :id", mapOf("id" to id)) { f, _ -> presupuesto(f) }.first()

    /** `BudgetSerializer`: con sus líneas (sin orden propio, como Django) y el id de su plan. */
    fun presupuesto(f: ResultSet): Map<String, Any?> {
        val id = f.uuid("id")
        val estado = f.getString("status")
        val lineas = jdbc.query("$SELECT_LINEA where i.budget_id = :id", mapOf("id" to id)) { l, _ -> lineaDePresupuesto(l) }
        val plan = jdbc.query("select id from billing_paymentplan where budget_id = :id", mapOf("id" to id)) { p, _ -> p.uuid("id") }
            .firstOrNull()
        return linkedMapOf(
            "id" to id.toString(),
            "patient" to f.uuid("patient_id").toString(),
            "status" to estado,
            "status_display" to (ESTADOS_DEL_PRESUPUESTO[estado] ?: estado),
            "total_amount" to dinero(f.getBigDecimal("total_amount")),
            "notes" to f.getString("notes"),
            "items" to lineas,
            "payment_plan_id" to plan?.toString(),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }

    /**
     * Sin `join` a propósito: `BudgetItem` no tiene orden y Django las lee con
     * un `prefetch` que recorre la tabla en su orden físico. Un `join` deja
     * que la base las devuelva en otro.
     */
    private val SELECT_LINEA = """
        select i.*, (select t.name from configuration_treatment t where t.id = i.treatment_id) as treatment_name
        from billing_budgetitem i"""

    fun lineaDePresupuesto(id: UUID): Map<String, Any?> =
        jdbc.query("$SELECT_LINEA where i.id = :id", mapOf("id" to id)) { f, _ -> lineaDePresupuesto(f) }.first()

    /** `BudgetItemSerializer`: el subtotal es cantidad × precio. */
    private fun lineaDePresupuesto(f: ResultSet): Map<String, Any?> {
        val precio = f.getBigDecimal("unit_price")
        val cantidad = f.getLong("quantity")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "treatment" to f.uuid("treatment_id").toString(),
            "treatment_name" to f.getString("treatment_name"),
            "tooth_fdi_code" to f.getString("tooth_fdi_code"),
            "quantity" to cantidad,
            "unit_price" to dinero(precio),
            "subtotal" to subtotal(precio, cantidad, precio.signum() < 0),
        )
    }

    /**
     * `quantity * unit_price` de Python: un producto cero lleva el signo del
     * precio (`0 * Decimal("-5.00")` es «-0.00»), cosa que `BigDecimal` no sabe.
     */
    fun subtotal(precio: BigDecimal, cantidad: Long, precioNegativo: Boolean): String {
        val producto = dinero(precio.multiply(BigDecimal(cantidad)))
        return if (precioNegativo && producto.trimStart('-').all { it == '0' || it == '.' }) "-" + producto.trimStart('-') else producto
    }

    /** `recalculate_total`: la suma de los subtotales, guardada sin tocar `updated_at`. */
    fun recalcularTotal(presupuesto: UUID) {
        jdbc.update(
            """update billing_budget set total_amount =
               (select coalesce(sum(quantity * unit_price), 0) from billing_budgetitem where budget_id = :id) where id = :id""",
            mapOf("id" to presupuesto),
        )
    }

    // ── Planes y cuotas ──────────────────────────────────────────────

    /** `PaymentPlanSerializer`: las cuotas por fecha de vencimiento (el `ordering` del modelo). */
    fun planDePago(id: UUID): Map<String, Any?> = jdbc.query("select * from billing_paymentplan where id = :id", mapOf("id" to id)) { f, _ ->
        linkedMapOf(
            "id" to id.toString(),
            "budget" to f.uuid("budget_id").toString(),
            "patient" to f.uuid("patient_id").toString(),
            "total_amount" to dinero(f.getBigDecimal("total_amount")),
            "installment_count" to f.getLong("installment_count"),
            "installments" to jdbc.query(
                "select * from billing_installment where payment_plan_id = :id order by due_date", mapOf("id" to id),
            ) { c, _ -> cuota(c) },
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }.first()

    fun cuota(id: UUID): Map<String, Any?> =
        jdbc.query("select * from billing_installment where id = :id", mapOf("id" to id)) { f, _ -> cuota(f) }.first()

    /** `InstallmentSerializer`: lo pagado es la suma de sus pagos; el saldo, lo que falta. */
    fun cuota(f: ResultSet): Map<String, Any?> {
        val id = f.uuid("id")
        val importe = f.getBigDecimal("amount")
        val pagado = jdbc.queryForObject(
            "select coalesce(sum(amount), 0) from billing_payment where installment_id = :id", mapOf("id" to id), BigDecimal::class.java,
        )!!
        val estado = f.getString("status")
        return linkedMapOf(
            "id" to id.toString(),
            "number" to f.getLong("number"),
            "due_date" to f.fecha("due_date").toString(),
            "amount" to dinero(importe),
            "status" to estado,
            "status_display" to (ESTADOS_DE_LA_CUOTA[estado] ?: estado),
            "total_paid" to dinero(pagado),
            "balance" to dinero(importe - pagado),
        )
    }

    // ── Pagos ────────────────────────────────────────────────────────

    /**
     * `profesional`: un `CharField` que admite nulo y vacío (y entonces no
     * hay profesional), y si no, `_profesional_de_la_clinica`: alguien activo
     * del equipo de ESTA clínica, ni paciente ni superadministrador. Un id
     * que no es un UUID da el error del `UUIDField` de Django.
     */
    fun leerProfesional(campos: CamposDrf): UUID? {
        val leido = campos.texto("profesional", requerido = false, enBlanco = true, nulo = true)
        val texto = (leido as? Leido.Valor)?.valor
        if (texto.isNullOrEmpty()) return null
        val id = CamposDrf.uuidDePython(texto)
        if (id == null) {
            campos.error("profesional", MensajesDeDjango.uuidInvalido(texto))
            return null
        }
        val valido = jdbc.queryForObject(
            """select count(*) from accounts_user where id = :id and tenant_id = :clinica and is_active
               and role not in ('patient', 'superadmin')""",
            mapOf("id" to id, "clinica" to contexto.clinica?.id), Long::class.java,
        )!! > 0
        if (!valido) {
            campos.error("profesional", "Ese profesional no es de esta clínica.")
            return null
        }
        return id
    }

    /**
     * `profesional_habitual`: el doctor de la última cita atendida del
     * paciente; si no hay, el de la última que ya empezó; si tampoco, el de
     * la primera agendada. Como en Django, mira todas sus citas.
     */
    fun profesionalHabitual(paciente: UUID): UUID? {
        val base = """select d.user_id from agenda_appointment a join agenda_doctor d on d.id = a.doctor_id
                      where a.patient_id = :paciente and a.doctor_id is not null"""
        val parametros = mapOf("paciente" to paciente, "ahora" to Timestamp.from(zona.ahora()))
        for (sql in listOf(
            "$base and a.status = 'completed' order by a.scheduled_start desc limit 1",
            "$base and a.scheduled_start <= :ahora order by a.scheduled_start desc limit 1",
            "$base order by a.scheduled_start asc limit 1",
        )) {
            jdbc.query(sql, parametros) { f, _ -> f.uuid("user_id") }.firstOrNull()?.let { return it }
        }
        return null
    }

    /** Un `Payment` nuevo, sin comprobante (se numera al emitirlo). */
    fun registrarPago(
        paciente: UUID, cuota: UUID?, importe: BigDecimal, metodo: String, fecha: LocalDate, registradoPor: UUID, profesional: UUID?,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into billing_payment (id, updated_at, amount, method, date, created_at, installment_id, patient_id,
               registered_by_id, tenant_id, numero_comprobante, profesional_id)
               values (:id, :actualizado, :importe, :metodo, :fecha, :creado, :cuota, :paciente, :usuario, :clinica, null, :profesional)""",
            mapOf(
                "id" to id, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "importe" to importe, "metodo" to metodo, "fecha" to fecha, "cuota" to cuota, "paciente" to paciente,
                "usuario" to registradoPor, "clinica" to contexto.clinica?.id, "profesional" to profesional,
            ),
        )
        return id
    }

    /**
     * `PaymentSerializer`: el profesional como texto (o nulo) y su nombre
     * (o "" si no hay).
     */
    fun pago(f: ResultSet): Map<String, Any?> {
        val metodo = f.getString("method")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "installment" to f.uuidONulo("installment_id")?.toString(),
            "patient" to f.uuid("patient_id").toString(),
            "amount" to dinero(f.getBigDecimal("amount")),
            "method" to metodo,
            "method_display" to (METODOS[metodo] ?: metodo),
            "date" to f.fecha("date").toString(),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
            "profesional" to f.uuidONulo("profesional_id")?.toString(),
            "profesional_nombre" to if (f.uuidONulo("profesional_id") != null) f.getString("profesional_nombre") else "",
            "numero_comprobante" to (f.getObject("numero_comprobante") as Number?)?.toLong(),
        )
    }

    val SELECT_PAGO = """
        select p.*, u.full_name as profesional_nombre from billing_payment p left join accounts_user u on u.id = p.profesional_id"""
}
