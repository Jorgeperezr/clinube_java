package com.clinube.clinica

import com.clinube.clinica.ReglasDeAlertas.Antecedentes
import com.clinube.clinica.ReglasDeAlertas.Formulario033
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las reglas de las alertas dan EXACTAMENTE lo que da el `alertas.py` de
 * Django. Los 3000 casos (los de tests_alertas.py y textos al azar con
 * negaciones, tildes, espacios de Unicode y casillas del formulario 033 de
 * todos los tipos) los calculó Django: `scripts/casos-de-alertas.py`.
 */
class ReglasDeAlertasTest {

    private val json = ObjectMapper()

    private fun texto(nodo: JsonNode?) = nodo?.takeUnless { it.isNull }?.textValue()

    private fun lista(nodo: JsonNode): List<Map<String, String>> =
        nodo.map { a -> linkedMapOf<String, String>().apply { a.properties().forEach { (k, v) -> put(k, v.textValue()) } } }

    @Test
    fun `las alertas y los choques son los de Django en todos los casos`() {
        val casos = json.readTree(javaClass.getResource("/alertas/casos.json"))
        assertTrue(casos.size() >= 3000)
        val distintos = mutableListOf<String>()
        var recetas = 0
        for ((i, caso) in casos.withIndex()) {
            val fondo = caso["fondo"].takeUnless { it.isNull }?.let {
                Antecedentes(texto(it["alergias"])!!, texto(it["medicacion"])!!, texto(it["condiciones"])!!,
                    it["embarazada"].takeUnless { e -> e.isNull }?.booleanValue())
            }
            val form033 = caso["form033"].takeUnless { it.isNull }?.let {
                Formulario033(it["antecedentes_personales"], it["embarazada"].takeUnless { e -> e.isNull }?.booleanValue())
            }
            val alertas = ReglasDeAlertas.deAntecedentes(fondo, form033)
            if (alertas != lista(caso["alertas"])) distintos += "caso $i: $caso\n  Kotlin: $alertas"
            for (receta in caso["recetas"]) {
                recetas++
                val choques = ReglasDeAlertas.revisarReceta(receta["texto"].textValue(), alertas)
                if (choques != lista(receta["choques"])) distintos += "caso $i, receta ${receta["texto"]}\n  Kotlin: $choques"
            }
        }
        assertTrue(recetas > 1000)
        assertEquals(emptyList(), distintos.take(5), "${distintos.size} casos distintos de Django")
    }

    @Test
    fun `una negacion solo cuenta al principio de la frase`() {
        val claves = ReglasDeAlertas.deAntecedentes(Antecedentes("Niega alergia al látex. Alérgica al ibuprofeno", "", "", null))
            .map { it["clave"] }
        assertEquals(listOf("alergia_aine"), claves)
        // «nosotros», «sinusitis»: «no» y «sin» solo niegan como palabra entera.
        assertEquals(listOf("alergia_latex"),
            ReglasDeAlertas.deAntecedentes(Antecedentes("sinusitis y látex", "", "", null)).map { it["clave"] })
    }

    @Test
    fun `la palabra de Python incluye superindices y no los signos de union`() {
        // «²» es alfanumérico en Python (y no en el \w de Java): no hay límite de palabra.
        assertEquals(emptyList(), ReglasDeAlertas.deAntecedentes(Antecedentes("²latex", "", "", null)))
        // «‿» une palabras en el \w de Java, pero no en Python: sí hay límite.
        assertEquals(1, ReglasDeAlertas.deAntecedentes(Antecedentes("‿latex", "", "", null)).size)
    }
}
