package com.clinube.comun.validacion

/**
 * Teléfonos en E.164 (+593991234567), como `apps/common/telefonos.py`.
 *
 * La recepción los escribe como los dicta el paciente —«099 123 4567»,
 * «0991234567», «+593 99…»— y si no se comparan en el mismo formato,
 * «0991234567» y «+593991234567» son dos personas distintas y el código no
 * llega nunca. El prefijo por omisión es el de Ecuador.
 */
object Telefonos {
    const val PREFIJO_PAIS = "+593"

    /** El teléfono en E.164, o "" si no parece un teléfono. */
    fun normalizar(texto: String?, prefijo: String = PREFIJO_PAIS): String {
        val limpio = (texto ?: "").replace(Regex("[^\\d+]"), "")
        if (limpio.isEmpty()) return ""
        val sinMas = prefijo.trimStart('+')
        val numero = when {
            limpio.startsWith("+") -> "+" + limpio.replace(Regex("\\D"), "")
            limpio.startsWith("00") -> "+" + limpio.substring(2)
            limpio.startsWith(sinMas) && limpio.length > 10 -> "+$limpio"
            limpio.startsWith("0") -> prefijo + limpio.substring(1)
            else -> prefijo + limpio
        }
        // E.164: hasta 15 cifras. Menos de 8 no es un móvil de ningún país.
        val cifras = numero.length - 1
        return if (cifras in 8..15) numero else ""
    }

    /** `normalizar_correo`: en minúsculas y sin espacios, o "" si no es un correo. */
    fun normalizarCorreo(texto: String?): String {
        val correo = (texto ?: "").trim().lowercase()
        if (correo.isEmpty() || !ValidadorDeCorreo.esValido(correo)) return ""
        return correo
    }
}
