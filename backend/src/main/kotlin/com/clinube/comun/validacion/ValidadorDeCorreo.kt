package com.clinube.comun.validacion

import java.net.InetAddress
import java.util.regex.Pattern

/**
 * El `EmailValidator` de Django, portado de su fuente
 * (`django/core/validators.py`), con las mismas expresiones regulares.
 * Una expresión «razonable» cualquiera aceptaría o rechazaría correos
 * distintos: `josé@…` no vale en Django (la parte de usuario es ASCII) y
 * `usuario@localhost` sí.
 */
object ValidadorDeCorreo {

    private const val UL = "¡-￿"
    private const val HOSTNAME = "[a-z${UL}0-9](?:[a-z${UL}0-9-]{0,61}[a-z${UL}0-9])?"
    private const val DOMINIO = "(?:\\.(?!-)[a-z${UL}0-9-]{1,63}(?<!-))*"
    private const val TLD = "\\.(?!-)(?:[a-z$UL-]{2,63}|xn--[a-z0-9]{1,59})(?<!-)"

    private val USUARIO = Pattern.compile(
        "(^[-!#$%&'*+/=?^_`{}|~0-9A-Z]+(\\.[-!#$%&'*+/=?^_`{}|~0-9A-Z]+)*\\z" +
            "|^\"([\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f!#-\\[\\]-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\"\\z)",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )
    private val DOMINIO_COMPLETO = Pattern.compile("^$HOSTNAME$DOMINIO$TLD\\z", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    private val LITERAL = Pattern.compile("\\[([A-F0-9:.]+)\\]\\z", Pattern.CASE_INSENSITIVE)
    private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")
    private val LISTA_PERMITIDA = setOf("localhost")

    fun esValido(valor: String): Boolean {
        // 320 caracteres como máximo (RFC 3696, sección 3).
        if (valor.isEmpty() || '@' !in valor || valor.length > 320) return false
        val usuario = valor.substringBeforeLast('@')
        val dominio = valor.substringAfterLast('@')
        if (!USUARIO.matcher(usuario).lookingAt()) return false
        return dominio in LISTA_PERMITIDA || dominioValido(dominio)
    }

    private fun dominioValido(dominio: String): Boolean {
        if (DOMINIO_COMPLETO.matcher(dominio).lookingAt()) return true
        val literal = LITERAL.matcher(dominio)
        if (!literal.lookingAt()) return false
        val ip = literal.group(1)
        if (IPV4.matches(ip)) return true
        // IPv6: una dirección con «:», que no provoca consulta DNS.
        return ':' in ip && runCatching { InetAddress.getByName(ip) }.isSuccess
    }
}
