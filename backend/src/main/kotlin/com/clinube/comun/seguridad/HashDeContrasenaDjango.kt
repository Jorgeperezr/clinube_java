package com.clinube.comun.seguridad

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Las contraseñas de Django, verificadas y generadas tal cual:
 *
 *     pbkdf2_sha256$<iteraciones>$<sal>$<hash en base64>
 *
 * Esto es lo que permite que entren los usuarios que YA existen: su hash
 * se queda donde está y este servidor lo comprueba. Las iteraciones se
 * leen de cada hash —no se suponen— porque conviven hashes de versiones
 * distintas de Django: el del usuario de pruebas tiene 1.000.000 y uno
 * creado hace dos años puede tener 390.000.
 *
 * PBKDF2 se calcula aquí a mano, con `Mac`, en vez de con
 * `SecretKeyFactory`. No es por gusto: el proveedor de la JVM rechaza la
 * contraseña vacía y convierte los caracteres por su cuenta, y las dos
 * cosas son diferencias de comportamiento frente a
 * `hashlib.pbkdf2_hmac`, que recibe los bytes UTF-8 tal cual. Con `Mac` el
 * resultado es el mismo byte a byte, incluidos los casos raros.
 */
@Component
class HashDeContrasenaDjango {

    companion object {
        const val ALGORITMO = "pbkdf2_sha256"

        /** Las de Django 5.2. Solo se usan al GENERAR; al verificar manda el hash. */
        const val ITERACIONES = 1_000_000

        private const val LARGO_SAL = 22
        private const val LARGO_CLAVE = 32
        private const val HMAC = "HmacSHA256"
        private const val ALFABETO_SAL =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    private val azar = SecureRandom()

    /**
     * ¿Es esta la contraseña? `false` también cuando la cuenta no tiene
     * contraseña usable: Django guarda `!` y 40 caracteres al azar en las
     * cuentas que solo entran por otro camino (los pacientes, con su
     * código), y con eso no se puede iniciar sesión de ninguna manera.
     */
    fun verificar(contrasena: String, guardado: String?): Boolean {
        if (guardado.isNullOrBlank() || guardado.startsWith("!")) return false
        val partes = guardado.split("$")
        if (partes.size != 4 || partes[0] != ALGORITMO) return false
        val iteraciones = partes[1].toIntOrNull() ?: return false
        val sal = partes[2]
        val esperado = partes[3]
        val calculado = Base64.getEncoder().encodeToString(pbkdf2(contrasena, sal, iteraciones))
        // Comparación en tiempo constante: si se usara `==`, el tiempo de
        // respuesta diría cuántos caracteres del hash se acertaron.
        return MessageDigest.isEqual(
            calculado.toByteArray(StandardCharsets.UTF_8),
            esperado.toByteArray(StandardCharsets.UTF_8),
        )
    }

    /** Un hash nuevo, con el formato que Django sabe leer. */
    fun generar(contrasena: String): String {
        val sal = (1..LARGO_SAL).map { ALFABETO_SAL[azar.nextInt(ALFABETO_SAL.length)] }.joinToString("")
        val hash = Base64.getEncoder().encodeToString(pbkdf2(contrasena, sal, ITERACIONES))
        return "$ALGORITMO\$$ITERACIONES\$$sal\$$hash"
    }

    /** Lo que Django guarda en una cuenta que no entra con contraseña. */
    fun inutilizable(): String {
        val cuerpo = (1..40).map { ALFABETO_SAL[azar.nextInt(ALFABETO_SAL.length)] }.joinToString("")
        return "!$cuerpo"
    }

    fun esInutilizable(guardado: String?): Boolean = guardado.isNullOrBlank() || guardado.startsWith("!")

    private fun pbkdf2(contrasena: String, sal: String, iteraciones: Int): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(contrasena.toByteArray(StandardCharsets.UTF_8), HMAC))
        val salBytes = sal.toByteArray(StandardCharsets.UTF_8)
        val resultado = ByteArray(LARGO_CLAVE)
        var escritos = 0
        var bloque = 1
        while (escritos < LARGO_CLAVE) {
            // U1 = HMAC(sal || contador de bloque, 4 bytes, big-endian)
            mac.update(salBytes)
            mac.update(byteArrayOf(
                (bloque ushr 24).toByte(), (bloque ushr 16).toByte(),
                (bloque ushr 8).toByte(), bloque.toByte(),
            ))
            var u = mac.doFinal()
            val acumulado = u.copyOf()
            for (i in 2..iteraciones) {
                u = mac.doFinal(u)
                for (j in acumulado.indices) acumulado[j] = (acumulado[j].toInt() xor u[j].toInt()).toByte()
            }
            val cuantos = minOf(acumulado.size, LARGO_CLAVE - escritos)
            acumulado.copyInto(resultado, escritos, 0, cuantos)
            escritos += cuantos
            bloque++
        }
        return resultado
    }
}
