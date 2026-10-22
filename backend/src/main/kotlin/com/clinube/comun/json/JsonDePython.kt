package com.clinube.comun.json

import com.clinube.comun.python.Python
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.util.RawValue

/**
 * Un valor de un `JSONField` como lo tendría Python: lo que llega en la
 * petición pasa por `json.loads` y lo que sale de la base, también. Los
 * números con decimales son `float` y se escriben con `repr()`: `1e-05`,
 * `1e+16`, `100.0`. Jackson escribiría `1.0E-5`, `1.0E16` y `100.0`.
 *
 * Así coinciden la respuesta y lo que queda guardado en la base: Django
 * guarda en `jsonb` el texto de `json.dumps`, y aquí se guarda el mismo.
 */
object JsonDePython {

    /** El mismo valor con cada número decimal ya escrito como Python. Las claves y su orden no cambian. */
    fun comoPython(valor: JsonNode): JsonNode = when {
        valor.isObject -> JsonNodeFactory.instance.objectNode().also { salida ->
            valor.properties().forEach { (clave, v) -> salida.set<JsonNode>(clave, comoPython(v)) }
        }
        valor.isArray -> JsonNodeFactory.instance.arrayNode().also { salida -> valor.forEach { salida.add(comoPython(it)) } }
        valor.isFloatingPointNumber -> JsonNodeFactory.instance.rawValueNode(RawValue(Python.reprFloat(valor.doubleValue())))
        else -> valor
    }

    /** Si hay un número que en Python sería infinito (`1e400`): DRF lo rechaza. */
    fun tieneInfinitos(valor: JsonNode): Boolean = when {
        valor.isContainerNode -> valor.any { tieneInfinitos(it) }
        valor.isFloatingPointNumber -> valor.doubleValue().isInfinite()
        else -> false
    }
}
