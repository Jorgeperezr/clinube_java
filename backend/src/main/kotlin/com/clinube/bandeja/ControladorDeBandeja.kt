package com.clinube.bandeja

import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** La bandeja de lo que llega desde la app del paciente (`apps/app_paciente/contacto.py`). */
@RestController
class ControladorDeBandeja(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /** GET /api/v1/bandeja-app/resumen/ — lo pendiente, para el contador del menú. */
    @GetMapping("/api/v1/bandeja-app/resumen/")
    fun resumen(): Map<String, Any?> {
        permisos.exigir(Permisos.PERSONAL, Permisos.funcionalidad("app_paciente"))
        val parametros = mapOf("clinica" to contexto.clinica?.id)
        return linkedMapOf(
            "solicitudes_pendientes" to jdbc.queryForObject(
                "select count(*) from app_paciente_solicitudcita where tenant_id = :clinica and estado = 'pendiente'",
                parametros, Long::class.java,
            ),
            "mensajes_sin_responder" to jdbc.queryForObject(
                "select count(*) from app_paciente_mensajeconsultorio where tenant_id = :clinica and respondido_en is null",
                parametros, Long::class.java,
            ),
        )
    }
}
