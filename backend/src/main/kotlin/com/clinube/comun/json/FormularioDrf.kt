package com.clinube.comun.json

import com.clinube.comun.archivos.AlmacenDeArchivos
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.paginacion.Pagina
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.Part
import org.springframework.http.HttpStatus

/**
 * El cuerpo de una petición tal como lo leen los tres analizadores de DRF
 * (`JSONParser`, `MultiPartParser`, `FormParser`), para las rutas que, como
 * la de los egresos, reciben formularios con un archivo. El resto de rutas
 * solo acepta JSON (ver docs/DIFERENCIAS.md).
 *
 * `html` dice si llegó como formulario: DRF lee entonces distinto los
 * vacíos y las casillas (ver [CamposDrf]).
 */
class FormularioDrf private constructor(
    val datos: JsonNode,
    val html: Boolean,
    val archivos: Map<String, Archivo>,
) {

    class Archivo(val nombre: String, val tamano: Long, private val parte: Part) {
        fun contenido() = parte.inputStream
    }

    /** Los campos como objeto, o el error de DRF si el JSON no es un diccionario. */
    fun objeto(): ObjectNode = CuerpoDrf.objeto(datos)

    /** Las claves de `request.data`: con los archivos, que DRF mezcla con los campos. */
    fun claves(): List<String> = (datos.fieldNames().asSequence().toList() + archivos.keys).distinct()

    companion object {
        fun leer(peticion: HttpServletRequest, json: ObjectMapper): FormularioDrf {
            val tipo = peticion.contentType
            val medio = tipo?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when {
                medio == "application/json" || medio.endsWith("+json") -> {
                    val texto = peticion.inputStream.readBytes().toString(Charsets.UTF_8)
                    val datos = if (texto.isEmpty()) JsonNodeFactory.instance.objectNode() else try {
                        json.readTree(texto)
                    } catch (e: JsonProcessingException) {
                        throw ExcepcionApi(HttpStatus.BAD_REQUEST, "parse_error", MensajesDeDjango.errorDeJson(e.originalMessage), emptyMap())
                    }
                    FormularioDrf(datos, false, emptyMap())
                }
                medio == "multipart/form-data" -> {
                    val datos = JsonNodeFactory.instance.objectNode()
                    val archivos = LinkedHashMap<String, Archivo>()
                    for (parte in peticion.parts) {
                        val original = parte.submittedFileName
                        if (original == null) {
                            datos.put(parte.name, parte.inputStream.readBytes().toString(Charsets.UTF_8))
                        } else {
                            // Un archivo sin nombre (el campo vacío) Django no lo da por recibido.
                            val nombre = AlmacenDeArchivos.nombreSubido(original) ?: continue
                            archivos[parte.name] = Archivo(nombre, parte.size, parte)
                        }
                    }
                    FormularioDrf(datos, true, archivos)
                }
                medio == "application/x-www-form-urlencoded" -> {
                    val datos = JsonNodeFactory.instance.objectNode()
                    Pagina.leerConsulta(peticion.inputStream.readBytes().toString(Charsets.UTF_8))
                        .forEach { (clave, valores) -> datos.put(clave, valores.last()) }
                    FormularioDrf(datos, true, emptyMap())
                }
                // Sin tipo y sin cuerpo, DRF lee un diccionario vacío.
                tipo.isNullOrEmpty() && peticion.contentLengthLong <= 0 -> FormularioDrf(JsonNodeFactory.instance.objectNode(), false, emptyMap())
                else -> throw ExcepcionApi(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", MensajesDeDjango.tipoDeMedioIncompatible(tipo ?: ""), emptyMap(),
                )
            }
        }
    }
}
