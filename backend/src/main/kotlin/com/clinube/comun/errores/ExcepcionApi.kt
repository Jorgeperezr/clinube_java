package com.clinube.comun.errores

import org.springframework.http.HttpStatus

/**
 * Un error de la API con la forma que usa Django:
 *
 *     {"error": {"code": "...", "message": "...", "details": {...}}}
 *
 * `codigo` es el `default_code` de la excepción de DRF, porque el panel lo
 * lee para decidir a dónde llevar al usuario (`password_change_required`,
 * `token_not_valid`…). No es decorativo.
 */
class ExcepcionApi(
    val estado: HttpStatus,
    val codigo: String,
    val mensaje: String,
    val detalles: Map<String, Any?> = emptyMap(),
) : RuntimeException(mensaje) {

    companion object {

        fun noAutenticado() = ExcepcionApi(
            HttpStatus.UNAUTHORIZED, "not_authenticated", MensajesDeDjango.CREDENCIALES_NO_PROVISTAS,
        )

        fun autenticacionFallida(mensaje: String, codigo: String = "authentication_failed") =
            ExcepcionApi(HttpStatus.UNAUTHORIZED, codigo, mensaje)

        /**
         * Igual que la anterior, pero con el código interno DENTRO de
         * `details`. Las excepciones propias de SimpleJWT heredan de
         * `DetailDictMixin`, que mete `{"detail":…, "code":…}`, y por eso
         * sus respuestas llevan `details.code` mientras que las de DRF a
         * secas lo dejan vacío. Son dos formas distintas y el panel recibe
         * las dos.
         */
        fun autenticacionFallidaConCodigo(mensaje: String, codigoInterno: String) = ExcepcionApi(
            HttpStatus.UNAUTHORIZED, "authentication_failed", mensaje, mapOf("code" to codigoInterno),
        )

        /** `InvalidToken` con un texto propio: el motivo va en `message`. */
        fun tokenInvalido(mensaje: String) = ExcepcionApi(
            HttpStatus.UNAUTHORIZED, "token_not_valid", mensaje, mapOf("code" to "token_not_valid"),
        )

        fun sinPermiso(mensaje: String = MensajesDeDjango.SIN_PERMISO, codigo: String = "permission_denied") =
            ExcepcionApi(HttpStatus.FORBIDDEN, codigo, mensaje)

        fun noEncontrado(mensaje: String = MensajesDeDjango.NO_ENCONTRADO) =
            ExcepcionApi(HttpStatus.NOT_FOUND, "not_found", mensaje)

        /**
         * El 404 de `get_object_or_404` de Django. El código es «http404»
         * —el nombre de la clase de la excepción, en minúsculas— y el
         * mensaje, el de Django sin traducir: «No Patient matches the given
         * query.». Así responde Django y así se copia.
         */
        fun http404(modelo: String) =
            ExcepcionApi(HttpStatus.NOT_FOUND, "http404", MensajesDeDjango.noHayCoincidencia(modelo))

        /** Un `raise Http404` a secas en una vista: «No encontrado.», con el código «http404». */
        fun http404SinModelo() = ExcepcionApi(HttpStatus.NOT_FOUND, "http404", MensajesDeDjango.NO_ENCONTRADO)

        /**
         * Error de validación de campos. Django no pone el motivo en
         * `message` —ahí va un texto genérico— sino en `details`, campo a
         * campo y con la lista de mensajes de cada uno.
         */
        fun validacion(detalles: Map<String, List<String>>) = ExcepcionApi(
            HttpStatus.BAD_REQUEST, "invalid", MensajesDeDjango.ERROR_GENERICO, detalles,
        )

        /**
         * Lo que DRF devuelve cuando `validate()` de un serializador lanza
         * un error que no es de un campo concreto: va bajo
         * `non_field_errors`.
         */
        fun validacionGeneral(mensaje: String) = validacion(mapOf("non_field_errors" to listOf(mensaje)))

        /** Token rechazado por la autenticación (cabecera `Authorization`). */
        fun tokenRechazado(motivo: String, claseDeToken: String = "AccessToken", tipo: String = "access") =
            ExcepcionApi(
                HttpStatus.UNAUTHORIZED, "token_not_valid",
                MensajesDeDjango.TOKEN_NO_VALIDO_PARA_NINGUN_TIPO,
                mapOf(
                    "code" to "token_not_valid",
                    "messages" to listOf(
                        mapOf("token_class" to claseDeToken, "token_type" to tipo, "message" to motivo),
                    ),
                ),
            )

        /**
         * Token rechazado al renovar. La forma es distinta de la de
         * `tokenRechazado`: el motivo va en `message` y `details` solo
         * lleva el código. Así responde `TokenRefreshView`.
         */
        fun tokenDeRefrescoRechazado(motivo: String) = ExcepcionApi(
            HttpStatus.UNAUTHORIZED, "token_not_valid", motivo, mapOf("code" to "token_not_valid"),
        )
    }
}

/** 403 con código propio para que el panel lleve a la pantalla de cambio de contraseña. */
fun cambioDeContrasenaRequerido() = ExcepcionApi(
    HttpStatus.FORBIDDEN, "password_change_required", MensajesDeDjango.CAMBIO_DE_CONTRASENA_REQUERIDO,
)
