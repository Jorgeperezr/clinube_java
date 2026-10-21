package com.clinube.configuracion

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.get

/**
 * Con el perfil de producción, el servidor NO arranca con valores de
 * ejemplo ni con la depuración encendida. Es lo mismo que hace
 * `config/produccion.py` en Django, y por el mismo motivo: un despliegue
 * con la clave de desarrollo es un despliegue cuyos tokens puede firmar
 * cualquiera que haya leído el repositorio.
 *
 * Se comprueba al preparar el entorno, antes de que exista el contexto:
 * así falla al arrancar y no a la primera petición.
 */
class ComprobacionDeProduccion : ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    companion object {
        const val SECRETO_DE_EJEMPLO = "dev-only-insecure-key-change-me-never-use-in-production"
        const val MINIMO_SECRETO = 32
    }

    override fun onApplicationEvent(evento: ApplicationEnvironmentPreparedEvent) {
        val entorno = evento.environment
        if (entorno["DJANGO_ENTORNO"] != "produccion" && entorno.getProperty("clinube.entorno") != "produccion") {
            return
        }

        val problemas = mutableListOf<String>()
        val secreto = entorno.getProperty("clinube.secreto") ?: ""
        if (secreto.isBlank() || secreto == SECRETO_DE_EJEMPLO) {
            problemas += "DJANGO_SECRET_KEY sigue siendo la de ejemplo."
        } else if (secreto.length < MINIMO_SECRETO) {
            problemas += "DJANGO_SECRET_KEY tiene menos de $MINIMO_SECRETO caracteres."
        }
        if (entorno.getProperty("clinube.depuracion", Boolean::class.javaObjectType, false)) {
            problemas += "DJANGO_DEBUG está encendido."
        }

        if (problemas.isNotEmpty()) {
            throw IllegalStateException(
                "No se puede arrancar en producción:\n" + problemas.joinToString("\n") { "  · $it" }
            )
        }
    }
}
