package com.clinube.comun.seguridad

import com.clinube.cuentas.dominio.Usuario
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * La identidad de la petición. El principal es el `Usuario` tal como está
 * en la base —no lo que diga el token—, porque el rol y la clínica pueden
 * haber cambiado desde que el token se emitió.
 */
class AutenticacionDeClinube(val usuario: Usuario) :
    AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_${usuario.rol.uppercase()}"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Usuario = usuario
}
