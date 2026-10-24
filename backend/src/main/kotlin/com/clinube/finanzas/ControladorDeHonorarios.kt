package com.clinube.finanzas

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.Finanzas.Companion.FORMAS_PAGO
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.cifras
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.dinero
import com.clinube.finanzas.Finanzas.Companion.error
import com.clinube.finanzas.Finanzas.Companion.errorInterno
import com.clinube.finanzas.Finanzas.Companion.texto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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
 * Las liquidaciones de honorarios (`Liquidacion*View`): lo que se le paga a
 * quien factura a la clínica, por un porcentaje de lo cobrado o un fijo.
 */
@RestController
class ControladorDeHonorarios(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val documentos: DocumentosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val json: ObjectMapper,
) {

    companion object {
        val ESTADOS = linkedMapOf("borrador" to "Por pagar", "pagada" to "Pagada", "anulada" to "Anulada")

        /** El cuerpo de una vista que lo lee a mano: un objeto, o el 500 de Python al pedirle `.get` a otra cosa. */
        fun datosSueltos(cuerpo: JsonNode?): JsonNode = when {
            cuerpo == null || cuerpo.isMissingNode -> JsonNodeFactory.instance.objectNode()
            cuerpo.isObject -> cuerpo
            else -> errorInterno("request.data no es un diccionario")
        }

        /** `request.data.get("forma_pago") or por_defecto`, y la forma tiene que existir. */
        fun forma(datos: JsonNode, porDefecto: String = "transferencia"): String? {
            val pedida = Finanzas.valor(datos, "forma_pago")
            val forma = if (Python.verdad(pedida)) pedida!!.takeIf { it.isTextual }?.textValue() else porDefecto
            return forma?.takeIf { it in FORMAS_PAGO }
        }
    }

    private val clinica get() = finanzas.clinica

    val SELECT = """select l.*, l.detalle::text as detalle_texto, u.full_name as profesional_nombre from finanzas_liquidacion l
        inner join accounts_user u on (l.profesional_id = u.id)"""

    /** `LiquidacionSerializer`. El detalle, tal como se guardó (o el recién calculado, en el alta). */
    fun liquidacion(f: ResultSet, detalle: Any? = null): Map<String, Any?> {
        val estado = f.getString("estado")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "numero" to f.getLong("numero"),
            "profesional" to f.uuid("profesional_id").toString(),
            "profesional_nombre" to f.getString("profesional_nombre"),
            "desde" to f.fecha("desde").toString(),
            "hasta" to f.fecha("hasta").toString(),
            "base" to dinero(f.getBigDecimal("base")),
            "porcentaje" to dinero(f.getBigDecimal("porcentaje")),
            "honorario" to dinero(f.getBigDecimal("honorario")),
            "descuentos" to dinero(f.getBigDecimal("descuentos")),
            "ajustes" to dinero(f.getBigDecimal("ajustes")),
            "retencion_porcentaje" to dinero(f.getBigDecimal("retencion_porcentaje")),
            "retencion" to dinero(f.getBigDecimal("retencion")),
            "a_pagar" to dinero(f.getBigDecimal("a_pagar")),
            "detalle" to (detalle ?: json.readTree(f.getString("detalle_texto"))),
            "nota" to f.getString("nota"),
            "estado" to estado,
            "estado_nombre" to (ESTADOS[estado] ?: estado),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }

    private fun liquidacion(id: UUID, detalle: Any? = null) =
        jdbc.query("$SELECT where l.id = :id", mapOf("id" to id)) { f, _ -> liquidacion(f, detalle) }.first()

    private data class Datos(val profesional: Finanzas.Persona, val desde: LocalDate, val hasta: LocalDate, val ajustes: BigDecimal)

    /** `_datos_liquidacion`: el profesional (o 404), el período y un ajuste opcional. */
    private fun datos(fuente: JsonNode): Datos {
        val profesional = finanzas.delEquipo(Finanzas.valor(fuente, "profesional"))
        val desde = Finanzas.fecha(Finanzas.valor(fuente, "desde"), null)
        val hasta = Finanzas.fecha(Finanzas.valor(fuente, "hasta"), null)
        val ajustes = Finanzas.decimal(Finanzas.valor(fuente, "ajustes"))
        if (desde == null || hasta == null || desde > hasta || ajustes == null) {
            error("Indica el período (desde y hasta) y, si hay, un ajuste en dólares.")
        }
        return Datos(profesional, desde, hasta, ajustes)
    }

    /** GET /api/v1/finanzas/liquidaciones/previa/?profesional=&desde=&hasta=&ajustes= — el cálculo, sin guardar. */
    @GetMapping("/api/v1/finanzas/liquidaciones/previa/")
    @Transactional
    fun previa(peticion: HttpServletRequest): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val d = datos(consulta(peticion))
        val r = calculos.liquidacion(d.profesional.id, d.desde, d.hasta, d.ajustes)
        return linkedMapOf(
            "base" to texto(r.base), "porcentaje" to texto(r.porcentaje), "honorario" to texto(r.honorario),
            "descuentos" to texto(r.descuentos), "ajustes" to texto(r.ajustes), "retencion_porcentaje" to texto(r.retencionPorcentaje),
            "retencion" to texto(r.retencion), "a_pagar" to texto(r.aPagar), "detalle" to r.detalle,
        )
    }

    /** GET /api/v1/finanzas/liquidaciones/?profesional= — las 200 últimas, por período y número. */
    @GetMapping("/api/v1/finanzas/liquidaciones/")
    fun lista(peticion: HttpServletRequest): List<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        val p = consulta(peticion)
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        var filtro = ""
        if (Python.verdad(p.get("profesional"))) {
            filtro = " and l.profesional_id = :profesional"
            parametros["profesional"] = CamposDrf.uuidDePython(p.get("profesional").asText()) ?: errorInterno("profesional: ${p.get("profesional")}")
        }
        return jdbc.query(
            "$SELECT where l.tenant_id = :clinica$filtro order by l.hasta desc, l.numero desc limit 200", parametros,
        ) { f, _ -> liquidacion(f) }
    }

    /**
     * POST /api/v1/finanzas/liquidaciones/ — la crea por pagar, con el
     * siguiente número de la clínica. No se solapa con otra vigente del
     * mismo profesional.
     */
    @PostMapping("/api/v1/finanzas/liquidaciones/")
    @Transactional
    fun alta(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        val usuario = permisos.exigir(PERMISO)
        val fuente = datosSueltos(cuerpo)
        val d = datos(fuente)
        val solapa = jdbc.query(
            """select numero from finanzas_liquidacion where tenant_id = :clinica and profesional_id = :profesional and desde <= :hasta
               and hasta >= :desde and not (estado = 'anulada') order by hasta desc, numero desc limit 1""",
            mapOf("clinica" to clinica, "profesional" to d.profesional.id, "desde" to d.desde, "hasta" to d.hasta),
        ) { f, _ -> f.getLong("numero") }.firstOrNull()
        if (solapa != null) {
            return detalle("Ya hay una liquidación de ${d.profesional.nombre} que cubre parte de ese período (N.º ${cifras(solapa, 5)}). " +
                "Anúlala antes si hay que rehacerla.")
        }
        val r = calculos.liquidacion(d.profesional.id, d.desde, d.hasta, d.ajustes)
        val numero = jdbc.queryForObject(
            "select coalesce(max(numero), 0) + 1 from finanzas_liquidacion where tenant_id = :clinica", mapOf("clinica" to clinica), Long::class.java,
        )!!
        val nota = Finanzas.valor(fuente, "nota").let {
            when {
                !Python.verdad(it) -> ""
                it!!.isTextual -> it.textValue().let { t -> if (Python.longitud(t) <= 300) t else t.substring(0, t.offsetByCodePoints(0, 300)) }
                else -> errorInterno("nota no es un texto")
            }
        }
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into finanzas_liquidacion (id, created_at, updated_at, numero, desde, hasta, base, porcentaje, honorario, descuentos,
               ajustes, retencion_porcentaje, retencion, a_pagar, detalle, nota, estado, creada_por_id, egreso_id, profesional_id, tenant_id)
               values (:id, :ahora, :ahora, :numero, :desde, :hasta, :base, :porcentaje, :honorario, :descuentos, :ajustes, :retencionPct,
               :retencion, :aPagar, :detalle::jsonb, :nota, 'borrador', :usuario, null, :profesional, :clinica)""",
            mapOf(
                "id" to id, "ahora" to ahora, "numero" to numero, "desde" to d.desde, "hasta" to d.hasta, "base" to r.base,
                "porcentaje" to r.porcentaje, "honorario" to r.honorario, "descuentos" to r.descuentos, "ajustes" to r.ajustes,
                "retencionPct" to r.retencionPorcentaje, "retencion" to r.retencion, "aPagar" to r.aPagar,
                "detalle" to json.writeValueAsString(r.detalle), "nota" to nota, "usuario" to usuario.id,
                "profesional" to d.profesional.id, "clinica" to clinica,
            ),
        )
        finanzas.auditar("create_settlement", "Liquidacion", id, mapOf("numero" to numero, "a_pagar" to texto(r.aPagar)))
        return ResponseEntity.status(HttpStatus.CREATED).body(liquidacion(id, r.detalle))
    }

    private data class Guardada(val id: UUID, val numero: Long, val estado: String, val egreso: UUID?, val aPagar: BigDecimal,
                                val profesional: UUID, val profesionalNombre: String)

    private fun guardada(id: UUID, bloquear: Boolean = false): Guardada = jdbc.query(
        """select l.id, l.numero, l.estado, l.egreso_id, l.a_pagar, l.profesional_id, u.full_name from finanzas_liquidacion l
           inner join accounts_user u on (l.profesional_id = u.id) where l.id = :id and l.tenant_id = :clinica""" +
            if (bloquear) " for update of l" else "",
        mapOf("id" to id, "clinica" to clinica),
    ) { f, _ ->
        Guardada(f.uuid("id"), f.getLong("numero"), f.getString("estado"), f.uuidONulo("egreso_id"), f.getBigDecimal("a_pagar"),
            f.uuid("profesional_id"), f.getString("full_name"))
    }.firstOrNull() ?: throw ExcepcionApi.http404("Liquidacion")

    /** POST /api/v1/finanzas/liquidaciones/{id}/pagar/ {"fecha", "forma_pago"} — la paga y la apunta como egreso de honorarios. */
    @PostMapping("/api/v1/finanzas/liquidaciones/{id}/pagar/")
    @Transactional
    fun pagar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val liq = guardada(id, bloquear = true)
        if (liq.estado != "borrador") return detalle("Solo se paga una liquidación pendiente.")
        val datos = datosSueltos(cuerpo)
        val forma = forma(datos) ?: return detalle("Forma de pago desconocida.")
        val fecha = Finanzas.fecha(Finanzas.valor(datos, "fecha"), finanzas.hoy()) ?: errorInterno("fecha nula")
        val egreso = finanzas.crearEgreso(
            fecha, finanzas.categoria("honorarios"), "Honorarios ${liq.profesionalNombre} — liquidación N.º ${cifras(liq.numero, 5)}",
            liq.aPagar, forma, profesional = liq.profesional, origen = "liquidacion",
        )
        jdbc.update(
            "update finanzas_liquidacion set egreso_id = :egreso, estado = 'pagada', updated_at = :ahora where id = :id",
            mapOf("egreso" to egreso, "ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        finanzas.auditar("pay_settlement", "Liquidacion", id, mapOf("numero" to liq.numero))
        return ResponseEntity.ok(liquidacion(id))
    }

    /** POST /api/v1/finanzas/liquidaciones/{id}/anular/ — también anula su egreso, si se pagó. */
    @PostMapping("/api/v1/finanzas/liquidaciones/{id}/anular/")
    @Transactional
    fun anular(@PathVariable id: UUID): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val liq = guardada(id)
        if (liq.estado == "anulada") return detalle("Ya estaba anulada.")
        // `QuerySet.update()`: sin tocar `updated_at` del egreso.
        liq.egreso?.let {
            jdbc.update(
                "update finanzas_egreso set anulado = true, motivo_anulacion = :motivo where id = :id",
                mapOf("motivo" to "Se anuló la liquidación N.º ${cifras(liq.numero, 5)}", "id" to it),
            )
        }
        jdbc.update(
            "update finanzas_liquidacion set estado = 'anulada', updated_at = :ahora where id = :id",
            mapOf("ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        finanzas.auditar("void_settlement", "Liquidacion", id, mapOf("numero" to liq.numero))
        return ResponseEntity.ok(liquidacion(id))
    }

    /** GET /api/v1/finanzas/liquidaciones/{id}/pdf/ — `liquidacion-00001.pdf`. */
    @GetMapping("/api/v1/finanzas/liquidaciones/{id}/pdf/")
    @Transactional(readOnly = true)
    fun pdf(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        permisos.exigir(PERMISO)
        val liq = guardada(id)
        return DocumentosDeFinanzas.respuesta(documentos.liquidacion(id), "liquidacion-${cifras(liq.numero, 5)}.pdf")
    }
}
