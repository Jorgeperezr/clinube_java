package com.clinube.comun.seguridad

import com.clinube.clinicas.Clinica
import com.clinube.clinicas.RepositorioDeClinicas
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.cuentas.dominio.Usuario
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

/**
 * Quién hace la petición y en qué clínica. El equivalente de `request.user`
 * y `request.tenant` de Django.
 *
 * **La clínica es la del usuario en la base, no la del token.** Esa es la
 * regla de aislamiento y no es un detalle: si se tomara del token, bastaría
 * con un token viejo —o manipulado y vuelto a firmar, si la clave se
 * filtrara— para leer los datos de otra clínica. Tomándola de la base, un
 * cambio de clínica surte efecto en la siguiente petición.
 *
 * Es de ámbito de petición y memoriza la clínica: una vista que la lea diez
 * veces hace UNA consulta, no diez.
 */
@Component
@RequestScope
class ContextoDeLaPeticion(private val clinicas: RepositorioDeClinicas) {

    private var resuelta = false
    private var clinicaMemorizada: Clinica? = null

    /** El usuario autenticado, o nulo si la petición es anónima. */
    val usuario: Usuario?
        get() = (SecurityContextHolder.getContext().authentication as? AutenticacionDeClinube)?.usuario

    /** El usuario autenticado; 401 con el texto de Django si no hay ninguno. */
    fun exigirUsuario(): Usuario = usuario ?: throw ExcepcionApi.noAutenticado()

    /** La clínica del usuario. Nula para el Super Administrador, que no tiene. */
    val clinica: Clinica?
        get() {
            if (!resuelta) {
                clinicaMemorizada = usuario?.clinicaId?.let { clinicas.findById(it).orElse(null) }
                resuelta = true
            }
            return clinicaMemorizada
        }

    /**
     * La clínica de la petición, exigida. Toda consulta de datos de
     * negocio se filtra por ella.
     */
    fun exigirClinica(): Clinica = clinica
        ?: throw ExcepcionApi.sinPermiso("Esta acción se realiza dentro de una clínica.")
}
