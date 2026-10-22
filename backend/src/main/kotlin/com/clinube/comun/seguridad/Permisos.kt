package com.clinube.comun.seguridad

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.cuentas.dominio.Funciones
import com.clinube.cuentas.dominio.Usuario
import com.clinube.plataforma.Funcionalidades
import com.clinube.plataforma.ServicioDeFuncionalidades
import org.springframework.stereotype.Component

/**
 * Las clases de permiso de Django, con sus mensajes. Cada vista las
 * comprueba EN EL MISMO ORDEN que su `permission_classes`, porque DRF
 * responde con el mensaje de la primera que falla:
 *
 *     permisos.exigir(rol("admin", "reception"), funcionalidad("inventario"))
 *
 * La autenticación ya la exige la configuración de seguridad: aquí solo se
 * llega con un usuario.
 */
@Component
class Permisos(
    private val contexto: ContextoDeLaPeticion,
    private val funcionalidades: ServicioDeFuncionalidades,
) {

    sealed interface Permiso

    /** `HasRole.for_roles(...)`: uno de esos roles, o ser superusuario. Mensaje por defecto de DRF. */
    data class Rol(val roles: Set<String>) : Permiso

    /**
     * `TieneFuncion.para(clave)`: la persona tiene la función Y su clínica
     * la funcionalidad de la que depende. Las dos cosas dan el MISMO
     * mensaje, el de la función, aunque lo que falte sea lo contratado:
     * así responde Django.
     */
    data class Funcion(val clave: String) : Permiso

    /** `RequiereFuncionalidad.para(clave)`: la clínica la tiene y la usa. */
    data class Funcionalidad(val clave: String) : Permiso

    fun exigir(vararg permisos: Permiso): Usuario {
        val usuario = contexto.exigirUsuario()
        permisos.forEach { permiso ->
            when (permiso) {
                is Rol -> if (usuario.rol !in permiso.roles && !usuario.esSuperusuario) {
                    throw ExcepcionApi.sinPermiso()
                }
                is Funcion -> {
                    val conFuncion = Funciones.tiene(usuario, permiso.clave)
                    val dependeDe = Funciones.CATALOGO.getValue(permiso.clave).funcionalidad
                    val contratada = dependeDe == null || funcionalidades.activa(contexto.clinica, dependeDe)
                    if (!conFuncion || !contratada) {
                        throw ExcepcionApi.sinPermiso("No tienes asignada esta función en la clínica.")
                    }
                }
                is Funcionalidad -> if (!funcionalidades.activa(contexto.clinica, permiso.clave)) {
                    val nombre = Funcionalidades.CATALOGO[permiso.clave]?.nombre ?: permiso.clave
                    throw ExcepcionApi.sinPermiso(
                        "«$nombre» no está activo en esta clínica. Pídeselo al administrador de la plataforma.",
                    )
                }
            }
        }
        return usuario
    }

    companion object {
        fun rol(vararg roles: String) = Rol(roles.toSet())
        fun funcion(clave: String) = Funcion(clave)
        fun funcionalidad(clave: String) = Funcionalidad(clave)

        /** Los cuatro roles del personal: lo que Django llama `CAN_VIEW` en casi todas las apps. */
        val PERSONAL = rol("admin", "reception", "doctor", "auxiliary")
    }
}
