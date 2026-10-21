package com.clinube.comun.seguridad

import com.clinube.comun.errores.CuerpoDeError
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.errores.cambioDeContrasenaRequerido
import com.clinube.comun.jwt.ErrorDeToken
import com.clinube.comun.jwt.Jwt
import com.clinube.comun.jwt.ServicioDeTokens
import com.clinube.configuracion.Ajustes
import com.clinube.clinicas.RepositorioDeClinicas
import com.clinube.cuentas.repositorio.RepositorioDeUsuarios
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Autenticación por token, equivalente de
 * `apps/common/authentication.TenantAwareJWTAuthentication`.
 *
 * El orden de las comprobaciones es el de SimpleJWT y se nota en la
 * respuesta, así que no se reordena:
 *
 *   1. sin cabecera, o con otro esquema que no sea `Bearer` → anónimo (no
 *      es un error: puede ser una ruta pública);
 *   2. cabecera que no tenga exactamente dos partes → `bad_authorization_header`;
 *   3. firma y caducidad del token;
 *   4. tipo de token (`access`);
 *   5. el `user_id`, el usuario y que esté activo;
 *   6. que su clínica no esté dada de baja;
 *   7. que no tenga una contraseña puesta por otra persona.
 *
 * Los dos últimos no son del protocolo, son del negocio, y viven aquí
 * porque este es el único sitio por el que pasa toda petición autenticada:
 * es el único que hay que revisar para saber quién puede operar.
 */
@Component
class FiltroDeAutenticacionJwt(
    private val ajustes: Ajustes,
    private val usuarios: RepositorioDeUsuarios,
    private val clinicas: RepositorioDeClinicas,
    private val json: ObjectMapper,
) : OncePerRequestFilter() {

    companion object {
        /**
         * Lo único que se puede hacer con una contraseña temporal. `me`
         * entra porque el panel lo pide nada más iniciar sesión para saber
         * quién es, y el refresco porque perder la sesión a mitad del
         * cambio sería absurdo.
         */
        val RUTAS_CON_CONTRASENA_TEMPORAL = listOf(
            "/api/v1/auth/change-password/",
            "/api/v1/auth/me/",
            "/api/v1/auth/token/refresh/",
        )
    }

    override fun doFilterInternal(
        peticion: HttpServletRequest,
        respuesta: HttpServletResponse,
        cadena: FilterChain,
    ) {
        try {
            autenticar(peticion)
        } catch (e: ExcepcionApi) {
            responderError(respuesta, e)
            return
        }
        cadena.doFilter(peticion, respuesta)
    }

    private fun autenticar(peticion: HttpServletRequest) {
        val cabecera = peticion.getHeader("Authorization") ?: return
        val partes = cabecera.split(" ", "\t").filter { it.isNotEmpty() }
        if (partes.isEmpty() || partes[0] != "Bearer") return
        if (partes.size != 2) {
            throw ExcepcionApi.autenticacionFallidaConCodigo(
                MensajesDeDjango.CABECERA_AUTORIZACION_INVALIDA, "bad_authorization_header",
            )
        }

        val claims = try {
            Jwt.leer(partes[1], ajustes.secreto)
        } catch (e: ErrorDeToken) {
            throw ExcepcionApi.tokenRechazado(e.motivo)
        }
        if (claims["token_type"] != ServicioDeTokens.ACCESO) {
            throw ExcepcionApi.tokenRechazado(MensajesDeDjango.TOKEN_DE_TIPO_INCORRECTO)
        }

        val id = (claims["user_id"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw ExcepcionApi.tokenInvalido(MensajesDeDjango.TOKEN_SIN_USUARIO)

        val usuario = usuarios.findById(id).orElse(null)
            ?: throw ExcepcionApi.autenticacionFallidaConCodigo(
                MensajesDeDjango.USUARIO_NO_ENCONTRADO, "user_not_found",
            )
        if (!usuario.activo) {
            throw ExcepcionApi.autenticacionFallidaConCodigo(
                MensajesDeDjango.USUARIO_INACTIVO, "user_inactive",
            )
        }

        // Sin esto, un usuario con un token vigente seguiría trabajando
        // después de que su clínica fuera dada de baja.
        usuario.clinicaId?.let { clinicaId ->
            val clinica = clinicas.findById(clinicaId).orElse(null)
            if (clinica == null || !clinica.activa) {
                throw ExcepcionApi.autenticacionFallida(MensajesDeDjango.CLINICA_DESACTIVADA)
            }
        }

        // Contraseña puesta por otra persona: mientras no se cambie, esa
        // cuenta solo puede hacer una cosa, elegir la suya. Si no, quien
        // entregó las credenciales conserva la llave de los datos
        // clínicos de esa clínica, que es lo contrario de entregarlas.
        if (usuario.debeCambiarContrasena &&
            RUTAS_CON_CONTRASENA_TEMPORAL.none { peticion.requestURI.startsWith(it) }
        ) {
            throw cambioDeContrasenaRequerido()
        }

        SecurityContextHolder.getContext().authentication = AutenticacionDeClinube(usuario)
    }

    private fun responderError(respuesta: HttpServletResponse, e: ExcepcionApi) {
        SecurityContextHolder.clearContext()
        respuesta.status = e.estado.value()
        respuesta.contentType = MediaType.APPLICATION_JSON_VALUE
        respuesta.characterEncoding = "UTF-8"
        respuesta.writer.write(
            json.writeValueAsString(
                CuerpoDeError(CuerpoDeError.Detalle(e.codigo, e.mensaje, e.detalles)),
            ),
        )
    }
}
