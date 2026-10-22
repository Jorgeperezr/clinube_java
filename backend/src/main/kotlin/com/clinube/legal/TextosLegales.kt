package com.clinube.legal

import com.clinube.plataforma.ConfiguracionPlataforma
import com.clinube.plataforma.ServicioDeConfiguracion
import org.springframework.stereotype.Service

/**
 * Los textos legales de la plataforma (`apps/common/legal.py`): el aviso de
 * privacidad, para los pacientes, y las condiciones de uso con el acuerdo de
 * tratamiento de datos, para las clínicas.
 *
 * Viven en el servidor y no en el panel ni en la app porque lo que se
 * registra es que alguien recibió o aceptó UNA VERSIÓN concreta. Si el texto
 * estuviera repetido en tres sitios, la versión registrada podría no
 * corresponder con lo que esa persona leyó.
 *
 * CAMBIAR UN TEXTO ES CAMBIAR SU VERSIÓN. Los textos están en
 * `TextosLegalesGenerados.kt`, generados desde Django: no se editan a mano.
 */
object TextosLegales {
    const val AVISO_VERSION = "2026.10"
    const val CONDICIONES_VERSION = "2026.10"
    const val VIGENTE_DESDE = "4 de octubre de 2026"

    /** Lo que se muestra mientras el Super Administrador no completa los datos del titular. */
    const val SIN_DATO = "[pendiente de completar]"
}

data class DatosDelTitular(
    val plataforma: String,
    val titular: String,
    val ruc: String,
    val correo: String,
    val proveedorCorreo: String,
)

data class Seccion(val titulo: String, val parrafos: List<String>)

@Service
class ServicioDeTextosLegales(private val configuracion: ServicioDeConfiguracion) {

    fun avisoDePrivacidad(): Map<String, Any?> {
        val (datos, pendientes) = datosDelTitular()
        return documento(
            "Aviso de privacidad", TextosLegales.AVISO_VERSION,
            TextosLegalesGenerados.seccionesDelAviso(datos, encargados(datos)), pendientes,
        )
    }

    fun condiciones(): Map<String, Any?> {
        val (datos, pendientes) = datosDelTitular()
        return documento(
            "Condiciones de uso y acuerdo de tratamiento de datos", TextosLegales.CONDICIONES_VERSION,
            TextosLegalesGenerados.seccionesDeLasCondiciones(datos, encargados(datos)), pendientes,
        )
    }

    /**
     * Los datos de quien opera la plataforma. Usa `obtenerOCrear()` y no
     * `primera()`, como `get_solo()` en Django: leer un texto legal en una
     * instalación nueva crea la fila de configuración.
     *
     * Lo que falta se rellena con un marcador visible y se dice en
     * `pendientes`, para que se note y no pase desapercibido.
     */
    private fun datosDelTitular(): Pair<DatosDelTitular, List<String>> {
        val c = configuracion.obtenerOCrear()
        val titular = c.titularNombre.trim()
        val ruc = c.titularRuc.trim()
        val correo = c.titularCorreo.trim()
        val pendientes = buildList {
            if (titular.isEmpty()) add("titular")
            if (ruc.isEmpty()) add("ruc")
            if (correo.isEmpty()) add("correo")
        }
        return DatosDelTitular(
            plataforma = c.nombrePlataforma.ifEmpty { "Clinube" },
            titular = titular.ifEmpty { TextosLegales.SIN_DATO },
            ruc = ruc.ifEmpty { TextosLegales.SIN_DATO },
            correo = correo.ifEmpty { TextosLegales.SIN_DATO },
            proveedorCorreo = c.proveedorCorreo.trim(),
        ) to pendientes
    }

    /**
     * Los proveedores que intervienen. Quién envía el código de la app
     * depende del canal elegido en Plataforma: el aviso tiene que decir el
     * que se usa de verdad.
     */
    private fun encargados(d: DatosDelTitular): List<String> {
        val correo = d.proveedorCorreo.ifEmpty { "El proveedor de correo de la plataforma" }
        val (meta, envios) = if (configuracion.canalDelCodigo() == ConfiguracionPlataforma.CANAL_CORREO) {
            "Meta Platforms (WhatsApp): envía, a quien lo autorizó, los recordatorios de citas y pagos." to
                "$correo: envía los correos del sistema: los códigos para entrar en la app y " +
                "los de recuperar una contraseña."
        } else {
            "Meta Platforms (WhatsApp): envía los códigos para entrar en la app y, a quien lo " +
                "autorizó, los recordatorios de citas y pagos." to
                "$correo: envía los correos del sistema, como los de recuperar una contraseña."
        }
        return listOf(
            "Oracle Cloud Infrastructure: aloja el servidor, en la región de Santiago de Chile.",
            "Cloudflare: por su red pasan, cifradas, las conexiones entre el navegador o la app y " +
                "el servidor, y guarda las copias de seguridad, que van cifradas antes de salir y no " +
                "puede leer.",
            meta,
            envios,
        )
    }

    private fun documento(
        titulo: String,
        version: String,
        secciones: List<Seccion>,
        pendientes: List<String>,
    ): Map<String, Any?> = linkedMapOf(
        "titulo" to titulo,
        "version" to version,
        "vigente_desde" to TextosLegales.VIGENTE_DESDE,
        "secciones" to secciones.map { linkedMapOf("titulo" to it.titulo, "parrafos" to it.parrafos) },
        "pendientes" to pendientes,
    )
}
