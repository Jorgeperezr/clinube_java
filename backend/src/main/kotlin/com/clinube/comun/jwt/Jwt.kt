package com.clinube.comun.jwt

import com.clinube.comun.errores.MensajesDeDjango
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Firma y comprobación de los JWT de SimpleJWT, a mano.
 *
 * A mano y sin biblioteca por tres razones, todas de paridad:
 *
 *   · La cabecera tiene que ser EXACTAMENTE `{"alg":"HS256","typ":"JWT"}`,
 *     en ese orden y sin espacios, porque forma parte de lo que se firma.
 *   · Los claims van en el orden de SimpleJWT (`token_type`, `exp`, `iat`,
 *     `jti`, `user_id`, `role`, `tenant_id`).
 *   · Hace falta distinguir «caducado» de «inválido» con los mismos
 *     textos que SimpleJWT, porque el panel y la app los reciben.
 *
 * Una biblioteca haría lo mismo con más dependencia y menos control.
 */
object Jwt {

    private val json = ObjectMapper()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()
    private const val HMAC = "HmacSHA256"

    /** `{"alg":"HS256","typ":"JWT"}` ya codificada: es constante. */
    private val CABECERA = b64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())

    fun firmar(claims: Map<String, Any?>, secreto: String): String {
        val cuerpo = b64.encodeToString(json.writeValueAsBytes(claims))
        val aFirmar = "$CABECERA.$cuerpo"
        return "$aFirmar.${b64.encodeToString(hmac(aFirmar, secreto))}"
    }

    /**
     * Comprueba la firma y la caducidad y devuelve los claims.
     *
     * El orden de las comprobaciones es el de SimpleJWT y se nota en las
     * respuestas: primero la firma y la caducidad, y solo después el tipo
     * de token. Por eso un token de acceso caducado enviado a
     * `/auth/token/refresh/` responde «Token is expired» y no «tipo
     * incorrecto».
     */
    fun leer(token: String, secreto: String, ahora: Instant = Instant.now()): Map<String, Any?> {
        val partes = token.split(".")
        if (partes.size != 3) throw ErrorDeToken(MensajesDeDjango.TOKEN_INVALIDO)
        val aFirmar = "${partes[0]}.${partes[1]}"
        val firma = try {
            b64d.decode(partes[2])
        } catch (e: IllegalArgumentException) {
            throw ErrorDeToken(MensajesDeDjango.TOKEN_INVALIDO)
        }
        if (!MessageDigest.isEqual(firma, hmac(aFirmar, secreto))) {
            throw ErrorDeToken(MensajesDeDjango.TOKEN_INVALIDO)
        }

        @Suppress("UNCHECKED_CAST")
        val claims = try {
            json.readValue(b64d.decode(partes[1]), Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            throw ErrorDeToken(MensajesDeDjango.TOKEN_INVALIDO)
        }

        val exp = (claims["exp"] as? Number)?.toLong() ?: throw ErrorDeToken(MensajesDeDjango.TOKEN_INVALIDO)
        // Sin margen de gracia: SimpleJWT tampoco lo tiene por defecto.
        if (ahora.epochSecond >= exp) throw ErrorDeToken(MensajesDeDjango.TOKEN_CADUCADO)
        return claims
    }

    private fun hmac(mensaje: String, secreto: String): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(secreto.toByteArray(StandardCharsets.UTF_8), HMAC))
        return mac.doFinal(mensaje.toByteArray(StandardCharsets.UTF_8))
    }
}

/** Motivo en los MISMOS términos que SimpleJWT: «Token is expired» o «Token is invalid». */
class ErrorDeToken(val motivo: String) : RuntimeException(motivo)
