package com.clinube.cobros

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.pacientes.ControladorDeFichas
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Presupuestos, planes de cuotas y cobro de cuotas (`apps/billing/views.py`,
 * RF-TRP-01 a 03): la pantalla de Pagos del panel.
 *
 * Permisos, como en Django: listar y crear presupuestos, administración,
 * recepción y doctores; tocarlos, aprobarlos, generar el plan y cobrar, quien
 * tenga la función «cobros»; ver las cuotas, esos tres roles O quien tenga la
 * función.
 */
@RestController
class ControladorDePresupuestos(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val cobros: Cobros,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val VEN_Y_CREAN = Permisos.rol("admin", "reception", "doctor")
        val COBRAN = Permisos.funcion("cobros")
        val VEN_COBROS = Permisos.o(Permisos.rol("admin", "reception", "doctor"), Permisos.funcion("cobros"))
    }

    private val clinica get() = contexto.clinica?.id

    // ── Presupuestos ─────────────────────────────────────────────────

    /** GET /api/v1/patients/{id}/budgets/ — paginada, el más reciente primero. */
    @GetMapping("/api/v1/patients/$ID/budgets/")
    fun lista(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(VEN_Y_CREAN)
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val total = jdbc.queryForObject(
            "select count(*) from billing_budget where patient_id = :id and tenant_id = :clinica", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """select * from billing_budget where patient_id = :id and tenant_id = :clinica
               order by created_at desc limit :cuantos offset :desde""",
            parametros,
        ) { f, _ -> cobros.presupuesto(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/patients/{id}/budgets/ — el serializador exige `patient`
     * en el cuerpo (de esta clínica), pero el presupuesto se crea para el
     * paciente de la dirección, que tiene que estar activo.
     */
    @PostMapping("/api/v1/patients/$ID/budgets/")
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(VEN_Y_CREAN)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val leidos = leerPresupuesto(campos)
        campos.validar()
        val activo = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!activo) throw ExcepcionApi.http404("Patient")
        val notas = (leidos["notes"] as String?) ?: ""
        val nuevo = UUID.randomUUID()
        jdbc.update(
            """insert into billing_budget (id, updated_at, status, total_amount, notes, created_at, created_by_id, patient_id, tenant_id)
               values (:id, :actualizado, 'draft', 0, :notas, :creado, :usuario, :paciente, :clinica)""",
            mapOf(
                "id" to nuevo, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "notas" to notas, "usuario" to usuario.id, "paciente" to id, "clinica" to clinica,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(cobros.presupuesto(nuevo))
    }

    /** GET /api/v1/budgets/{id}/ */
    @GetMapping("/api/v1/budgets/$ID/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(COBRAN)
        return presupuestoDeEstaClinica(id)
    }

    @PutMapping("/api/v1/budgets/$ID/")
    fun reemplazar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, parcial = false)

    @PatchMapping("/api/v1/budgets/$ID/")
    fun modificar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, parcial = true)

    /** PUT/PATCH /api/v1/budgets/{id}/ — el paciente (de esta clínica) y las notas; el estado y el total no. */
    private fun editar(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(COBRAN)
        presupuestoDeEstaClinica(id)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo), parcial)
        val leidos = leerPresupuesto(campos)
        campos.validar()
        val cambios = LinkedHashMap<String, Any?>()
        leidos["patient"]?.let { cambios["patient_id"] = it }
        if ("notes" in leidos) cambios["notes"] = leidos["notes"]
        val asignaciones = cambios.keys.map { "$it = :$it" } + "updated_at = :actualizado"
        jdbc.update(
            "update billing_budget set ${asignaciones.joinToString(", ")} where id = :id",
            cambios + mapOf("id" to id, "actualizado" to Timestamp.from(zona.ahora())),
        )
        return cobros.presupuesto(id)
    }

    /**
     * POST /api/v1/budgets/{id}/items/ — se valida antes de buscar el
     * presupuesto; la línea se añade y el total se recalcula sumando las
     * líneas (sin tocar `updated_at`).
     *
     * DIFERENCIA DELIBERADA: el tratamiento tiene que ser de esta clínica
     * (Django lo busca en todas).
     */
    @PostMapping("/api/v1/budgets/$ID/items/")
    fun nuevaLinea(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(COBRAN)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val tratamiento = campos.clave("treatment") {
            jdbc.queryForObject(
                "select count(*) from configuration_treatment where id = :id and tenant_id = :clinica",
                mapOf("id" to it, "clinica" to clinica), Long::class.java,
            )!! > 0
        }
        val pieza = campos.texto("tooth_fdi_code", maximo = 2, requerido = false, enBlanco = true)
        val cantidad = campos.entero("quantity", minimo = 0, maximo = 2147483647)
        val precio = campos.decimal("unit_price", maxDigitos = 10, decimales = 2, requerido = true)
        campos.validar()
        val existe = jdbc.queryForObject(
            "select count(*) from billing_budget where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404("Budget")
        val linea = UUID.randomUUID()
        val unidades = (cantidad as? Leido.Valor)?.valor ?: 1L
        val unitario = (precio as Leido.Valor).valor!!
        jdbc.update(
            """insert into billing_budgetitem (id, tooth_fdi_code, quantity, unit_price, budget_id, treatment_id)
               values (:id, :pieza, :cantidad, :precio, :presupuesto, :tratamiento)""",
            mapOf(
                "id" to linea, "pieza" to ((pieza as? Leido.Valor)?.valor ?: ""), "cantidad" to unidades,
                "precio" to unitario.valor, "presupuesto" to id, "tratamiento" to (tratamiento as Leido.Valor).valor,
            ),
        )
        cobros.recalcularTotal(id)
        // La respuesta es la del objeto en memoria: el precio tal como llegó (con su «-0.00»).
        val respuesta = LinkedHashMap(cobros.lineaDePresupuesto(linea))
        respuesta["unit_price"] = unitario.texto
        respuesta["subtotal"] = cobros.subtotal(unitario.valor, unidades, unitario.texto.startsWith("-"))
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta)
    }

    /**
     * POST /api/v1/budgets/{id}/approve/ — queda aprobado, lo estuviera o no
     * (también uno rechazado). Sin tocar `updated_at`.
     */
    @PostMapping("/api/v1/budgets/$ID/approve/")
    fun aprobar(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(COBRAN)
        presupuestoDeEstaClinica(id)
        jdbc.update("update billing_budget set status = 'approved' where id = :id", mapOf("id" to id))
        return cobros.presupuesto(id)
    }

    // ── Plan de cuotas ───────────────────────────────────────────────

    /**
     * POST /api/v1/budgets/{id}/generate-payment-plan/ — cuotas mensuales
     * iguales (redondeo comercial a céntimos) desde `first_due_date`; la
     * última absorbe la diferencia para que la suma sea exactamente el
     * total. El mes siguiente a un 31 es el último día del mes, como
     * `relativedelta`. El orden de las comprobaciones es el de Django:
     * aprobado (400), sin plan (409) y DESPUÉS los datos.
     */
    @PostMapping("/api/v1/budgets/$ID/generate-payment-plan/")
    @Transactional
    fun generarPlan(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(COBRAN)
        val presupuesto = jdbc.query(
            """select b.status, b.total_amount, b.patient_id, (select count(*) from billing_paymentplan p where p.budget_id = b.id) as planes
               from billing_budget b where b.id = :id and b.tenant_id = :clinica""",
            mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> Triple(f.getString("status"), f.getBigDecimal("total_amount"), f.uuid("patient_id")) to f.getLong("planes") }
            .firstOrNull() ?: throw ExcepcionApi.http404("Budget")
        val (estado, total, paciente) = presupuesto.first
        if (estado != "approved") {
            return ResponseEntity.badRequest()
                .body(mapOf("detail" to "El presupuesto debe estar aprobado antes de generar el plan de pago."))
        }
        if (presupuesto.second > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("detail" to "Este presupuesto ya tiene un plan de pago."))
        }
        // `GeneratePaymentPlanSerializer(data=request.data)`: un cuerpo que no es un objeto es su error de DRF.
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val cuantas = campos.entero("installment_count", minimo = 1, maximo = 60, requerido = true)
        val primera = campos.fecha("first_due_date")
        campos.validar()
        val n = (cuantas as Leido.Valor).valor!!.toInt()
        val desde = (primera as Leido.Valor).valor!!

        val plan = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into billing_paymentplan (id, updated_at, total_amount, installment_count, created_at, budget_id, patient_id, tenant_id)
               values (:id, :ahora, :total, :n, :ahora, :presupuesto, :paciente, :clinica)""",
            mapOf("id" to plan, "ahora" to ahora, "total" to total, "n" to n, "presupuesto" to id, "paciente" to paciente, "clinica" to clinica),
        )
        // `(total / n).quantize(Decimal("0.01"), ROUND_HALF_UP)`: la división de Decimal usa 28 cifras.
        val base = total.divide(BigDecimal(n), MathContext(28, RoundingMode.HALF_EVEN)).setScale(2, RoundingMode.HALF_UP)
        var acumulado = BigDecimal("0.00")
        for (i in 1..n) {
            val importe = if (i < n) base.also { acumulado += it } else total - acumulado
            jdbc.update(
                """insert into billing_installment (id, updated_at, number, due_date, amount, status, created_at, patient_id,
                   payment_plan_id, tenant_id)
                   values (:id, :ahora, :numero, :vence, :importe, 'pending', :ahora, :paciente, :plan, :clinica)""",
                mapOf(
                    "id" to UUID.randomUUID(), "ahora" to ahora, "numero" to i, "vence" to desde.plusMonths((i - 1).toLong()),
                    "importe" to importe, "paciente" to paciente, "plan" to plan, "clinica" to clinica,
                ),
            )
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cobros.planDePago(plan))
    }

    /** GET /api/v1/payment-plans/{id}/installments/ — paginada, por número. */
    @GetMapping("/api/v1/payment-plans/$ID/installments/")
    fun cuotas(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(VEN_COBROS)
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val total = jdbc.queryForObject(
            "select count(*) from billing_installment where payment_plan_id = :id and tenant_id = :clinica", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """select * from billing_installment where payment_plan_id = :id and tenant_id = :clinica
               order by number limit :cuantos offset :desde""",
            parametros,
        ) { f, _ -> cobros.cuota(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/installments/{id}/pay/ — registra el pago (sin cuota
     * cerrada no hay límite: se puede pagar de más o volver a pagar una
     * pagada, como en Django) y, si el saldo llega a cero o menos, la cuota
     * queda pagada. El profesional, si no se dice, es el habitual del
     * paciente.
     */
    @PostMapping("/api/v1/installments/$ID/pay/")
    @Transactional
    fun pagar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): Map<String, Any?> {
        val usuario = permisos.exigir(COBRAN)
        val paciente = jdbc.query(
            "select patient_id from billing_installment where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> f.uuid("patient_id") }.firstOrNull() ?: throw ExcepcionApi.http404("Installment")
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val importe = campos.decimal("amount", maxDigitos = 10, decimales = 2, requerido = true)
        if (importe is Leido.Valor && importe.valor!!.valor < BigDecimal("0.01")) {
            campos.error("amount", "Asegúrese de que este valor es mayor o igual a 0.01.")
        }
        val metodo = campos.opcion("method", Cobros.METODOS.keys, requerido = true)
        val fecha = campos.fecha("date", requerido = false)
        val profesional = cobros.leerProfesional(campos)
        campos.validar()
        cobros.registrarPago(
            paciente = paciente, cuota = id, importe = (importe as Leido.Valor).valor!!.valor,
            metodo = (metodo as Leido.Valor).valor!!, fecha = (fecha as? Leido.Valor)?.valor ?: zona.hoy(),
            registradoPor = usuario.id, profesional = profesional ?: cobros.profesionalHabitual(paciente),
        )
        val saldo = jdbc.queryForObject(
            """select i.amount - coalesce((select sum(p.amount) from billing_payment p where p.installment_id = i.id), 0)
               from billing_installment i where i.id = :id""",
            mapOf("id" to id), BigDecimal::class.java,
        )!!
        // `save(update_fields=["status"])`: no toca `updated_at`.
        if (saldo.signum() <= 0) jdbc.update("update billing_installment set status = 'paid' where id = :id", mapOf("id" to id))
        return cobros.cuota(id)
    }

    // ── Comunes ──────────────────────────────────────────────────────

    /** `patient` (obligatorio, de esta clínica) y `notes`, en el orden de `BudgetSerializer`. */
    private fun leerPresupuesto(campos: CamposDrf): Map<String, Any?> {
        val leidos = LinkedHashMap<String, Any?>()
        val paciente = campos.clave("patient") {
            jdbc.queryForObject("select count(*) from patients_patient where id = :id", mapOf("id" to it), Long::class.java)!! > 0
        }
        if (paciente is Leido.Valor) {
            val deEsta = jdbc.queryForObject(
                "select count(*) from patients_patient where id = :id and tenant_id = :clinica",
                mapOf("id" to paciente.valor, "clinica" to clinica), Long::class.java,
            )!! > 0
            if (deEsta) leidos["patient"] = paciente.valor
            else campos.error("patient", "El paciente no pertenece a esta clínica.")
        }
        val notas = campos.texto("notes", requerido = false, enBlanco = true)
        if (notas is Leido.Valor) leidos["notes"] = notas.valor
        return leidos
    }

    private fun presupuestoDeEstaClinica(id: UUID): Map<String, Any?> {
        val existe = jdbc.queryForObject(
            "select count(*) from billing_budget where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404("Budget")
        return cobros.presupuesto(id)
    }
}
