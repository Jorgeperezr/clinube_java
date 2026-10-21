package com.clinube.cuentas.servicio

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.jwt.ServicioDeTokens
import com.clinube.comun.seguridad.HashDeContrasenaDjango
import com.clinube.cuentas.dominio.Rol
import com.clinube.cuentas.dominio.Usuario
import com.clinube.cuentas.repositorio.RepositorioDeUsuarios
import com.clinube.cuentas.web.RespuestaDeLogin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Entrar con correo y contraseña. Equivalente de `StaffLoginSerializer` +
 * el `ModelBackend` de Django, y el reparto de responsabilidades entre
 * los dos se conserva porque cambia el código de estado:
 *
 *   · Lo que rechaza el *backend* (no existe, contraseña mala, cuenta
 *     desactivada) es un **401** con «La combinación de credenciales no
 *     tiene una cuenta activa». Las tres cosas dan el mismo mensaje a
 *     propósito: decir «esa cuenta no existe» le confirma a quien prueba
 *     correos cuáles son válidos.
 *   · Lo que rechaza el *serializador* (es un paciente) es un **400** con
 *     el motivo en `non_field_errors`, porque ahí ya se sabe quién es y el
 *     mensaje le dice a la persona qué tiene que hacer: entrar por la app
 *     con su código.
 */
@Service
class ServicioDeAutenticacion(
    private val usuarios: RepositorioDeUsuarios,
    private val hash: HashDeContrasenaDjango,
    private val tokens: ServicioDeTokens,
) {

    @Transactional
    fun entrar(correo: String, contrasena: String): RespuestaDeLogin {
        val usuario = autenticar(correo, contrasena)
            ?: throw ExcepcionApi.autenticacionFallida(MensajesDeDjango.SIN_CUENTA_ACTIVA)

        if (usuario.esPaciente) {
            throw ExcepcionApi.validacionGeneral(MensajesDeDjango.PACIENTES_CON_OTP)
        }
        if (!usuario.activo) {
            throw ExcepcionApi.validacionGeneral(MensajesDeDjango.USUARIO_DESACTIVADO)
        }

        val pareja = tokens.paraUsuario(usuario)
        return RespuestaDeLogin(
            refresh = pareja.refresh,
            access = pareja.access,
            role = usuario.rol,
            mustChangePassword = usuario.debeCambiarContrasena,
        )
    }

    /**
     * Lo que hace `ModelBackend.authenticate`: busca por el campo de
     * usuario, comprueba la contraseña y exige que la cuenta esté activa.
     *
     * Cuando el correo no existe se comprueba la contraseña contra un hash
     * de descarte. Parece inútil y no lo es: sin eso, la respuesta a un
     * correo que no existe llega en menos de un milisegundo y la de uno
     * que sí existe tarda lo que tarda PBKDF2 con un millón de
     * iteraciones. Esa diferencia de tiempo es, por sí sola, una forma de
     * averiguar qué correos tienen cuenta.
     */
    private fun autenticar(correo: String, contrasena: String): Usuario? {
        val usuario = usuarios.findByCorreo(correo)
        if (usuario == null) {
            hash.verificar(contrasena, HASH_DE_DESCARTE)
            return null
        }
        if (!hash.verificar(contrasena, usuario.contrasena)) return null
        if (!usuario.activo) return null
        return usuario
    }

    companion object {
        /** Un hash real de una contraseña que nadie usa, para igualar los tiempos. */
        private const val HASH_DE_DESCARTE =
            "pbkdf2_sha256\$1000000\$noExisteEsteUsuario1\$Rr0qG0wv1vJ8pQ4mXk0mJ0vO1nM2lK3jI4hG5fE6dC7="
    }
}
