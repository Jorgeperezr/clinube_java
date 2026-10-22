package com.clinube.configuracion.tratamientos

import com.clinube.clinica.ControladorDeEvoluciones
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.uuid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Catálogo de tratamientos (`configuration.Treatment`, RF-CFG-02): de momento, la lista. */
@RestController
class ControladorDeTratamientos(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /**
     * GET /api/v1/config/treatments/ — paginada y por nombre, para todo el
     * personal. Filtros de django-filter: `?specialty=` (una especialidad de
     * cualquier clínica para el filtro; la de otra no devuelve nada) e
     * `?is_active=`.
     */
    @GetMapping("/api/v1/config/treatments/")
    fun lista(peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(Permisos.PERSONAL)
        fun parametro(nombre: String) = peticion.getParameterValues(nombre)?.lastOrNull()
        val condiciones = mutableListOf("t.tenant_id = :clinica")
        val parametros = mutableMapOf<String, Any?>("clinica" to contexto.clinica?.id)
        val especialidad = parametro("specialty")
        if (!especialidad.isNullOrEmpty()) {
            val id = CamposDrf.uuidDePython(especialidad)
                ?: throw ExcepcionApi.validacion(mapOf("specialty" to listOf(MensajesDeDjango.uuidInvalido(especialidad))))
            val existe = jdbc.queryForObject(
                "select count(*) from specialties_specialty where id = :id", mapOf("id" to id), Long::class.java,
            )!! > 0
            if (!existe) throw ExcepcionApi.validacion(mapOf("specialty" to listOf(MensajesDeDjango.OPCION_INEXISTENTE)))
            condiciones += "t.specialty_id = :especialidad"
            parametros["especialidad"] = id
        }
        ControladorDeEvoluciones.booleanoDeFiltro(parametro("is_active"))?.let {
            condiciones += "t.is_active = :activo"
            parametros["activo"] = it
        }
        val donde = condiciones.joinToString(" and ")
        val total = jdbc.queryForObject("select count(*) from configuration_treatment t where $donde", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(parametro("page"), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query(
            """select t.*, s.name as specialty_name from configuration_treatment t
               join specialties_specialty s on s.id = t.specialty_id
               where $donde order by t.name limit :cuantos offset :desde""",
            parametros,
        ) { f, _ ->
            linkedMapOf<String, Any?>(
                "id" to f.uuid("id").toString(),
                "name" to f.getString("name"),
                "specialty" to f.uuid("specialty_id").toString(),
                "specialty_name" to f.getString("specialty_name"),
                "base_price" to f.getBigDecimal("base_price").setScale(2).toPlainString(),
                "consumes_inventory" to f.getBoolean("consumes_inventory"),
                "is_active" to f.getBoolean("is_active"),
            )
        }
        return Pagina.de(total, filas, pagina, peticion)
    }
}
