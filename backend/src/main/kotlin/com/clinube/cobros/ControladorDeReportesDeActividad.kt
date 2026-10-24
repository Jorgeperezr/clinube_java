package com.clinube.cobros

import com.clinube.agenda.RepresentacionDeCitas
import com.clinube.comun.documentos.HojasDeCalculo
import com.clinube.comun.documentos.HojasDeCalculo.Numero
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.cuentas.dominio.Rol
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset

/**
 * El resto de la pantalla de Reportes (`apps/billing/views.py`): producción
 * por doctor, pacientes nuevos, inventario, actividad de citas y las
 * exportaciones a Excel (`report_export.build_xlsx`).
 */
@RestController
class ControladorDeReportesDeActividad(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val hojas: HojasDeCalculo,
) {

    private val clinica get() = contexto.clinica?.id

    /** `scheduled_start__date` / `created_at__date`: el día en la zona de la clínica. */
    private fun diaLocal(columna: String) = "($columna at time zone :zona)::date"

    /**
     * GET /api/v1/reports/financial/?date_from=&date_to=&export=excel — el
     * Excel de ingresos: un cobro por fila, por fecha.
     */
    fun ingresosEnExcel(desde: java.time.LocalDate?, hasta: java.time.LocalDate?): ResponseEntity<Any> {
        val parametros = mapOf("clinica" to clinica, "desde" to desde, "hasta" to hasta)
        val filtro = "p.tenant_id = :clinica" + (if (desde != null) " and p.date >= :desde" else "") + (if (hasta != null) " and p.date <= :hasta" else "")
        val filas = jdbc.query(
            """select p.date, p.method, p.amount, pa.first_name, pa.last_name from billing_payment p
               inner join patients_patient pa on (p.patient_id = pa.id) where $filtro order by p.date asc""",
            parametros,
        ) { f, _ ->
            val metodo = f.getString("method")
            listOf<Any?>(f.getObject("date", java.time.LocalDate::class.java).toString(), "${f.getString("first_name")} ${f.getString("last_name")}",
                Cobros.METODOS[metodo] ?: metodo, Numero(f.getBigDecimal("amount").toDouble()))
        }
        return HojasDeCalculo.descarga(
            hojas.reporte(clinica, "Ingresos por período", listOf("Fecha", "Paciente", "Forma de pago", "Monto"), filas), "ingresos.xlsx",
        )
    }

    /**
     * GET /api/v1/reports/production-by-doctor/?doctor_id= — RF-REP-03. Las
     * citas completadas de cada doctor activo, de siempre. Un doctor solo ve
     * la suya. Un `doctor_id` que no es un UUID es un 500 en Django, y aquí.
     */
    @GetMapping("/api/v1/reports/production-by-doctor/")
    fun produccion(@RequestParam(name = "doctor_id", required = false) doctor: String?): Map<String, Any?> {
        val usuario = permisos.exigir(Permisos.rol("admin", "doctor"))
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        var filtro = "d.tenant_id = :clinica and d.is_active"
        if (!doctor.isNullOrEmpty()) {
            parametros["doctor"] = com.clinube.comun.json.CamposDrf.uuidDePython(doctor) ?: throw IllegalArgumentException("doctor_id: $doctor")
            filtro += " and d.id = :doctor"
        }
        if (usuario.rol == Rol.DOCTOR) { parametros["usuario"] = usuario.id; filtro += " and d.user_id = :usuario" }
        val doctores = jdbc.query(
            """select d.id, d.user_id, u.full_name, u.email,
               (select count(*) from agenda_appointment a where a.tenant_id = :clinica and a.doctor_id = d.id and a.status = 'completed') as hechas
               from agenda_doctor d inner join accounts_user u on (d.user_id = u.id) where $filtro""",
            parametros,
        ) { f, _ ->
            linkedMapOf(
                "doctor_id" to f.uuid("id").toString(),
                "doctor_name" to RepresentacionDeCitas.nombreDelDoctor(f.getString("full_name"), f.getString("email"), f.uuid("user_id")),
                "completed_appointments" to f.getLong("hechas"),
            )
        }
        return mapOf("doctors" to doctores)
    }

    /**
     * GET /api/v1/reports/new-patients/?date_from=&date_to=&export=excel —
     * RF-REP-04. Los pacientes activos registrados en el período (por el día
     * en la clínica). La fecha que se escribe es la del registro en UTC: la
     * de Django, que la toma tal como sale de la base.
     */
    @GetMapping("/api/v1/reports/new-patients/")
    fun pacientesNuevos(
        @RequestParam(name = "date_from", required = false) desde: String?,
        @RequestParam(name = "date_to", required = false) hasta: String?,
        @RequestParam(required = false) export: String?,
    ): ResponseEntity<Any> {
        permisos.exigir(Permisos.funcion("reportes"))
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica, "zona" to zona.zona.id)
        var filtro = "tenant_id = :clinica and is_active"
        if (!desde.isNullOrEmpty()) { parametros["desde"] = ControladorDeReportes.fechaDeDjango(desde); filtro += " and ${diaLocal("created_at")} >= :desde" }
        if (!hasta.isNullOrEmpty()) { parametros["hasta"] = ControladorDeReportes.fechaDeDjango(hasta); filtro += " and ${diaLocal("created_at")} <= :hasta" }
        data class Paciente(val nombre: String, val cedula: String, val telefono: String, val registro: String)
        val pacientes = jdbc.query(
            "select first_name, last_name, national_id, phone, created_at from patients_patient where $filtro order by created_at asc", parametros,
        ) { f, _ ->
            Paciente("${f.getString("first_name")} ${f.getString("last_name")}", f.getString("national_id"), f.getString("phone") ?: "",
                f.instante("created_at").atOffset(ZoneOffset.UTC).toLocalDate().toString())
        }
        if (export == "excel") {
            return HojasDeCalculo.descarga(
                hojas.reporte(clinica, "Pacientes nuevos", listOf("Nombre", "Identificación", "Teléfono", "Fecha de registro"),
                    pacientes.map { listOf(it.nombre, it.cedula, it.telefono, it.registro) }),
                "pacientes_nuevos.xlsx",
            )
        }
        return ResponseEntity.ok(linkedMapOf(
            "count" to pacientes.size,
            "patients" to pacientes.map { linkedMapOf("name" to it.nombre, "national_id" to it.cedula, "registered" to it.registro) },
        ))
    }

    /**
     * GET /api/v1/reports/inventory/?export=excel — RF-REP-05. Los productos
     * activos, por nombre, con su stock (un producto sin lotes, «0») y si
     * está bajo.
     */
    @GetMapping("/api/v1/reports/inventory/")
    fun inventario(@RequestParam(required = false) export: String?): ResponseEntity<Any> {
        permisos.exigir(Permisos.funcion("inventario"))
        data class Producto(val nombre: String, val unidad: String, val stock: String, val minimo: String, val bajo: Boolean)
        val productos = jdbc.query(
            """select p.name, p.unit, p.min_stock, (select sum(b.quantity) from inventory_batch b where b.product_id = p.id) as stock
               from inventory_product p where p.tenant_id = :clinica and p.is_active order by p.name asc""",
            mapOf("clinica" to clinica),
        ) { f, _ ->
            val stock: BigDecimal? = f.getBigDecimal("stock")
            val minimo = f.getBigDecimal("min_stock")
            Producto(f.getString("name"), f.getString("unit"), stock?.toPlainString() ?: "0", minimo.toPlainString(),
                (stock ?: BigDecimal.ZERO) <= minimo)
        }
        if (export == "excel") {
            return HojasDeCalculo.descarga(
                hojas.reporte(clinica, "Inventario", listOf("Producto", "Unidad", "Stock actual", "Stock mínimo", "Stock bajo"),
                    productos.map { listOf(it.nombre, it.unidad, it.stock, it.minimo, if (it.bajo) "SÍ" else "NO") }),
                "inventario.xlsx",
            )
        }
        return ResponseEntity.ok(mapOf("products" to productos.map {
            linkedMapOf("name" to it.nombre, "current_stock" to it.stock, "is_low_stock" to it.bajo)
        }))
    }

    /**
     * GET /api/v1/reports/appointments-summary/?date_from=&date_to= — las
     * citas del período por estado (en el orden en que la base las va
     * encontrando, como el diccionario de Django), la asistencia (completadas
     * frente a completadas más ausencias) y las cancelaciones, con un decimal
     * redondeado como `round()` de Python.
     */
    @GetMapping("/api/v1/reports/appointments-summary/")
    fun resumenDeCitas(
        @RequestParam(name = "date_from", required = false) desde: String?,
        @RequestParam(name = "date_to", required = false) hasta: String?,
    ): Map<String, Any?> {
        permisos.exigir(Permisos.funcion("reportes"))
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica, "zona" to zona.zona.id)
        var filtro = "tenant_id = :clinica"
        if (!desde.isNullOrEmpty()) { parametros["desde"] = ControladorDeReportes.fechaDeDjango(desde); filtro += " and ${diaLocal("scheduled_start")} >= :desde" }
        if (!hasta.isNullOrEmpty()) { parametros["hasta"] = ControladorDeReportes.fechaDeDjango(hasta); filtro += " and ${diaLocal("scheduled_start")} <= :hasta" }
        val porEstado = LinkedHashMap<String, Int>()
        jdbc.query("select status from agenda_appointment where $filtro", parametros) { f -> porEstado.merge(f.getString("status"), 1, Int::plus) }
        val hechas = porEstado["completed"] ?: 0
        val ausencias = porEstado["no_show"] ?: 0
        val canceladas = porEstado["cancelled"] ?: 0
        val total = porEstado.values.sum()
        fun redondeo(x: Double) = BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).toDouble()
        return linkedMapOf(
            "total" to total,
            "by_status" to porEstado,
            "attendance_rate" to if (hechas + ausencias > 0) redondeo(hechas.toDouble() / (hechas + ausencias) * 100) else null,
            "cancellation_rate" to if (total > 0) redondeo(canceladas.toDouble() / total * 100) else null,
        )
    }
}
