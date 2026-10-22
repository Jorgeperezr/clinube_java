package com.clinube.comun.errores

import com.fasterxml.jackson.core.JsonProcessingException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

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
 *
 * Los textos son los que responde Django, capturados; ver
 * `MensajesDeDjango`.
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
        val detalles = LinkedHashMap<String, MutableList<String>>()
        e.bindingResult.fieldErrors.forEach { error ->
            detalles.getOrPut(error.field) { mutableListOf() }
                .add(error.defaultMessage ?: MensajesDeDjango.CAMPO_REQUERIDO)
        }
        return respuesta(HttpStatus.BAD_REQUEST, "invalid", MensajesDeDjango.ERROR_GENERICO, detalles)
    }

    /**
     * JSON ilegible: 400 `parse_error`, como el `JSONParser` de DRF. El
     * texto de detrás es el de Jackson, no el de Python; ver
     * docs/DIFERENCIAS.md. Que el JSON sea legible pero no un objeto lo
     * resuelve `CuerpoDrf`, porque ahí DRF responde otra cosa.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun deCuerpoIlegible(e: HttpMessageNotReadableException): ResponseEntity<CuerpoDeError> {
        val detalle = (e.cause as? JsonProcessingException)?.originalMessage ?: e.mostSpecificCause.message ?: ""
        return respuesta(HttpStatus.BAD_REQUEST, "parse_error", MensajesDeDjango.errorDeJson(detalle), emptyMap())
    }

    /**
     * 415 con el tipo TAL COMO LLEGÓ en la cabecera, que es lo que pone
     * Django (`request.content_type`). Spring lo normaliza a su manera
     * (`text/plain;charset=UTF-8`) y el texto no coincidiría.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun deTipoDeMedio(e: HttpMediaTypeNotSupportedException, peticion: HttpServletRequest): ResponseEntity<CuerpoDeError> {
        val tipo = peticion.contentType ?: e.contentType?.toString() ?: ""
        return respuesta(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
            MensajesDeDjango.tipoDeMedioIncompatible(tipo), emptyMap(),
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun deMetodo(e: HttpRequestMethodNotSupportedException): ResponseEntity<CuerpoDeError> =
        respuesta(
            HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
            MensajesDeDjango.metodoNoPermitido(e.method), emptyMap(),
        )

    /**
     * Ruta desconocida. Desde Spring Boot 3.2 llega como
     * `NoResourceFoundException` (la busca el manejador de recursos
     * estáticos); sin esto caía en el manejador genérico y respondía 500.
     * Django responde aquí una página HTML de 404; ver docs/DIFERENCIAS.md.
     */
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun deRutaDesconocida(e: Exception): ResponseEntity<CuerpoDeError> =
        respuesta(HttpStatus.NOT_FOUND, "not_found", MensajesDeDjango.NO_ENCONTRADO, emptyMap())

    /**
     * Lo que no estaba previsto. Las excepciones de Spring que ya saben su
     * código de estado (`ErrorResponse`) lo conservan; las demás son un 500,
     * que se registra con la traza —en Django esto no existía y un 500 no
     * dejaba rastro— y se responde sin contar nada de dentro.
     */
    @ExceptionHandler(Exception::class)
    fun deLoInesperado(e: Exception, peticion: HttpServletRequest): ResponseEntity<CuerpoDeError> {
        if (e is ErrorResponse) {
            val estado = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.BAD_REQUEST
            if (estado.is4xxClientError) {
                return respuesta(estado, "error", MensajesDeDjango.ERROR_GENERICO, emptyMap())
            }
        }
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
