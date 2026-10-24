package com.clinube.cobros

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.pacientes.ControladorDeFichas
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

/**
 * Los cobros del paciente desde su ficha y su estado de cuenta
 * (`apps/billing/views.py`, RF-TRP-03/04).
 */
@RestController
class ControladorDePagos(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val cobros: Cobros,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
    }

    private val clinica get() = contexto.clinica?.id

    /** GET /api/v1/patients/{id}/payments/ — paginada, por fecha y creación, el más reciente primero. */
    @GetMapping("/api/v1/patients/$ID/payments/")
    fun lista(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(ControladorDePresupuestos.COBRAN)
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val total = jdbc.queryForObject(
            "select count(*) from billing_payment where tenant_id = :clinica and patient_id = :id", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """${cobros.SELECT_PAGO} where p.tenant_id = :clinica and p.patient_id = :id
               order by p.date desc, p.created_at desc limit :cuantos offset :desde""",
            parametros,
        ) { f, _ -> cobros.pago(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/patients/{id}/payments/ — un cobro directo, normalmente
     * sin cuota. Se valida antes de buscar al paciente, que basta con que
     * sea de la clínica (como en Django, aunque esté dado de baja). Una
     * cuota indicada aquí NO se marca como pagada: eso lo hace su ruta.
     *
     * DIFERENCIA DELIBERADA: la cuota tiene que ser de esta clínica (Django
     * la busca en todas).
     */
    @PostMapping("/api/v1/patients/$ID/payments/")
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(ControladorDePresupuestos.COBRAN)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val cuota = campos.clave("installment", requerido = false, nulo = true) {
            jdbc.queryForObject(
                "select count(*) from billing_installment where id = :id and tenant_id = :clinica",
                mapOf("id" to it, "clinica" to clinica), Long::class.java,
            )!! > 0
        }
        val importe = campos.decimal("amount", maxDigitos = 10, decimales = 2, requerido = true)
        val metodo = campos.opcion("method", Cobros.METODOS.keys, requerido = true)
        val fecha = campos.fecha("date")
        val profesional = cobros.leerProfesional(campos)
        campos.validar()
        val existe = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404("Patient")
        val decimal = (importe as Leido.Valor).valor!!
        val nuevo = cobros.registrarPago(
            paciente = id, cuota = (cuota as? Leido.Valor)?.valor, importe = decimal.valor,
            metodo = (metodo as Leido.Valor).valor!!, fecha = (fecha as Leido.Valor).valor!!,
            registradoPor = usuario.id, profesional = profesional ?: cobros.profesionalHabitual(id),
        )
        val pago = LinkedHashMap(jdbc.query("${cobros.SELECT_PAGO} where p.id = :id", mapOf("id" to nuevo)) { f, _ -> cobros.pago(f) }.first())
        // La respuesta es la del objeto en memoria: el importe tal como llegó (con su «-0.00»).
        pago["amount"] = decimal.texto
        return ResponseEntity.status(HttpStatus.CREATED).body(pago)
    }

    /**
     * GET /api/v1/patients/{id}/account-statement/ — total de sus cuotas, lo
     * pagado (TODOS sus pagos, también los sueltos), el saldo y las cuotas
     * vencidas: no pagadas y con vencimiento antes de hoy en la clínica. Los
     * importes son `str(Decimal)`: con dos decimales y, si toca, negativos.
     */
    @GetMapping("/api/v1/patients/$ID/account-statement/")
    fun estadoDeCuenta(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(ControladorDePresupuestos.VEN_Y_CREAN)
        val activo = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!activo) throw ExcepcionApi.http404("Patient")
        val parametros = mapOf("id" to id, "clinica" to clinica)
        data class Cuota(val importe: BigDecimal, val pagada: Boolean, val vencida: Boolean, val pagado: BigDecimal)
        val hoy = zona.hoy()
        val cuotas = jdbc.query(
            """select i.amount, i.status, i.due_date,
                      coalesce((select sum(p.amount) from billing_payment p where p.installment_id = i.id), 0) as pagado
               from billing_installment i where i.patient_id = :id and i.tenant_id = :clinica""",
            parametros,
        ) { f, _ ->
            Cuota(f.getBigDecimal("amount"), f.getString("status") == "paid", f.fecha("due_date").isBefore(hoy), f.getBigDecimal("pagado"))
        }
        val total = cuotas.fold(BigDecimal("0.00")) { suma, c -> suma + c.importe }
        val pagado = jdbc.queryForObject(
            "select coalesce(sum(amount), 0) from billing_payment where patient_id = :id and tenant_id = :clinica",
            parametros, BigDecimal::class.java,
        )!!.let { BigDecimal("0.00") + it }
        val vencidas = cuotas.filter { !it.pagada && it.vencida }
        return linkedMapOf(
            "patient_id" to id.toString(),
            "total_amount" to total.toPlainString(),
            "total_paid" to pagado.toPlainString(),
            "balance" to (total - pagado).toPlainString(),
            "overdue_count" to vencidas.size,
            "overdue_amount" to vencidas.fold(BigDecimal("0.00")) { suma, c -> suma + (c.importe - c.pagado) }.toPlainString(),
        )
    }
}
