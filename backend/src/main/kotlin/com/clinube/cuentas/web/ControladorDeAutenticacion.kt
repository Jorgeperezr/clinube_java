package com.clinube.cuentas.web

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.jwt.ServicioDeTokens
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.cuentas.dominio.Funciones
import com.clinube.cuentas.dominio.Preferencias
import com.clinube.cuentas.servicio.ServicioDeAutenticacion
import com.clinube.legal.Condiciones
import com.clinube.plataforma.Funcionalidades
import com.clinube.plataforma.ServicioDeFuncionalidades
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Las rutas de autenticación. Las direcciones llevan barra final porque
 * así las pide el panel y las define Django (`/api/v1/auth/login/`): sin
 * ella, el panel recibiría un 404 o una redirección.
 */
@RestController
@RequestMapping("/api/v1/auth")
class ControladorDeAutenticacion(
    private val autenticacion: ServicioDeAutenticacion,
    private val tokens: ServicioDeTokens,
    private val contexto: ContextoDeLaPeticion,
    private val funcionalidades: ServicioDeFuncionalidades,
    private val condiciones: Condiciones,
) {

    /** POST /api/v1/auth/login/ — RF-USR-01 */
    @PostMapping("/login/")
    fun entrar(@RequestBody(required = false) peticion: PeticionDeLogin?): RespuestaDeLogin {
        val datos = peticion ?: PeticionDeLogin()
        // Django valida los dos campos y devuelve los DOS que faltan en la
        // misma respuesta, no el primero que encuentra.
        // Un campo AUSENTE y un campo VACÍO no dan el mismo error en
        // Django («es requerido» frente a «no puede estar en blanco»), y
        // el panel enseña el texto que recibe.
        val faltan = LinkedHashMap<String, List<String>>()
        motivo(datos.email)?.let { faltan["email"] = listOf(it) }
        motivo(datos.password)?.let { faltan["password"] = listOf(it) }
        if (faltan.isNotEmpty()) throw ExcepcionApi.validacion(faltan)

        return autenticacion.entrar(datos.email!!, datos.password!!)
    }

    private fun motivo(valor: String?): String? = when {
        valor == null -> MensajesDeDjango.CAMPO_REQUERIDO
        valor.isEmpty() -> MensajesDeDjango.CAMPO_EN_BLANCO
        else -> null
    }

    /** POST /api/v1/auth/token/refresh/ — refresco rotatorio, como SimpleJWT. */
    @PostMapping("/token/refresh/")
    fun renovar(@RequestBody(required = false) peticion: PeticionDeRefresco?): RespuestaDeRefresco {
        val token = peticion?.refresh
        motivo(token)?.let {
            throw ExcepcionApi.validacion(mapOf("refresh" to listOf(it)))
        }
        val pareja = tokens.renovar(token!!)
        return RespuestaDeRefresco(access = pareja.access, refresh = pareja.refresh)
    }

    /**
     * GET /api/v1/auth/me/ — el perfil del usuario autenticado.
     *
     * Se devuelve un mapa con orden en vez de una clase de datos porque
     * las claves no son todas campos del usuario: hay dos derivadas que se
     * mantienen por compatibilidad (`puede_gestionar_logros` y
     * `puede_gestionar_whatsapp`, para un panel con el perfil en caché de
     * una sesión anterior) y porque el orden tiene que ser exactamente el
     * de Django.
     */
    @GetMapping("/me/")
    fun yo(): Map<String, Any?> {
        val usuario = contexto.exigirUsuario()
        val funciones = Funciones.de(usuario)
        return linkedMapOf(
            "id" to usuario.id.toString(),
            "email" to usuario.correo,
            "full_name" to usuario.nombreCompleto,
            "role" to usuario.rol,
            "must_change_password" to usuario.debeCambiarContrasena,
            // La administración de una clínica que no ha aceptado la
            // versión vigente de las condiciones va primero a aceptarlas.
            "condiciones_pendientes" to condiciones.pendientesPara(usuario),
            "funciones" to funciones,
            "puede_gestionar_logros" to funciones["logros"],
            "puede_gestionar_whatsapp" to funciones["whatsapp"],
            // El panel esconde los módulos que la clínica no tiene. Es
            // cortesía: el candado de verdad está en la API.
            "funcionalidades" to funcionalidadesDe(usuario.clinicaId),
            "preferencias" to Preferencias.normalizar(usuario.preferencias),
            // El panel lo enseña junto al nombre de la clínica: quien
            // trabaja en ella ve funciones que las demás no tienen aún.
            "clinica_de_pruebas" to (contexto.clinica?.esDePruebas ?: false),
        )
    }

    /**
     * El Super Administrador opera sobre la plataforma, no dentro de una
     * clínica: se le devuelve todo apagado para que el panel no le pinte
     * módulos de clínica que no le corresponden.
     */
    private fun funcionalidadesDe(clinicaId: java.util.UUID?): Map<String, Boolean> =
        if (clinicaId == null) Funcionalidades.CATALOGO.keys.associateWith { false }
        else funcionalidades.efectivas(contexto.clinica)
}
