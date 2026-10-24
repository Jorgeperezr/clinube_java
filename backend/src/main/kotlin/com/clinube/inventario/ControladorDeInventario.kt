package com.clinube.inventario

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
import com.clinube.comun.sql.Filas.fechaONula
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.clinube.finanzas.Finanzas
import com.clinube.plataforma.ServicioDeFuncionalidades
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
import java.math.RoundingMode
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Inventario (`apps/inventory`, RF-INV): productos, lotes con su
 * vencimiento, las alertas y el historial de movimientos.
 */
@RestController
class ControladorDeInventario(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val funcionalidades: ServicioDeFuncionalidades,
    private val finanzas: Finanzas,
) {

    companion object {
        /** `CAN_MANAGE`: la función «Inventario» de cada profesional (por defecto, el auxiliar). */
        val GESTIONAN = Permisos.funcion("inventario")
        /** `CAN_VIEW`. */
        val VEN = Permisos.rol("admin", "auxiliary", "doctor", "reception")
        val TIPOS = linkedMapOf("in" to "Entrada", "out" to "Salida")
        val MOTIVOS = linkedMapOf("purchase" to "Compra", "consumption" to "Consumo por tratamiento", "adjustment" to "Ajuste")

        /** Un `DecimalField(decimal_places=2)` de DRF: a céntimos, a la mitad hacia el par. */
        fun dinero(valor: BigDecimal): String = valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString()

        /**
         * El `BooleanWidget` de django-filter: «true»/«false»/«1»/«0» sin
         * mirar mayúsculas; cualquier otra cosa no filtra (y no es un error).
         */
        fun booleanoDeFiltro(texto: String?): Boolean? = when (texto?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private val clinica get() = contexto.clinica?.id

    /**
     * GET /api/v1/inventory/alerts/low-stock/ — RF-INV-03. Los productos
     * activos cuyo stock (la suma de sus lotes) está en el mínimo o por
     * debajo.
     *
     * Dos consultas, no una por producto. Y una rareza que se copia: un
     * producto SIN lotes tiene de stock el entero `0` de Python
     * (`sum(..., start=0)`), y sale como «0», no como «0.00».
     */
    @GetMapping("/api/v1/inventory/alerts/low-stock/")
    fun stockBajo(): Map<String, Any?> {
        permisos.exigir(VEN, Permisos.funcionalidad("inventario"))
        val parametros = mapOf("clinica" to clinica)
        // La misma consulta que Django, sin ORDER BY: el orden es el suyo.
        val productos = jdbc.query(
            "select id, name, min_stock from inventory_product where tenant_id = :clinica and is_active",
            parametros,
        ) { fila, _ -> Triple(fila.uuid("id"), fila.getString("name"), fila.getBigDecimal("min_stock")) }

        val stock = HashMap<UUID, BigDecimal>()
        jdbc.query(
            """
            select b.product_id, sum(b.quantity) as total from inventory_batch b
            join inventory_product p on p.id = b.product_id
            where p.tenant_id = :clinica and p.is_active group by b.product_id
            """,
            parametros,
        ) { fila -> stock[fila.uuid("product_id")] = fila.getBigDecimal("total") }

        val bajos = productos.mapNotNull { (id, nombre, minimo) ->
            val actual = stock[id]
            val cantidad = actual ?: BigDecimal.ZERO
            if (cantidad > minimo) return@mapNotNull null
            linkedMapOf(
                "product_id" to id.toString(),
                "name" to nombre,
                "current_stock" to (actual?.toPlainString() ?: "0"),
                "min_stock" to minimo.toPlainString(),
            )
        }
        return mapOf("low_stock_products" to bajos)
    }

    // ── Productos ────────────────────────────────────────────────────

    private val SELECT_PRODUCTO = """select p.*, (select sum(b.quantity) from inventory_batch b where b.product_id = p.id) as stock
        from inventory_product p"""

    /**
     * `ProductSerializer`. Aquí el stock sin lotes sí sale «0.00»: el
     * `DecimalField` del serializador lo escribe con sus dos decimales.
     */
    private fun producto(f: ResultSet): Map<String, Any?> {
        val stock = f.getBigDecimal("stock") ?: BigDecimal.ZERO
        val minimo = f.getBigDecimal("min_stock")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "name" to f.getString("name"),
            "unit" to f.getString("unit"),
            "min_stock" to dinero(minimo),
            "current_stock" to dinero(stock),
            "is_low_stock" to (stock <= minimo),
            "is_active" to f.getBoolean("is_active"),
        )
    }

    private fun producto(id: UUID) = jdbc.query("$SELECT_PRODUCTO where p.id = :id", mapOf("id" to id)) { f, _ -> producto(f) }.first()

    /** GET /api/v1/products/?is_active= — RF-INV-01. Paginado y por nombre; lo ve todo el personal. */
    @GetMapping("/api/v1/products/")
    fun productos(peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(VEN)
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        var filtro = "p.tenant_id = :clinica"
        booleanoDeFiltro(peticion.getParameterValues("is_active")?.lastOrNull())?.let {
            parametros["activo"] = it; filtro += " and p.is_active = :activo"
        }
        val total = jdbc.queryForObject("select count(*) from inventory_product p where $filtro", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query("$SELECT_PRODUCTO where $filtro order by p.name limit :cuantos offset :desde", parametros) { f, _ -> producto(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    private data class DatosDeProducto(val nombre: String?, val unidad: String?, val minimo: BigDecimal?, val activo: Boolean?)

    private fun leerProducto(cuerpo: JsonNode?, parcial: Boolean): DatosDeProducto {
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo), parcial = parcial)
        val nombre = campos.texto("name", maximo = 150)
        val unidad = campos.texto("unit", maximo = 30, requerido = false)
        val minimo = campos.decimal("min_stock", 10, 2)
        val activo = campos.booleano("is_active")
        campos.validar()
        return DatosDeProducto((nombre as? Leido.Valor)?.valor, (unidad as? Leido.Valor)?.valor,
            (minimo as? Leido.Valor)?.valor?.valor, (activo as? Leido.Valor)?.valor)
    }

    /** POST /api/v1/products/ — quien tiene la función «Inventario». */
    @PostMapping("/api/v1/products/")
    @Transactional
    fun crearProducto(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(GESTIONAN)
        val d = leerProducto(cuerpo, parcial = false)
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into inventory_product (id, created_at, updated_at, name, unit, min_stock, is_active, tenant_id)
               values (:id, :ahora, :ahora, :nombre, :unidad, :minimo, :activo, :clinica)""",
            mapOf("id" to id, "ahora" to ahora, "nombre" to d.nombre, "unidad" to (d.unidad ?: "unidad"),
                "minimo" to (d.minimo ?: BigDecimal.ZERO), "activo" to (d.activo ?: true), "clinica" to clinica),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(producto(id))
    }

    private fun exigirProducto(id: UUID) {
        val hay = jdbc.queryForObject(
            "select count(*) from inventory_product where id = :id and tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica), Long::class.java,
        )!! > 0
        if (!hay) throw ExcepcionApi.http404("Product")
    }

    /** GET /api/v1/products/{id}/ — con la función «Inventario» y la funcionalidad contratada. */
    @GetMapping("/api/v1/products/{id}/")
    fun verProducto(@PathVariable id: UUID): Map<String, Any?> {
        permisos.exigir(GESTIONAN, Permisos.funcionalidad("inventario"))
        exigirProducto(id)
        return producto(id)
    }

    @PutMapping("/api/v1/products/{id}/")
    @Transactional
    fun reemplazarProducto(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = cambiarProducto(id, cuerpo, false)

    @PatchMapping("/api/v1/products/{id}/")
    @Transactional
    fun corregirProducto(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?) = cambiarProducto(id, cuerpo, true)

    private fun cambiarProducto(id: UUID, cuerpo: JsonNode?, parcial: Boolean): Map<String, Any?> {
        permisos.exigir(GESTIONAN, Permisos.funcionalidad("inventario"))
        exigirProducto(id)
        val d = leerProducto(cuerpo, parcial)
        jdbc.update(
            """update inventory_product set updated_at = :ahora, name = coalesce(:nombre, name), unit = coalesce(:unidad, unit),
               min_stock = coalesce(:minimo, min_stock), is_active = coalesce(:activo, is_active) where id = :id""",
            mapOf("ahora" to Timestamp.from(zona.ahora()), "nombre" to d.nombre, "unidad" to d.unidad, "minimo" to d.minimo,
                "activo" to d.activo, "id" to id),
        )
        return producto(id)
    }

    // ── Lotes ────────────────────────────────────────────────────────

    /** `BatchSerializer`, sin los campos de la compra (`write_only`). */
    private fun lote(f: ResultSet): Map<String, Any?> = linkedMapOf(
        "id" to f.uuid("id").toString(),
        "product" to f.uuid("product_id").toString(),
        "batch_number" to f.getString("batch_number"),
        "quantity" to dinero(f.getBigDecimal("quantity")),
        "expiration_date" to f.fechaONula("expiration_date")?.toString(),
        "received_at" to f.fecha("received_at").toString(),
    )

    /**
     * GET /api/v1/products/{id}/batches/ — RF-INV-02. Paginado y por
     * vencimiento (los que no vencen, al final). Un producto que no existe
     * da una lista vacía, no un 404: así lo hace Django.
     */
    @GetMapping("/api/v1/products/{id}/batches/")
    fun lotes(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(GESTIONAN, Permisos.funcionalidad("inventario"))
        val parametros = mutableMapOf<String, Any?>("id" to id, "clinica" to clinica)
        val filtro = "product_id = :id and tenant_id = :clinica"
        val total = jdbc.queryForObject("select count(*) from inventory_batch where $filtro", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            "select * from inventory_batch where $filtro order by expiration_date limit :cuantos offset :desde", parametros,
        ) { f, _ -> lote(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/products/{id}/batches/ — un lote nuevo, con su
     * movimiento de entrada. Si lleva su costo y la clínica tiene Finanzas,
     * la compra queda además como egreso de insumos.
     *
     * El producto del cuerpo es obligatorio (y tiene que existir, de la
     * clínica que sea), pero manda el de la ruta; se busca DESPUÉS de validar.
     */
    @PostMapping("/api/v1/products/{id}/batches/")
    @Transactional
    fun crearLote(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(GESTIONAN, Permisos.funcionalidad("inventario"))
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        campos.clave("product") {
            jdbc.queryForObject("select count(*) from inventory_product where id = :id", mapOf("id" to it), Long::class.java)!! > 0
        }
        val numero = campos.texto("batch_number", maximo = 100)
        val cantidad = campos.decimal("quantity", 10, 2)
        val vence = campos.fecha("expiration_date", requerido = false, nulo = true)
        val costo = (campos.decimal("costo_total", 12, 2) as? Leido.Valor)?.valor?.valor?.also {
            if (it.signum() < 0) campos.error("costo_total", MensajesDeDjango.mayorOIgual(0))
        }
        val proveedor = campos.texto("proveedor", maximo = 150, requerido = false, enBlanco = true)
        val ruc = campos.texto("proveedor_ruc", maximo = 13, requerido = false, enBlanco = true)
        val factura = campos.texto("numero_factura", maximo = 20, requerido = false, enBlanco = true)
        val forma = campos.texto("forma_pago", maximo = 15, requerido = false, enBlanco = true)
        campos.validar()

        data class Producto(val nombre: String, val unidad: String)
        val producto = jdbc.query(
            "select name, unit from inventory_product where id = :id and tenant_id = :clinica", mapOf("id" to id, "clinica" to clinica),
        ) { f, _ -> Producto(f.getString("name"), f.getString("unit")) }.firstOrNull() ?: throw ExcepcionApi.http404("Product")

        val lote = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        val leida = (cantidad as? Leido.Valor)?.valor
        val unidades = leida?.valor ?: BigDecimal.ZERO
        jdbc.update(
            """insert into inventory_batch (id, created_at, updated_at, batch_number, quantity, expiration_date, received_at, product_id, tenant_id)
               values (:id, :ahora, :ahora, :numero, :cantidad, :vence, :hoy, :producto, :clinica)""",
            mapOf("id" to lote, "ahora" to ahora, "numero" to (numero as Leido.Valor).valor, "cantidad" to unidades,
                "vence" to (vence as? Leido.Valor)?.valor, "hoy" to zona.hoy(), "producto" to id, "clinica" to clinica),
        )
        jdbc.update(
            """insert into inventory_inventorymovement (created_at, updated_at, id, movement_type, quantity, reason, date,
               batch_id, related_treatment_plan_item_id, tenant_id, product_id)
               values (:ahora, :ahora, :id, 'in', :cantidad, 'purchase', :ahora, :lote, null, :clinica, :producto)""",
            mapOf("ahora" to ahora, "id" to UUID.randomUUID(), "cantidad" to unidades, "lote" to lote, "clinica" to clinica, "producto" to id),
        )
        // `_apuntar_egreso`: con costo (no cero) y con Finanzas.
        if (costo != null && costo.signum() != 0 && funcionalidades.activa(contexto.clinica, "finanzas")) {
            val pedida = (forma as? Leido.Valor)?.valor
            // El modelo pone 0 (entero) si no llega cantidad: «(0 unidad)».
            val cuantas = leida?.texto ?: "0"
            finanzas.crearEgreso(
                fecha = zona.hoy(), categoria = finanzas.categoria("insumos"),
                concepto = "Compra: ${producto.nombre} — lote ${numero.valor} ($cuantas ${producto.unidad})",
                monto = costo, forma = pedida?.takeIf { it in Finanzas.FORMAS_PAGO } ?: "efectivo",
                origen = "inventario", proveedor = (proveedor as? Leido.Valor)?.valor.orEmpty(),
                proveedorRuc = (ruc as? Leido.Valor)?.valor.orEmpty().trim(), numeroFactura = (factura as? Leido.Valor)?.valor.orEmpty(),
            )
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
            jdbc.query("select * from inventory_batch where id = :id", mapOf("id" to lote)) { f, _ -> lote(f) }.first(),
        )
    }

    // ── Alertas y movimientos ────────────────────────────────────────

    /**
     * GET /api/v1/inventory/alerts/expiring/?days=30 — RF-INV-04. Los lotes
     * con existencias que vencen en ese plazo (o ya vencieron). Un plazo que
     * no es un entero de Python cuenta como 30.
     */
    @GetMapping("/api/v1/inventory/alerts/expiring/")
    fun porVencer(peticion: HttpServletRequest): Map<String, Any?> {
        permisos.exigir(VEN, Permisos.funcionalidad("inventario"))
        val dias = peticion.getParameterValues("days")?.lastOrNull()?.let { Python.entero(it) }?.toLong() ?: 30L
        val hoy = zona.hoy()
        val lotes = jdbc.query(
            """select b.*, p.name as product_name from inventory_batch b inner join inventory_product p on (b.product_id = p.id)
               where b.tenant_id = :clinica and b.quantity > 0 and b.expiration_date is not null and b.expiration_date <= :limite
               order by b.expiration_date asc""",
            mapOf("clinica" to clinica, "limite" to hoy.plusDays(dias)),
        ) { f, _ ->
            val vence = f.fecha("expiration_date")
            linkedMapOf(
                "batch_id" to f.uuid("id").toString(),
                "product_name" to f.getString("product_name"),
                "batch_number" to f.getString("batch_number"),
                "quantity" to f.getBigDecimal("quantity").toPlainString(),
                "expiration_date" to vence.toString(),
                "days_to_expiry" to ChronoUnit.DAYS.between(hoy, vence),
            )
        }
        return mapOf("expiring_batches" to lotes)
    }

    /**
     * GET /api/v1/inventory/movements/?product=&movement_type=&reason= — el
     * historial, paginado y del más reciente al más antiguo. Los filtros se
     * validan todos y los errores salen juntos, como en django-filter.
     */
    @GetMapping("/api/v1/inventory/movements/")
    fun movimientos(peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(VEN, Permisos.funcionalidad("inventario"))
        fun parametro(nombre: String) = peticion.getParameterValues(nombre)?.lastOrNull()?.takeIf { it.isNotEmpty() }
        val parametros = mutableMapOf<String, Any?>("clinica" to clinica)
        var filtro = "m.tenant_id = :clinica"
        val errores = LinkedHashMap<String, List<String>>()
        parametro("product")?.let { valor ->
            val id = CamposDrf.uuidDePython(valor)
            when {
                id == null -> errores["product"] = listOf(MensajesDeDjango.uuidInvalido(valor))
                jdbc.queryForObject("select count(*) from inventory_product where id = :id", mapOf("id" to id), Long::class.java)!! == 0L ->
                    errores["product"] = listOf(MensajesDeDjango.OPCION_INEXISTENTE)
                else -> { parametros["producto"] = id; filtro += " and m.product_id = :producto" }
            }
        }
        for ((nombre, opciones) in listOf("movement_type" to TIPOS, "reason" to MOTIVOS)) {
            parametro(nombre)?.let { valor ->
                if (valor !in opciones) errores[nombre] = listOf(MensajesDeDjango.opcionNoValida(valor))
                else { parametros[nombre] = valor; filtro += " and m.$nombre = :$nombre" }
            }
        }
        if (errores.isNotEmpty()) throw ExcepcionApi.validacion(errores)
        val total = jdbc.queryForObject("select count(*) from inventory_inventorymovement m where $filtro", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(parametro("page"), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """select m.*, p.name as product_name from inventory_inventorymovement m inner join inventory_product p on (m.product_id = p.id)
               where $filtro order by m.date desc limit :cuantos offset :desde""",
            parametros,
        ) { f, _ ->
            val tipo = f.getString("movement_type")
            val motivo = f.getString("reason")
            linkedMapOf(
                "id" to f.uuid("id").toString(),
                "product" to f.uuid("product_id").toString(),
                "product_name" to f.getString("product_name"),
                "batch" to f.uuidONulo("batch_id")?.toString(),
                "movement_type" to tipo,
                "movement_type_display" to (TIPOS[tipo] ?: tipo),
                "quantity" to dinero(f.getBigDecimal("quantity")),
                "reason" to motivo,
                "reason_display" to (MOTIVOS[motivo] ?: motivo),
                "date" to FormatoDeDrf.deDrf(f.instante("date"), zona.zona),
            )
        }
        return Pagina.de(total, filas, pagina, peticion)
    }
}
