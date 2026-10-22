package com.clinube.clinica

import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.temporal.ChronoUnit

/** Historia clínica (`apps/clinical`): de momento, las alertas de seguimiento. */
@RestController
class ControladorDeSeguimientos(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    /**
     * GET /api/v1/clinical/follow-ups/ — las evoluciones cuya fecha de
     * seguimiento ya llegó (hoy o vencida), las 50 más antiguas. Alimenta la
     * alerta del inicio y la línea de tiempo.
     *
     * Django usa `date.today()`, que allí es el día de la clínica porque
     * Django fija la zona del proceso con `TIME_ZONE` al arrancar. Aquí,
     * `zona.hoy()`.
     */
    @GetMapping("/api/v1/clinical/follow-ups/")
    fun seguimientos(): Map<String, Any?> {
        permisos.exigir(Permisos.rol("admin", "doctor", "auxiliary", "reception"))
        val hoy = zona.hoy()
        val resultados = jdbc.query(
            """
            select e.id, e.patient_id, e.follow_up_date, e.notes, p.first_name, p.last_name
            from clinical_evolution e join patients_patient p on p.id = e.patient_id
            where e.tenant_id = :clinica and e.follow_up_date is not null and e.follow_up_date <= :hoy
            order by e.follow_up_date
            limit 50
            """,
            mapOf("clinica" to contexto.clinica?.id, "hoy" to hoy),
        ) { fila, _ ->
            val seguimiento = fila.fecha("follow_up_date")
            linkedMapOf<String, Any?>(
                "id" to fila.uuid("id").toString(),
                "patient_id" to fila.uuid("patient_id").toString(),
                "patient_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
                "follow_up_date" to seguimiento.toString(),
                "days_overdue" to ChronoUnit.DAYS.between(seguimiento, hoy),
                "notes" to primerosCaracteres(fila.getString("notes"), 120),
            )
        }
        return mapOf("results" to resultados)
    }

    /** `texto[:n]` de Python: n puntos de código, no n unidades UTF-16 (un emoji cuenta como uno). */
    private fun primerosCaracteres(texto: String, n: Int): String {
        val puntos = texto.codePointCount(0, texto.length)
        return if (puntos <= n) texto else texto.substring(0, texto.offsetByCodePoints(0, n))
    }
}
