package com.clinube.clinica

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.pacientes.ControladorDeFichas
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.util.UUID

/**
 * Alertas clínicas del paciente (`apps/clinical/alertas_views.py`; las
 * reglas, en [ReglasDeAlertas]).
 *
 *   GET  /api/v1/patients/{id}/alertas/          las alertas activas
 *   POST /api/v1/patients/{id}/alertas/revisar/  {"texto": "..."} → choques
 *                                                de una receta con ellas
 *
 * Salen de los antecedentes médicos, así que tienen el mismo acceso que
 * ellos: administración, doctores y auxiliares; recepción no. Y cada
 * consulta queda en la auditoría como un acceso a los antecedentes (LOPDP),
 * igual que abrirlos. El panel enseña el editor de antecedentes dentro de
 * estas alertas: sin ellas, la ficha no lo muestra.
 */
@RestController
class ControladorDeAlertas(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val mapeador: ObjectMapper,
) {

    companion object {
        const val LARGO_MAXIMO = 5000
    }

    @GetMapping("/api/v1/patients/${ControladorDeFichas.ID}/alertas/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        val usuario = permisos.exigir(ControladorDeFichas.CLINICOS)
        return mapOf("alertas" to alertas(id, usuario.id, "view_clinical_alerts"))
    }

    /**
     * Avisa; no bloquea. Guardar la receta sigue siendo decisión del
     * profesional. Como en Django, el texto se comprueba ANTES de buscar al
     * paciente: sin texto es un 400 aunque el paciente no exista.
     */
    @PostMapping("/api/v1/patients/${ControladorDeFichas.ID}/alertas/revisar/")
    fun revisar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(ControladorDeFichas.CLINICOS)
        val texto = cuerpo?.takeIf { it.isObject }?.get("texto")?.takeIf { it.isTextual }?.textValue()
        if (texto == null || ReglasDeAlertas.recortar(texto).isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("detail" to "Falta el texto de la receta."))
        }
        // `len()` de Python cuenta caracteres, no unidades de UTF-16.
        if (texto.codePointCount(0, texto.length) > LARGO_MAXIMO) {
            return ResponseEntity.badRequest().body(mapOf("detail" to "La receta es demasiado larga para revisarla."))
        }
        val alertas = alertas(id, usuario.id, "check_prescription_alerts")
        return ResponseEntity.ok(linkedMapOf("choques" to ReglasDeAlertas.revisarReceta(texto, alertas), "alertas" to alertas))
    }

    /** El paciente activo de esta clínica (o el 404 de Django), su acceso auditado y sus alertas. */
    private fun alertas(id: UUID, usuario: UUID, accion: String): List<Map<String, String>> {
        val parametros = mapOf("id" to id, "clinica" to contexto.clinica?.id)
        val hay = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            parametros, Long::class.java,
        )!! > 0
        if (!hay) throw ExcepcionApi.http404("Patient")

        val fondo = jdbc.query(
            "select allergies, medications, conditions, is_pregnant from patients_medicalbackground where patient_id = :id",
            parametros,
        ) { f, _ ->
            ReglasDeAlertas.Antecedentes(f.getString("allergies"), f.getString("medications"), f.getString("conditions"),
                f.getObject("is_pregnant") as Boolean?)
        }.firstOrNull()
        // El último formulario: `order_by("-date", "-created_at")`, como en Django.
        val form033 = jdbc.query(
            """select antecedentes_personales::text as seccion, embarazada from clinical_form033record
               where patient_id = :id order by date desc, created_at desc limit 1""",
            parametros,
        ) { f, _ ->
            ReglasDeAlertas.Formulario033(mapeador.readTree(f.getString("seccion")), f.getObject("embarazada") as Boolean?)
        }.firstOrNull()

        jdbc.update(
            """insert into accounts_auditlog (id, action, entity_type, entity_id, ip_address, metadata, created_at,
               tenant_id, user_id) values (:aid, :accion, 'MedicalBackground', :pid, null, '{}'::jsonb, :ahora, :clinica, :usuario)""",
            mapOf(
                "aid" to UUID.randomUUID(), "accion" to accion, "pid" to id.toString(),
                "ahora" to Timestamp.from(zona.ahora()), "clinica" to contexto.clinica?.id, "usuario" to usuario,
            ),
        )
        return ReglasDeAlertas.deAntecedentes(fondo, form033)
    }
}
