package com.clinube.inventario

import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.uuid
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

/** Inventario (`apps/inventory`): de momento, la alerta de stock bajo. */
@RestController
class ControladorDeInventario(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
) {

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
        permisos.exigir(Permisos.rol("admin", "auxiliary", "doctor", "reception"), Permisos.funcionalidad("inventario"))
        val parametros = mapOf("clinica" to contexto.clinica?.id)
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
}
