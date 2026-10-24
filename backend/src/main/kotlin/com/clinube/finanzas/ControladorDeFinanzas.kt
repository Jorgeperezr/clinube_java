package com.clinube.finanzas

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.ROLES
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.dinero
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
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

/**
 * La parte de `apps/finanzas/views.py` que no mueve dinero: el resumen del
 * mes, los valores de ley, las categorías de egreso y cómo se le paga a
 * cada persona del equipo. Todo exige la función «Finanzas».
 */
@RestController
class ControladorDeFinanzas(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    private val clinica get() = finanzas.clinica

    // ── Resumen ──────────────────────────────────────────────────────

    /** GET /api/v1/finanzas/resumen/?mes=2026-10 — el mes, el anterior y los seis últimos. */
    @GetMapping("/api/v1/finanzas/resumen/")
    @Transactional
    fun resumen(peticion: HttpServletRequest): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val (desde, hasta) = Finanzas.mesDe(consulta(peticion).get("mes"), finanzas.hoy()) ?: return detalle("El mes va como 2026-10.")
        val anteriorHasta = desde.minusDays(1)
        val anterior = calculos.resumen(anteriorHasta.withDayOfMonth(1), anteriorHasta)
        val cuerpo = calculos.resumen(desde, hasta)
        cuerpo["mes_anterior"] = listOf("ingresos", "egresos", "utilidad").associateWith { anterior[it] }
        cuerpo["serie"] = calculos.serie(hasta)
        return ResponseEntity.ok(cuerpo)
    }

    // ── Configuración ────────────────────────────────────────────────

    /** `ConfiguracionSerializer`. */
    private fun configuracion(c: Finanzas.Configuracion): Map<String, Any?> = linkedMapOf(
        "salario_basico" to dinero(c.salarioBasico),
        "aporte_personal_iess" to dinero(c.aportePersonalIess),
        "aporte_patronal_iess" to dinero(c.aportePatronalIess),
        "aporte_iece_secap" to dinero(c.aporteIeceSecap),
        "fondos_reserva" to dinero(c.fondosReserva),
        "retencion_honorarios" to dinero(c.retencionHonorarios),
        "siguiente_comprobante" to c.siguienteComprobante,
        "region" to c.region,
        "recargo_suplementarias" to dinero(c.recargoSuplementarias),
        "recargo_extraordinarias" to dinero(c.recargoExtraordinarias),
    )

    /** GET /api/v1/finanzas/configuracion/ — los valores de ley; la primera vez se crean. */
    @GetMapping("/api/v1/finanzas/configuracion/")
    @Transactional
    fun verConfiguracion(): Map<String, Any?> {
        permisos.exigir(PERMISO)
        return configuracion(finanzas.configuracion())
    }

    /**
     * PATCH /api/v1/finanzas/configuracion/ — los valores que lleguen. Los
     * rangos los comprueba `validate()` después de los campos, y solo el
     * primero que falla: es un `raise` dentro de un bucle.
     */
    @PatchMapping("/api/v1/finanzas/configuracion/")
    @Transactional
    fun cambiarConfiguracion(@RequestBody(required = false) cuerpo: JsonNode?): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val actual = finanzas.configuracion()
        val datos = CuerpoDrf.objeto(cuerpo)
        val campos = CamposDrf(datos, parcial = true)
        val leidos = linkedMapOf<String, Any?>()
        fun decimal(campo: String, digitos: Int) {
            (campos.decimal(campo, digitos, 2) as? Leido.Valor)?.let { leidos[campo] = it.valor!!.valor }
        }
        decimal("salario_basico", 8)
        for (campo in listOf("aporte_personal_iess", "aporte_patronal_iess", "aporte_iece_secap", "fondos_reserva", "retencion_honorarios")) {
            decimal(campo, 5)
        }
        (campos.entero("siguiente_comprobante", minimo = 0, maximo = 2147483647) as? Leido.Valor)?.let { leidos["siguiente_comprobante"] = it.valor }
        (campos.opcion("region", Finanzas.REGIONES.keys) as? Leido.Valor)?.let { leidos["region"] = it.valor }
        decimal("recargo_suplementarias", 5)
        decimal("recargo_extraordinarias", 5)
        campos.validar()

        fun fuera(campo: String, maximo: Int) = (leidos[campo] as BigDecimal?)?.let { it < BigDecimal.ZERO || it > BigDecimal(maximo) } == true
        listOf("aporte_personal_iess", "aporte_patronal_iess", "aporte_iece_secap", "fondos_reserva", "retencion_honorarios")
            .firstOrNull { fuera(it, 100) }?.let { throw ExcepcionApi.validacion(mapOf(it to listOf("Un porcentaje entre 0 y 100."))) }
        listOf("recargo_suplementarias", "recargo_extraordinarias")
            .firstOrNull { fuera(it, 300) }?.let { throw ExcepcionApi.validacion(mapOf(it to listOf("Un recargo entre 0 y 300 %."))) }
        if ((leidos["salario_basico"] as BigDecimal?)?.let { it.signum() <= 0 } == true) {
            throw ExcepcionApi.validacion(mapOf("salario_basico" to listOf("El salario básico tiene que ser mayor que cero.")))
        }

        val asignaciones = leidos.keys.joinToString("") { ", $it = :$it" }
        jdbc.update(
            "update finanzas_configuracionfinanzas set updated_at = :ahora$asignaciones where id = :id",
            leidos + mapOf("ahora" to Timestamp.from(zona.ahora()), "id" to actual.id),
        )
        finanzas.auditar("update_finance_settings", "ConfiguracionFinanzas", actual.id, mapOf("campos" to datos.fieldNames().asSequence().sortedWith(Python::compararTextos).toList()))
        // La respuesta es la del objeto en memoria: lo que llegó, a céntimos.
        val cambiada = configuracion(finanzas.configuracion()).toMutableMap()
        leidos.forEach { (campo, valor) -> cambiada[campo] = if (valor is BigDecimal) dinero(valor) else valor }
        return cambiada
    }

    // ── Categorías ───────────────────────────────────────────────────

    /** `CategoriaSerializer`. */
    private fun categoria(f: ResultSet): Map<String, Any?> = linkedMapOf(
        "id" to f.uuid("id").toString(),
        "nombre" to f.getString("nombre"),
        "clave" to f.getString("clave"),
        "activa" to f.getBoolean("activa"),
        "orden" to f.getInt("orden"),
    )

    /** GET /api/v1/finanzas/categorias/ — sin paginar; la primera vez se crean las de serie. */
    @GetMapping("/api/v1/finanzas/categorias/")
    @Transactional
    fun categorias(): List<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        finanzas.asegurarCategorias()
        return jdbc.query(
            "select * from finanzas_categoriaegreso where tenant_id = :clinica order by orden, nombre", mapOf("clinica" to clinica),
        ) { f, _ -> categoria(f) }
    }

    private data class DatosDeCategoria(val nombre: String?, val activa: Boolean?, val orden: Long?)

    private fun leerCategoria(cuerpo: JsonNode?, parcial: Boolean): DatosDeCategoria {
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo), parcial = parcial)
        val nombre = campos.texto("nombre", maximo = 80)
        val activa = campos.booleano("activa")
        val orden = campos.entero("orden", minimo = 0, maximo = 32767)
        campos.validar()
        return DatosDeCategoria((nombre as? Leido.Valor)?.valor, (activa as? Leido.Valor)?.valor, (orden as? Leido.Valor)?.valor)
    }

    /**
     * POST /api/v1/finanzas/categorias/ — una propia (sin clave). No pasa por
     * `categorias_de`: si es la primera de la clínica, las de serie ya no se
     * crearán, como en Django.
     */
    @PostMapping("/api/v1/finanzas/categorias/")
    @Transactional
    fun crearCategoria(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        val datos = leerCategoria(cuerpo, parcial = false)
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into finanzas_categoriaegreso (id, created_at, updated_at, nombre, clave, activa, orden, tenant_id)
               values (:id, :ahora, :ahora, :nombre, '', :activa, :orden, :clinica)""",
            mapOf("id" to id, "ahora" to ahora, "nombre" to datos.nombre, "activa" to (datos.activa ?: true),
                "orden" to (datos.orden ?: 50), "clinica" to clinica),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(unaCategoria(id))
    }

    private fun unaCategoria(id: UUID) = jdbc.query("select * from finanzas_categoriaegreso where id = :id", mapOf("id" to id)) { f, _ -> categoria(f) }.first()

    /** PUT/PATCH /api/v1/finanzas/categorias/{id}/ — renombrarla, desactivarla, moverla. La clave no cambia. */
    @PutMapping("/api/v1/finanzas/categorias/{id}/")
    @Transactional
    fun reemplazarCategoria(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = cambiarCategoria(id, cuerpo, false)

    @PatchMapping("/api/v1/finanzas/categorias/{id}/")
    @Transactional
    fun corregirCategoria(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = cambiarCategoria(id, cuerpo, true)

    private fun cambiarCategoria(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val existe = jdbc.queryForObject(
            "select count(*) from finanzas_categoriaegreso where id = :id and tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!existe) throw ExcepcionApi.http404("CategoriaEgreso")
        val datos = leerCategoria(cuerpo, parcial)
        jdbc.update(
            """update finanzas_categoriaegreso set updated_at = :ahora, nombre = coalesce(:nombre, nombre), activa = coalesce(:activa, activa),
               orden = coalesce(:orden, orden) where id = :id""",
            mapOf("ahora" to Timestamp.from(zona.ahora()), "nombre" to datos.nombre, "activa" to datos.activa, "orden" to datos.orden, "id" to id),
        )
        return unaCategoria(id)
    }

    // ── Equipo ───────────────────────────────────────────────────────

    /** `PerfilLaboralSerializer`. */
    private fun perfil(p: CalculosDeFinanzas.Perfil): Map<String, Any?> = linkedMapOf(
        "modalidad" to p.modalidad,
        "cedula" to p.cedula,
        "cargo" to p.cargo,
        "base_honorarios" to p.baseHonorarios,
        "porcentaje" to dinero(p.porcentaje),
        "monto_fijo" to dinero(p.montoFijo),
        "emite_factura" to p.emiteFactura,
        "sueldo" to dinero(p.sueldo),
        "fecha_ingreso" to p.fechaIngreso?.toString(),
        "decimo_tercero_mensual" to p.decimoTerceroMensual,
        "decimo_cuarto_mensual" to p.decimoCuartoMensual,
        "fondos_reserva_mensual" to p.fondosReservaMensual,
    )

    private fun perfilDeLaClinica(usuario: UUID): CalculosDeFinanzas.Perfil? = jdbc.query(
        "select usuario_id from finanzas_perfillaboral where tenant_id = :clinica and usuario_id = :usuario",
        mapOf("clinica" to clinica, "usuario" to usuario),
    ) { f, _ -> f.uuid("usuario_id") }.firstOrNull()?.let { calculos.perfil(it) }

    /** GET /api/v1/finanzas/equipo/ — las personas de la clínica con su forma de pago. */
    @GetMapping("/api/v1/finanzas/equipo/")
    fun equipo(): List<Map<String, Any?>> {
        permisos.exigir(PERMISO)
        return finanzas.equipo().map { u ->
            linkedMapOf<String, Any?>("id" to u.id.toString(), "nombre" to u.nombre.ifEmpty { u.correo }, "rol" to (ROLES[u.rol] ?: u.rol)) +
                perfil(perfilDeLaClinica(u.id) ?: CalculosDeFinanzas.Perfil(null))
        }
    }

    /**
     * PATCH /api/v1/finanzas/equipo/{id}/ — su modalidad, porcentaje,
     * sueldo… La primera vez se le crea el perfil.
     */
    @PatchMapping("/api/v1/finanzas/equipo/{id}/")
    @Transactional
    fun cambiarPerfil(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val usuario = finanzas.delEquipo(com.fasterxml.jackson.databind.node.TextNode(id.toString()))
        val actual = calculos.perfil(usuario.id)
        val datos = CuerpoDrf.objeto(cuerpo)
        val campos = CamposDrf(datos, parcial = true)
        val leidos = linkedMapOf<String, Any?>()
        fun guardar(campo: String, leido: Leido<*>) { if (leido is Leido.Valor) leidos[campo] = leido.valor }
        guardar("modalidad", campos.opcion("modalidad", listOf("honorarios", "dependencia", "ninguna")))
        val cedula = campos.texto("cedula", maximo = 13, enBlanco = true)
        (cedula as? Leido.Valor)?.valor?.let { texto ->
            if (texto.isNotEmpty() && (!texto.codePoints().allMatch(Python::esDigito) || Python.longitud(texto) !in listOf(10, 13))) {
                campos.error("cedula", "La cédula tiene 10 dígitos y el RUC 13.")
            } else leidos["cedula"] = texto
        }
        guardar("cargo", campos.texto("cargo", maximo = 80, enBlanco = true))
        guardar("base_honorarios", campos.opcion("base_honorarios", listOf("cobrado", "fijo")))
        (campos.decimal("porcentaje", 5, 2) as? Leido.Valor)?.valor?.let {
            if (it.valor < BigDecimal.ZERO || it.valor > BigDecimal(100)) campos.error("porcentaje", "Un porcentaje entre 0 y 100.")
            else leidos["porcentaje"] = it.valor
        }
        (campos.decimal("monto_fijo", 10, 2) as? Leido.Valor)?.let { leidos["monto_fijo"] = it.valor!!.valor }
        guardar("emite_factura", campos.booleano("emite_factura"))
        (campos.decimal("sueldo", 10, 2) as? Leido.Valor)?.let { leidos["sueldo"] = it.valor!!.valor }
        guardar("fecha_ingreso", campos.fecha("fecha_ingreso", requerido = false, nulo = true))
        guardar("decimo_tercero_mensual", campos.booleano("decimo_tercero_mensual"))
        guardar("decimo_cuarto_mensual", campos.booleano("decimo_cuarto_mensual"))
        guardar("fondos_reserva_mensual", campos.booleano("fondos_reserva_mensual"))
        campos.validar()

        val ahora = Timestamp.from(zona.ahora())
        val perfilId = actual.id ?: UUID.randomUUID().also { nuevo ->
            val p = CalculosDeFinanzas.Perfil(nuevo)
            jdbc.update(
                """insert into finanzas_perfillaboral (id, created_at, updated_at, modalidad, cedula, cargo, base_honorarios, porcentaje,
                   monto_fijo, emite_factura, sueldo, fecha_ingreso, decimo_tercero_mensual, decimo_cuarto_mensual, fondos_reserva_mensual,
                   tenant_id, usuario_id) values (:id, :ahora, :ahora, :modalidad, '', '', :base, :porcentaje, :fijo, :factura, :sueldo, null,
                   false, false, true, :clinica, :usuario)""",
                mapOf("id" to nuevo, "ahora" to ahora, "modalidad" to p.modalidad, "base" to p.baseHonorarios, "porcentaje" to p.porcentaje,
                    "fijo" to p.montoFijo, "factura" to p.emiteFactura, "sueldo" to p.sueldo, "clinica" to clinica, "usuario" to usuario.id),
            )
        }
        val asignaciones = leidos.keys.joinToString("") { ", $it = :$it" }
        jdbc.update(
            "update finanzas_perfillaboral set updated_at = :ahora, tenant_id = :clinica$asignaciones where id = :id",
            leidos + mapOf("ahora" to ahora, "clinica" to clinica, "id" to perfilId),
        )
        finanzas.auditar("update_work_profile", "PerfilLaboral", perfilId, mapOf(
            "usuario" to usuario.id.toString(), "campos" to datos.fieldNames().asSequence().sortedWith(Python::compararTextos).toList(),
        ))
        val guardado = calculos.perfil(usuario.id)
        val representado = perfil(guardado).toMutableMap()
        leidos.forEach { (campo, valor) ->
            representado[campo] = when (valor) {
                is BigDecimal -> dinero(valor)
                is LocalDate -> valor.toString()
                else -> valor
            }
        }
        return linkedMapOf<String, Any?>("id" to usuario.id.toString(), "nombre" to usuario.nombre, "rol" to (ROLES[usuario.rol] ?: usuario.rol)) + representado
    }
}
