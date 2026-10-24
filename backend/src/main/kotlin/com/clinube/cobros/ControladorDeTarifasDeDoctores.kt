package com.clinube.cobros

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.CuerpoDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Las reglas de honorarios de un doctor (`DoctorFeeListCreateView`,
 * RF-TRP-05): un porcentaje o un monto fijo, para todos sus tratamientos o
 * para uno. Solo el administrador.
 *
 * DIFERENCIA DELIBERADA: el doctor de la ruta tiene que ser de la clínica
 * (Django da 500 si no existe y apunta la regla si es de otra), y el
 * tratamiento también (Django lo busca en todas).
 */
@RestController
class ControladorDeTarifasDeDoctores(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        val TIPOS = linkedMapOf("percentage" to "Porcentaje", "fixed" to "Monto fijo")
    }

    private val clinica get() = contexto.clinica?.id

    /** `DoctorFeeSerializer`. */
    private fun tarifa(f: ResultSet): Map<String, Any?> {
        val tipo = f.getString("fee_type")
        return linkedMapOf(
            "id" to f.uuid("id").toString(),
            "doctor" to f.uuid("doctor_id").toString(),
            "treatment" to f.uuidONulo("treatment_id")?.toString(),
            "fee_type" to tipo,
            "fee_type_display" to (TIPOS[tipo] ?: tipo),
            "value" to Cobros.dinero(f.getBigDecimal("value")),
        )
    }

    /** GET /api/v1/doctors/{id}/fees/ — paginado, en el orden de la base (el modelo no tiene uno). */
    @GetMapping("/api/v1/doctors/{id}/fees/")
    fun lista(@PathVariable id: UUID, peticion: HttpServletRequest): Pagina<Map<String, Any?>> {
        permisos.exigir(Permisos.rol("admin"))
        val parametros = mutableMapOf<String, Any?>("doctor" to id, "clinica" to clinica)
        val filtro = "doctor_id = :doctor and tenant_id = :clinica"
        val total = jdbc.queryForObject("select count(*) from billing_doctorfee where $filtro", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(peticion.getParameterValues("page")?.lastOrNull(), total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA
        val filas = jdbc.query("select * from billing_doctorfee where $filtro limit :cuantos offset :desde", parametros) { f, _ -> tarifa(f) }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * POST /api/v1/doctors/{id}/fees/ — el `doctor` del cuerpo es
     * obligatorio y tiene que existir, pero manda el de la ruta.
     */
    @PostMapping("/api/v1/doctors/{id}/fees/")
    @Transactional
    fun alta(@PathVariable id: UUID, @RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Map<String, Any?>> {
        permisos.exigir(Permisos.rol("admin"))
        val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
        campos.clave("doctor") { existe("select count(*) from agenda_doctor where id = :id", it) }
        val tratamiento = campos.clave("treatment", requerido = false, nulo = true) {
            existe("select count(*) from configuration_treatment where id = :id and tenant_id = :clinica", it)
        }
        val tipo = campos.opcion("fee_type", TIPOS.keys, requerido = true)
        val valor = campos.decimal("value", 10, 2, requerido = true)
        campos.validar()
        if (!existe("select count(*) from agenda_doctor where id = :id and tenant_id = :clinica", id)) throw ExcepcionApi.http404("Doctor")
        val nueva = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into billing_doctorfee (id, created_at, updated_at, fee_type, value, doctor_id, tenant_id, treatment_id)
               values (:id, :ahora, :ahora, :tipo, :valor, :doctor, :clinica, :tratamiento)""",
            mapOf("id" to nueva, "ahora" to ahora, "tipo" to (tipo as Leido.Valor).valor, "valor" to (valor as Leido.Valor).valor!!.valor,
                "doctor" to id, "clinica" to clinica, "tratamiento" to (tratamiento as? Leido.Valor)?.valor),
        )
        val creada = LinkedHashMap(jdbc.query("select * from billing_doctorfee where id = :id", mapOf("id" to nueva)) { f, _ -> tarifa(f) }.first())
        // La respuesta es la del objeto en memoria: el valor tal como llegó, con su «-0.00».
        creada["value"] = valor.valor!!.texto
        return ResponseEntity.status(HttpStatus.CREATED).body(creada)
    }

    private fun existe(sql: String, id: UUID) =
        jdbc.queryForObject(sql, mapOf("id" to id, "clinica" to clinica), Long::class.java)!! > 0
}
