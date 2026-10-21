package com.clinube.cuentas

import com.clinube.cuentas.dominio.Preferencias
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreferenciasTest {

    @Test
    fun `sin nada guardado salen los valores por defecto, en el orden de Django`() {
        val p = Preferencias.normalizar(null)
        assertEquals(listOf("odontograma_3d", "acento", "barra_oculta", "tema"), p.keys.toList())
        assertEquals(true, p["odontograma_3d"])
        assertEquals("", p["acento"])
        assertEquals(emptyList<String>(), p["barra_oculta"])
        assertEquals("", p["tema"])
    }

    @Test
    fun `las claves desconocidas se tiran`() {
        // Así, quitar una preferencia del catálogo no necesita migración.
        val p = Preferencias.normalizar(mapOf("inventada" to true, "odontograma_3d" to false))
        assertEquals(listOf("odontograma_3d", "acento", "barra_oculta", "tema"), p.keys.toList())
        assertEquals(false, p["odontograma_3d"])
    }

    @Test
    fun `un valor con mala forma vuelve a su valor por defecto, sin reventar`() {
        // Lo guardado puede venir de una versión anterior del panel. Una
        // preferencia mal formada no puede tirar `/auth/me/`.
        val p = Preferencias.normalizar(
            mapOf("acento" to 42, "tema" to "lo-que-sea", "barra_oculta" to "no-es-una-lista"),
        )
        assertEquals("", p["acento"])
        assertEquals("", p["tema"])
        assertEquals(emptyList<String>(), p["barra_oculta"])
    }

    @Test
    fun `la barra oculta se ordena y se quitan los repetidos`() {
        val p = Preferencias.normalizar(mapOf("barra_oculta" to listOf("pagos", "agenda", "pagos")))
        assertEquals(listOf("agenda", "pagos"), p["barra_oculta"])
    }

    @Test
    fun `el tema admite solo los dos origenes`() {
        assertEquals("clinica:menta", Preferencias.validarApariencia("tema", "clinica:menta"))
        assertEquals("clinube:oscuro", Preferencias.validarApariencia("tema", "clinube:oscuro"))
        assertEquals("", Preferencias.validarApariencia("tema", ""))
        assertFailsWith<Preferencias.ValorInvalido> {
            Preferencias.validarApariencia("tema", "otra:cosa")
        }
    }

    @Test
    fun `no se pueden ocultar mas secciones que el maximo`() {
        val demasiadas = (1..Preferencias.MAX_OCULTAS + 1).map { "seccion_$it" }
        assertFailsWith<Preferencias.ValorInvalido> {
            Preferencias.validarApariencia("barra_oculta", demasiadas)
        }
    }

    @Test
    fun `una preferencia que no existe se rechaza`() {
        assertFailsWith<Preferencias.ValorInvalido> {
            Preferencias.validarApariencia("inventada", "x")
        }
    }
}
