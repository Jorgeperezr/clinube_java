package com.clinube.cobros

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.decimal
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Los informes de cobros (`apps/billing`): ingresos del período y morosidad. */
@RestController
class ControladorDeReportes(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val actividad: ControladorDeReportesDeActividad,
) {

    companion object {
        private val FECHA = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")

        /** `parse_date` de Django; una fecha mal escrita es un 500 allí, y un error del servidor aquí. */
        fun fechaDeDjango(texto: String): LocalDate {
            val m = FECHA.matchEntire(texto) ?: throw IllegalArgumentException("Fecha no válida: $texto")
            return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }

        /**
         * Dos decimales siempre. Una suma de la base puede venir con otra
         * escala, y el panel pintaba «$150.5»: una optimización que cambia
         * las cifras de un informe de ingresos no es una optimización.
         */
        fun dinero(valor: BigDecimal?): String =
            (valor ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).toPlainString()
    }

    /**
     * GET /api/v1/reports/financial/?date_from=&date_to= — RF-TRP-06,
     * RF-REP-01. Ingresos del período, desglosados por forma de pago. Las
     * fechas son de un campo `date`, así que `date_to` incluye el último día
     * entero.
     *
     * Suma la base, no un bucle. El desglose usa la MISMA consulta que
     * Django (`GROUP BY method` sin `ORDER BY`), para que las formas de pago
     * salgan en el mismo orden.
     *
     * Con `?export=excel`, el Excel de los cobros del período.
     */
    @GetMapping("/api/v1/reports/financial/")
    fun ingresos(
        @RequestParam(name = "date_from", required = false) desde: String?,
        @RequestParam(name = "date_to", required = false) hasta: String?,
        @RequestParam(required = false) export: String?,
    ): ResponseEntity<Any> {
        permisos.exigir(Permisos.funcion("reportes"), Permisos.funcionalidad("reportes"))
        if (export == "excel") {
            return actividad.ingresosEnExcel(desde?.takeIf { it.isNotEmpty() }?.let(::fechaDeDjango), hasta?.takeIf { it.isNotEmpty() }?.let(::fechaDeDjango))
        }
        val parametros = mutableMapOf<String, Any?>("clinica" to contexto.clinica?.id)
        var filtro = "tenant_id = :clinica"
        if (!desde.isNullOrEmpty()) { parametros["desde"] = fechaDeDjango(desde); filtro += " and date >= :desde" }
        if (!hasta.isNullOrEmpty()) { parametros["hasta"] = fechaDeDjango(hasta); filtro += " and date <= :hasta" }

        val (total, cuantos) = jdbc.queryForObject(
            "select sum(amount) as total, count(id) as n from billing_payment where $filtro", parametros,
        ) { fila, _ -> fila.getBigDecimal("total") to fila.getLong("n") }!!
        val porMetodo = LinkedHashMap<String, String>()
        jdbc.query("select method, sum(amount) as subtotal from billing_payment where $filtro group by method", parametros) { fila ->
            porMetodo[fila.getString("method")] = dinero(fila.getBigDecimal("subtotal"))
        }
        return ResponseEntity.ok(linkedMapOf("total_income" to dinero(total), "by_method" to porMetodo, "payment_count" to cuantos))
    }

    /**
     * GET /api/v1/reports/delinquency/ — RF-REP-02. Quién tiene cuotas
     * vencidas sin pagar, cuánto debe y desde cuándo, y si ya pasó el umbral
     * de morosidad que bloquea la agenda (parámetro `dias_morosidad`, 30 por
     * defecto).
     *
     * Django recorre las cuotas en Python y pide los pagos de cada una por
     * separado. Aquí agrupa la base, y los pacientes salen en el mismo orden:
     * el de su cuota vencida más antigua, que es el orden en que Django se
     * los va encontrando al recorrer las cuotas por fecha.
     */
    @GetMapping("/api/v1/reports/delinquency/")
    fun morosidad(): Map<String, Any?> {
        permisos.exigir(Permisos.funcion("cobros"))
        val clinica = contexto.clinica?.id
        val umbral = diasDeMorosidad(clinica)
        val hoy = zona.hoy()

        val pacientes = jdbc.query(
            """
            select i.patient_id, p.first_name, p.last_name,
                   sum(i.amount - coalesce((select sum(pg.amount) from billing_payment pg
                                            where pg.installment_id = i.id), 0)) as debe,
                   min(i.due_date) as mas_antigua, count(*) as cuotas
            from billing_installment i
            join patients_patient p on p.id = i.patient_id
            where i.tenant_id = :clinica and i.due_date < :hoy and i.status <> 'paid'
            group by i.patient_id, p.first_name, p.last_name
            order by min(i.due_date)
            """,
            mapOf("clinica" to clinica, "hoy" to hoy),
        ) { fila, _ ->
            val antigua = fila.fecha("mas_antigua")
            val dias = ChronoUnit.DAYS.between(antigua, hoy)
            linkedMapOf<String, Any?>(
                "patient_id" to fila.uuid("patient_id").toString(),
                "patient_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
                "overdue_amount" to fila.decimal("debe").setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                "oldest_due_date" to antigua.toString(),
                "installment_count" to fila.getLong("cuotas"),
                "days_overdue" to dias,
                "is_blocking" to (dias >= umbral),
            )
        }
        return linkedMapOf("delinquency_threshold_days" to umbral, "patients" to pacientes)
    }

    /** `get_delinquency_days`: el parámetro de la clínica, o 30 si falta o no es un entero. */
    private fun diasDeMorosidad(clinica: java.util.UUID?): Int =
        jdbc.queryForList(
            "select value from configuration_systemparameter where tenant_id = :clinica and key = 'dias_morosidad' order by id limit 1",
            mapOf("clinica" to clinica), String::class.java,
        ).firstOrNull()?.trim()?.toIntOrNull() ?: 30
}
