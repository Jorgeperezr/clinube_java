package com.clinube.cuentas.web

import com.clinube.plataforma.ServicioDeConfiguracion
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * «Entrar con Google» (`apps/accounts/google.py`). De momento solo la
 * configuración: la pantalla de entrada del panel la consulta para decidir
 * si enseña el botón. Entrar (`POST /auth/google/`) está pendiente.
 */
@RestController
class ControladorDeGoogle(private val configuracion: ServicioDeConfiguracion) {

    /**
     * GET /api/v1/auth/google/config/ — el ID de cliente OAuth, o vacío si
     * no está configurado (y entonces el botón no aparece). Lee la
     * configuración sin crearla, como `objects.first()` en Django.
     */
    @GetMapping("/api/v1/auth/google/config/")
    fun config(): Map<String, String> =
        mapOf("client_id" to (configuracion.primera()?.googleClientId?.trim() ?: ""))
}
