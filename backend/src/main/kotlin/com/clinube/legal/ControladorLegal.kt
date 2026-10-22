package com.clinube.legal

import com.clinube.auditoria.DireccionDelCliente
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.cuentas.dominio.Rol
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset

/**
 * Los textos legales (`apps/common/legal_views.py`).
 *
 *     GET  /api/v1/legal/privacidad/            aviso de privacidad, público
 *     GET  /api/v1/legal/condiciones/           condiciones de uso, públicas
 *     POST /api/v1/legal/condiciones/aceptar/   la administración las acepta
 *
 * Los textos son públicos a propósito: el aviso tiene que poder leerlo un
 * paciente antes de dar ningún dato, y las condiciones, quien todavía no ha
 * decidido si usar la plataforma. Tampoco tienen límite de peticiones:
 * quien se equivoca varias veces de contraseña y luego pulsa el enlace del
 * aviso tiene que poder leerlo.
 */
@RestController
@RequestMapping("/api/v1/legal")
class ControladorLegal(
    private val textos: ServicioDeTextosLegales,
    private val condiciones: Condiciones,
    private val contexto: ContextoDeLaPeticion,
) {

    @GetMapping("/privacidad/")
    fun privacidad(): Map<String, Any?> = textos.avisoDePrivacidad()

    @GetMapping("/condiciones/")
    fun condicionesDeUso(): Map<String, Any?> = textos.condiciones()

    /**
     * Acepta la versión vigente en nombre de la clínica: 201 la primera vez,
     * 200 si ya estaba aceptada.
     *
     * Quien tenía abierta una versión anterior no acepta la nueva sin
     * haberla visto: si la versión que manda no es la vigente, 409. Esa
     * respuesta la construye la vista en Django, así que va con la forma
     * `{"detail": …}` y no con la de los errores.
     */
    @PostMapping("/condiciones/aceptar/")
    fun aceptar(
        @RequestBody(required = false) cuerpo: JsonNode?,
        peticion: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val usuario = contexto.exigirUsuario()
        // `HasRole.for_roles("admin")`: el rol, o ser superusuario.
        if (usuario.rol != Rol.ADMIN && !usuario.esSuperusuario) throw ExcepcionApi.sinPermiso()

        // Como `request.data.get("version") != CONDICIONES_VERSION`: tiene
        // que ser el TEXTO de la versión. Un número 2026.10 no vale, igual
        // que en Django.
        val version = cuerpo?.takeIf { it.isObject }?.get("version")
        if (version == null || !version.isTextual || version.textValue() != Condiciones.VERSION) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("detail" to MensajesDeDjango.CONDICIONES_CAMBIARON))
        }

        val resultado = condiciones.aceptar(usuario, contexto.exigirClinica().id, DireccionDelCliente.de(peticion))
        val a = resultado.aceptacion
        // `aceptada_en` sale de un `Response` con un objeto de fecha, no de
        // un serializador: DRF lo escribe en UTC con «Z», no en la hora de
        // la clínica como las demás fechas. Comprobado contra Django.
        return ResponseEntity.status(if (resultado.creada) HttpStatus.CREATED else HttpStatus.OK).body(
            linkedMapOf(
                "version" to a.version,
                "aceptada_en" to FormatoDeDrf.escribir(a.creadaEn.atOffset(ZoneOffset.UTC)),
                "aceptada_por" to a.nombre,
            ),
        )
    }
}
