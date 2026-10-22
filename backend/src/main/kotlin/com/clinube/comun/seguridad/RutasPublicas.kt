package com.clinube.comun.seguridad

import org.springframework.util.AntPathMatcher

/**
 * Las rutas que no exigen sesión, en los DOS grupos que tiene Django. La
 * diferencia entre los dos se nota en las respuestas, así que no se juntan:
 *
 *   · **Sin autenticación** (`authentication_classes = ()` o `[]`): la vista
 *     ni mira la cabecera `Authorization`. Un token caducado o mal formado
 *     se ignora, y `/health/` responde 200 aunque lo lleve. Comprobado
 *     contra Django.
 *   · **Abiertas, pero con autenticación** (`AllowAny` con las clases de
 *     autenticación por defecto): se puede entrar sin token, pero si llega
 *     uno y no vale, Django responde 401. Comprobado con `otp/request`.
 *
 * Cada módulo que se porte añade aquí las suyas, mirando qué hace su vista
 * en Django.
 */
object RutasPublicas {

    /** Django no autentica: el token, si llega, se ignora. */
    val SIN_AUTENTICACION = listOf(
        "/api/v1/health/",
        "/api/v1/ready/",
        "/api/v1/legal/privacidad/",
        "/api/v1/legal/condiciones/",
        "/api/v1/publico/solicitudes/",
        "/api/v1/publico/solicitudes/*/confirmar/",
        // TokenObtainPairView y TokenRefreshView de SimpleJWT.
        "/api/v1/auth/login/",
        "/api/v1/auth/token/refresh/",
        "/api/v1/auth/google/config/",
        "/api/v1/auth/google/",
        "/api/v1/auth/otp/canal/",
    )

    /** Django deja entrar sin token, pero si llega uno, lo comprueba. */
    val ABIERTAS_CON_AUTENTICACION = listOf(
        "/api/v1/auth/otp/request/",
        "/api/v1/auth/otp/verify/",
        "/api/v1/auth/recovery/request/",
        "/api/v1/auth/recovery/confirm/",
    )

    private val comparador = AntPathMatcher()

    fun sinAutenticacion(ruta: String): Boolean = SIN_AUTENTICACION.any { comparador.match(it, ruta) }
}
