package com.clinube.clinica

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.json.JsonDePython
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.cuentas.dominio.Usuario
import com.clinube.pacientes.ControladorDeFichas
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Historia clínica odontológica según el formulario MSP HCU-form.033/2021
 * (`apps/clinical/form033_views.py`): una por consulta, con las secciones
 * B-G e I. La O —quién la registró— no la escribe nadie: se toma de la
 * credencial con la que se guarda, en cada alta y en cada edición.
 *
 * Ver la lista, todo el personal (también recepción); crearla, verla una a
 * una y editarla, administración, doctores y auxiliares.
 */
@RestController
class ControladorDeFormulario033(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val mapeador: ObjectMapper,
) {

    companion object {
        const val ID = ControladorDeFichas.ID
        val EDITAN = Permisos.rol("admin", "doctor", "auxiliary")
        val VEN = Permisos.rol("admin", "doctor", "auxiliary", "reception")

        /** Las secciones con casillas y detalle: `JSONField`, por omisión `{}`. */
        val SECCIONES = listOf("antecedentes_personales", "antecedentes_familiares", "examen_estomatognatico", "indicadores_salud_bucal")
    }

    /** GET /api/v1/patients/{id}/form033/ — sin paginar, la última primero. */
    @GetMapping("/api/v1/patients/$ID/form033/")
    fun lista(@PathVariable id: UUID): List<Map<String, Any?>> {
        permisos.exigir(VEN)
        // Como Django: filtra por clínica y paciente, sin comprobar que el
        // paciente exista (uno que no existe da una lista vacía).
        return jdbc.query(
            """select * from clinical_form033record where tenant_id = :clinica and patient_id = :id
               order by date desc, created_at desc""",
            mapOf("clinica" to contexto.clinica?.id, "id" to id),
        ) { f, _ -> deFila(f) }
    }

    /**
     * POST /api/v1/patients/{id}/form033/ — primero se valida, después se
     * busca al paciente. Se admite un paciente dado de baja, como en Django
     * (`Patient.objects.get` sin `is_active`).
     */
    @PostMapping("/api/v1/patients/$ID/form033/")
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(EDITAN)
        val leidos = leer(CamposDrf(CuerpoDrf.objeto(cuerpo)))
        val clinica = contexto.clinica?.id
        val existe = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica",
            mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        // Django da aquí un 500 (`Patient.DoesNotExist` sin capturar): docs/DIFERENCIAS.md.
        if (!existe) throw ExcepcionApi.http404("Patient")
        val (doctor, constancia) = constancia(usuario)
        val nuevo = UUID.randomUUID()
        val creado = zona.ahora()
        val actualizado = zona.ahora()
        val columnas = linkedMapOf<String, Any?>(
            "date" to null, "motivo_consulta" to "", "embarazada" to null, "enfermedad_actual" to "",
            "temperatura" to null, "pulso" to null, "frecuencia_respiratoria" to null, "presion_arterial" to "",
        )
        SECCIONES.forEach { columnas[it] = mapeador.createObjectNode() }
        columnas.putAll(leidos)
        val parametros = columnas.mapValues { (_, v) -> paraLaBase(v) } + mapOf(
            "id" to nuevo, "clinica" to clinica, "paciente" to id, "doctor" to doctor, "usuario" to usuario.id,
            "constancia" to mapeador.writeValueAsString(constancia),
            "creado" to Timestamp.from(creado), "actualizado" to Timestamp.from(actualizado),
        )
        jdbc.update(
            """insert into clinical_form033record (id, motivo_consulta, embarazada, enfermedad_actual, antecedentes_personales,
               antecedentes_familiares, temperatura, pulso, frecuencia_respiratoria, presion_arterial, examen_estomatognatico,
               indicadores_salud_bucal, professional_snapshot, date, created_at, updated_at, created_by_id, doctor_id,
               patient_id, tenant_id)
               values (:id, :motivo_consulta, :embarazada, :enfermedad_actual, :antecedentes_personales::jsonb,
               :antecedentes_familiares::jsonb, :temperatura, :pulso, :frecuencia_respiratoria, :presion_arterial,
               :examen_estomatognatico::jsonb, :indicadores_salud_bucal::jsonb, :constancia::jsonb, :date, :creado,
               :actualizado, :usuario, :doctor, :paciente, :clinica)""",
            parametros,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            representar(nuevo, columnas, registradoPor(constancia), creado, actualizado),
        )
    }

    /** GET /api/v1/form033/{id}/ */
    @GetMapping("/api/v1/form033/$ID/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(EDITAN)
        return representar(fila(id))
    }

    @PutMapping("/api/v1/form033/$ID/")
    fun reemplazar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, parcial = false)

    @PatchMapping("/api/v1/form033/$ID/")
    fun modificar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, parcial = true)

    /**
     * PUT/PATCH /api/v1/form033/{id}/ — cada edición vuelve a tomar la
     * constancia del profesional (O) de quien guarda. El doctor de la
     * historia NO cambia: Django solo refresca la constancia.
     */
    private fun editar(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        val usuario = permisos.exigir(EDITAN)
        val actual = fila(id)
        val leidos = leer(CamposDrf(CuerpoDrf.objeto(cuerpo), parcial))
        val (_, constancia) = constancia(usuario)
        val actualizado = zona.ahora()
        val asignaciones = leidos.keys.map { if (it in SECCIONES) "$it = :$it::jsonb" else "$it = :$it" } +
            listOf("professional_snapshot = :constancia::jsonb", "updated_at = :actualizado")
        jdbc.update(
            "update clinical_form033record set ${asignaciones.joinToString(", ")} where id = :id",
            leidos.mapValues { (_, v) -> paraLaBase(v) } + mapOf(
                "id" to id, "constancia" to mapeador.writeValueAsString(constancia), "actualizado" to Timestamp.from(actualizado),
            ),
        )
        // La respuesta es la del objeto en memoria: lo que llegó, tal cual
        // llegó (el orden de las claves, el «-0.0»), y el resto, de la base.
        val columnas = LinkedHashMap(actual.columnas).apply { putAll(leidos) }
        return representar(id, columnas, registradoPor(constancia), actual.creado, actualizado)
    }

    // ── Lectura y escritura ──────────────────────────────────────────

    /** Los campos del serializador, en su orden: así salen los errores. */
    private fun leer(campos: CamposDrf): Map<String, Any?> {
        val leidos = LinkedHashMap<String, Any?>()
        fun <T> poner(columna: String, leido: Leido<T>) {
            if (leido is Leido.Valor) leidos[columna] = leido.valor
        }
        poner("date", campos.fecha("date"))
        poner("motivo_consulta", campos.texto("motivo_consulta", requerido = false, enBlanco = true))
        poner("embarazada", campos.booleano("embarazada", nulo = true))
        poner("enfermedad_actual", campos.texto("enfermedad_actual", requerido = false, enBlanco = true))
        poner("antecedentes_personales", campos.json("antecedentes_personales"))
        poner("antecedentes_familiares", campos.json("antecedentes_familiares"))
        poner("temperatura", campos.decimal("temperatura", maxDigitos = 4, decimales = 1, nulo = true))
        poner("pulso", campos.entero("pulso", minimo = 0, maximo = 32767, nulo = true))
        poner("frecuencia_respiratoria", campos.entero("frecuencia_respiratoria", minimo = 0, maximo = 32767, nulo = true))
        poner("presion_arterial", campos.texto("presion_arterial", maximo = 15, requerido = false, enBlanco = true))
        poner("examen_estomatognatico", campos.json("examen_estomatognatico"))
        poner("indicadores_salud_bucal", campos.json("indicadores_salud_bucal"))
        campos.validar()
        return leidos
    }

    private fun paraLaBase(valor: Any?): Any? = when (valor) {
        is JsonNode -> mapeador.writeValueAsString(JsonDePython.comoPython(valor))
        is CamposDrf.Decimal -> valor.valor
        else -> valor
    }

    private class Fila(val columnas: Map<String, Any?>, val registradoPor: String?, val creado: Instant, val actualizado: Instant, val id: UUID)

    private fun fila(id: UUID): Fila = jdbc.query(
        "select * from clinical_form033record where id = :id and tenant_id = :clinica",
        mapOf("id" to id, "clinica" to contexto.clinica?.id),
    ) { f, _ -> leerFila(f) }.firstOrNull() ?: throw ExcepcionApi.http404("Form033Record")

    private fun leerFila(f: ResultSet): Fila {
        val columnas = linkedMapOf<String, Any?>(
            "date" to f.fecha("date"),
            "motivo_consulta" to f.getString("motivo_consulta"),
            "embarazada" to f.getObject("embarazada") as Boolean?,
            "enfermedad_actual" to f.getString("enfermedad_actual"),
            "temperatura" to f.getBigDecimal("temperatura")?.let { CamposDrf.Decimal(it, it.setScale(1).toPlainString()) },
            "pulso" to (f.getObject("pulso") as Number?)?.toLong(),
            "frecuencia_respiratoria" to (f.getObject("frecuencia_respiratoria") as Number?)?.toLong(),
            "presion_arterial" to f.getString("presion_arterial"),
        )
        SECCIONES.forEach { columnas[it] = mapeador.readTree(f.getString(it)) }
        val constancia = mapeador.readTree(f.getString("professional_snapshot"))
        return Fila(columnas, registradoPor(constancia), f.instante("created_at"), f.instante("updated_at"), f.uuid("id"))
    }

    private fun deFila(f: ResultSet) = representar(leerFila(f))

    private fun representar(fila: Fila) = representar(fila.id, fila.columnas, fila.registradoPor, fila.creado, fila.actualizado)

    /** `Form033Serializer`, en el orden de sus campos. */
    private fun representar(id: UUID, c: Map<String, Any?>, registradoPor: String?, creado: Instant, actualizado: Instant) =
        linkedMapOf(
            "id" to id.toString(),
            "date" to c["date"].toString(),
            "motivo_consulta" to c["motivo_consulta"],
            "embarazada" to c["embarazada"],
            "enfermedad_actual" to c["enfermedad_actual"],
            "antecedentes_personales" to JsonDePython.comoPython(c["antecedentes_personales"] as JsonNode),
            "antecedentes_familiares" to JsonDePython.comoPython(c["antecedentes_familiares"] as JsonNode),
            "temperatura" to (c["temperatura"] as CamposDrf.Decimal?)?.texto,
            "pulso" to c["pulso"],
            "frecuencia_respiratoria" to c["frecuencia_respiratoria"],
            "presion_arterial" to c["presion_arterial"],
            "examen_estomatognatico" to JsonDePython.comoPython(c["examen_estomatognatico"] as JsonNode),
            "indicadores_salud_bucal" to JsonDePython.comoPython(c["indicadores_salud_bucal"] as JsonNode),
            "registered_by" to registradoPor,
            "created_at" to FormatoDeDrf.deDrf(creado, zona.zona),
            "updated_at" to FormatoDeDrf.deDrf(actualizado, zona.zona),
        )

    // ── La constancia del profesional (literal O) ────────────────────

    /**
     * `_professional_snapshot`: el doctor de quien guarda en esta clínica (si
     * lo es) y la constancia, armada SOLO desde la credencial.
     */
    private fun constancia(usuario: Usuario): Pair<UUID?, JsonNode> {
        val doctor = jdbc.query(
            "select id, license_number, signature_image from agenda_doctor where tenant_id = :clinica and user_id = :usuario order by id limit 1",
            mapOf("clinica" to contexto.clinica?.id, "usuario" to usuario.id),
        ) { f, _ -> Triple(f.uuid("id"), f.getString("license_number"), f.getString("signature_image")) }.firstOrNull()
        val constancia = mapeador.createObjectNode()
            .put("full_name", usuario.nombreCompleto.ifEmpty { usuario.correo ?: "" })
            .put("email", usuario.correo)
            .put("role", usuario.rol)
            .put("license_number", doctor?.second ?: "")
            .put("user_id", usuario.id.toString())
            .put("recorded_at", FormatoDeDrf.isoformatDePython(zona.ahora()))
            .put("has_signature", !doctor?.third.isNullOrEmpty())
        return doctor?.first to constancia
    }

    /** `get_registered_by`: «Nombre · Registro», o null si no hay nada. */
    private fun registradoPor(constancia: JsonNode): String? {
        val nombre = constancia.get("full_name")?.let { Python.str(it) } ?: ""
        val registro = constancia.get("license_number")
        val texto = nombre + if (registro != null && Python.verdad(registro)) " · ${Python.str(registro)}" else ""
        return texto.ifEmpty { null }
    }
}
