package com.clinube.finanzas

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.ControladorDeHonorarios.Companion.datosSueltos
import com.clinube.finanzas.ControladorDeHonorarios.Companion.forma
import com.clinube.finanzas.Finanzas.Companion.MESES
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.anioMes
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.dinero
import com.clinube.finanzas.Finanzas.Companion.error
import com.clinube.finanzas.Finanzas.Companion.errorInterno
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

/**
 * El rol de pagos de quien está en relación de dependencia (`Rol*View`):
 * un borrador por persona y mes, que se recalcula al pagarlo.
 */
@RestController
class ControladorDeRoles(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val documentos: DocumentosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val json: ObjectMapper,
) {

    companion object {
        val ESTADOS = linkedMapOf("borrador" to "Por pagar", "pagado" to "Pagado", "anulado" to "Anulado")

        /** `CAMPOS_ROL`, en su orden: así se comprueban. */
        val CAMPOS = listOf("dias_trabajados", "horas_suplementarias", "horas_extraordinarias", "horas_extra",
            "otros_ingresos", "anticipos", "prestamos_iess", "otros_descuentos")
    }

    private val clinica get() = finanzas.clinica

    val SELECT = """select r.*, r.calculo::text as calculo_texto, u.full_name as empleado_nombre from finanzas_rolpagos r
        inner join accounts_user u on (r.empleado_id = u.id)"""

    /** `RolSerializer`. El cálculo, tal como se guardó (o el recién hecho, si se acaba de calcular). */
    fun rol(f: ResultSet, calculo: Any? = null): Map<String, Any?> {
        val estado = f.getString("estado")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "empleado" to f.uuid("empleado_id").toString(),
            "empleado_nombre" to f.getString("empleado_nombre"),
            "mes" to f.fecha("mes").toString(),
            "dias_trabajados" to f.getInt("dias_trabajados"),
            "horas_suplementarias" to dinero(f.getBigDecimal("horas_suplementarias")),
            "horas_extraordinarias" to dinero(f.getBigDecimal("horas_extraordinarias")),
            "horas_extra" to dinero(f.getBigDecimal("horas_extra")),
            "otros_ingresos" to dinero(f.getBigDecimal("otros_ingresos")),
            "anticipos" to dinero(f.getBigDecimal("anticipos")),
            "prestamos_iess" to dinero(f.getBigDecimal("prestamos_iess")),
            "otros_descuentos" to dinero(f.getBigDecimal("otros_descuentos")),
            "calculo" to (calculo ?: json.readTree(f.getString("calculo_texto"))),
            "neto" to dinero(f.getBigDecimal("neto")),
            "costo_empleador" to dinero(f.getBigDecimal("costo_empleador")),
            "estado" to estado,
            "estado_nombre" to (ESTADOS[estado] ?: estado),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }

    private fun rol(id: UUID, calculo: Any? = null) = jdbc.query("$SELECT where r.id = :id", mapOf("id" to id)) { f, _ -> rol(f, calculo) }.first()

    private data class Datos(val empleado: Finanzas.Persona, val inicio: LocalDate, val valores: CalculosDeFinanzas.ValoresDelRol,
                             val enBruto: Map<String, BigDecimal>)

    /** `_datos_rol`: la persona (o 404), el mes y los valores, positivos y con un mes de 30 días. */
    private fun datos(fuente: JsonNode): Datos {
        val empleado = finanzas.delEquipo(Finanzas.valor(fuente, "empleado"))
        val (inicio, _) = Finanzas.mesDe(Finanzas.valor(fuente, "mes"), finanzas.hoy()) ?: error("El mes va como 2026-10.")
        val valores = linkedMapOf<String, BigDecimal>()
        for (campo in CAMPOS) {
            val v = Finanzas.decimal(Finanzas.valor(fuente, campo), if (campo == "dias_trabajados") "30" else "0")
            if (v == null || v.signum() < 0) error("Los valores del rol son números positivos.")
            // `int(v)`: los días se truncan.
            valores[campo] = if (campo == "dias_trabajados") BigDecimal(v.toBigInteger()) else v
        }
        if (valores.getValue("dias_trabajados") > BigDecimal(30)) error("El mes del rol se cuenta de 30 días.")
        if (valores.getValue("horas_suplementarias").add(valores.getValue("horas_extraordinarias"), Finanzas.PY) > BigDecimal(200)) {
            error("Revisa las horas extra: son más de 200 en el mes.")
        }
        return Datos(empleado, inicio, valoresDelRol(valores), valores)
    }

    private fun valoresDelRol(v: Map<String, BigDecimal>) = CalculosDeFinanzas.ValoresDelRol(
        diasTrabajados = v.getValue("dias_trabajados").toInt(),
        horasSuplementarias = v.getValue("horas_suplementarias"),
        horasExtraordinarias = v.getValue("horas_extraordinarias"),
        horasExtra = v.getValue("horas_extra"),
        otrosIngresos = v.getValue("otros_ingresos"),
        anticipos = v.getValue("anticipos"),
        prestamosIess = v.getValue("prestamos_iess"),
        otrosDescuentos = v.getValue("otros_descuentos"),
    )

    /** GET /api/v1/finanzas/roles/previa/?empleado=&mes=&dias_trabajados=&… — el cálculo, sin guardar. */
    @GetMapping("/api/v1/finanzas/roles/previa/")
    @Transactional
    fun previa(peticion: HttpServletRequest): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val d = datos(consulta(peticion))
        return calculos.rol(d.empleado.id, d.inicio, d.valores)
    }

    /** GET /api/v1/finanzas/roles/?mes= — los vigentes (no anulados), hasta 300. */
    @GetMapping("/api/v1/finanzas/roles/")
    fun lista(peticion: HttpServletRequest): List<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        val p = consulta(peticion)
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        var filtro = ""
        if (Python.verdad(p.get("mes"))) {
            parametros["mes"] = (Finanzas.mesDe(p.get("mes"), finanzas.hoy()) ?: errorInterno("mes_de: ${p.get("mes")}")).first
            filtro = " and r.mes = :mes"
        }
        return jdbc.query(
            "$SELECT where r.tenant_id = :clinica and not (r.estado = 'anulado')$filtro order by r.mes desc limit 300", parametros,
        ) { f, _ -> rol(f) }
    }

    /**
     * POST /api/v1/finanzas/roles/ — crea el borrador del mes (201) o rehace
     * el que ya había (200). Uno pagado no se rehace.
     */
    @PostMapping("/api/v1/finanzas/roles/")
    @Transactional
    fun guardar(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        val usuario = permisos.exigir(PERMISO)
        val d = datos(datosSueltos(cuerpo))
        val existente = jdbc.query(
            """select id, estado from finanzas_rolpagos where tenant_id = :clinica and empleado_id = :empleado and mes = :mes
               and not (estado = 'anulado') order by mes desc limit 1""",
            mapOf("clinica" to clinica, "empleado" to d.empleado.id, "mes" to d.inicio),
        ) { f, _ -> f.uuid("id") to f.getString("estado") }.firstOrNull()
        if (existente?.second == "pagado") return detalle("El rol de ese mes ya está pagado. Anúlalo antes si hay que rehacerlo.")
        val calculo = calculos.rol(d.empleado.id, d.inicio, d.valores)
        val ahora = Timestamp.from(zona.ahora())
        val id = existente?.first ?: UUID.randomUUID()
        val parametros = d.enBruto + mapOf(
            "id" to id, "ahora" to ahora, "calculo" to json.writeValueAsString(calculo), "neto" to BigDecimal(calculo["neto"] as String),
            "costo" to BigDecimal(calculo["costo_empleador"] as String), "empleado" to d.empleado.id, "mes" to d.inicio,
            "usuario" to usuario.id, "clinica" to clinica, "dias" to d.valores.diasTrabajados,
        )
        if (existente != null) {
            jdbc.update(
                """update finanzas_rolpagos set updated_at = :ahora, dias_trabajados = :dias, horas_suplementarias = :horas_suplementarias,
                   horas_extraordinarias = :horas_extraordinarias, horas_extra = :horas_extra, otros_ingresos = :otros_ingresos,
                   anticipos = :anticipos, prestamos_iess = :prestamos_iess, otros_descuentos = :otros_descuentos,
                   calculo = :calculo::jsonb, neto = :neto, costo_empleador = :costo where id = :id""",
                parametros,
            )
        } else {
            jdbc.update(
                """insert into finanzas_rolpagos (id, created_at, updated_at, mes, dias_trabajados, horas_extra, otros_ingresos, anticipos,
                   prestamos_iess, otros_descuentos, calculo, neto, costo_empleador, estado, creado_por_id, egreso_id, empleado_id, tenant_id,
                   horas_extraordinarias, horas_suplementarias)
                   values (:id, :ahora, :ahora, :mes, :dias, :horas_extra, :otros_ingresos, :anticipos, :prestamos_iess, :otros_descuentos,
                   :calculo::jsonb, :neto, :costo, 'borrador', :usuario, null, :empleado, :clinica, :horas_extraordinarias, :horas_suplementarias)""",
                parametros,
            )
        }
        finanzas.auditar("save_payroll", "RolPagos", id, mapOf("mes" to anioMes(d.inicio), "neto" to calculo["neto"]))
        return ResponseEntity.status(if (existente == null) HttpStatus.CREATED else HttpStatus.OK).body(rol(id, calculo))
    }

    /** POST /api/v1/finanzas/roles/generar/ {"mes"} — un borrador para cada persona en relación de dependencia que no lo tenga. */
    @PostMapping("/api/v1/finanzas/roles/generar/")
    @Transactional
    fun generar(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        val usuario = permisos.exigir(PERMISO)
        val datos = datosSueltos(cuerpo)
        val (inicio, _) = Finanzas.mesDe(Finanzas.valor(datos, "mes"), finanzas.hoy()) ?: return detalle("El mes va como 2026-10.")
        val empleados = jdbc.query(
            """select p.usuario_id from finanzas_perfillaboral p inner join accounts_user u on (p.usuario_id = u.id)
               where p.tenant_id = :clinica and u.is_active and p.modalidad = 'dependencia'""",
            mapOf("clinica" to clinica),
        ) { f, _ -> f.uuid("usuario_id") }
        var creados = 0
        for (empleado in empleados) {
            val ya = jdbc.queryForObject(
                """select exists(select 1 from finanzas_rolpagos where tenant_id = :clinica and empleado_id = :empleado and mes = :mes
                   and not (estado = 'anulado'))""",
                mapOf("clinica" to clinica, "empleado" to empleado, "mes" to inicio), Boolean::class.java,
            )!!
            if (ya) continue
            val calculo = calculos.rol(empleado, inicio)
            val ahora = Timestamp.from(zona.ahora())
            jdbc.update(
                """insert into finanzas_rolpagos (id, created_at, updated_at, mes, dias_trabajados, horas_extra, otros_ingresos, anticipos,
                   prestamos_iess, otros_descuentos, calculo, neto, costo_empleador, estado, creado_por_id, egreso_id, empleado_id, tenant_id,
                   horas_extraordinarias, horas_suplementarias)
                   values (:id, :ahora, :ahora, :mes, 30, 0.00, 0.00, 0.00, 0.00, 0.00, :calculo::jsonb, :neto, :costo, 'borrador', :usuario,
                   null, :empleado, :clinica, 0.00, 0.00)""",
                mapOf("id" to UUID.randomUUID(), "ahora" to ahora, "mes" to inicio, "calculo" to json.writeValueAsString(calculo),
                    "neto" to BigDecimal(calculo["neto"] as String), "costo" to BigDecimal(calculo["costo_empleador"] as String),
                    "usuario" to usuario.id, "empleado" to empleado, "clinica" to clinica),
            )
            creados++
        }
        return ResponseEntity.ok(mapOf("creados" to creados))
    }

    private data class Guardado(val id: UUID, val estado: String, val egreso: UUID?, val empleado: UUID, val nombre: String, val mes: LocalDate,
                                val valores: CalculosDeFinanzas.ValoresDelRol)

    private fun guardado(id: UUID, bloquear: Boolean = false): Guardado = jdbc.query(
        "$SELECT where r.id = :id and r.tenant_id = :clinica" + if (bloquear) " for update of r" else "",
        mapOf("id" to id, "clinica" to clinica),
    ) { f, _ ->
        Guardado(
            f.uuid("id"), f.getString("estado"), f.uuidONulo("egreso_id"), f.uuid("empleado_id"), f.getString("empleado_nombre"), f.fecha("mes"),
            CalculosDeFinanzas.ValoresDelRol(
                f.getInt("dias_trabajados"), f.getBigDecimal("horas_suplementarias"), f.getBigDecimal("horas_extraordinarias"),
                f.getBigDecimal("horas_extra"), f.getBigDecimal("otros_ingresos"), f.getBigDecimal("anticipos"),
                f.getBigDecimal("prestamos_iess"), f.getBigDecimal("otros_descuentos"),
            ),
        )
    }.firstOrNull() ?: throw ExcepcionApi.http404("RolPagos")

    /**
     * POST /api/v1/finanzas/roles/{id}/pagar/ — se recalcula ANTES de pagar
     * (un anticipo apuntado después del borrador tiene que estar en lo que se
     * paga), y ese recálculo se queda aunque la forma de pago no valga.
     */
    @PostMapping("/api/v1/finanzas/roles/{id}/pagar/")
    @Transactional
    fun pagar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val r = guardado(id, bloquear = true)
        if (r.estado != "borrador") return detalle("Solo se paga un rol pendiente.")
        val calculo = calculos.rol(r.empleado, r.mes, r.valores)
        val neto = BigDecimal(calculo["neto"] as String)
        jdbc.update(
            "update finanzas_rolpagos set calculo = :calculo::jsonb, neto = :neto, costo_empleador = :costo, updated_at = :ahora where id = :id",
            mapOf("calculo" to json.writeValueAsString(calculo), "neto" to neto, "costo" to BigDecimal(calculo["costo_empleador"] as String),
                "ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        val datos = datosSueltos(cuerpo)
        val forma = forma(datos) ?: return detalle("Forma de pago desconocida.")
        val fecha = Finanzas.fecha(Finanzas.valor(datos, "fecha"), finanzas.hoy()) ?: errorInterno("fecha nula")
        val egreso = finanzas.crearEgreso(
            fecha, finanzas.categoria("sueldos"), "Sueldo ${r.nombre} — ${MESES[r.mes.monthValue - 1]} ${r.mes.year}", neto, forma,
            profesional = r.empleado, origen = "rol",
        )
        jdbc.update(
            "update finanzas_rolpagos set egreso_id = :egreso, estado = 'pagado', updated_at = :ahora where id = :id",
            mapOf("egreso" to egreso, "ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        finanzas.auditar("pay_payroll", "RolPagos", id, mapOf("mes" to anioMes(r.mes)))
        return ResponseEntity.ok(rol(id, calculo))
    }

    /** POST /api/v1/finanzas/roles/{id}/anular/ — también anula su egreso, si se pagó. */
    @PostMapping("/api/v1/finanzas/roles/{id}/anular/")
    @Transactional
    fun anular(@PathVariable id: UUID): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val r = guardado(id)
        if (r.estado == "anulado") return detalle("Ya estaba anulado.")
        r.egreso?.let {
            jdbc.update(
                "update finanzas_egreso set anulado = true, motivo_anulacion = 'Se anuló el rol de pagos' where id = :id", mapOf("id" to it),
            )
        }
        jdbc.update("update finanzas_rolpagos set estado = 'anulado', updated_at = :ahora where id = :id",
            mapOf("ahora" to Timestamp.from(zona.ahora()), "id" to id))
        finanzas.auditar("void_payroll", "RolPagos", id, mapOf("mes" to anioMes(r.mes)))
        return ResponseEntity.ok(rol(id))
    }

    /** GET /api/v1/finanzas/roles/{id}/pdf/ — `rol-2026-10-ana.pdf`, con el primer nombre en minúsculas. */
    @GetMapping("/api/v1/finanzas/roles/{id}/pdf/")
    @Transactional(readOnly = true)
    fun pdf(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        permisos.exigir(PERMISO)
        val r = guardado(id)
        val nombre = r.nombre.ifEmpty { "empleado" }.split(" ")[0].lowercase()
        return DocumentosDeFinanzas.respuesta(documentos.rol(id), "rol-${anioMes(r.mes)}-$nombre.pdf")
    }
}
