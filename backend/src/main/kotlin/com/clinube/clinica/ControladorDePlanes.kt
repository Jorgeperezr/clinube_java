package com.clinube.clinica

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.pacientes.ControladorDeFichas
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Planes de tratamiento (RF-HCL-05, `apps/clinical/views.py`): el plan, sus
 * líneas y el avance. Todo es de administración, doctores y auxiliares.
 *
 * Dos reglas que deciden qué se cobra y qué se gasta:
 *   · `price_is_manual`: si una petición trae `estimated_price`, ese precio
 *     lo decidió una persona y el tarifario no lo vuelve a tocar.
 *   · Al quedar una línea «realizada», se descuentan del inventario los
 *     insumos del tratamiento (RF-INV-05), primero los lotes que caducan
 *     antes. Un fallo del inventario no deshace el acto clínico.
 */
@RestController
class ControladorDePlanes(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val CLINICOS = Permisos.rol("admin", "doctor", "auxiliary")
        val ESTADOS_DEL_PLAN = linkedMapOf("draft" to "Borrador", "active" to "Activo", "completed" to "Completado")
        val ESTADOS_DE_LA_LINEA = linkedMapOf("planned" to "Planificado", "in_progress" to "En progreso", "done" to "Realizado")
        private val registro = LoggerFactory.getLogger(ControladorDePlanes::class.java)
    }

    private val clinica get() = contexto.clinica?.id

    /** GET /api/v1/patients/{id}/treatment-plans/ — paginada, el más reciente primero. */
    @GetMapping("/api/v1/patients/$ID/treatment-plans/")
    fun lista(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(CLINICOS)
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val total = jdbc.queryForObject(
            "select count(*) from clinical_treatmentplan where patient_id = :id and tenant_id = :clinica", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val planes = jdbc.query(
            """select * from clinical_treatmentplan where patient_id = :id and tenant_id = :clinica
               order by created_at desc limit :cuantos offset :desde""",
            parametros,
        ) { f, _ -> plan(f) }
        return Pagina.de(total, planes, pagina, peticion)
    }

    /** POST /api/v1/patients/{id}/treatment-plans/ — se valida y después se busca al paciente (activo). */
    @PostMapping("/api/v1/patients/$ID/treatment-plans/")
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(CLINICOS)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val estado = campos.opcion("status", ESTADOS_DEL_PLAN.keys)
        val notas = campos.texto("notes", requerido = false, enBlanco = true)
        campos.validar()
        val activo = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!activo) throw ExcepcionApi.http404("Patient")
        val doctor = jdbc.query(
            "select id from agenda_doctor where user_id = :usuario and tenant_id = :clinica order by id limit 1",
            mapOf("usuario" to usuario.id, "clinica" to clinica),
        ) { f, _ -> f.uuid("id") }.firstOrNull()
        val nuevo = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_treatmentplan (id, updated_at, status, notes, created_at, created_by_id, patient_id, tenant_id)
               values (:id, :actualizado, :estado, :notas, :creado, :doctor, :paciente, :clinica)""",
            mapOf(
                "id" to nuevo, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "estado" to ((estado as? Leido.Valor)?.valor ?: "draft"), "notas" to ((notas as? Leido.Valor)?.valor ?: ""),
                "doctor" to doctor, "paciente" to id, "clinica" to clinica,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(planDeEstaClinica(nuevo))
    }

    /** GET /api/v1/treatment-plans/{id}/ */
    @GetMapping("/api/v1/treatment-plans/$ID/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(CLINICOS)
        return planDeEstaClinica(id)
    }

    @PutMapping("/api/v1/treatment-plans/$ID/")
    fun reemplazar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editarPlan(id, cuerpo, parcial = false)

    @PatchMapping("/api/v1/treatment-plans/$ID/")
    fun modificar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editarPlan(id, cuerpo, parcial = true)

    /** PUT/PATCH /api/v1/treatment-plans/{id}/ — el estado y las notas. */
    private fun editarPlan(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(CLINICOS)
        planDeEstaClinica(id)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo), parcial)
        val cambios = LinkedHashMap<String, Any?>()
        (campos.opcion("status", ESTADOS_DEL_PLAN.keys) as? Leido.Valor)?.let { cambios["status"] = it.valor }
        (campos.texto("notes", requerido = false, enBlanco = true) as? Leido.Valor)?.let { cambios["notes"] = it.valor }
        campos.validar()
        val asignaciones = cambios.keys.map { "$it = :$it" } + "updated_at = :actualizado"
        jdbc.update(
            "update clinical_treatmentplan set ${asignaciones.joinToString(", ")} where id = :id",
            cambios + mapOf("id" to id, "actualizado" to Timestamp.from(zona.ahora())),
        )
        return planDeEstaClinica(id)
    }

    /**
     * POST /api/v1/treatment-plans/{id}/items/ — una línea nueva. Se valida
     * ANTES de buscar el plan (un plan ajeno con datos malos da 400, no 404).
     */
    @PostMapping("/api/v1/treatment-plans/$ID/items/")
    fun nuevaLinea(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(CLINICOS)
        val datos = CuerpoDrf.objeto(cuerpo)
        val leidos = leerLinea(CamposDrf(datos))
        val existe = jdbc.queryForObject(
            "select count(*) from clinical_treatmentplan where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404("TreatmentPlan")
        val nueva = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_treatmentplanitem (id, tooth_fdi_code, "order", status, estimated_price, treatment_id,
               treatment_plan_id, price_is_manual)
               values (:id, :pieza, :orden, :estado, :precio, :tratamiento, :plan, :manual)""",
            mapOf(
                "id" to nueva, "pieza" to (leidos["tooth_fdi_code"] ?: ""), "orden" to (leidos["order"] ?: 1L),
                "estado" to (leidos["status"] ?: "planned"),
                "precio" to ((leidos["estimated_price"] as CamposDrf.Decimal?)?.valor ?: BigDecimal.ZERO),
                "tratamiento" to leidos["treatment"], "plan" to id, "manual" to datos.has("estimated_price"),
            ),
        )
        // Al crear no se descuenta el inventario, aunque llegue «realizada»:
        // Django solo lo hace al editar.
        return ResponseEntity.status(HttpStatus.CREATED).body(conPrecioPedido(linea(nueva), leidos))
    }

    @PutMapping("/api/v1/treatment-plan-items/$ID/")
    fun reemplazarLinea(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editarLinea(id, cuerpo, parcial = false)

    @PatchMapping("/api/v1/treatment-plan-items/$ID/")
    fun modificarLinea(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editarLinea(id, cuerpo, parcial = true)

    /**
     * PUT/PATCH /api/v1/treatment-plan-items/{id}/ — normalmente el estado.
     *
     * Como en Django, el inventario se descuenta CADA VEZ que la línea queda
     * «realizada» tras guardar, también si ya lo estaba (un PATCH del precio
     * de una línea realizada vuelve a descontar). Es un fallo de Django que
     * se copia a propósito para que los dos servidores dejen la misma base
     * mientras convivan (docs/DIFERENCIAS.md).
     */
    private fun editarLinea(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(CLINICOS)
        val existe = jdbc.queryForObject(
            """select count(*) from clinical_treatmentplanitem i join clinical_treatmentplan p on p.id = i.treatment_plan_id
               where i.id = :id and p.tenant_id = :clinica""",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404("TreatmentPlanItem")
        val datos = CuerpoDrf.objeto(cuerpo)
        val leidos = leerLinea(CamposDrf(datos, parcial))
        val columnas = LinkedHashMap<String, Any?>()
        leidos.forEach { (campo, valor) ->
            when (campo) {
                "treatment" -> columnas["treatment_id"] = valor
                "estimated_price" -> columnas["estimated_price"] = (valor as CamposDrf.Decimal).valor
                else -> columnas[campo] = valor
            }
        }
        if (datos.has("estimated_price")) columnas["price_is_manual"] = true
        if (columnas.isNotEmpty()) {
            val asignaciones = columnas.keys.map { if (it == "order") "\"order\" = :order" else "$it = :$it" }
            jdbc.update(
                "update clinical_treatmentplanitem set ${asignaciones.joinToString(", ")} where id = :id",
                columnas + mapOf("id" to id),
            )
        }
        val linea = conPrecioPedido(linea(id), leidos)
        consumirSiSeRealizo(id)
        return linea
    }

    /** La respuesta es la del objeto en memoria: el precio tal como llegó (con el «-0.00» de Python). */
    private fun conPrecioPedido(linea: Map<String, Any?>, leidos: Map<String, Any?>): Map<String, Any?> {
        val precio = leidos["estimated_price"] as CamposDrf.Decimal? ?: return linea
        return LinkedHashMap(linea).apply { put("estimated_price", precio.texto) }
    }

    // ── Inventario ───────────────────────────────────────────────────

    /**
     * `consume_inventory_for_treatment_item`, si la línea está realizada:
     * cada insumo del tratamiento sale de los lotes con existencias, el que
     * caduca antes primero, y cada salida queda como movimiento. Sin
     * transacción, como en Django: lo que ya se descontó se queda aunque
     * algo falle después, y el fallo solo se registra.
     */
    private fun consumirSiSeRealizo(lineaId: UUID) {
        val (estado, tratamiento, clinicaDelPlan) = jdbc.query(
            """select i.status, i.treatment_id, p.tenant_id from clinical_treatmentplanitem i
               join clinical_treatmentplan p on p.id = i.treatment_plan_id where i.id = :id""",
            mapOf("id" to lineaId),
        ) { f, _ -> Triple(f.getString("status"), f.uuid("treatment_id"), f.uuid("tenant_id")) }.first()
        if (estado != "done") return
        try {
            val insumos = jdbc.query(
                "select product_id, quantity_used from configuration_treatmentinventoryitem where treatment_id = :t",
                mapOf("t" to tratamiento),
            ) { f, _ -> f.uuid("product_id") to f.getBigDecimal("quantity_used") }
            for ((producto, cantidad) in insumos) {
                var falta = cantidad
                val lotes = jdbc.query(
                    """select id, quantity from inventory_batch
                       where product_id = :producto and tenant_id = :clinica and quantity > 0 order by expiration_date""",
                    mapOf("producto" to producto, "clinica" to clinicaDelPlan),
                ) { f, _ -> f.uuid("id") to f.getBigDecimal("quantity") }
                for ((lote, existencias) in lotes) {
                    if (falta.signum() <= 0) break
                    val sale = existencias.min(falta)
                    // `save(update_fields=["quantity"])`: no toca `updated_at`.
                    jdbc.update("update inventory_batch set quantity = :q where id = :id", mapOf("q" to existencias - sale, "id" to lote))
                    val ahora = Timestamp.from(zona.ahora())
                    jdbc.update(
                        """insert into inventory_inventorymovement (created_at, updated_at, id, movement_type, quantity, reason, date,
                           batch_id, related_treatment_plan_item_id, tenant_id, product_id)
                           values (:ahora, :ahora, :id, 'out', :q, 'consumption', :ahora, :lote, :linea, :clinica, :producto)""",
                        mapOf("ahora" to ahora, "id" to UUID.randomUUID(), "q" to sale, "lote" to lote, "linea" to lineaId,
                            "clinica" to clinicaDelPlan, "producto" to producto),
                    )
                    falta -= sale
                }
            }
        } catch (e: Exception) {
            registro.warn("No se pudo descontar el inventario de un tratamiento realizado (línea {}, tratamiento {})", lineaId, tratamiento, e)
        }
    }

    // ── Lectura y representación ─────────────────────────────────────

    /**
     * Los campos de `TreatmentPlanItemSerializer`, en su orden.
     *
     * DIFERENCIA DELIBERADA: el tratamiento tiene que ser de esta clínica;
     * Django lo busca en todas (`Treatment.objects.all()`).
     */
    private fun leerLinea(campos: CamposDrf): Map<String, Any?> {
        val leidos = LinkedHashMap<String, Any?>()
        fun <T> poner(campo: String, leido: Leido<T>) {
            if (leido is Leido.Valor) leidos[campo] = leido.valor
        }
        poner("treatment", campos.clave("treatment") {
            jdbc.queryForObject(
                "select count(*) from configuration_treatment where id = :id and tenant_id = :clinica",
                mapOf("id" to it, "clinica" to clinica), Long::class.java,
            )!! > 0
        })
        poner("tooth_fdi_code", campos.texto("tooth_fdi_code", maximo = 2, requerido = false, enBlanco = true))
        poner("order", campos.entero("order", minimo = 0, maximo = 2147483647))
        poner("status", campos.opcion("status", ESTADOS_DE_LA_LINEA.keys))
        poner("estimated_price", campos.decimal("estimated_price", maxDigitos = 10, decimales = 2))
        campos.validar()
        return leidos
    }

    private fun planDeEstaClinica(id: UUID): Map<String, Any?> = jdbc.query(
        "select * from clinical_treatmentplan where id = :id and tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica),
    ) { f, _ -> plan(f) }.firstOrNull() ?: throw ExcepcionApi.http404("TreatmentPlan")

    /** `TreatmentPlanSerializer`: con sus líneas por `order` y el avance. */
    private fun plan(f: ResultSet): Map<String, Any?> {
        val id = f.uuid("id")
        val estado = f.getString("status")
        val lineas = jdbc.query("$SELECT_LINEA where i.treatment_plan_id = :id order by i.\"order\"", mapOf("id" to id)) { l, _ ->
            linea(l)
        }
        val hechas = lineas.count { it["status"] == "done" }
        return linkedMapOf(
            "id" to id.toString(),
            "created_by" to f.uuidONulo("created_by_id")?.toString(),
            "status" to estado,
            "status_display" to (ESTADOS_DEL_PLAN[estado] ?: estado),
            "notes" to f.getString("notes"),
            "items" to lineas,
            // `round()` de Python redondea los medios al par: 12.5 → 12.
            "progress" to linkedMapOf(
                "done" to hechas, "total" to lineas.size,
                "percent" to if (lineas.isEmpty()) 0 else Math.rint(hechas * 100.0 / lineas.size).toInt(),
            ),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }

    private val SELECT_LINEA = """
        select i.*, t.name as treatment_name from clinical_treatmentplanitem i
        join configuration_treatment t on t.id = i.treatment_id"""

    private fun linea(id: UUID) = jdbc.query("$SELECT_LINEA where i.id = :id", mapOf("id" to id)) { f, _ -> linea(f) }.first()

    /** `TreatmentPlanItemSerializer`, en el orden de sus campos. */
    private fun linea(f: ResultSet): Map<String, Any?> {
        val estado = f.getString("status")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "treatment" to f.uuid("treatment_id").toString(),
            "treatment_name" to f.getString("treatment_name"),
            "tooth_fdi_code" to f.getString("tooth_fdi_code"),
            "order" to f.getLong("order"),
            "status" to estado,
            "status_display" to (ESTADOS_DE_LA_LINEA[estado] ?: estado),
            "estimated_price" to f.getBigDecimal("estimated_price").setScale(2).toPlainString(),
        )
    }
}
