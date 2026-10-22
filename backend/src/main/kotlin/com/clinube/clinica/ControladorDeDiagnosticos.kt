package com.clinube.clinica

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Diagnósticos del paciente (RF-HCL-04, literal N del formulario 033) y el
 * catálogo CIE-10 para buscarlos (`apps/clinical/views.py` y
 * `form033_views.py`).
 */
@RestController
class ControladorDeDiagnosticos(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val CLINICOS = Permisos.rol("admin", "doctor", "auxiliary")
        val TIPOS = linkedMapOf("pre" to "Presuntivo", "def" to "Definitivo")
    }

    /**
     * GET /api/v1/patients/{id}/diagnoses/ — paginada y por fecha, la más
     * reciente primero. Como en Django, sin comprobar que el paciente exista.
     */
    @GetMapping("/api/v1/patients/$ID/diagnoses/")
    fun lista(@PathVariable id: UUID, @RequestParam(required = false) page: String?, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(CLINICOS)
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to contexto.clinica?.id)
        val total = jdbc.queryForObject(
            "select count(*) from clinical_diagnosis where patient_id = :id and tenant_id = :clinica", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(page, total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """select * from clinical_diagnosis where patient_id = :id and tenant_id = :clinica
               order by date desc limit :cuantos offset :desde""",
            parametros,
        ) { f, _ -> deFila(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/patients/{id}/diagnoses/ — primero se valida y después se
     * busca al paciente (activo y de esta clínica). El doctor es el de quien
     * lo registra, si lo es; si no, queda vacío.
     */
    @PostMapping("/api/v1/patients/$ID/diagnoses/")
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(CLINICOS)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val pieza = campos.texto("tooth_fdi_code", maximo = 2, requerido = false, enBlanco = true)
        val codigo = campos.texto("code", maximo = 20, requerido = false, enBlanco = true)
        val descripcion = campos.texto("description", maximo = null)
        val tipo = campos.opcion("diagnosis_kind", TIPOS.keys)
        val fecha = campos.fecha("date")
        campos.validar()
        val clinica = contexto.clinica?.id
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
            """insert into clinical_diagnosis (id, updated_at, tooth_fdi_code, code, description, date, created_at, doctor_id,
               patient_id, tenant_id, diagnosis_kind)
               values (:id, :actualizado, :pieza, :codigo, :descripcion, :fecha, :creado, :doctor, :paciente, :clinica, :tipo)""",
            mapOf(
                "id" to nuevo, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "pieza" to ((pieza as? Leido.Valor)?.valor ?: ""), "codigo" to ((codigo as? Leido.Valor)?.valor ?: ""),
                "descripcion" to (descripcion as Leido.Valor).valor, "fecha" to (fecha as Leido.Valor).valor,
                "tipo" to ((tipo as? Leido.Valor)?.valor ?: "pre"),
                "doctor" to doctor, "paciente" to id, "clinica" to clinica,
            ),
        )
        val creado = jdbc.query("select * from clinical_diagnosis where id = :id", mapOf("id" to nuevo)) { f, _ -> deFila(f) }.first()
        return ResponseEntity.status(HttpStatus.CREATED).body(creado)
    }

    /** `DiagnosisSerializer`, en el orden de sus campos. */
    private fun deFila(f: ResultSet): Map<String, Any?> {
        val tipo = f.getString("diagnosis_kind")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "doctor" to f.uuidONulo("doctor_id")?.toString(),
            "tooth_fdi_code" to f.getString("tooth_fdi_code"),
            "code" to f.getString("code"),
            "description" to f.getString("description"),
            "diagnosis_kind" to tipo,
            // `get_diagnosis_kind_display`: el valor tal cual si no es una de las opciones.
            "kind_display" to (TIPOS[tipo] ?: tipo),
            "date" to f.fecha("date").toString(),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }

    /**
     * GET /api/v1/cie10/?q= — por el principio del código o por cualquier
     * parte de la descripción, sin mayúsculas, los 20 primeros por código.
     * Con menos de dos caracteres, nada. Es la misma consulta que arma
     * Django (`UPPER(…) LIKE UPPER(…)`, con `%`, `_` y `\` escapados), para
     * que la base decida igual las mayúsculas de «ñ» o «á».
     */
    @GetMapping("/api/v1/cie10/")
    fun cie10(peticion: HttpServletRequest): Map<String, Any?> {
        permisos.exigir(Permisos.rol("admin", "doctor", "auxiliary", "reception"))
        // `query_params.get("q")` de Django: si se repite, el último.
        val q = Python.recortar(peticion.getParameterValues("q")?.lastOrNull() ?: "")
        if (Python.longitud(q) < 2) return mapOf("results" to emptyList<Any>())
        val escapado = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val resultados = jdbc.query(
            """select code, description from clinical_cie10
               where upper(code::text) like upper(:empieza) or upper(description::text) like upper(:contiene)
               order by code asc limit 20""",
            mapOf("empieza" to "$escapado%", "contiene" to "%$escapado%"),
        ) { f, _ -> linkedMapOf("code" to f.getString("code"), "description" to f.getString("description")) }
        return mapOf("results" to resultados)
    }
}
