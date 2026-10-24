package com.clinube.finanzas

import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.dinero
import com.clinube.finanzas.Finanzas.Companion.errorInterno
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * La caja del día (`CajaView`): lo que tendría que haber en efectivo y su
 * cierre. Cerrar dos veces el mismo día corrige el cierre, no crea otro.
 */
@RestController
class ControladorDeCaja(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    private val clinica get() = finanzas.clinica

    private val SELECT = """select c.*, u.full_name as cerrado_por_nombre from finanzas_cierrecaja c
        left outer join accounts_user u on (c.cerrado_por_id = u.id)"""

    /** `CierreCajaSerializer`. */
    private fun cierre(f: ResultSet): Map<String, Any?> = linkedMapOf(
        "id" to f.uuid("id").toString(),
        "fecha" to f.fecha("fecha").toString(),
        "saldo_inicial" to dinero(f.getBigDecimal("saldo_inicial")),
        "cobros_efectivo" to dinero(f.getBigDecimal("cobros_efectivo")),
        "egresos_efectivo" to dinero(f.getBigDecimal("egresos_efectivo")),
        "esperado" to dinero(f.getBigDecimal("esperado")),
        "contado" to dinero(f.getBigDecimal("contado")),
        "diferencia" to dinero(f.getBigDecimal("diferencia")),
        "retirado" to dinero(f.getBigDecimal("retirado")),
        "observaciones" to f.getString("observaciones"),
        "cerrado_por_nombre" to if (f.uuidONulo("cerrado_por_id") != null) f.getString("cerrado_por_nombre") else "",
        "updated_at" to FormatoDeDrf.deDrf(f.instante("updated_at"), zona.zona),
    )

    /**
     * GET /api/v1/finanzas/caja/?fecha= — lo esperado del día, su cierre si
     * lo hay y los diez últimos. Un cobro o un pago en efectivo apuntado
     * después de cerrar cambia lo esperado: se avisa con `desactualizado`.
     */
    @GetMapping("/api/v1/finanzas/caja/")
    fun ver(peticion: HttpServletRequest): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val dia = Finanzas.fecha(consulta(peticion).get("fecha"), finanzas.hoy()) ?: return detalle("Fecha no válida.")
        val parametros = mapOf("clinica" to clinica, "dia" to dia)
        val esperadoAlCerrar = jdbc.query(
            "select esperado from finanzas_cierrecaja where tenant_id = :clinica and fecha = :dia order by fecha desc limit 1", parametros,
        ) { f, _ -> f.getBigDecimal("esperado") }.firstOrNull()
        val delDia = jdbc.query("$SELECT where c.tenant_id = :clinica and c.fecha = :dia order by c.fecha desc limit 1", parametros) { f, _ -> cierre(f) }
            .firstOrNull()
        val cuerpo = calculos.caja(dia)
        cuerpo["desactualizado"] = esperadoAlCerrar != null && BigDecimal(cuerpo["esperado"] as String).compareTo(esperadoAlCerrar) != 0
        cuerpo["cierre"] = delDia
        cuerpo["ultimos"] = jdbc.query("$SELECT where c.tenant_id = :clinica order by c.fecha desc limit 10", parametros) { f, _ -> cierre(f) }
        return ResponseEntity.ok(cuerpo)
    }

    /**
     * POST /api/v1/finanzas/caja/ {"fecha", "contado", "retirado", "observaciones"} —
     * cierra (o corrige el cierre de) un día que ya llegó.
     */
    @PostMapping("/api/v1/finanzas/caja/")
    @Transactional
    fun cerrar(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        val usuario = permisos.exigir(PERMISO)
        val datos = when {
            cuerpo == null || cuerpo.isMissingNode -> com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            cuerpo.isObject -> cuerpo
            else -> errorInterno("request.data no es un diccionario")
        }
        val dia = Finanzas.fecha(Finanzas.valor(datos, "fecha"), finanzas.hoy())
        val contado = Finanzas.decimal(Finanzas.valor(datos, "contado"), null)
        val retirado = Finanzas.decimal(Finanzas.valor(datos, "retirado"), "0")
        if (dia == null || contado == null || retirado == null || contado.signum() < 0 || retirado.signum() < 0) {
            return detalle("Indica el efectivo contado (y lo retirado, si hubo).")
        }
        if (retirado > contado) return detalle("No se puede retirar más de lo que hay en caja.")
        if (dia > finanzas.hoy()) return detalle("No se cierra un día que aún no llega.")
        val crudas = Finanzas.valor(datos, "observaciones")
        val observaciones = when {
            !Python.verdad(crudas) -> ""
            crudas!!.isTextual -> crudas.textValue().let { if (Python.longitud(it) <= 300) it else it.substring(0, it.offsetByCodePoints(0, 300)) }
            else -> errorInterno("observaciones no es un texto")
        }

        val c = calculos.caja(dia)
        val esperado = BigDecimal(c["esperado"] as String)
        val diferencia = contado - esperado
        val ahora = Timestamp.from(zona.ahora())
        val valores = mapOf(
            "saldo" to BigDecimal(c["saldo_inicial"] as String), "cobros" to BigDecimal(c["cobros_efectivo"] as String),
            "egresos" to BigDecimal(c["egresos_efectivo"] as String), "esperado" to esperado, "contado" to contado,
            "diferencia" to diferencia, "retirado" to retirado, "observaciones" to observaciones, "usuario" to usuario.id,
            "ahora" to ahora, "clinica" to clinica, "dia" to dia,
        )
        // `update_or_create`: el del día si ya existe, con sus valores nuevos.
        val existente = jdbc.query(
            "select id from finanzas_cierrecaja where tenant_id = :clinica and fecha = :dia for update", valores,
        ) { f, _ -> f.uuid("id") }.firstOrNull()
        val id = existente ?: UUID.randomUUID()
        if (existente != null) {
            jdbc.update(
                """update finanzas_cierrecaja set saldo_inicial = :saldo, cobros_efectivo = :cobros, egresos_efectivo = :egresos,
                   esperado = :esperado, contado = :contado, diferencia = :diferencia, retirado = :retirado, observaciones = :observaciones,
                   cerrado_por_id = :usuario, updated_at = :ahora where id = :id""",
                valores + ("id" to id),
            )
        } else {
            jdbc.update(
                """insert into finanzas_cierrecaja (id, created_at, updated_at, fecha, saldo_inicial, cobros_efectivo, egresos_efectivo,
                   esperado, contado, diferencia, retirado, observaciones, cerrado_por_id, tenant_id)
                   values (:id, :ahora, :ahora, :dia, :saldo, :cobros, :egresos, :esperado, :contado, :diferencia, :retirado, :observaciones,
                   :usuario, :clinica)""",
                valores + ("id" to id),
            )
        }
        finanzas.auditar("close_cash", "CierreCaja", id, mapOf("fecha" to dia.toString(), "diferencia" to diferencia.toPlainString()))
        // La respuesta es la del objeto en memoria, con lo que llegó a céntimos.
        return ResponseEntity.status(HttpStatus.CREATED).body(linkedMapOf(
            "id" to id.toString(),
            "fecha" to dia.toString(),
            "saldo_inicial" to dinero(valores["saldo"] as BigDecimal),
            "cobros_efectivo" to dinero(valores["cobros"] as BigDecimal),
            "egresos_efectivo" to dinero(valores["egresos"] as BigDecimal),
            "esperado" to dinero(esperado),
            "contado" to dinero(contado),
            "diferencia" to dinero(diferencia),
            "retirado" to dinero(retirado),
            "observaciones" to observaciones,
            "cerrado_por_nombre" to usuario.nombreCompleto,
            "updated_at" to FormatoDeDrf.deDrf(ahora.toInstant(), zona.zona),
        ))
    }
}
