package com.clinube.clinica

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
import com.clinube.comun.sql.Filas.fechaONula
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Evoluciones del paciente (RF-HCL-03, `apps/clinical/views.py`): notas
 * clínicas, recetas e indicaciones de cuidado, que la app enseña al
 * paciente si son visibles para él.
 *
 * La historia clínica no pierde lo que decía: cada edición deja en la
 * auditoría el contenido anterior, y cada borrado, el contenido entero.
 * Todo es de administración, doctores y auxiliares.
 */
@RestController
class ControladorDeEvoluciones(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val mapeador: ObjectMapper,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val CLINICOS = Permisos.rol("admin", "doctor", "auxiliary")
        val TIPOS = linkedMapOf(
            "clinical_note" to "Nota clínica", "prescription" to "Receta", "care_instruction" to "Indicación de cuidado",
        )

        /**
         * `BooleanFilter` de django-filter: su `BooleanWidget` pasa a
         * minúsculas y solo entiende estas cuatro; cualquier otra cosa no
         * filtra (no es un error).
         */
        fun booleanoDeFiltro(valor: String?): Boolean? = when (valor?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }

        private const val SELECT = """
            select e.*, u.full_name as doctor_nombre, u.email as doctor_correo, d.user_id as doctor_usuario,
                   c.full_name as autor_nombre, c.email as autor_correo
            from clinical_evolution e
            left join agenda_doctor d on d.id = e.doctor_id
            left join accounts_user u on u.id = d.user_id
            left join accounts_user c on c.id = e.created_by_id"""
    }

    private val clinica get() = contexto.clinica?.id

    /**
     * GET /api/v1/patients/{id}/evolutions/ — paginada, por fecha y después
     * por creación, la más reciente primero. Filtros: `?type=` y
     * `?visible_to_patient=`.
     */
    @GetMapping("/api/v1/patients/$ID/evolutions/")
    fun lista(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(CLINICOS)
        fun parametro(nombre: String) = peticion.getParameterValues(nombre)?.lastOrNull()
        val condiciones = mutableListOf("e.patient_id = :id", "e.tenant_id = :clinica")
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val tipo = parametro("type")
        if (!tipo.isNullOrEmpty()) {
            if (tipo !in TIPOS) throw ExcepcionApi.validacion(mapOf("type" to listOf(MensajesDeDjango.opcionNoValida(tipo))))
            condiciones += "e.type = :tipo"
            parametros["tipo"] = tipo
        }
        booleanoDeFiltro(parametro("visible_to_patient"))?.let {
            condiciones += "e.visible_to_patient = :visible"
            parametros["visible"] = it
        }
        val donde = condiciones.joinToString(" and ")
        val total = jdbc.queryForObject("select count(*) from clinical_evolution e where $donde", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(parametro("page"), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            "$SELECT where $donde order by e.date desc, e.created_at desc limit :cuantos offset :desde", parametros,
        ) { f, _ -> evolucion(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/patients/{id}/evolutions/ — se valida, después se busca
     * al paciente (activo). El doctor es el de quien la registra, si lo es,
     * y el autor queda siempre.
     */
    @PostMapping("/api/v1/patients/$ID/evolutions/")
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(CLINICOS)
        val leidos = leer(CamposDrf(CuerpoDrf.objeto(cuerpo)))
        val activo = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!activo) throw ExcepcionApi.http404("Patient")
        val doctor = jdbc.query(
            "select id from agenda_doctor where user_id = :usuario and tenant_id = :clinica order by id limit 1",
            mapOf("usuario" to usuario.id, "clinica" to clinica),
        ) { f, _ -> f.uuid("id") }.firstOrNull()
        val nueva = UUID.randomUUID()
        jdbc.update(
            """insert into clinical_evolution (id, updated_at, type, date, notes, visible_to_patient, created_at, appointment_id,
               doctor_id, patient_id, tenant_id, created_by_id, follow_up_date)
               values (:id, :actualizado, :type, :date, :notes, :visible_to_patient, :creado, :appointment, :doctor, :paciente,
               :clinica, :usuario, :follow_up_date)""",
            mapOf(
                "id" to nueva, "creado" to Timestamp.from(zona.ahora()), "actualizado" to Timestamp.from(zona.ahora()),
                "type" to (leidos["type"] ?: "clinical_note"), "date" to leidos["date"], "notes" to leidos["notes"],
                "visible_to_patient" to (leidos["visible_to_patient"] ?: false), "appointment" to leidos["appointment"],
                "doctor" to doctor, "paciente" to id, "clinica" to clinica, "usuario" to usuario.id,
                "follow_up_date" to leidos["follow_up_date"],
            ),
        )
        auditar("create_evolution", id.toString(), usuario.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(una(nueva))
    }

    /** GET /api/v1/evolutions/{id}/ */
    @GetMapping("/api/v1/evolutions/$ID/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(CLINICOS)
        return deEstaClinica(id)
    }

    @PutMapping("/api/v1/evolutions/$ID/")
    fun reemplazar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, parcial = false)

    @PatchMapping("/api/v1/evolutions/$ID/")
    fun modificar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, parcial = true)

    /**
     * PUT/PATCH /api/v1/evolutions/{id}/ — antes de guardar, la auditoría
     * apunta lo que decía (notas, tipo y visibilidad). Si los datos no son
     * válidos no se apunta nada: Django valida antes.
     */
    private fun editar(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        val usuario = permisos.exigir(CLINICOS)
        val anterior = deEstaClinica(id)
        val leidos = leer(CamposDrf(CuerpoDrf.objeto(cuerpo), parcial))
        val metadatos = linkedMapOf(
            "previous_notes" to anterior["notes"], "previous_type" to anterior["type"],
            "previous_visible_to_patient" to anterior["visible_to_patient"],
        )
        auditar("update_evolution", id.toString(), usuario.id, mapeador.writeValueAsString(metadatos))
        val columnas = leidos.mapKeys { (campo, _) -> if (campo == "appointment") "appointment_id" else campo }
        val asignaciones = columnas.keys.map { "$it = :$it" } + "updated_at = :actualizado"
        jdbc.update(
            "update clinical_evolution set ${asignaciones.joinToString(", ")} where id = :id",
            columnas + mapOf("id" to id, "actualizado" to Timestamp.from(zona.ahora())),
        )
        return una(id)
    }

    /** DELETE /api/v1/evolutions/{id}/ — la auditoría guarda la evolución entera. */
    @DeleteMapping("/api/v1/evolutions/$ID/")
    fun borrar(@PathVariable id: UUID): ResponseEntity<Void> {
        val usuario = permisos.exigir(CLINICOS)
        val fila = jdbc.query(
            "select patient_id, type, date, notes from clinical_evolution where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica),
        ) { f, _ ->
            linkedMapOf(
                "patient_id" to f.uuid("patient_id").toString(), "type" to f.getString("type"),
                "date" to f.fecha("date").toString(), "notes" to f.getString("notes"),
            )
        }.firstOrNull() ?: throw ExcepcionApi.http404("Evolution")
        auditar("delete_evolution", id.toString(), usuario.id, mapeador.writeValueAsString(fila))
        jdbc.update("delete from clinical_evolution where id = :id", mapOf("id" to id))
        return ResponseEntity.noContent().build()
    }

    // ── Comunes ──────────────────────────────────────────────────────

    /**
     * Los campos de `EvolutionSerializer`, en su orden.
     *
     * DIFERENCIA DELIBERADA: la cita tiene que ser de esta clínica; Django la
     * busca en todas (`Appointment.objects.all()`).
     */
    private fun leer(campos: CamposDrf): Map<String, Any?> {
        val leidos = LinkedHashMap<String, Any?>()
        fun <T> poner(campo: String, leido: Leido<T>) {
            if (leido is Leido.Valor) leidos[campo] = leido.valor
        }
        poner("appointment", campos.clave("appointment", requerido = false, nulo = true) {
            jdbc.queryForObject(
                "select count(*) from agenda_appointment where id = :id and tenant_id = :clinica",
                mapOf("id" to it, "clinica" to clinica), Long::class.java,
            )!! > 0
        })
        poner("type", campos.opcion("type", TIPOS.keys))
        poner("date", campos.fecha("date"))
        poner("notes", campos.texto("notes", maximo = null))
        poner("visible_to_patient", campos.booleano("visible_to_patient"))
        poner("follow_up_date", campos.fecha("follow_up_date", requerido = false, nulo = true))
        campos.validar()
        return leidos
    }

    private fun una(id: UUID) = jdbc.query("$SELECT where e.id = :id", mapOf("id" to id)) { f, _ -> evolucion(f) }.first()

    private fun deEstaClinica(id: UUID) =
        jdbc.query("$SELECT where e.id = :id and e.tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica)) { f, _ ->
            evolucion(f)
        }.firstOrNull() ?: throw ExcepcionApi.http404("Evolution")

    private fun auditar(accion: String, entidadId: String, usuario: UUID, metadatos: String = "{}") {
        jdbc.update(
            """insert into accounts_auditlog (id, action, entity_type, entity_id, ip_address, metadata, created_at, tenant_id,
               user_id) values (:aid, :accion, 'Evolution', :eid, null, :metadatos::jsonb, :ahora, :clinica, :usuario)""",
            mapOf(
                "aid" to UUID.randomUUID(), "accion" to accion, "eid" to entidadId, "metadatos" to metadatos,
                "ahora" to Timestamp.from(zona.ahora()), "clinica" to clinica, "usuario" to usuario,
            ),
        )
    }

    /**
     * `EvolutionSerializer`. `doctor_name` es `source="doctor.full_name"`:
     * sin doctor, DRF no lo escribe (ni siquiera como nulo).
     */
    private fun evolucion(f: ResultSet): Map<String, Any?> {
        val doctor = f.uuidONulo("doctor_id")
        // `Doctor.full_name`: el nombre del usuario, o su correo, o su id.
        val nombreDelDoctor = doctor?.let {
            f.getString("doctor_nombre").takeUnless { n -> n.isNullOrEmpty() }
                ?: f.getString("doctor_correo").takeUnless { c -> c.isNullOrEmpty() }
                ?: f.uuid("doctor_usuario").toString()
        }
        val autor = f.uuidONulo("created_by_id")?.let {
            f.getString("autor_nombre").takeUnless { n -> n.isNullOrEmpty() } ?: f.getString("autor_correo")
        }
        val tipo = f.getString("type")
        return linkedMapOf<String, Any?>("id" to f.uuid("id").toString(), "doctor" to doctor?.toString()).apply {
            if (doctor != null) put("doctor_name", nombreDelDoctor)
            put("registered_by", nombreDelDoctor ?: autor)
            put("appointment", f.uuidONulo("appointment_id")?.toString())
            put("type", tipo)
            put("type_display", TIPOS[tipo] ?: tipo)
            put("date", f.fecha("date").toString())
            put("notes", f.getString("notes"))
            put("visible_to_patient", f.getBoolean("visible_to_patient"))
            put("follow_up_date", f.fechaONula("follow_up_date")?.toString())
            put("created_at", FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona))
        }
    }
}
