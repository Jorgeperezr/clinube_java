package com.clinube.clinica

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
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
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * El odontograma (RF-HCL-02, `apps/clinical/views.py`) y los índices CPO/ceo
 * del formulario 033 (literal J, `form033_views.py`).
 *
 * El historial de cada pieza no se edita nunca: cada cambio es un registro
 * nuevo, y el «estado actual» es el último por pieza y superficie. Solo se
 * puede borrar un registro equivocado, y el borrado queda auditado.
 *
 * Todo es de administración, doctores y auxiliares, salvo el índice CPO/ceo,
 * que también ve recepción (está en `form033_views.py`, con otros permisos).
 */
@RestController
class ControladorDeOdontograma(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val mapeador: ObjectMapper,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val CLINICOS = Permisos.rol("admin", "doctor", "auxiliary")

        val SUPERFICIES = linkedMapOf(
            "whole" to "Toda la pieza", "vestibular" to "Vestibular", "palatal_lingual" to "Palatina/Lingual",
            "mesial" to "Mesial", "distal" to "Distal", "occlusal" to "Oclusal",
        )

        private const val SELECT = """
            select r.*, s.code as state_code, s.label as state_label, s.color as state_color
            from clinical_toothrecord r join clinical_odontogramstate s on s.id = r.state_id"""
    }

    private val clinica get() = contexto.clinica?.id

    /** GET /api/v1/odontogram-states/ — el catálogo activo de la clínica, sin paginar. */
    @GetMapping("/api/v1/odontogram-states/")
    fun estados(): List<Map<String, Any?>> {
        permisos.exigir(CLINICOS)
        // `order_by("order")` a secas, como Django: el `ordering` del modelo no cuenta.
        return jdbc.query(
            """select id, code, label, color, "order", is_active from clinical_odontogramstate
               where tenant_id = :clinica and is_active order by "order"""",
            mapOf("clinica" to clinica),
        ) { f, _ ->
            linkedMapOf(
                "id" to f.uuid("id").toString(), "code" to f.getString("code"), "label" to f.getString("label"),
                "color" to f.getString("color"), "order" to f.getInt("order"), "is_active" to f.getBoolean("is_active"),
            )
        }
    }

    /**
     * GET /api/v1/patients/{id}/tooth-records/ — el historial, el más
     * reciente primero, paginado. Filtros de django-filter: `?tooth_fdi_code=`
     * (exacto, recortado) y `?surface=` (una de las superficies).
     */
    @GetMapping("/api/v1/patients/$ID/tooth-records/")
    fun historial(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(CLINICOS)
        // `QueryDict.get`: si un parámetro se repite, el último.
        fun parametro(nombre: String) = peticion.getParameterValues(nombre)?.lastOrNull()
        val condiciones = mutableListOf("r.patient_id = :id", "r.tenant_id = :clinica")
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val pieza = parametro("tooth_fdi_code")?.let(Python::recortar)
        if (!pieza.isNullOrEmpty()) { condiciones += "r.tooth_fdi_code = :pieza"; parametros["pieza"] = pieza }
        val superficie = parametro("surface")
        if (!superficie.isNullOrEmpty()) {
            if (superficie !in SUPERFICIES) {
                throw ExcepcionApi.validacion(mapOf("surface" to listOf(MensajesDeDjango.opcionNoValida(superficie))))
            }
            condiciones += "r.surface = :superficie"
            parametros["superficie"] = superficie
        }
        val donde = condiciones.joinToString(" and ")
        val total = jdbc.queryForObject("select count(*) from clinical_toothrecord r where $donde", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(parametro("page"), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query("$SELECT where $donde order by r.created_at desc limit :cuantos offset :desde", parametros) { f, _ ->
            registro(f)
        }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/patients/{id}/tooth-records/ — un registro nuevo, nunca
     * una edición. Se valida, después se busca al paciente (activo) y el
     * doctor es el de quien registra, si lo es.
     *
     * DIFERENCIA DELIBERADA: el estado y el ítem del plan tienen que ser de
     * esta clínica. Django los busca en todas (`objects.all()`) y aceptaría
     * el estado de otra clínica, con su nombre y su color (docs/DIFERENCIAS.md).
     */
    @PostMapping("/api/v1/patients/$ID/tooth-records/")
    fun registrar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(CLINICOS)
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        val pieza = campos.texto("tooth_fdi_code", maximo = 2)
        val superficie = campos.opcion("surface", SUPERFICIES.keys)
        val estado = campos.clave("state") { existe("select count(*) from clinical_odontogramstate where id = :id and tenant_id = :clinica", it) }
        val movilidad = campos.entero("mobility", minimo = 0, maximo = 32767, nulo = true)
        val recesion = campos.entero("recession", minimo = 0, maximo = 32767, nulo = true)
        val item = campos.clave("treatment_plan_item", requerido = false, nulo = true) {
            existe(
                """select count(*) from clinical_treatmentplanitem i join clinical_treatmentplan p on p.id = i.treatment_plan_id
                   where i.id = :id and p.tenant_id = :clinica""",
                it,
            )
        }
        val fecha = campos.fecha("date")
        val notas = campos.texto("notes", requerido = false, enBlanco = true)
        campos.validar()

        pacienteActivo(id)
        val doctor = jdbc.query(
            "select id from agenda_doctor where user_id = :usuario and tenant_id = :clinica order by id limit 1",
            mapOf("usuario" to usuario.id, "clinica" to clinica),
        ) { f, _ -> f.uuid("id") }.firstOrNull()
        val nuevo = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_toothrecord (id, updated_at, tooth_fdi_code, surface, date, notes, created_at, doctor_id,
               patient_id, state_id, tenant_id, treatment_plan_item_id, mobility, recession)
               values (:id, :actualizado, :pieza, :superficie, :fecha, :notas, :creado, :doctor, :paciente, :estado, :clinica,
               :item, :movilidad, :recesion)""",
            mapOf(
                "id" to nuevo, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "pieza" to (pieza as Leido.Valor).valor, "superficie" to ((superficie as? Leido.Valor)?.valor ?: "whole"),
                "fecha" to (fecha as Leido.Valor).valor, "notas" to ((notas as? Leido.Valor)?.valor ?: ""),
                "doctor" to doctor, "paciente" to id, "estado" to (estado as Leido.Valor).valor, "clinica" to clinica,
                "item" to (item as? Leido.Valor)?.valor, "movilidad" to (movilidad as? Leido.Valor)?.valor,
                "recesion" to (recesion as? Leido.Valor)?.valor,
            ),
        )
        auditar("create_tooth_record", "ToothRecord", id.toString(), usuario.id)
        val creado = jdbc.query("$SELECT where r.id = :id", mapOf("id" to nuevo)) { f, _ -> registro(f) }.first()
        return ResponseEntity.status(HttpStatus.CREATED).body(creado)
    }

    /**
     * GET /api/v1/patients/{id}/odontogram/current/ — el último registro de
     * cada pieza y superficie, en el orden en que aparecen del más reciente
     * al más antiguo. Es lo que el panel dibuja. Cada consulta queda en la
     * auditoría.
     */
    @GetMapping("/api/v1/patients/$ID/odontogram/current/")
    fun actual(@PathVariable id: UUID): Map<String, Any?> {
        val usuario = permisos.exigir(CLINICOS)
        pacienteActivo(id)
        val vistos = HashSet<Pair<String, String>>()
        val actuales = mutableListOf<Map<String, Any?>>()
        jdbc.query(
            "$SELECT where r.patient_id = :id and r.tenant_id = :clinica order by r.created_at desc",
            mapOf("id" to id, "clinica" to clinica),
        ) { f, _ ->
            if (vistos.add(f.getString("tooth_fdi_code") to f.getString("surface"))) actuales += registro(f)
        }
        auditar("view_odontogram", "Odontogram", id.toString(), usuario.id)
        return linkedMapOf("patient_id" to id.toString(), "teeth" to actuales)
    }

    /**
     * DELETE /api/v1/tooth-records/{id}/ — corrige un registro equivocado.
     * Queda en la auditoría con el paciente, la pieza, el estado y la fecha.
     */
    @DeleteMapping("/api/v1/tooth-records/$ID/")
    fun borrar(@PathVariable id: UUID): ResponseEntity<Void> {
        val usuario = permisos.exigir(CLINICOS)
        val fila = jdbc.query(
            "$SELECT where r.id = :id and r.tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> Triple(f.uuid("patient_id"), f.getString("tooth_fdi_code"), f.getString("state_code") to f.fecha("date")) }
            .firstOrNull() ?: throw ExcepcionApi.http404("ToothRecord")
        val metadatos = linkedMapOf(
            "patient_id" to fila.first.toString(), "tooth_fdi_code" to fila.second,
            "state" to fila.third.first, "date" to fila.third.second.toString(),
        )
        auditar("delete_tooth_record", "ToothRecord", id.toString(), usuario.id, mapeador.writeValueAsString(metadatos))
        jdbc.update("delete from clinical_toothrecord where id = :id", mapOf("id" to id))
        return ResponseEntity.noContent().build()
    }

    /**
     * GET /api/v1/patients/{id}/cpo-ceo/ — índices del literal J a partir
     * del último registro de cada pieza (cualquier superficie; por fecha y
     * después por creación). CPO de las permanentes (cuadrantes 1-4): C
     * cariadas, P perdidas por caries o con extracción indicada, O obturadas
     * o con corona. ceo de las temporales: c, e, o.
     *
     * El paciente puede estar dado de baja, como en Django
     * (`Patient.objects.get` sin `is_active`). Uno que no existe da aquí un
     * 404; en Django, un 500.
     */
    @GetMapping("/api/v1/patients/$ID/cpo-ceo/")
    fun cpoCeo(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(Permisos.rol("admin", "doctor", "auxiliary", "reception"))
        if (!existe("select count(*) from patients_patient where id = :id and tenant_id = :clinica", id)) {
            throw ExcepcionApi.http404("Patient")
        }
        val ultimos = LinkedHashMap<String, String>()
        jdbc.query(
            """select r.tooth_fdi_code, s.code from clinical_toothrecord r join clinical_odontogramstate s on s.id = r.state_id
               where r.tenant_id = :clinica and r.patient_id = :id order by r.tooth_fdi_code, r.date desc, r.created_at desc""",
            mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> ultimos.putIfAbsent(f.getString("tooth_fdi_code"), f.getString("code")) }

        val cpo = linkedMapOf("C" to 0, "P" to 0, "O" to 0)
        val ceo = linkedMapOf("c" to 0, "e" to 0, "o" to 0)
        for ((pieza, codigo) in ultimos) {
            val permanente = pieza.take(1) in setOf("1", "2", "3", "4")
            val clave = when (codigo) {
                "CARIES" -> if (permanente) "C" else "c"
                "PERDIDA_CARIES", "INDICADA_EXTRACCION" -> if (permanente) "P" else "e"
                "OBTURADO", "CORONA" -> if (permanente) "O" else "o"
                else -> continue
            }
            val indice = if (permanente) cpo else ceo
            indice[clave] = indice.getValue(clave) + 1
        }
        return linkedMapOf(
            "cpo" to cpo + ("total" to cpo.values.sum()),
            "ceo" to ceo + ("total" to ceo.values.sum()),
            "teeth_evaluated" to ultimos.size,
        )
    }

    // ── Comunes ──────────────────────────────────────────────────────

    private fun existe(sql: String, id: UUID) = jdbc.queryForObject(sql, mapOf("id" to id, "clinica" to clinica), Long::class.java)!! > 0

    /** `_get_patient` de `views.py`: activo y de esta clínica, o el 404 de Django. */
    private fun pacienteActivo(id: UUID) {
        if (!existe("select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active", id)) {
            throw ExcepcionApi.http404("Patient")
        }
    }

    private fun auditar(accion: String, entidad: String, entidadId: String, usuario: UUID, metadatos: String = "{}") {
        jdbc.update(
            """insert into accounts_auditlog (id, action, entity_type, entity_id, ip_address, metadata, created_at, tenant_id,
               user_id) values (:aid, :accion, :entidad, :eid, null, :metadatos::jsonb, :ahora, :clinica, :usuario)""",
            mapOf(
                "aid" to UUID.randomUUID(), "accion" to accion, "entidad" to entidad, "eid" to entidadId,
                "metadatos" to metadatos, "ahora" to Timestamp.from(zona.ahora()), "clinica" to clinica, "usuario" to usuario,
            ),
        )
    }

    /** `ToothRecordSerializer`, en el orden de sus campos. */
    private fun registro(f: ResultSet): Map<String, Any?> {
        val superficie = f.getString("surface")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "tooth_fdi_code" to f.getString("tooth_fdi_code"),
            "surface" to superficie,
            // `get_surface_display`: el valor tal cual si no es una de las opciones.
            "surface_display" to (SUPERFICIES[superficie] ?: superficie),
            "state" to f.uuid("state_id").toString(),
            "state_code" to f.getString("state_code"),
            "state_label" to f.getString("state_label"),
            "state_color" to f.getString("state_color"),
            "mobility" to (f.getObject("mobility") as Number?)?.toInt(),
            "recession" to (f.getObject("recession") as Number?)?.toInt(),
            "doctor" to f.uuidONulo("doctor_id")?.toString(),
            "treatment_plan_item" to f.uuidONulo("treatment_plan_item_id")?.toString(),
            "date" to f.fecha("date").toString(),
            "notes" to f.getString("notes"),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }
}
