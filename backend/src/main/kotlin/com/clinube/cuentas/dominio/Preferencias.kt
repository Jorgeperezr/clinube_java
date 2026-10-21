package com.clinube.cuentas.dominio

/**
 * Preferencias de cada profesional. Portado de
 * `apps/accounts/preferencias.py`.
 *
 * A diferencia de las funcionalidades, que decide el dueño de la
 * plataforma para TODA una clínica, esto lo decide cada persona para sí
 * misma. Las dos cosas se combinan: una preferencia solo tiene efecto si
 * la clínica tiene contratada la funcionalidad de la que depende. Un
 * doctor no puede encenderse el odontograma 3D si su clínica no lo tiene;
 * sí puede apagárselo aunque la clínica lo tenga.
 *
 * Las claves desconocidas se descartan y las que faltan toman su valor por
 * defecto, así que añadir una preferencia nueva no necesita migración.
 */
object Preferencias {

    data class Entrada(val etiqueta: String, val porDefecto: Boolean, val funcionalidad: String?)

    val CATALOGO: Map<String, Entrada> = linkedMapOf(
        "odontograma_3d" to Entrada("Odontograma 3D", true, "odontograma_3d"),
    )

    val POR_DEFECTO: Map<String, Boolean> = CATALOGO.mapValues { it.value.porDefecto }

    /**
     * Apariencia del panel, también de cada persona. No dependen de nada
     * contratado, así que van aparte del catálogo:
     *
     *   · `acento`: el color de acento propio. "" = el de la clínica.
     *   · `barra_oculta`: las secciones que esa persona quitó de su barra
     *     lateral. Ocultar no quita permisos: la página sigue funcionando
     *     si se entra por su dirección.
     *   · `tema`: "" = el de la clínica; «clinica:<clave>» o
     *     «clinube:<clave>». Elegirlo exige la función «temas_personales»,
     *     y eso lo comprueba la vista, que conoce la clínica; aquí, la forma.
     */
    val APARIENCIA: Map<String, Any> = linkedMapOf(
        "acento" to "",
        "barra_oculta" to emptyList<String>(),
        "tema" to "",
    )

    private val CLAVE = Regex("^[a-z_]{1,30}$")
    private val TEMA = Regex("^(clinica|clinube):[a-z0-9_]{1,30}$")
    const val MAX_OCULTAS = 20

    class ValorInvalido(mensaje: String) : IllegalArgumentException(mensaje)

    /** El valor limpio, o `ValorInvalido` con el motivo. */
    fun validarApariencia(clave: String, valor: Any?): Any = when (clave) {
        "acento" -> {
            val texto = valor as? String ?: throw ValorInvalido("el acento tiene que ser el nombre de un color")
            if (texto.isNotEmpty() && !CLAVE.matches(texto)) {
                throw ValorInvalido("el acento tiene que ser el nombre de un color")
            }
            texto
        }
        "tema" -> {
            val texto = valor as? String
                ?: throw ValorInvalido("el tema tiene que ser uno de los de la clínica o de Clinube")
            if (texto.isNotEmpty() && !TEMA.matches(texto)) {
                throw ValorInvalido("el tema tiene que ser uno de los de la clínica o de Clinube")
            }
            texto
        }
        "barra_oculta" -> {
            val lista = valor as? List<*>
                ?: throw ValorInvalido("la barra lateral tiene que ser una lista de secciones")
            if (lista.size > MAX_OCULTAS || lista.any { it !is String || !CLAVE.matches(it) }) {
                throw ValorInvalido("la barra lateral tiene que ser una lista de secciones")
            }
            lista.filterIsInstance<String>().distinct().sorted()
        }
        else -> throw ValorInvalido("preferencia desconocida")
    }

    /** Lo guardado, completado con los valores por defecto y sin claves ajenas. */
    fun normalizar(valores: Map<String, Any?>?): Map<String, Any?> {
        val limpio = LinkedHashMap<String, Any?>()
        POR_DEFECTO.forEach { (clave, defecto) ->
            limpio[clave] = (valores?.get(clave) as? Boolean) ?: defecto
        }
        APARIENCIA.forEach { (clave, defecto) ->
            limpio[clave] = try {
                validarApariencia(clave, valores?.get(clave) ?: defecto)
            } catch (e: ValorInvalido) {
                defecto
            }
        }
        return limpio
    }
}
