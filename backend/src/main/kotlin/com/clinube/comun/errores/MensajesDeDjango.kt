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
    const val CAMPO_NULO = "Este campo no puede ser nulo."
    const val CADENA_INVALIDA = "No es una cadena válida."
    const val CORREO_INVALIDO = "Introduzca una dirección de correo electrónico válida."
    const val FECHA_INVALIDA = "Fecha con formato erróneo. Use uno de los siguientes formatos en su lugar: YYYY-MM-DD."
    const val FECHA_HORA_INVALIDA =
        "Fecha/hora con formato erróneo. Use uno de los siguientes formatos en su lugar: " +
            "YYYY-MM-DDThh:mm[:ss[.uuuuuu]][+HH:MM|-HH:MM|Z]."
    const val BOOLEANO_INVALIDO = "Debe ser un booleano válido."

    fun maximoDeCaracteres(n: Int) = "Asegúrese de que este campo no tenga más de $n caracteres."
    fun claveInexistente(clave: String) = "Clave primaria \"$clave\" inválida - objeto no existe."

    /** Con las comillas tipográficas de Django: «“abc” no es un UUID válido.». */
    fun uuidInvalido(texto: String) = "“$texto” no es un UUID válido."

    /** Los filtros de django-filter. */
    fun opcionNoValida(valor: String) = "Escoja una opción válida. $valor no es una de las opciones disponibles."
    const val OPCION_INEXISTENTE = "Escoja una opción válida. Esa opción no está entre las disponibles."

    /** El 404 de `get_object_or_404`: el nombre del modelo, en inglés y sin traducir. */
    fun noHayCoincidencia(modelo: String) = "No $modelo matches the given query."

    /** Cuerpo `null`. Sin traducir en DRF, y así se deja. */
    const val SIN_DATOS = "No data provided"

    /** El cuerpo no es un objeto JSON. `tipo` es el nombre del tipo en Python: list, str, int… */
    fun noEsUnDiccionario(tipo: String) = "Datos inválidos. Se esperaba un diccionario pero es un $tipo."

    fun tipoDeMedioIncompatible(tipo: String) = "Tipo de medio \"$tipo\" incompatible en la solicitud."

    /**
     * JSON ilegible. Django pone detrás el texto de la excepción de Python
     * («Expecting property name enclosed in double quotes: line 1 column 2
     * (char 1)»), que no se puede reproducir: aquí va el de Jackson. Ver
     * docs/DIFERENCIAS.md.
     */
    fun errorDeJson(detalle: String) = "JSON parse error - $detalle"
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
    /** 409 al aceptar una versión de las condiciones que ya no es la vigente. Va como `{"detail": …}`. */
    const val CONDICIONES_CAMBIARON = "Las condiciones cambiaron mientras las leías. Vuelve a cargarlas."
    const val CAMBIO_DE_CONTRASENA_REQUERIDO =
        "Debes elegir tu propia contraseña antes de usar el sistema."
}
