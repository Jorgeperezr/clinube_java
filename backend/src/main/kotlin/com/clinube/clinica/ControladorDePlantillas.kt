package com.clinube.clinica

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.configuracion.tarifas.Tarifas
import com.clinube.pacientes.ControladorDeFichas
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Plantillas de planes de tratamiento (`apps/clinical/sprint22_views.py`):
 * protocolos reutilizables que, al aplicarse a un paciente, crean su plan con
 * todas las líneas y el precio que le corresponde a ESE paciente (con su
 * convenio), no el de catálogo.
 *
 * Verlas, todo el personal clínico y recepción; crearlas y tocar sus líneas,
 * administración y doctores; aplicarlas, administración, doctores y auxiliares.
 */
@RestController
class ControladorDePlantillas(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val mapeador: ObjectMapper,
    private val tarifas: Tarifas,
    private val planes: ControladorDePlanes,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val VEN = Permisos.rol("admin", "doctor", "auxiliary", "reception")
        val GESTIONAN = Permisos.rol("admin", "doctor")
        val APLICAN = Permisos.rol("admin", "doctor", "auxiliary")
        const val ITEMS_INVALIDOS = "Ítems de plantilla inválidos."

        /**
         * `int(x)` de Python sobre un valor JSON, o null donde Python lanza
         * (y Django responde 500): un número con decimales se trunca, un
         * booleano es 1 o 0 y un texto pasa por `int()`.
         */
        fun enteroDePython(valor: JsonNode?): Long? = when {
            valor == null || valor.isNull -> null
            valor.isBoolean -> if (valor.booleanValue()) 1 else 0
            valor.isIntegralNumber -> valor.bigIntegerValue().takeIf { it.bitLength() < 63 }?.toLong()
            valor.isNumber -> valor.doubleValue().takeIf { it.isFinite() }?.let { it.toLong() }
            valor.isTextual -> Python.entero(valor.textValue())?.takeIf { it.bitLength() < 63 }?.toLong()
            else -> null
        }

        /** `str(x)[:255]` de Python. */
        fun notas(valor: JsonNode?): String {
            val texto = valor?.let { Python.str(it) } ?: ""
            return if (Python.longitud(texto) <= 255) texto else texto.substring(0, texto.offsetByCodePoints(0, 255))
        }
    }

    private val clinica get() = contexto.clinica?.id

    /** GET /api/v1/clinical/plan-templates/ — las activas, por nombre, sin paginar. */
    @GetMapping("/api/v1/clinical/plan-templates/")
    fun lista(): List<Map<String, Any?>> {
        permisos.exigir(VEN)
        return jdbc.query(
            "select * from clinical_treatmentplantemplate where tenant_id = :clinica and is_active order by name",
            mapOf("clinica" to clinica),
        ) { f, _ -> plantilla(f) }
    }

    /**
     * POST /api/v1/clinical/plan-templates/ — nace activa siempre. Las
     * líneas pueden venir en el mismo cuerpo (`items`); las de un tratamiento
     * que no es de la clínica se ignoran, como en Django.
     *
     * Donde Django da un 500 (una línea que no es un objeto, un id que no es
     * un UUID, un orden que no es un número) y deja la plantilla creada a
     * medias, aquí es un 400 y no se crea nada (docs/DIFERENCIAS.md).
     */
    @PostMapping("/api/v1/clinical/plan-templates/")
    fun alta(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(GESTIONAN)
        val datos = CuerpoDrf.objeto(cuerpo)
        val campos = CamposDrf(datos)
        val nombre = campos.texto("name", maximo = 150)
        val descripcion = campos.texto("description", requerido = false, enBlanco = true)
        campos.booleano("is_active")
        campos.validar()

        val lineas = mutableListOf<Triple<UUID, Long, String>>()
        val items = datos.get("items")
        if (items != null) {
            if (!items.isArray) throw ExcepcionApi.validacion(mapOf("items" to listOf(ITEMS_INVALIDOS)))
            for (crudo in items) {
                if (!crudo.isObject) throw ExcepcionApi.validacion(mapOf("items" to listOf(ITEMS_INVALIDOS)))
                val tratamiento = crudo.get("treatment")
                val id = when {
                    tratamiento == null || tratamiento.isNull -> null
                    tratamiento.isTextual -> CamposDrf.uuidDePython(tratamiento.textValue())
                        ?: throw ExcepcionApi.validacion(mapOf("items" to listOf(ITEMS_INVALIDOS)))
                    // `UUID(int=n)` (y un booleano es un int en Python): no existe, se ignora.
                    tratamiento.isIntegralNumber || tratamiento.isBoolean -> null
                    else -> throw ExcepcionApi.validacion(mapOf("items" to listOf(ITEMS_INVALIDOS)))
                }
                val orden = if (crudo.has("order")) enteroDePython(crudo.get("order")) else 1L
                if (orden == null || orden < 0) throw ExcepcionApi.validacion(mapOf("items" to listOf(ITEMS_INVALIDOS)))
                if (id != null && tratamientoDeLaClinica(id)) lineas += Triple(id, orden, notas(crudo.get("notes")))
            }
        }

        val nueva = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_treatmentplantemplate (id, name, description, is_active, created_at, updated_at, tenant_id)
               values (:id, :nombre, :descripcion, true, :creada, :actualizada, :clinica)""",
            mapOf(
                "id" to nueva, "nombre" to (nombre as Leido.Valor).valor,
                "descripcion" to ((descripcion as? Leido.Valor)?.valor ?: ""),
                "creada" to Timestamp.from(zona.ahora()), "actualizada" to Timestamp.from(zona.ahora()), "clinica" to clinica,
            ),
        )
        for ((tratamiento, orden, nota) in lineas) insertarLinea(nueva, tratamiento, orden, nota)
        val creada = jdbc.query("select * from clinical_treatmentplantemplate where id = :id", mapOf("id" to nueva)) { f, _ ->
            plantilla(f)
        }.first()
        return ResponseEntity.status(HttpStatus.CREATED).body(creada)
    }

    /**
     * POST /api/v1/clinical/plan-templates/{id}/items/ — añade un
     * tratamiento. Sin orden (o con un orden «falso»: 0, ""), va al final.
     */
    @PostMapping("/api/v1/clinical/plan-templates/$ID/items/")
    fun nuevaLinea(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(GESTIONAN)
        val existe = jdbc.queryForObject(
            "select count(*) from clinical_treatmentplantemplate where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404SinModelo()
        val datos = cuerpo?.takeIf { it.isObject }
        val tratamiento = datos?.get("treatment")?.takeIf { it.isTextual }?.let { CamposDrf.uuidDePython(it.textValue()) }
        val nombre = tratamiento?.let {
            jdbc.query(
                "select name from configuration_treatment where id = :id and tenant_id = :clinica",
                mapOf("id" to it, "clinica" to clinica),
            ) { f, _ -> f.getString("name") }.firstOrNull()
        } ?: return ResponseEntity.badRequest().body(mapOf("detail" to "Tratamiento inválido."))

        val pedido = datos?.get("order")
        val orden = if (Python.verdad(pedido)) {
            enteroDePython(pedido)?.takeIf { it >= 0 } ?: return ResponseEntity.badRequest().body(mapOf("detail" to "Orden inválido."))
        } else {
            jdbc.queryForObject("select count(*) from clinical_treatmentplantemplateitem where template_id = :id", mapOf("id" to id), Long::class.java)!! + 1
        }
        val linea = insertarLinea(id, tratamiento!!, orden, notas(datos?.get("notes")))
        return ResponseEntity.status(HttpStatus.CREATED).body(linkedMapOf("id" to linea.toString(), "treatment_name" to nombre, "order" to orden))
    }

    /** DELETE /api/v1/clinical/plan-template-items/{id}/ */
    @DeleteMapping("/api/v1/clinical/plan-template-items/$ID/")
    fun borrarLinea(@PathVariable id: UUID): ResponseEntity<Void> {
        permisos.exigir(GESTIONAN)
        val borradas = jdbc.update(
            """delete from clinical_treatmentplantemplateitem i using clinical_treatmentplantemplate t
               where i.id = :id and t.id = i.template_id and t.tenant_id = :clinica""",
            mapOf("id" to id, "clinica" to clinica),
        )
        if (borradas == 0) throw ExcepcionApi.http404SinModelo()
        return ResponseEntity.noContent().build()
    }

    /**
     * POST /api/v1/patients/{id}/apply-plan-template/ {template_id} — crea el
     * plan del paciente, ACTIVO, con una línea por cada una de la plantilla
     * (en su orden) al precio que le toca con su convenio. Una plantilla
     * inactiva también se puede aplicar, como en Django.
     */
    @PostMapping("/api/v1/patients/$ID/apply-plan-template/")
    fun aplicar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(APLICAN)
        val activo = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!activo) throw ExcepcionApi.http404("Patient")
        // Un id que no es un UUID da un 500 en Django; aquí, «no encontrada».
        val plantilla = cuerpo?.takeIf { it.isObject }?.get("template_id")?.takeIf { it.isTextual }
            ?.let { CamposDrf.uuidDePython(it.textValue()) }
            ?.let { pid ->
                jdbc.query(
                    "select id, name from clinical_treatmentplantemplate where id = :id and tenant_id = :clinica",
                    mapOf("id" to pid, "clinica" to clinica),
                ) { f, _ -> f.uuid("id") to f.getString("name") }.firstOrNull()
            } ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("detail" to "Plantilla no encontrada."))

        val doctor = jdbc.query(
            "select id from agenda_doctor where user_id = :usuario and tenant_id = :clinica order by id limit 1",
            mapOf("usuario" to usuario.id, "clinica" to clinica),
        ) { f, _ -> f.uuid("id") }.firstOrNull()
        val plan = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_treatmentplan (id, updated_at, status, notes, created_at, created_by_id, patient_id, tenant_id)
               values (:id, :actualizado, 'active', :notas, :creado, :doctor, :paciente, :clinica)""",
            mapOf(
                "id" to plan, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "notas" to "Creado desde la plantilla: ${plantilla.second}", "doctor" to doctor, "paciente" to id, "clinica" to clinica,
            ),
        )
        val precios = tarifas.precios(clinica, tarifas.convenioDelPaciente(id))
        jdbc.query(
            """select i.treatment_id, i."order", t.base_price from clinical_treatmentplantemplateitem i
               join configuration_treatment t on t.id = i.treatment_id where i.template_id = :id order by i."order"""",
            mapOf("id" to plantilla.first),
        ) { f, _ -> Triple(f.uuid("treatment_id"), f.getLong("order"), f.getBigDecimal("base_price")) }
            .forEach { (tratamiento, orden, base) ->
                jdbc.update(
                    """insert into clinical_treatmentplanitem (id, tooth_fdi_code, "order", status, estimated_price, treatment_id,
                       treatment_plan_id, price_is_manual) values (:id, '', :orden, 'planned', :precio, :tratamiento, :plan, false)""",
                    mapOf("id" to UUID.randomUUID(), "orden" to orden, "precio" to precios.de(tratamiento, base),
                        "tratamiento" to tratamiento, "plan" to plan),
                )
            }
        jdbc.update(
            """insert into accounts_auditlog (id, action, entity_type, entity_id, ip_address, metadata, created_at, tenant_id,
               user_id) values (:aid, 'apply_plan_template', 'TreatmentPlan', :eid, null, :metadatos::jsonb, :ahora, :clinica, :usuario)""",
            mapOf(
                "aid" to UUID.randomUUID(), "eid" to plan.toString(), "ahora" to Timestamp.from(zona.ahora()),
                "metadatos" to mapeador.writeValueAsString(mapOf("template" to plantilla.second, "patient_id" to id.toString())),
                "clinica" to clinica, "usuario" to usuario.id,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(planes.planDeEstaClinica(plan))
    }

    // ── Comunes ──────────────────────────────────────────────────────

    private fun tratamientoDeLaClinica(id: UUID) = jdbc.queryForObject(
        "select count(*) from configuration_treatment where id = :id and tenant_id = :clinica",
        mapOf("id" to id, "clinica" to clinica), Long::class.java,
    )!! > 0

    private fun insertarLinea(plantilla: UUID, tratamiento: UUID, orden: Long, nota: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_treatmentplantemplateitem (id, "order", notes, template_id, treatment_id)
               values (:id, :orden, :notas, :plantilla, :tratamiento)""",
            mapOf("id" to id, "orden" to orden, "notas" to nota, "plantilla" to plantilla, "tratamiento" to tratamiento),
        )
        return id
    }

    /**
     * `TreatmentPlanTemplateSerializer`. `total_estimated` es
     * `str(sum(precios base, start=0))`: «0» sin líneas, «225.50» con ellas.
     */
    private fun plantilla(f: ResultSet): Map<String, Any?> {
        val id = f.uuid("id")
        val lineas = jdbc.query(
            """select i.id, i.treatment_id, i."order", i.notes, t.name, t.base_price from clinical_treatmentplantemplateitem i
               join configuration_treatment t on t.id = i.treatment_id where i.template_id = :id order by i."order"""",
            mapOf("id" to id),
        ) { l, _ ->
            linkedMapOf<String, Any?>(
                "id" to l.uuid("id").toString(),
                "treatment" to l.uuid("treatment_id").toString(),
                "treatment_name" to l.getString("name"),
                "base_price" to l.getBigDecimal("base_price").setScale(2).toPlainString(),
                "order" to l.getLong("order"),
                "notes" to l.getString("notes"),
            )
        }
        val total = lineas.fold(null as BigDecimal?) { suma, l -> (suma ?: BigDecimal.ZERO) + BigDecimal(l["base_price"] as String) }
        return linkedMapOf(
            "id" to id.toString(),
            "name" to f.getString("name"),
            "description" to f.getString("description"),
            "is_active" to f.getBoolean("is_active"),
            "items" to lineas,
            "total_estimated" to (total?.toPlainString() ?: "0"),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }
}
