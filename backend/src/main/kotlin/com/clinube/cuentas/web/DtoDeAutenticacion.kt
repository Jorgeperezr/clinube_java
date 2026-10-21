package com.clinube.cuentas.web

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lo que entra y sale de las rutas de autenticación, con las claves y el
 * ORDEN de Django. El orden no es capricho: el panel y la app leen JSON por
 * clave, pero el comparador de contrato (`scripts/comparar-contrato`)
 * compara las respuestas de los dos servidores, y una diferencia de orden
 * sería ruido que tapa las diferencias de verdad.
 */
data class PeticionDeLogin(
    val email: String? = null,
    val password: String? = null,
)

/** `{refresh, access, role, must_change_password}`, en ese orden. */
data class RespuestaDeLogin(
    val refresh: String,
    val access: String,
    val role: String,
    @get:JsonProperty("must_change_password") val mustChangePassword: Boolean,
)

data class PeticionDeRefresco(
    val refresh: String? = null,
)

/** `{access, refresh}`: al renovar, el acceso va primero. */
data class RespuestaDeRefresco(
    val access: String,
    val refresh: String,
)
