package com.clinube.auditoria

import com.clinube.comun.seguridad.AutenticacionDeClinube
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * El `AuditLogMiddleware` de Django: apunta automáticamente toda escritura
 * (POST, PUT, PATCH, DELETE) que hace un usuario autenticado y que termina
 * bien (2xx). Las lecturas no se apuntan aquí: se auditan de forma
 * explícita solo donde se exige (ver historia clínica).
 *
 * Es un filtro del contenedor que corre DENTRO de la cadena de Spring
 * Security (que va antes, con orden -100), así que la identidad de la
 * petición ya está puesta. La fila se escribe cuando el controlador ya
 * terminó y su transacción se confirmó: por eso, como en Django, va detrás
 * de los registros explícitos que haga la vista. Comprobado al aceptar las
 * condiciones: primero `accept_terms`, después `post`.
 *
 * No apunta las peticiones anónimas: entrar y renovar el token no dejan
 * rastro aquí, igual que en Django.
 */
@Component
class FiltroDeAuditoria(private val registros: RepositorioDeAuditoria) : OncePerRequestFilter() {

    companion object {
        val METODOS_AUDITADOS = setOf("POST", "PUT", "PATCH", "DELETE")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        peticion: HttpServletRequest,
        respuesta: HttpServletResponse,
        cadena: FilterChain,
    ) {
        cadena.doFilter(peticion, respuesta)

        if (peticion.method !in METODOS_AUDITADOS || respuesta.status !in 200..299) return
        val identidad = SecurityContextHolder.getContext().authentication as? AutenticacionDeClinube ?: return
        try {
            registros.save(
                RegistroDeAuditoria(
                    clinicaId = identidad.clinica?.id,
                    usuarioId = identidad.usuario.id,
                    accion = peticion.method.lowercase(),
                    tipoDeEntidad = peticion.requestURI,
                    idDeEntidad = "",
                    ip = DireccionDelCliente.comoInet(DireccionDelCliente.de(peticion)),
                    datos = mutableMapOf("status_code" to respuesta.status),
                ),
            )
        } catch (e: Exception) {
            // La respuesta ya está enviada: un fallo aquí no puede cambiarla,
            // pero no puede pasar en silencio.
            log.error("No se pudo apuntar la auditoría de {} {}", peticion.method, peticion.requestURI, e)
        }
    }
}
