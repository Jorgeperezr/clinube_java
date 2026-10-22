package com.clinube.comun.jwt

import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.configuracion.Ajustes
import com.clinube.cuentas.dominio.TokenEnListaNegra
import com.clinube.cuentas.dominio.TokenVigente
import com.clinube.cuentas.dominio.Usuario
import com.clinube.cuentas.repositorio.RepositorioDeTokensEnListaNegra
import com.clinube.cuentas.repositorio.RepositorioDeTokensVigentes
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Emite y comprueba los tokens igual que SimpleJWT, con las MISMAS tablas
 * (`token_blacklist_outstandingtoken` y `token_blacklist_blacklistedtoken`).
 *
 * Que las tablas sean las mismas es lo que permite cambiar un servidor por
 * el otro por partes: un refresco emitido por Django se renueva aquí, y el
 * que se invalidó aquí sigue invalidado allí.
 */
@Service
class ServicioDeTokens(
    private val ajustes: Ajustes,
    private val vigentes: RepositorioDeTokensVigentes,
    private val listaNegra: RepositorioDeTokensEnListaNegra,
) {

    companion object {
        const val ACCESO = "access"
        const val REFRESCO = "refresh"
    }

    /** Los dos tokens del login. El de refresco queda registrado, como en SimpleJWT. */
    @Transactional
    fun paraUsuario(usuario: Usuario): Pareja {
        val ahora = Instant.now().epochSecond
        val refresco = claims(REFRESCO, usuario, ahora, ajustes.jwt.vidaRefresco.seconds)
        val acceso = claims(ACCESO, usuario, ahora, ajustes.jwt.vidaAcceso.seconds)
        val refrescoFirmado = Jwt.firmar(refresco, ajustes.secreto)

        // SimpleJWT apunta en `OutstandingToken` cada refresco que emite
        // (`BlacklistMixin.for_user`). Sin esta fila no se podría saber
        // después qué tokens hay vivos de una persona.
        vigentes.save(
            TokenVigente(
                usuarioId = usuario.id,
                jti = refresco["jti"] as String,
                token = refrescoFirmado,
                creadoEn = Instant.ofEpochSecond(ahora),
                caducaEn = Instant.ofEpochSecond(refresco["exp"] as Long),
            ),
        )
        return Pareja(refresh = refrescoFirmado, access = Jwt.firmar(acceso, ajustes.secreto))
    }

    /**
     * Renueva: comprueba el refresco, lo deja en la lista negra y emite
     * una pareja nueva. Es el `ROTATE_REFRESH_TOKENS` +
     * `BLACKLIST_AFTER_ROTATION` de Django, y por eso un refresco usado
     * dos veces falla la segunda.
     */
    @Transactional
    fun renovar(tokenDeRefresco: String): Pareja {
        val claims = try {
            Jwt.leer(tokenDeRefresco, ajustes.secreto)
        } catch (e: ErrorDeToken) {
            throw com.clinube.comun.errores.ExcepcionApi.tokenDeRefrescoRechazado(e.motivo)
        }

        val jti = claims["jti"] as? String
            ?: throw com.clinube.comun.errores.ExcepcionApi.tokenDeRefrescoRechazado(MensajesDeDjango.TOKEN_INVALIDO)

        // El orden importa y es el de SimpleJWT: caducidad (ya hecha en
        // `Jwt.leer`), después lista negra, y el tipo al final.
        if (listaNegra.contarEnListaNegra(jti) > 0) {
            throw com.clinube.comun.errores.ExcepcionApi
                .tokenDeRefrescoRechazado(MensajesDeDjango.TOKEN_EN_LISTA_NEGRA)
        }
        if (claims["token_type"] != REFRESCO) {
            throw com.clinube.comun.errores.ExcepcionApi
                .tokenDeRefrescoRechazado(MensajesDeDjango.TOKEN_DE_TIPO_INCORRECTO)
        }

        val usuarioId = UUID.fromString(claims["user_id"] as String)
        // `blacklist()` de SimpleJWT: si el refresco no estaba apuntado
        // (porque salió de una rotación anterior), se apunta ahora y
        // después se invalida.
        val fila = vigentes.findByJti(jti) ?: vigentes.save(
            TokenVigente(
                usuarioId = usuarioId,
                jti = jti,
                token = tokenDeRefresco,
                creadoEn = (claims["iat"] as? Number)?.let { Instant.ofEpochSecond(it.toLong()) },
                caducaEn = Instant.ofEpochSecond((claims["exp"] as Number).toLong()),
            ),
        )
        if (!listaNegra.existsByTokenId(fila.id!!)) {
            listaNegra.save(TokenEnListaNegra(tokenId = fila.id!!, invalidadoEn = Instant.now()))
        }

        val ahora = Instant.now().epochSecond
        // La rotación reutiliza los claims del token presentado y le
        // cambia `jti`, `exp` e `iat`: el rol y la clínica son los que
        // llevaba. Igual que `set_jti/set_exp/set_iat` en SimpleJWT, que
        // tampoco vuelve a leer el usuario de la base.
        val nuevoRefresco = LinkedHashMap<String, Any?>().apply {
            put("token_type", REFRESCO)
            put("exp", ahora + ajustes.jwt.vidaRefresco.seconds)
            put("iat", ahora)
            put("jti", nuevoJti())
            put("user_id", claims["user_id"])
            put("role", claims["role"])
            put("tenant_id", claims["tenant_id"])
        }
        val nuevoAcceso = LinkedHashMap<String, Any?>().apply {
            put("token_type", ACCESO)
            put("exp", ahora + ajustes.jwt.vidaAcceso.seconds)
            put("iat", ahora)
            put("jti", nuevoJti())
            put("user_id", claims["user_id"])
            put("role", claims["role"])
            put("tenant_id", claims["tenant_id"])
        }
        return Pareja(
            refresh = Jwt.firmar(nuevoRefresco, ajustes.secreto),
            access = Jwt.firmar(nuevoAcceso, ajustes.secreto),
        )
    }

    private fun claims(
        tipo: String,
        usuario: Usuario,
        ahora: Long,
        vida: Long,
    ): LinkedHashMap<String, Any?> = LinkedHashMap<String, Any?>().apply {
        put("token_type", tipo)
        put("exp", ahora + vida)
        put("iat", ahora)
        put("jti", nuevoJti())
        put("user_id", usuario.id.toString())
        put("role", usuario.rol)
        // Ojo: Django escribe `str(user.tenant_id)`, y para el Super
        // Administrador —que no tiene clínica— eso da la CADENA "None",
        // no un nulo. Se copia tal cual: un token tiene que ser el mismo
        // en los dos servidores, rarezas incluidas.
        put("tenant_id", usuario.clinicaId?.toString() ?: "None")
    }

    private fun nuevoJti() = UUID.randomUUID().toString().replace("-", "")

    data class Pareja(val refresh: String, val access: String)
}
