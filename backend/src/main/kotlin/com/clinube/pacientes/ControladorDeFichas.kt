package com.clinube.pacientes

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fechaONula
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.instanteONulo
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.comun.validacion.Telefonos
import com.clinube.configuracion.marca.ControladorDeMarca
import com.clinube.legal.TextosLegales
import com.clinube.plataforma.ConfiguracionPlataforma
import com.clinube.plataforma.ServicioDeConfiguracion
import com.clinube.plataforma.ServicioDeFuncionalidades
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.sql.Timestamp
import java.util.UUID

/**
 * La ficha del paciente (`apps/patients`): alta, ficha, edición, baja,
 * antecedentes, aviso de privacidad entregado y estado del acceso a la app.
 *
 * Los permisos son los de la matriz del SRS, como en Django: ver y listar,
 * todo el personal; dar de alta, editar y dar de baja, administración y
 * recepción; los antecedentes (datos clínicos), administración, doctores y
 * auxiliares, nunca recepción.
 */
@RestController
class ControladorDeFichas(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val configuracion: ServicioDeConfiguracion,
    private val funcionalidades: ServicioDeFuncionalidades,
) {

    companion object {
        /** El conversor `<uuid:pk>` de Django: solo minúsculas. Lo demás no es una ruta (404). */
        const val ID = "{id:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}}"

        val EDITAN = Permisos.rol("admin", "reception")
        val CLINICOS = Permisos.rol("admin", "doctor", "auxiliary")

        val MEDIOS = linkedMapOf(
            "en_persona" to "En persona (impreso o en pantalla)",
            "enlace" to "Enlace enviado al paciente",
        )
    }

    // ── Alta, ficha, edición y baja ──────────────────────────────────

    /**
     * POST /api/v1/patients/ — RF-PAC-01. Solo administración y recepción.
     *
     * La foto se guarda como texto vacío y no como nulo, como hace Django con
     * un `ImageField` que no llega: las dos cosas se responden igual (`null`),
     * pero la base tiene que quedar igual también.
     */
    @PostMapping("/api/v1/patients/")
    @Transactional
    fun alta(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(EDITAN)
        val datos = leerFicha(CuerpoDrf.objeto(cuerpo), parcial = false, actual = null)
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """
            insert into patients_patient (id, created_at, updated_at, is_active, deleted_at, first_name, last_name,
              national_id, birth_date, phone, email, address, photo, tenant_id, user_id, agreement_id, sex)
            values (:id, :ahora, :ahora, true, null, :first_name, :last_name, :national_id, :birth_date,
                    :phone, :email, :address, '', :clinica, null, :agreement, '')
            """,
            mapOf(
                "id" to id, "ahora" to ahora, "clinica" to contexto.clinica?.id,
                "first_name" to datos["first_name"], "last_name" to datos["last_name"],
                "national_id" to datos["national_id"], "birth_date" to datos["birth_date"],
                "phone" to (datos["phone"] ?: ""), "email" to (datos["email"] ?: ""),
                "address" to (datos["address"] ?: ""), "agreement" to datos["agreement"],
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ficha(id, parcial = false))
    }

    /** GET /api/v1/patients/{id}/ — todo el personal. */
    @GetMapping("/api/v1/patients/$ID/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(Permisos.PERSONAL)
        pacienteActivo(id)
        return ficha(id, parcial = false)
    }

    @PutMapping("/api/v1/patients/$ID/")
    @Transactional
    fun reemplazar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, false)

    @PatchMapping("/api/v1/patients/$ID/")
    @Transactional
    fun modificar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = editar(id, cuerpo, true)

    /**
     * PUT y PATCH. Con PUT, los obligatorios tienen que venir; los demás, si
     * no vienen, se quedan como estaban (así es DRF). Solo se tocan los que
     * llegan, y `updated_at` cambia porque Django guarda la fila entera.
     */
    private fun editar(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(EDITAN)
        pacienteActivo(id)
        val datos = leerFicha(CuerpoDrf.objeto(cuerpo), parcial, actual = id)
        if (datos.isNotEmpty()) {
            val columnas = datos.keys.joinToString(", ") { columna ->
                if (columna == "agreement") "agreement_id = :agreement" else "$columna = :$columna"
            }
            jdbc.update(
                "update patients_patient set $columnas, updated_at = :ahora where id = :id",
                datos + mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())),
            )
        } else {
            jdbc.update("update patients_patient set updated_at = :ahora where id = :id",
                mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())))
        }
        return ficha(id, parcial)
    }

    /**
     * DELETE /api/v1/patients/{id}/ — baja LÓGICA: nunca se borra un
     * paciente. Se guarda solo `is_active` y `deleted_at`, así que
     * `updated_at` no cambia (como `soft_delete` de Django).
     */
    @DeleteMapping("/api/v1/patients/$ID/")
    @Transactional
    fun baja(@PathVariable id: UUID): ResponseEntity<Void> {
        permisos.exigir(EDITAN)
        pacienteActivo(id)
        jdbc.update(
            "update patients_patient set is_active = false, deleted_at = :ahora where id = :id",
            mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora())),
        )
        return ResponseEntity.noContent().build()
    }

    /**
     * Los campos del `PatientSerializer`, EN SU ORDEN: los errores salen en
     * ese orden. Devuelve solo los que hay que guardar.
     */
    private fun leerFicha(cuerpo: com.fasterxml.jackson.databind.node.ObjectNode, parcial: Boolean, actual: UUID?): Map<String, Any?> {
        val campos = CamposDrf(cuerpo, parcial)
        val clinica = contexto.clinica?.id
        val guardar = LinkedHashMap<String, Any?>()
        fun <T> poner(columna: String, leido: Leido<T>) {
            if (leido is Leido.Valor) guardar[columna] = leido.valor
        }
        poner("first_name", campos.texto("first_name", maximo = 100))
        poner("last_name", campos.texto("last_name", maximo = 100))
        val cedula = campos.texto("national_id", maximo = 20)
        if (cedula is Leido.Valor) {
            // `validate_national_id`: una sola ficha ACTIVA por cédula en la clínica.
            val repetida = jdbc.queryForObject(
                """select count(*) from patients_patient where tenant_id = :clinica and national_id = :cedula
                   and is_active and (cast(:actual as uuid) is null or id <> :actual)""",
                mapOf("clinica" to clinica, "cedula" to cedula.valor, "actual" to actual), Long::class.java,
            )!! > 0
            if (repetida) campos.error("national_id", "Ya existe un paciente activo con esta identificación.")
            else guardar["national_id"] = cedula.valor
        }
        poner("birth_date", campos.fecha("birth_date", requerido = false, nulo = true))
        poner("phone", campos.texto("phone", maximo = 20, requerido = false, enBlanco = true))
        poner("email", campos.texto("email", maximo = 254, requerido = false, enBlanco = true, correo = true))
        poner("address", campos.texto("address", maximo = 255, requerido = false, enBlanco = true))
        // El convenio existe en CUALQUIER clínica para DRF (su queryset no
        // filtra), y después `validate_agreement` comprueba que sea de esta.
        val convenio = campos.clave("agreement", requerido = false, nulo = true) { c ->
            jdbc.queryForObject("select count(*) from configuration_agreement where id = :id", mapOf("id" to c), Long::class.java)!! > 0
        }
        if (convenio is Leido.Valor) {
            val c = convenio.valor
            val deEsta = c == null || jdbc.queryForObject(
                "select count(*) from configuration_agreement where id = :id and tenant_id = :clinica",
                mapOf("id" to c, "clinica" to clinica), Long::class.java,
            )!! > 0
            if (!deEsta) campos.error("agreement", "El convenio no pertenece a esta clínica.") else guardar["agreement"] = c
        }
        campos.validar()
        return guardar
    }

    /**
     * La ficha como la devuelve el `PatientSerializer`. Una rareza de DRF
     * que se copia: en una actualización PARCIAL, si el paciente se queda
     * sin convenio, `agreement_name` no sale —ni siquiera como nulo—,
     * porque DRF salta los valores por defecto en los PATCH.
     */
    private fun ficha(id: UUID, parcial: Boolean): Map<String, Any?> =
        jdbc.queryForObject(
            """select p.*, a.name as agreement_name from patients_patient p
               left join configuration_agreement a on a.id = p.agreement_id where p.id = :id""",
            mapOf("id" to id),
        ) { f, _ ->
            val convenio = f.uuidONulo("agreement_id")
            linkedMapOf<String, Any?>(
                "id" to f.uuid("id").toString(),
                "first_name" to f.getString("first_name"),
                "last_name" to f.getString("last_name"),
                "full_name" to "${f.getString("first_name")} ${f.getString("last_name")}",
                "national_id" to f.getString("national_id"),
                "birth_date" to f.fechaONula("birth_date")?.toString(),
                "phone" to f.getString("phone"),
                "email" to f.getString("email"),
                "address" to f.getString("address"),
                "photo" to urlAbsoluta(f.getString("photo")),
                "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
                "agreement" to convenio?.toString(),
            ).also { if (convenio != null || !parcial) it["agreement_name"] = f.getString("agreement_name") }
        }!!

    /** `ImageField` de DRF con petición: la dirección ABSOLUTA del archivo. */
    private fun urlAbsoluta(nombre: String?): String? {
        val relativa = ControladorDeMarca.urlDeArchivo(nombre) ?: return null
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(relativa).build().toUriString()
    }

    /** El paciente ACTIVO de esta clínica, o el 404 de Django. */
    private fun pacienteActivo(id: UUID) {
        val hay = jdbc.queryForObject(
            "select count(*) from patients_patient where id = :id and tenant_id = :clinica and is_active",
            mapOf("id" to id, "clinica" to contexto.clinica?.id), Long::class.java,
        )!! > 0
        if (!hay) throw ExcepcionApi.http404("Patient")
    }

    // ── Antecedentes ─────────────────────────────────────────────────

    @GetMapping("/api/v1/patients/$ID/medical-background/")
    fun antecedentes(@PathVariable id: UUID): Map<String, Any?> = antecedentes(id, null, parcial = false)

    @PutMapping("/api/v1/patients/$ID/medical-background/")
    fun reemplazarAntecedentes(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) =
        antecedentes(id, CuerpoDrf.objeto(cuerpo), parcial = false)

    @PatchMapping("/api/v1/patients/$ID/medical-background/")
    fun modificarAntecedentes(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) =
        antecedentes(id, CuerpoDrf.objeto(cuerpo), parcial = true)

    /**
     * GET/PUT/PATCH /api/v1/patients/{id}/medical-background/ — RF-PAC-03.
     *
     * El acceso a datos clínicos se audita SIEMPRE, también al leer
     * (LOPDP, RNF-004). En el orden de Django: primero se busca (y si no
     * existe, se crea) la fila de antecedentes y se apunta el acceso, y
     * DESPUÉS se valida lo que llega. Un PATCH con datos malos deja su
     * rastro igual.
     *
     * Por eso estos métodos NO son `@Transactional`: con una transacción, el
     * error de validación la deshacía y se llevaba la fila de auditoría. En
     * Django la petición no es atómica y la fila se queda. Lo cazó la
     * comparación del estado de las dos bases (`scripts/comparar-estado.py`).
     */
    private fun antecedentes(id: UUID, cuerpo: com.fasterxml.jackson.databind.node.ObjectNode?, parcial: Boolean): Map<String, Any?> {
        val usuario = permisos.exigir(CLINICOS)
        pacienteActivo(id)
        val ahora = Timestamp.from(zona.ahora())
        if (jdbc.queryForObject("select count(*) from patients_medicalbackground where patient_id = :id",
                mapOf("id" to id), Long::class.java)!! == 0L) {
            jdbc.update(
                """insert into patients_medicalbackground (allergies, medications, conditions, is_pregnant, updated_at,
                   updated_by_id, patient_id) values ('', '', '', null, :ahora, null, :id)""",
                mapOf("id" to id, "ahora" to ahora),
            )
        }
        jdbc.update(
            """insert into accounts_auditlog (id, action, entity_type, entity_id, ip_address, metadata, created_at,
               tenant_id, user_id) values (:aid, :accion, 'MedicalBackground', :pid, null, '{}'::jsonb, :ahora, :clinica, :usuario)""",
            mapOf(
                "aid" to UUID.randomUUID(), "pid" to id.toString(), "ahora" to ahora,
                "accion" to if (cuerpo == null) "view_medical_background" else "update_medical_background",
                "clinica" to contexto.clinica?.id, "usuario" to usuario.id,
            ),
        )
        if (cuerpo != null) {
            val campos = CamposDrf(cuerpo, parcial)
            val guardar = LinkedHashMap<String, Any?>()
            for (texto in listOf("allergies", "medications", "conditions")) {
                val leido = campos.texto(texto, requerido = false, enBlanco = true)
                if (leido is Leido.Valor) guardar[texto] = leido.valor
            }
            val embarazo = campos.booleano("is_pregnant", nulo = true)
            if (embarazo is Leido.Valor) guardar["is_pregnant"] = embarazo.valor
            campos.validar()
            val asignaciones = (guardar.keys.map { "$it = :$it" } + listOf("updated_at = :ahora", "updated_by_id = :usuario"))
                .joinToString(", ")
            jdbc.update(
                "update patients_medicalbackground set $asignaciones where patient_id = :id",
                guardar + mapOf("id" to id, "ahora" to Timestamp.from(zona.ahora()), "usuario" to usuario.id),
            )
        }
        return jdbc.queryForObject(
            "select * from patients_medicalbackground where patient_id = :id", mapOf("id" to id),
        ) { f, _ ->
            linkedMapOf<String, Any?>(
                "allergies" to f.getString("allergies"),
                "medications" to f.getString("medications"),
                "conditions" to f.getString("conditions"),
                "is_pregnant" to f.getObject("is_pregnant") as Boolean?,
                "updated_at" to FormatoDeDrf.deDrf(f.instante("updated_at"), zona.zona),
            )
        }!!
    }

    // ── Aviso de privacidad entregado ────────────────────────────────

    /**
     * GET /api/v1/patients/{id}/aviso-privacidad/ — si se informó al
     * paciente de la versión vigente del aviso (LOPDP) y el historial.
     */
    @GetMapping("/api/v1/patients/$ID/aviso-privacidad/")
    fun aviso(@PathVariable id: UUID): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(EDITAN)
        pacienteActivo(id)
        return respuestaDelAviso(id, HttpStatus.OK)
    }

    /**
     * POST /api/v1/patients/{id}/aviso-privacidad/ — registra la entrega de
     * la versión vigente, con quién la registra. Registrarla dos veces no
     * duplica nada: 201 la primera, 200 después.
     */
    @PostMapping("/api/v1/patients/$ID/aviso-privacidad/")
    @Transactional
    fun registrarAviso(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(EDITAN)
        pacienteActivo(id)
        val medioNodo = cuerpo?.takeIf { it.isObject }?.get("medio")
        val medio = if (medioNodo == null) "en_persona" else medioNodo.takeIf { it.isTextual }?.textValue()
        if (medio == null || medio !in MEDIOS) {
            return ResponseEntity.badRequest().body(mapOf("detail" to "Medio de entrega no válido."))
        }
        val parametros = mapOf("clinica" to contexto.clinica?.id, "paciente" to id, "version" to TextosLegales.AVISO_VERSION)
        val ya = jdbc.queryForObject(
            "select count(*) from patients_avisoprivacidadentregado where tenant_id = :clinica and patient_id = :paciente and version = :version",
            parametros, Long::class.java,
        )!! > 0
        if (!ya) {
            val ahora = Timestamp.from(zona.ahora())
            jdbc.update(
                """insert into patients_avisoprivacidadentregado (id, created_at, updated_at, version, medio, patient_id,
                   registrado_por_id, tenant_id) values (:aid, :ahora, :ahora, :version, :medio, :paciente, :usuario, :clinica)""",
                parametros + mapOf("aid" to UUID.randomUUID(), "ahora" to ahora, "medio" to medio, "usuario" to usuario.id),
            )
        }
        return respuestaDelAviso(id, if (ya) HttpStatus.OK else HttpStatus.CREATED)
    }

    /** La fecha de cada entrega sale de un diccionario, no de un serializador: en UTC con «Z». */
    private fun respuestaDelAviso(id: UUID, estado: HttpStatus): ResponseEntity<Map<String, Any?>> {
        val entregas = jdbc.query(
            """select e.version, e.medio, e.created_at, u.full_name from patients_avisoprivacidadentregado e
               left join accounts_user u on u.id = e.registrado_por_id
               where e.tenant_id = :clinica and e.patient_id = :paciente order by e.created_at desc""",
            mapOf("clinica" to contexto.clinica?.id, "paciente" to id),
        ) { f, _ ->
            linkedMapOf<String, Any?>(
                "version" to f.getString("version"),
                "medio" to f.getString("medio"),
                "medio_texto" to (MEDIOS[f.getString("medio")] ?: f.getString("medio")),
                "fecha" to FormatoDeDrf.escribir(f.instante("created_at").atOffset(java.time.ZoneOffset.UTC)),
                "registrado_por" to (f.getString("full_name") ?: ""),
            )
        }
        return ResponseEntity.status(estado).body(
            linkedMapOf(
                "version_vigente" to TextosLegales.AVISO_VERSION,
                "al_dia" to entregas.any { it["version"] == TextosLegales.AVISO_VERSION },
                "entregas" to entregas,
            ),
        )
    }

    // ── Acceso a la app ──────────────────────────────────────────────

    /**
     * GET /api/v1/patients/{id}/app-access/ — si el paciente tiene acceso a
     * la app, con qué teléfono o correo y por dónde le llega el código.
     * Administración y recepción. Activar y quitar el acceso (POST/DELETE)
     * llegan con la fase de la app del paciente.
     *
     * Busca el paciente de la clínica esté activo o no, como Django.
     */
    @GetMapping("/api/v1/patients/$ID/app-access/")
    fun accesoApp(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(EDITAN)
        val clinica = contexto.clinica
        val fila = jdbc.queryForList(
            """select p.phone, p.email, u.id as cuenta_id, u.is_active as cuenta_activa, u.phone as cuenta_telefono,
                      u.email as cuenta_correo, u.last_login
               from patients_patient p left join accounts_user u on u.id = p.user_id
               where p.id = :id and p.tenant_id = :clinica""",
            mapOf("id" to id, "clinica" to clinica?.id),
        ).firstOrNull() ?: throw ExcepcionApi.http404("Patient")

        val conCuenta = fila["cuenta_id"] != null && fila["cuenta_activa"] == true
        val telefono = Telefonos.normalizar(fila["phone"] as String?)
        val correo = Telefonos.normalizarCorreo(fila["email"] as String?)
        val canal = if (configuracion.canalDelCodigo() == ConfiguracionPlataforma.CANAL_WHATSAPP &&
            clinica != null && funcionalidades.activa(clinica, "whatsapp")) ConfiguracionPlataforma.CANAL_WHATSAPP
        else ConfiguracionPlataforma.CANAL_CORREO
        // El paciente ya creó su cuenta con el correo de la ficha y espera
        // que la clínica le dé acceso: al activar se enlaza esa misma.
        val cuentaPropia = !conCuenta && correo.isNotEmpty() && jdbc.queryForObject(
            """select count(*) from accounts_user u where upper(u.email) = upper(:correo) and u.role = 'patient'
               and u.is_active and not exists (select 1 from patients_patient p where p.user_id = u.id)""",
            mapOf("correo" to correo), Long::class.java,
        )!! > 0
        val ultimo = (fila["last_login"] as java.sql.Timestamp?)?.toInstant()?.takeIf { conCuenta }
        return linkedMapOf(
            "activo" to conCuenta,
            "telefono" to if (conCuenta) fila["cuenta_telefono"] else telefono,
            "telefono_ficha" to telefono,
            "telefono_valido" to telefono.isNotEmpty(),
            "correo" to if (conCuenta) ((fila["cuenta_correo"] as String?)?.ifEmpty { null } ?: correo) else correo,
            "correo_valido" to correo.isNotEmpty(),
            "canal" to canal,
            "cuenta_propia" to cuentaPropia,
            "ultimo_ingreso" to ultimo?.let { FormatoDeDrf.escribir(it.atOffset(java.time.ZoneOffset.UTC)) },
            "funcion_activa" to funcionalidades.activa(clinica, "app_paciente"),
        )
    }
}
