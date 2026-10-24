package com.clinube.finanzas

import com.clinube.comun.archivos.AlmacenDeArchivos
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.json.FormularioDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.fechaONula
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.Finanzas.Companion.FORMAS_PAGO
import com.clinube.finanzas.Finanzas.Companion.ORIGENES_CALCULADOS
import com.clinube.finanzas.Finanzas.Companion.ORIGENES_OBLIGACION
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.cifras
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.dinero
import com.clinube.finanzas.Finanzas.Companion.error
import com.clinube.finanzas.Finanzas.Companion.errorInterno
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

/**
 * Lo que sale (`EgresosView`, `EgresoView`, anular, pagar y la factura del
 * proveedor). Nada se borra: un egreso equivocado se anula, con su motivo.
 */
@RestController
class ControladorDeEgresos(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val json: ObjectMapper,
    private val almacen: AlmacenDeArchivos,
) {

    companion object {
        const val CARPETA = "finanzas/comprobantes/"
        const val MAXIMO_ARCHIVO = 8L * 1024 * 1024
        val TIPOS_ARCHIVO = listOf(".pdf", ".jpg", ".jpeg", ".png", ".webp", ".heic", ".xml")
        val ESTADOS = linkedMapOf("pagado" to "Pagado", "por_pagar" to "Por pagar")

        /** `mimetypes.guess_type` de Python (sus tablas propias, no las del sistema) para lo que se admite subir. */
        val TIPOS_MIME = mapOf(
            ".pdf" to "application/pdf", ".jpg" to "image/jpeg", ".jpeg" to "image/jpeg", ".png" to "image/png",
            ".webp" to "image/webp", ".xml" to "text/xml",
        )

        val SELECT = """select e.*, c.nombre as categoria_nombre, u.full_name as profesional_nombre from finanzas_egreso e
            inner join finanzas_categoriaegreso c on (e.categoria_id = c.id) left outer join accounts_user u on (e.profesional_id = u.id)"""
    }

    private val clinica get() = finanzas.clinica

    /** `EgresoSerializer`, en el orden de sus campos (`comprobante` no se escribe: es `write_only`). */
    fun egreso(f: ResultSet): Map<String, Any?> {
        val forma = f.getString("forma_pago")
        val profesional = f.uuidONulo("profesional_id")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "fecha" to f.fecha("fecha").toString(),
            "categoria" to f.uuid("categoria_id").toString(),
            "categoria_nombre" to f.getString("categoria_nombre"),
            "concepto" to f.getString("concepto"),
            "monto" to dinero(f.getBigDecimal("monto")),
            "forma_pago" to forma,
            "forma_pago_nombre" to (FORMAS_PAGO[forma] ?: forma),
            "estado" to f.getString("estado"),
            "vence" to f.fechaONula("vence")?.toString(),
            "proveedor" to f.getString("proveedor"),
            "proveedor_ruc" to f.getString("proveedor_ruc"),
            "numero_factura" to f.getString("numero_factura"),
            "tiene_comprobante" to !f.getString("comprobante").isNullOrEmpty(),
            "profesional" to profesional?.toString(),
            "profesional_nombre" to if (profesional != null) f.getString("profesional_nombre") else "",
            "descontar_al_profesional" to f.getBoolean("descontar_al_profesional"),
            "origen" to f.getString("origen"),
            "periodo" to f.fechaONula("periodo")?.toString(),
            "anulado" to f.getBoolean("anulado"),
            "motivo_anulacion" to f.getString("motivo_anulacion"),
            "created_at" to FormatoDeDrf.deDrf(f.instante("created_at"), zona.zona),
        )
    }

    fun egreso(id: UUID): Map<String, Any?> = jdbc.query("$SELECT where e.id = :id", mapOf("id" to id)) { f, _ -> egreso(f) }.first()

    private data class Guardado(
        val id: UUID, val fecha: LocalDate, val profesional: UUID?, val descontar: Boolean, val anulado: Boolean, val origen: String,
        val estado: String, val vence: LocalDate?, val formaPago: String,
    )

    private fun guardado(id: UUID, soloVigente: Boolean = false): Guardado = jdbc.query(
        "select * from finanzas_egreso where id = :id and tenant_id = :clinica" + if (soloVigente) " and not anulado" else "",
        mapOf("id" to id, "clinica" to clinica),
    ) { f, _ ->
        Guardado(f.uuid("id"), f.fecha("fecha"), f.uuidONulo("profesional_id"), f.getBoolean("descontar_al_profesional"),
            f.getBoolean("anulado"), f.getString("origen"), f.getString("estado"), f.fechaONula("vence"), f.getString("forma_pago"))
    }.firstOrNull() ?: throw ExcepcionApi.http404("Egreso")

    // ── Lista y alta ─────────────────────────────────────────────────

    /**
     * GET /api/v1/finanzas/egresos/?mes=&categoria=&estado=&anulados=1 —
     * los 500 más recientes, sin paginar. Las cuentas por pagar se ven
     * todas, sean del mes que sean. Un mes o una categoría que no se
     * entienden hacen saltar a Django con 500, y aquí también.
     */
    @GetMapping("/api/v1/finanzas/egresos/")
    fun lista(peticion: HttpServletRequest): List<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        val p = consulta(peticion)
        val filtros = mutableListOf("e.tenant_id = :clinica")
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        if (Python.verdad(p.get("mes"))) {
            val (desde, hasta) = Finanzas.mesDe(p.get("mes"), finanzas.hoy()) ?: errorInterno("mes_de: ${p.get("mes")}")
            filtros += "e.fecha >= :desde and e.fecha <= :hasta"
            parametros += mapOf("desde" to desde, "hasta" to hasta)
        }
        if (p.get("estado")?.asText() == "por_pagar") {
            filtros.clear()
            filtros += "e.tenant_id = :clinica and e.estado = 'por_pagar'"
        }
        if (Python.verdad(p.get("categoria"))) {
            filtros += "e.categoria_id = :categoria"
            parametros["categoria"] = CamposDrf.uuidDePython(p.get("categoria").asText()) ?: errorInterno("categoria: ${p.get("categoria")}")
        }
        if (p.get("anulados")?.asText() != "1") filtros += "not e.anulado"
        return jdbc.query(
            "$SELECT where ${filtros.joinToString(" and ")} order by e.fecha desc, e.created_at desc limit 500", parametros,
        ) { f, _ -> egreso(f) }
    }

    /** Lo que se leyó del `EgresoSerializer`: solo los campos que llegaron. */
    private class Datos(val valores: LinkedHashMap<String, Any?>, val archivo: FormularioDrf.Archivo?, val conArchivo: Boolean)

    /**
     * `EgresoSerializer` al escribir, campo a campo en su orden. La categoría
     * y el profesional se buscan en TODAS las clínicas (es el `queryset` del
     * campo) y después se rechaza el de otra con su propio mensaje.
     */
    private fun leer(formulario: FormularioDrf, parcial: Boolean, actual: Guardado?): Datos {
        val campos = CamposDrf(formulario.objeto(), parcial = parcial, html = formulario.html)
        val v = linkedMapOf<String, Any?>()
        fun guardar(campo: String, leido: Leido<*>) { if (leido is Leido.Valor) v[campo] = leido.valor }

        guardar("fecha", campos.fecha("fecha"))
        var tenantDeLaCategoria: UUID? = null
        val categoria = campos.clave("categoria") { id ->
            jdbc.query("select tenant_id from finanzas_categoriaegreso where id = :id", mapOf("id" to id)) { f, _ -> f.uuid("tenant_id") }
                .firstOrNull()?.also { tenantDeLaCategoria = it } != null
        }
        if (categoria is Leido.Valor) {
            if (tenantDeLaCategoria != clinica) campos.error("categoria", "Esa categoría no es de esta clínica.") else v["categoria_id"] = categoria.valor
        }
        guardar("concepto", campos.texto("concepto", maximo = 200))
        (campos.decimal("monto", 12, 2, requerido = true) as? Leido.Valor)?.valor?.let {
            if (it.valor.signum() <= 0) campos.error("monto", "El monto tiene que ser mayor que cero.") else v["monto"] = it.valor
        }
        guardar("forma_pago", campos.opcion("forma_pago", FORMAS_PAGO.keys))
        guardar("estado", campos.opcion("estado", ESTADOS.keys))
        guardar("vence", campos.fecha("vence", requerido = false, nulo = true))
        guardar("proveedor", campos.texto("proveedor", maximo = 150, requerido = false, enBlanco = true))
        (campos.texto("proveedor_ruc", maximo = 13, requerido = false, enBlanco = true) as? Leido.Valor)?.valor?.let { ruc ->
            if (ruc.isNotEmpty() && (!ruc.codePoints().allMatch(Python::esDigito) || Python.longitud(ruc) !in listOf(10, 13))) {
                campos.error("proveedor_ruc", "La cédula tiene 10 dígitos y el RUC 13.")
            } else v["proveedor_ruc"] = ruc
        }
        guardar("numero_factura", campos.texto("numero_factura", maximo = 20, requerido = false, enBlanco = true))
        val (archivo, conArchivo) = comprobante(formulario, campos)
        var profesionalValido: Boolean? = null
        val profesional = campos.clave("profesional", requerido = false, nulo = true) { id ->
            jdbc.query("select tenant_id, role from accounts_user where id = :id", mapOf("id" to id)) { f, _ ->
                profesionalValido = f.uuidONulo("tenant_id") == clinica && f.getString("role") != "patient"
            }.isNotEmpty()
        }
        if (profesional is Leido.Valor) {
            if (profesional.valor != null && profesionalValido == false) campos.error("profesional", "Ese profesional no es de esta clínica.")
            else v["profesional_id"] = profesional.valor
        }
        guardar("descontar_al_profesional", campos.booleano("descontar_al_profesional"))
        campos.validar()

        // `validate()`: una cuenta por pagar sin vencimiento vence el día de la factura.
        val estado = v["estado"] ?: actual?.estado ?: "pagado"
        val vence = if ("vence" in v) v["vence"] else actual?.vence
        if (estado == "por_pagar" && vence == null) v["vence"] = v["fecha"] ?: actual?.fecha
        return Datos(v, archivo, conArchivo)
    }

    /**
     * El `FileField` del comprobante: opcional y que admite nulo. En un
     * formulario, un texto vacío es nulo (lo borra); un texto que no lo
     * está, o cualquier valor en JSON, no es un archivo.
     */
    private fun comprobante(formulario: FormularioDrf, campos: CamposDrf): Pair<FormularioDrf.Archivo?, Boolean> {
        formulario.archivos["comprobante"]?.let { a ->
            when {
                a.tamano == 0L -> campos.error("comprobante", "El archivo enviado está vació.")
                Python.longitud(a.nombre) > 100 ->
                    campos.error("comprobante", "Asegúrese de que el nombre de archivo no tenga más de 100 caracteres (tiene ${Python.longitud(a.nombre)}).")
                a.tamano > MAXIMO_ARCHIVO -> campos.error("comprobante", "El archivo pasa de 8 MB.")
                TIPOS_ARCHIVO.none { a.nombre.lowercase().endsWith(it) } -> campos.error("comprobante", "Sube una foto, un PDF o el XML de la factura.")
                else -> return a to true
            }
            return null to false
        }
        val texto = formulario.datos.get("comprobante") ?: return null to false
        if (texto.isNull || (formulario.html && texto.isTextual && texto.textValue().isEmpty())) return null to true
        campos.error("comprobante", "La información enviada no era un archivo. Compruebe el tipo de codificación del formulario.")
        return null to false
    }

    /** `_rol_ya_pagado`: un anticipo para descontar en un rol que ya se pagó no se descontaría nunca. */
    private fun rolYaPagado(datos: Map<String, Any?>, actual: Guardado?) {
        val descontar = (if ("descontar_al_profesional" in datos) datos["descontar_al_profesional"] else actual?.descontar) as Boolean?
        val profesional = (if ("profesional_id" in datos) datos["profesional_id"] else actual?.profesional) as UUID?
        val fecha = (datos["fecha"] ?: actual?.fecha) as LocalDate?
        if (descontar != true || profesional == null || fecha == null) return
        val pagado = jdbc.query(
            """select u.full_name from finanzas_rolpagos r inner join accounts_user u on (r.empleado_id = u.id)
               where r.tenant_id = :clinica and r.empleado_id = :empleado and r.mes = :mes and r.estado = 'pagado' order by r.mes desc limit 1""",
            mapOf("clinica" to clinica, "empleado" to profesional, "mes" to fecha.withDayOfMonth(1)),
        ) { f, _ -> f.getString("full_name") }.firstOrNull() ?: return
        error("El rol de $pagado de ese mes ya está pagado: no se le podría descontar. Anula el rol para rehacerlo o registra el anticipo en el mes siguiente.")
    }

    /** `_ya_descontado`: la liquidación o el rol pagado en que entró este egreso, si alguno. */
    private fun yaDescontado(e: Guardado): String? {
        if (!e.descontar || e.profesional == null) return null
        val hechos = calculos.yaLiquidado(e.profesional)
        if (e.id.toString() in hechos.descuentos || hechos.rangos.any { (a, b) -> e.fecha in a..b }) {
            val liquidaciones = jdbc.query(
                """select numero, desde, hasta, detalle::text as detalle from finanzas_liquidacion
                   where tenant_id = :clinica and profesional_id = :profesional and not (estado = 'anulada') order by hasta desc, numero desc""",
                mapOf("clinica" to clinica, "profesional" to e.profesional),
            ) { f, _ -> Triple(f.getLong("numero"), f.fecha("desde") to f.fecha("hasta"), json.readTree(f.getString("detalle"))) }
            for ((numero, rango, detalle) in liquidaciones) {
                val ids = detalle.get("descuentos_ids")
                if ((ids?.isArray == true && ids.any { it.asText() == e.id.toString() }) ||
                    (!detalle.has("descuentos_ids") && e.fecha in rango.first..rango.second)
                ) return "la liquidación N.º ${cifras(numero, 5)}"
            }
            return "una liquidación"
        }
        val rolPagado = jdbc.queryForObject(
            """select exists(select 1 from finanzas_rolpagos where tenant_id = :clinica and empleado_id = :empleado and mes = :mes
               and estado = 'pagado')""",
            mapOf("clinica" to clinica, "empleado" to e.profesional, "mes" to e.fecha.withDayOfMonth(1)), Boolean::class.java,
        )!!
        return if (rolPagado) "un rol de pagos ya pagado" else null
    }

    private fun archivoGuardado(datos: Datos): String? =
        // Sin archivo, Django guarda "" y no NULL (`str(FieldFile)` de uno vacío).
        datos.archivo?.let { almacen.guardar(CARPETA, it.nombre, it.contenido()) } ?: ""

    /** POST /api/v1/finanzas/egresos/ — JSON o formulario, con la factura del proveedor si se sube. */
    @PostMapping("/api/v1/finanzas/egresos/")
    @Transactional
    fun alta(peticion: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val usuario = permisos.exigir(PERMISO)
        val formulario = FormularioDrf.leer(peticion, json)
        val datos = leer(formulario, parcial = false, actual = null)
        rolYaPagado(datos.valores, null)
        val v = datos.valores
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into finanzas_egreso (id, created_at, updated_at, fecha, concepto, monto, forma_pago, estado, vence, proveedor,
               proveedor_ruc, numero_factura, comprobante, descontar_al_profesional, origen, anulado, motivo_anulacion, categoria_id,
               profesional_id, registrado_por_id, tenant_id, periodo)
               values (:id, :ahora, :ahora, :fecha, :concepto, :monto, :forma, :estado, :vence, :proveedor, :ruc, :factura, :comprobante,
               :descontar, '', false, '', :categoria, :profesional, :usuario, :clinica, null)""",
            mapOf(
                "id" to id, "ahora" to ahora, "fecha" to v["fecha"], "concepto" to v["concepto"], "monto" to v["monto"],
                "forma" to (v["forma_pago"] ?: "efectivo"), "estado" to (v["estado"] ?: "pagado"), "vence" to v["vence"],
                "proveedor" to (v["proveedor"] ?: ""), "ruc" to (v["proveedor_ruc"] ?: ""), "factura" to (v["numero_factura"] ?: ""),
                "comprobante" to archivoGuardado(datos), "descontar" to (v["descontar_al_profesional"] ?: false),
                "categoria" to v["categoria_id"], "profesional" to v["profesional_id"], "usuario" to usuario.id, "clinica" to clinica,
            ),
        )
        finanzas.auditar("create_expense", "Egreso", id, mapOf("monto" to dinero(v["monto"] as BigDecimal)))
        return ResponseEntity.status(HttpStatus.CREATED).body(egreso(id))
    }

    // ── Un egreso ────────────────────────────────────────────────────

    /** GET /api/v1/finanzas/egresos/{id}/ — también los anulados. */
    @GetMapping("/api/v1/finanzas/egresos/{id}/")
    fun ver(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(PERMISO)
        guardado(id)
        return egreso(id)
    }

    @PutMapping("/api/v1/finanzas/egresos/{id}/")
    @Transactional
    fun reemplazar(@PathVariable id: UUID, peticion: HttpServletRequest) = cambiar(id, peticion, parcial = false)

    @PatchMapping("/api/v1/finanzas/egresos/{id}/")
    @Transactional
    fun corregir(@PathVariable id: UUID, peticion: HttpServletRequest) = cambiar(id, peticion, parcial = true)

    /**
     * PUT/PATCH — primero se valida el cuerpo y DESPUÉS se mira si el
     * egreso se puede tocar (`perform_update`): anulado, creado por un
     * cálculo o ya descontado a alguien, no.
     */
    private fun cambiar(id: UUID, peticion: HttpServletRequest, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val actual = guardado(id)
        val formulario = FormularioDrf.leer(peticion, json)
        val datos = leer(formulario, parcial, actual)
        if (actual.anulado) error("Un egreso anulado no se modifica.")
        if (actual.origen in ORIGENES_CALCULADOS) error("Este egreso lo creó Clinube al registrar un pago: se cambia desde allí.")
        yaDescontado(actual)?.let { error("Ya se descontó en $it. Anúlala antes de cambiarlo.") }
        rolYaPagado(datos.valores, actual)

        val columnas = LinkedHashMap(datos.valores)
        if (datos.conArchivo) columnas["comprobante"] = archivoGuardado(datos)
        val asignaciones = columnas.keys.joinToString("") { ", $it = :$it" }
        jdbc.update(
            "update finanzas_egreso set updated_at = :ahora$asignaciones where id = :id",
            columnas + mapOf("ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        finanzas.auditar("update_expense", "Egreso", id, mapOf("campos" to formulario.claves().sortedWith(Python::compararTextos)))
        return egreso(id)
    }

    /** `request.data` de una vista que lo lee a mano: un objeto, o el 500 de Python al pedirle `.get` a otra cosa. */
    private fun datosSueltos(cuerpo: JsonNode?): JsonNode = when {
        cuerpo == null || cuerpo.isMissingNode -> JsonNodeFactory.instance.objectNode()
        cuerpo.isObject -> cuerpo
        else -> errorInterno("request.data no es un diccionario")
    }

    /** POST /api/v1/finanzas/egresos/{id}/anular/ {"motivo": "…"} — deja de contar, con su motivo. */
    @PostMapping("/api/v1/finanzas/egresos/{id}/anular/")
    @Transactional
    fun anular(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val e = guardado(id)
        val datos = datosSueltos(cuerpo)
        val crudo = Finanzas.valor(datos, "motivo")
        val motivo = when {
            !Python.verdad(crudo) -> ""
            crudo!!.isTextual -> Python.recortar(crudo.textValue())
            else -> errorInterno("motivo sin strip()")
        }
        if (motivo.isEmpty()) return detalle("Escribe por qué se anula.")
        if (e.origen in ORIGENES_CALCULADOS && e.origen !in ORIGENES_OBLIGACION) {
            return detalle("Se anula desde la liquidación o el rol que lo creó.")
        }
        yaDescontado(e)?.let { return detalle("Ya se descontó en $it. Anúlala antes.") }
        jdbc.update(
            "update finanzas_egreso set anulado = true, motivo_anulacion = :motivo, updated_at = :ahora where id = :id",
            mapOf("motivo" to cortar(motivo, 200), "ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        finanzas.auditar("void_expense", "Egreso", id, mapOf("motivo" to motivo))
        return ResponseEntity.ok(egreso(id))
    }

    /** `texto[:n]` de Python: por caracteres, no por unidades UTF-16. */
    private fun cortar(texto: String, n: Int): String =
        if (Python.longitud(texto) <= n) texto else texto.substring(0, texto.offsetByCodePoints(0, n))

    /** POST /api/v1/finanzas/egresos/{id}/pagar/ {"fecha", "forma_pago"} — una cuenta por pagar, saldada. */
    @PostMapping("/api/v1/finanzas/egresos/{id}/pagar/")
    @Transactional
    fun pagar(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val e = guardado(id, soloVigente = true)
        if (e.estado == "pagado") return detalle("Ya estaba pagado.")
        val datos = datosSueltos(cuerpo)
        val pedida = Finanzas.valor(datos, "forma_pago")
        val forma = if (Python.verdad(pedida)) pedida!!.takeIf { it.isTextual }?.textValue() else e.formaPago
        if (forma == null || forma !in FORMAS_PAGO) return detalle("Forma de pago desconocida.")
        // Una fecha que no se entiende es un nulo que la base rechaza: 500, como en Django.
        val fecha = Finanzas.fecha(Finanzas.valor(datos, "fecha"), finanzas.hoy()) ?: errorInterno("fecha nula")
        jdbc.update(
            "update finanzas_egreso set estado = 'pagado', fecha = :fecha, forma_pago = :forma, updated_at = :ahora where id = :id",
            mapOf("fecha" to fecha, "forma" to forma, "ahora" to Timestamp.from(zona.ahora()), "id" to id),
        )
        finanzas.auditar("pay_expense", "Egreso", id)
        return ResponseEntity.ok(egreso(id))
    }

    /** GET /api/v1/finanzas/egresos/{id}/comprobante/ — la factura del proveedor (no es pública). */
    @GetMapping("/api/v1/finanzas/egresos/{id}/comprobante/")
    fun comprobante(@PathVariable id: UUID): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        guardado(id)
        val nombre = jdbc.queryForObject("select comprobante from finanzas_egreso where id = :id", mapOf("id" to id), String::class.java)
        if (nombre.isNullOrEmpty()) return detalle("Este egreso no tiene comprobante.", HttpStatus.NOT_FOUND)
        val archivo = almacen.abrir(nombre) ?: errorInterno("No está en disco: $nombre")
        val base = nombre.substringAfterLast('/')
        val tipo = TIPOS_MIME[AlmacenDeArchivos.separarExtension(base).second.lowercase()] ?: "application/octet-stream"
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(tipo))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$base\"")
            .body(FileSystemResource(archivo))
    }
}
