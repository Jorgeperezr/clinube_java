package com.clinube.comun

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.json.CamposDrf.Leido
import com.clinube.comun.json.JsonDePython
import com.clinube.comun.python.Python
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los campos de `CamposDrf` hacen lo mismo que los de DRF con cualquier
 * valor: los casos (unos 7000, con números, textos raros, fechas, listas…)
 * los calculó el DRF de clinube con `scripts/casos-de-campos.py`.
 */
class CamposDrfTest {

    private val json = ObjectMapper()

    /** Lee el campo como lo declara el serializador de Django y devuelve el valor ya escrito en JSON. */
    private fun leer(nombre: String, campos: CamposDrf): Leido<String> {
        fun <T> escrito(leido: Leido<T>, escribir: (T) -> Any?): Leido<String> = when (leido) {
            is Leido.Ausente -> Leido.Ausente
            is Leido.Valor -> Leido.Valor(json.writeValueAsString(leido.valor?.let(escribir)))
        }
        return when (nombre) {
            "texto" -> escrito(campos.texto(nombre, maximo = 15, requerido = false, enBlanco = true)) { it }
            "texto_obligatorio" -> escrito(campos.texto(nombre, maximo = null)) { it }
            "texto_corto" -> escrito(campos.texto(nombre, maximo = 2, requerido = false, enBlanco = true)) { it }
            "correo" -> escrito(campos.texto(nombre, maximo = 254, requerido = false, enBlanco = true, correo = true)) { it }
            "decimal" -> escrito(campos.decimal(nombre, maxDigitos = 4, decimales = 1, nulo = true)) { it.texto }
            "entero" -> escrito(campos.entero(nombre, minimo = 0, maximo = 32767, nulo = true)) { it }
            "booleano_nulo" -> escrito(campos.booleano(nombre, nulo = true)) { it }
            "json" -> escrito(campos.json(nombre)) { JsonDePython.comoPython(it) }
            "opcion" -> escrito(campos.opcion(nombre, listOf("pre", "def"))) { it }
            "fecha" -> escrito(campos.fecha(nombre)) { it.toString() }
            else -> error("campo desconocido: $nombre")
        }
    }

    /** Jackson escribe `\u001F` y Python `\u001f`: lo mismo para quien lo lee, distinto al compararlo. */
    private fun minusculas(texto: String) = Regex("""\\u([0-9A-F]{4})""").replace(texto) { "\\u" + it.groupValues[1].lowercase() }

    @Test
    fun `cada campo da el valor o los errores de DRF`() {
        val casos = json.readTree(javaClass.getResource("/campos/casos.json"))
        assertTrue(casos.size() > 5000)
        val distintos = mutableListOf<String>()
        for (caso in casos) {
            val nombre = caso["campo"].textValue()
            val datos = JsonNodeFactory.instance.objectNode()
            caso["entrada"]?.let { datos.set<JsonNode>(nombre, json.readTree(it.textValue())) }
            val campos = CamposDrf(datos)
            val leido = leer(nombre, campos)
            val errores = runCatching { campos.validar() }.exceptionOrNull()
                ?.let { (it as ExcepcionApi).detalles[nombre] as List<*> }
            val obtenido = when {
                errores != null -> "errores $errores"
                leido is Leido.Valor -> "salida ${minusculas(leido.valor!!)}"
                else -> "ausente"
            }
            val esperado = when {
                caso.has("errores") -> "errores ${caso["errores"].map { it.textValue() }}"
                caso.has("salida") -> "salida ${caso["salida"].textValue()}"
                else -> "ausente"
            }
            if (obtenido != esperado) distintos += "$nombre ${caso["entrada"]}: Django $esperado, Kotlin $obtenido"
        }
        assertEquals(emptyList(), distintos.take(15), "${distintos.size} casos distintos de DRF")
    }

    @Test
    fun `los floats se escriben como en Python`() {
        val casos = mapOf(
            1e16 to "1e+16", 1e15 to "1000000000000000.0", 1e-4 to "0.0001", 1e-5 to "1e-05", -0.0 to "-0.0",
            0.1 to "0.1", 100.0 to "100.0", 1.5e300 to "1.5e+300", 123456789.123 to "123456789.123",
            5e-324 to "5e-324", Double.MAX_VALUE to "1.7976931348623157e+308",
        )
        casos.forEach { (x, esperado) -> assertEquals(esperado, Python.reprFloat(x)) }
    }
}
