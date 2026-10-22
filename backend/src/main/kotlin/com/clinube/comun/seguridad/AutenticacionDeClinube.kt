package com.clinube.comun.seguridad

import com.clinube.clinicas.Clinica
import com.clinube.cuentas.dominio.Usuario
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * La identidad de la petición: el `Usuario` y su `Clinica` tal como están
 * en la base —no lo que diga el token—, porque el rol y la clínica pueden
 * haber cambiado desde que el token se emitió.
 *
 * La clínica viaja aquí porque el filtro ya la tiene que leer para saber
 * si está dada de baja. Guardarla evita una segunda consulta idéntica en
 * cada petición.
 */
class AutenticacionDeClinube(
    val usuario: Usuario,
    val clinica: Clinica?,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_${usuario.rol.uppercase()}"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Usuario = usuario
}
