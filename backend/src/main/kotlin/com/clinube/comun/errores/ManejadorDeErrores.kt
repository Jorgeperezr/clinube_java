package com.clinube.comun.errores

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException

/**
 * Pone todo error de la API en la forma de Django. Es el equivalente de
 * `apps/common/exceptions.custom_exception_handler`.
 *
 * Hay DOS formas en la API de Django y las dos se respetan:
 *
 *   · `{"error": {code, message, details}}` — lo que sale de una excepción
 *     de DRF, que es la mayoría.
 *   · `{"detail": "..."}` — lo que devuelve una vista que construye su
 *     propia respuesta con `Response({"detail": ...}, status=...)`. Eso no
 *     pasa por aquí: lo devuelve el controlador, igual que en Django.
 */
@RestControllerAdvice
class ManejadorDeErrores {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ExcepcionApi::class)
    fun deLaApi(e: ExcepcionApi): ResponseEntity<CuerpoDeError> =
        respuesta(e.estado, e.codigo, e.mensaje, e.detalles)

    /** Campos que faltan o no valen: el motivo va en `details`, campo a campo. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun deValidacion(e: MethodArgumentNotValidException): ResponseEntity<CuerpoDeError> {
        val detalles = LinkedHashMap<String, Any?>()
        e.bindingResult.fieldErrors.forEach { error ->
            val mensajes = detalles.getOrPut(error.field) { mutableListOf<String>() }
            @Suppress("UNCHECKED_CAST")
            (mensajes as MutableList<String>).add(error.defaultMessage ?: MensajesDeDjango.CAMPO_REQUERIDO)
        }
        return respuesta(HttpStatus.BAD_REQUEST, "invalid", MensajesDeDjango.ERROR_GENERICO, detalles)
    }

    /**
     * Cuerpo ilegible o ausente. Django, con un POST sin cuerpo, se queja
     * de los campos que faltan, no del formato: el serializador ve un
     * diccionario vacío. Se hace lo mismo para que el panel reciba lo que
     * espera.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun deCuerpoIlegible(e: HttpMessageNotReadableException): ResponseEntity<CuerpoDeError> =
        respuesta(
            HttpStatus.BAD_REQUEST, "parse_error",
            "Petición malformada.", emptyMap(),
        )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun deMetodo(e: HttpRequestMethodNotSupportedException): ResponseEntity<CuerpoDeError> =
        respuesta(
            HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
            MensajesDeDjango.metodoNoPermitido(e.method), emptyMap(),
        )

    @ExceptionHandler(NoHandlerFoundException::class)
    fun deRutaDesconocida(e: NoHandlerFoundException): ResponseEntity<CuerpoDeError> =
        respuesta(HttpStatus.NOT_FOUND, "not_found", MensajesDeDjango.NO_ENCONTRADO, emptyMap())

    /**
     * Lo que no estaba previsto. Se registra con la traza —en Django esto
     * no existía y un 500 no dejaba rastro— y se responde un 500 sin
     * contar nada de dentro.
     */
    @ExceptionHandler(Exception::class)
    fun deLoInesperado(e: Exception, peticion: HttpServletRequest): ResponseEntity<CuerpoDeError> {
        log.error("Excepción no controlada en {} {}", peticion.method, peticion.requestURI, e)
        return respuesta(
            HttpStatus.INTERNAL_SERVER_ERROR, "error",
            "Ocurrió un error interno en el servidor.", emptyMap(),
        )
    }

    private fun respuesta(
        estado: HttpStatus,
        codigo: String,
        mensaje: String,
        detalles: Map<String, Any?>,
    ) = ResponseEntity.status(estado).body(CuerpoDeError(CuerpoDeError.Detalle(codigo, mensaje, detalles)))
}

/** El cuerpo tal como lo escribe Django: las claves en inglés y en este orden. */
data class CuerpoDeError(val error: Detalle) {
    data class Detalle(
        val code: String,
        val message: String,
        val details: Map<String, Any?>,
    )
}
