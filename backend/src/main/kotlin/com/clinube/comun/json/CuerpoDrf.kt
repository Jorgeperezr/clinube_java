package com.clinube.comun.json

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Lee el cuerpo de una petición con las reglas de un serializador de DRF.
 *
 * Los controladores reciben el cuerpo como `JsonNode` y no como una clase
 * de datos porque DRF distingue casos que Jackson junta en una sola
 * excepción, y cada uno tiene su respuesta. Todas se comprobaron contra
 * Django:
 *
 *     sin cuerpo        → los campos, «Este campo es requerido.»
 *     null              → non_field_errors: «No data provided»
 *     [1, 2]            → non_field_errors: «… se esperaba un diccionario pero es un list.»
 *     {"email": null}   → «Este campo no puede ser nulo.»
 *     {"email": ""}     → «Este campo no puede estar en blanco.»
 *     {"email": "   "}  → «Este campo no puede estar en blanco.» (se recorta)
 *     {"email": true}   → «No es una cadena válida.»
 *     {"email": 123}    → vale, como el texto "123"
 */
object CuerpoDrf {

    /** El cuerpo como objeto, o el error de DRF si no lo es. Sin cuerpo cuenta como `{}`. */
    fun objeto(cuerpo: JsonNode?): ObjectNode {
        if (cuerpo == null || cuerpo.isMissingNode) return JsonNodeFactory.instance.objectNode()
        if (cuerpo.isNull) throw ExcepcionApi.validacionGeneral(MensajesDeDjango.SIN_DATOS)
        if (!cuerpo.isObject) throw ExcepcionApi.validacionGeneral(MensajesDeDjango.noEsUnDiccionario(tipoEnPython(cuerpo)))
        return cuerpo as ObjectNode
    }

    /** El nombre que tendría ese valor en Python, que es lo que DRF pone en el mensaje. */
    fun tipoEnPython(valor: JsonNode): String = when {
        valor.isArray -> "list"
        valor.isTextual -> "str"
        valor.isBoolean -> "bool"
        valor.isIntegralNumber -> "int"
        valor.isFloatingPointNumber -> "float"
        valor.isNull -> "NoneType"
        else -> "dict"
    }
}

/**
 * Los campos de un serializador, validados todos antes de responder: DRF
 * devuelve los errores de TODOS los campos a la vez, en el orden en que se
 * declararon, no el primero que encuentra.
 *
 *     val campos = CamposDrf(CuerpoDrf.objeto(cuerpo))
 *     val correo = campos.texto("email")
 *     val clave = campos.texto("password")
 *     campos.validar()   // 400 con los que fallaron
 *     // aquí correo y clave ya no son nulos
 */
class CamposDrf(private val datos: ObjectNode) {

    private val errores = LinkedHashMap<String, List<String>>()

    /**
     * Un `CharField` obligatorio, sin blancos ni nulos y recortando los
     * espacios, que es lo que trae DRF por defecto. Ojo: también recorta la
     * contraseña (`PasswordField` de SimpleJWT no lo desactiva), así que una
     * contraseña con un espacio al final entra sin él. Así funciona Django
     * y así tiene que funcionar aquí.
     */
    fun texto(campo: String): String? {
        val valor = datos.get(campo)
        val motivo = when {
            valor == null || valor.isMissingNode -> MensajesDeDjango.CAMPO_REQUERIDO
            valor.isNull -> MensajesDeDjango.CAMPO_NULO
            valor.isBoolean || valor.isContainerNode -> MensajesDeDjango.CADENA_INVALIDA
            valor.asText().trim().isEmpty() -> MensajesDeDjango.CAMPO_EN_BLANCO
            else -> null
        }
        if (motivo != null) {
            errores[campo] = listOf(motivo)
            return null
        }
        return valor!!.asText().trim()
    }

    /** 400 con los errores de todos los campos, si hubo alguno. */
    fun validar() {
        if (errores.isNotEmpty()) throw ExcepcionApi.validacion(errores)
    }
}
