package com.clinube.configuracion.convenios

import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.uuid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.RoundingMode

/** Convenios y aseguradoras (`configuration.Agreement`, RF-CFG-04): de momento, la lista. */
@RestController
class ControladorDeConvenios(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /**
     * GET /api/v1/config/agreements/ — paginada y por nombre. Con cuántos
     * pacientes ACTIVOS cubre cada uno: es el dato que decide si se puede
     * desactivar sin dejar a nadie con una tarifa a medias. El formulario
     * del paciente la pide para su desplegable.
     */
    @GetMapping("/api/v1/config/agreements/")
    fun lista(@RequestParam(required = false) page: String?, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(Permisos.PERSONAL)
        val parametros = mutableMapOf<String, Any?>("clinica" to contexto.clinica?.id)
        val total = jdbc.queryForObject(
            "select count(*) from configuration_agreement where tenant_id = :clinica", parametros, Long::class.java,
        )!!
        val pagina = Pagina.numeroDePagina(page, total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """
            select a.id, a.name, a.discount_percentage, a.is_active,
                   (select count(*) from patients_patient p where p.agreement_id = a.id and p.is_active) as pacientes
            from configuration_agreement a where a.tenant_id = :clinica
            order by a.name limit :cuantos offset :desde
            """,
            parametros,
        ) { f, _ ->
            linkedMapOf<String, Any?>(
                "id" to f.uuid("id").toString(),
                "name" to f.getString("name"),
                // `DecimalField(decimal_places=2)`: siempre con dos decimales.
                "discount_percentage" to f.getBigDecimal("discount_percentage")?.setScale(2, RoundingMode.HALF_EVEN)?.toPlainString(),
                "is_active" to f.getBoolean("is_active"),
                "patient_count" to f.getLong("pacientes"),
            )
        }
        return Pagina.de(total, filas, pagina, peticion)
    }
}
