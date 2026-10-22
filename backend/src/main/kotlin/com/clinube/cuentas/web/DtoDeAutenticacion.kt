package com.clinube.cuentas.web

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lo que sale de las rutas de autenticación, con las claves y el
 * ORDEN de Django. El orden no es capricho: el panel y la app leen JSON por
 * clave, pero el comparador de contrato (`scripts/comparar-contrato`)
 * compara las respuestas de los dos servidores, y una diferencia de orden
 * sería ruido que tapa las diferencias de verdad.
 *
 * Lo que ENTRA no tiene clase: se lee como `JsonNode` con `CamposDrf`,
 * porque DRF distingue casos (nulo, en blanco, tipo inválido, cuerpo que
 * no es un objeto) que una clase de datos de Jackson junta en uno.
 */
/** `{refresh, access, role, must_change_password}`, en ese orden. */
data class RespuestaDeLogin(
    val refresh: String,
    val access: String,
    val role: String,
    @get:JsonProperty("must_change_password") val mustChangePassword: Boolean,
)

/** `{access, refresh}`: al renovar, el acceso va primero. */
data class RespuestaDeRefresco(
    val access: String,
    val refresh: String,
)
