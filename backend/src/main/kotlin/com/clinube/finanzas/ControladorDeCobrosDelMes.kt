package com.clinube.finanzas

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.ControladorDeHonorarios.Companion.datosSueltos
import com.clinube.finanzas.Finanzas.Companion.FORMAS_COBRO
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.cifras
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.errorInterno
import com.clinube.finanzas.Finanzas.Companion.texto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.util.UUID

/**
 * Los cobros vistos desde Finanzas (`CobrosView`): la lista del mes con su
 * profesional, la corrección de a quién corresponde un cobro y el
 * comprobante en PDF (`ComprobanteCobroView`), que también se saca desde la
 * ficha y desde Pagos.
 */
@RestController
class ControladorDeCobrosDelMes(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val documentos: DocumentosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val json: ObjectMapper,
) {

    private val clinica get() = finanzas.clinica

    /**
     * GET /api/v1/finanzas/cobros/?mes= — los 500 últimos del mes, con su
     * profesional y su número de comprobante. Un mes que no se entiende es
     * un 500 en Django (aquí `mes_de` no está protegido).
     */
    @GetMapping("/api/v1/finanzas/cobros/")
    fun lista(peticion: HttpServletRequest): List<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        val (desde, hasta) = Finanzas.mesDe(consulta(peticion).get("mes"), finanzas.hoy()) ?: errorInterno("mes_de")
        return jdbc.query(
            """select p.*, pa.first_name, pa.last_name, u.full_name as profesional_nombre from billing_payment p
               inner join patients_patient pa on (p.patient_id = pa.id) left outer join accounts_user u on (p.profesional_id = u.id)
               where p.tenant_id = :clinica and p.date >= :desde and p.date <= :hasta order by p.date desc, p.created_at desc limit 500""",
            mapOf("clinica" to clinica, "desde" to desde, "hasta" to hasta),
        ) { f, _ ->
            val metodo = f.getString("method")
            val profesional = f.uuidONulo("profesional_id")
            linkedMapOf(
                "id" to f.uuid("id").toString(),
                "fecha" to f.fecha("date").toString(),
                "paciente" to "${f.getString("first_name")} ${f.getString("last_name")}",
                "monto" to texto(f.getBigDecimal("amount")),
                "forma" to (FORMAS_COBRO[metodo] ?: metodo),
                "profesional" to profesional?.toString(),
                "profesional_nombre" to if (profesional != null) f.getString("profesional_nombre") else "",
                "numero_comprobante" to (f.getObject("numero_comprobante") as Number?)?.toLong(),
            )
        }
    }

    /**
     * La misma vista atiende las dos rutas y cada método espera la suya:
     * GET con id o PATCH sin él revientan en Django (500) por el argumento.
     */
    @GetMapping("/api/v1/finanzas/cobros/{id}/")
    fun verUno(@PathVariable id: UUID): Nothing {
        permisos.exigir(PERMISO)
        errorInterno("CobrosView.get() no recibe pk")
    }

    @PatchMapping("/api/v1/finanzas/cobros/")
    fun corregirSinId(): Nothing {
        permisos.exigir(PERMISO)
        errorInterno("CobrosView.patch() necesita pk")
    }

    /**
     * PATCH /api/v1/finanzas/cobros/{id}/ {"profesional"} — corrige a qué
     * profesional corresponde. No si ya entró en una liquidación vigente de
     * quien lo tenía: le habría pagado honorarios a quien no era.
     */
    @PatchMapping("/api/v1/finanzas/cobros/{id}/")
    @Transactional
    fun corregir(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val pago = jdbc.query(
            "select profesional_id, date from billing_payment where id = :id and tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> f.uuidONulo("profesional_id") to f.fecha("date") }.firstOrNull() ?: throw ExcepcionApi.http404("Payment")
        val valor = Finanzas.valor(datosSueltos(cuerpo), "profesional")
        val profesional = if (Python.verdad(valor)) finanzas.delEquipo(valor) else null
        val (anterior, fecha) = pago
        if (anterior != null) {
            val enlazada = jdbc.query(
                """select numero, desde, hasta, detalle::text as detalle from finanzas_liquidacion
                   where tenant_id = :clinica and profesional_id = :profesional and not (estado = 'anulada') order by hasta desc, numero desc""",
                mapOf("clinica" to clinica, "profesional" to anterior),
            ) { f, _ -> Triple(f.getLong("numero"), f.fecha("desde")..f.fecha("hasta"), json.readTree(f.getString("detalle"))) }
                .firstOrNull { (_, rango, d) ->
                    val ids = d.get("cobros_ids")
                    (ids?.isArray == true && ids.any { it.asText() == id.toString() }) || (!d.has("cobros_ids") && fecha in rango)
                }
            if (enlazada != null) {
                return detalle("Ese cobro ya entró en la liquidación N.º ${cifras(enlazada.first, 5)}. Anúlala antes de cambiarlo.")
            }
        }
        // `save(update_fields=["profesional"])`: sin tocar `updated_at`.
        jdbc.update("update billing_payment set profesional_id = :profesional where id = :id", mapOf("profesional" to profesional?.id, "id" to id))
        finanzas.auditar("reassign_payment", "Payment", id, mapOf("profesional" to (profesional?.id?.toString() ?: "")))
        return ResponseEntity.ok(linkedMapOf(
            "id" to id.toString(), "profesional" to profesional?.id?.toString(), "profesional_nombre" to (profesional?.nombre ?: ""),
        ))
    }

    /**
     * GET /api/v1/finanzas/cobros/{id}/comprobante/ — el PDF del
     * comprobante. El número se asigna la primera vez, del contador de la
     * clínica, y ya no cambia: reimprimir da el mismo. Lo saca quien registra
     * cobros, no solo quien ve las finanzas, si la clínica tiene Finanzas.
     */
    @GetMapping("/api/v1/finanzas/cobros/{id}/comprobante/")
    @Transactional
    fun comprobante(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        permisos.exigir(Permisos.funcion("cobros"), Permisos.funcionalidad("finanzas"))
        var numero = jdbc.query(
            "select numero_comprobante from billing_payment where id = :id and tenant_id = :clinica for update",
            mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> (f.getObject("numero_comprobante") as Number?)?.toLong() ?: 0L }.firstOrNull() ?: throw ExcepcionApi.http404("Payment")
        // `if not pago.numero_comprobante`: un 0 (si el contador se puso a 0) también se renumera.
        if (numero == 0L) {
            val config = finanzas.configuracion(bloquear = true)
            numero = config.siguienteComprobante
            jdbc.update(
                "update finanzas_configuracionfinanzas set siguiente_comprobante = :siguiente, updated_at = :ahora where id = :id",
                mapOf("siguiente" to numero + 1, "ahora" to Timestamp.from(zona.ahora()), "id" to config.id),
            )
            jdbc.update("update billing_payment set numero_comprobante = :numero where id = :id", mapOf("numero" to numero, "id" to id))
        }
        return DocumentosDeFinanzas.respuesta(documentos.comprobanteDeCobro(id, numero), "comprobante-${cifras(numero, 6)}.pdf")
    }
}
