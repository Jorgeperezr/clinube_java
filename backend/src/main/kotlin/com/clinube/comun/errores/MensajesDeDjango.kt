package com.clinube.comun.errores

/**
 * Los textos de error, COPIADOS de lo que responde Django.
 *
 * No están traducidos aquí ni redactados de nuevo: se capturaron de un
 * servidor Django real (`scripts/capturar-contrato.sh`) porque el panel y
 * la app los muestran tal cual a quien está en recepción. Algunos vienen
 * de las traducciones de DRF y SimpleJWT y tienen rarezas —faltan tildes
 * en «no es valido para ningun tipo de token», falta el punto final en
 * «no tiene una cuenta activa»—. Se dejan EXACTAMENTE así: corregirlas
 * sería dejar de responder igual que Django.
 */
object MensajesDeDjango {

    // ── DRF ──────────────────────────────────────────────────────────
    const val CREDENCIALES_NO_PROVISTAS = "Las credenciales de autenticación no se proveyeron."
    const val CAMPO_REQUERIDO = "Este campo es requerido."
    const val CAMPO_EN_BLANCO = "Este campo no puede estar en blanco."
    const val ERROR_GENERICO = "Ocurrió un error al procesar la solicitud."
    const val NO_ENCONTRADO = "No encontrado."
    const val PAGINA_INVALIDA = "Página inválida."
    const val SIN_PERMISO = "Usted no tiene permiso para realizar esta acción."

    fun metodoNoPermitido(metodo: String) = "Método \"$metodo\" no permitido."

    // ── SimpleJWT ────────────────────────────────────────────────────
    /** Login con credenciales que no corresponden a ninguna cuenta activa. Sin punto final, como en Django. */
    const val SIN_CUENTA_ACTIVA = "La combinación de credenciales no tiene una cuenta activa"

    /** El mensaje de fuera cuando la autenticación rechaza un token. Sin tildes, como en Django. */
    const val TOKEN_NO_VALIDO_PARA_NINGUN_TIPO = "El token dado no es valido para ningun tipo de token"

    const val TOKEN_EN_LISTA_NEGRA = "El token está en lista negra"
    const val TOKEN_DE_TIPO_INCORRECTO = "El token tiene un tipo incorrecto"
    const val TOKEN_SIN_USUARIO = "El token no contenía identificación de usuario reconocible"
    const val CABECERA_AUTORIZACION_INVALIDA =
        "El encabezado 'Authorization' debe contener valores delimitados por espacios"
    const val USUARIO_NO_ENCONTRADO = "Usuario no encontrado"
    const val USUARIO_INACTIVO = "El usuario está inactivo"

    /** Los mensajes de DENTRO de `details.messages` no están traducidos en SimpleJWT. */
    const val TOKEN_INVALIDO = "Token is invalid"
    const val TOKEN_CADUCADO = "Token is expired"

    // ── Propios de Clinube (apps/accounts, apps/common) ──────────────
    const val PACIENTES_CON_OTP =
        "Los pacientes deben ingresar con el código OTP enviado por WhatsApp."
    const val USUARIO_DESACTIVADO = "Este usuario está desactivado."
    const val CLINICA_DESACTIVADA = "La clínica de este usuario está desactivada."
    const val CAMBIO_DE_CONTRASENA_REQUERIDO =
        "Debes elegir tu propia contraseña antes de usar el sistema."
}
