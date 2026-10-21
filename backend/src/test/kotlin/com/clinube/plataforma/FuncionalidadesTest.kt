package com.clinube.plataforma

import com.clinube.clinicas.Clinica
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Portada de `apps/common/tests_funcionalidades.py`. */
class FuncionalidadesTest {

    private fun clinica(
        funcionalidades: Map<String, Any?> = emptyMap(),
        modulos: Map<String, Any?> = emptyMap(),
        prueba: Boolean = false,
    ) = Clinica(
        funcionalidades = funcionalidades.toMutableMap(),
        modulosDeLaClinica = modulos.toMutableMap(),
        esDePruebas = prueba,
    )

    @Test
    fun `el orden del catalogo es el de Django`() {
        assertEquals(
            listOf("inventario", "whatsapp", "convenios", "app_paciente", "formulario_033",
                   "reportes", "temas_personales", "finanzas", "odontograma_3d", "logros"),
            Funcionalidades.CATALOGO.keys.toList(),
        )
    }

    @Test
    fun `una clinica nueva trae las incluidas y no las adicionales`() {
        val n = Funcionalidades.normalizar(null)
        assertTrue(n["inventario"]!!)
        assertTrue(n["convenios"]!!)
        assertTrue(n["app_paciente"]!!)
        assertFalse(n["whatsapp"]!!, "WhatsApp se contrata aparte")
        assertFalse(n["odontograma_3d"]!!)
        assertFalse(n["logros"]!!)
    }

    @Test
    fun `las claves desconocidas se tiran y las que faltan toman su defecto`() {
        // Añadir una funcionalidad nueva no obliga a migrar los datos de
        // nadie: una «incluida» llega encendida en cuanto se despliega.
        val n = Funcionalidades.normalizar(mapOf("ya_no_existe" to true))
        assertEquals(Funcionalidades.CATALOGO.keys, n.keys)
        assertTrue(n["inventario"]!!)
    }

    @Test
    fun `una funcion en prueba solo existe en una clinica de prueba`() {
        val enPrueba = setOf("temas_personales", "finanzas")
        val normal = Funcionalidades.contratadas(clinica(mapOf("finanzas" to true)), enPrueba)
        assertFalse(normal["finanzas"]!!, "marcada, pero todavía no publicada")

        val deLaboratorio = Funcionalidades.contratadas(clinica(mapOf("finanzas" to true), prueba = true), enPrueba)
        assertTrue(deLaboratorio["finanzas"]!!)
    }

    @Test
    fun `lo marcado se conserva y vale en cuanto se publica`() {
        val clinica = clinica(mapOf("finanzas" to true))
        assertFalse(Funcionalidades.contratadas(clinica, setOf("finanzas"))["finanzas"]!!)
        // Publicada: ya no está en prueba.
        assertTrue(Funcionalidades.contratadas(clinica, emptySet())["finanzas"]!!)
    }

    @Test
    fun `la clinica puede apagar lo que tiene, pero no encender lo que no`() {
        // Dos llaves, cada una de quien le toca: la plataforma decide qué
        // se puede usar y la clínica, qué quiere usar.
        val apagado = Funcionalidades.efectivas(
            clinica(modulos = mapOf("inventario" to false)), emptySet(),
        )
        assertFalse(apagado["inventario"]!!, "contratado, pero la clínica no lo usa")

        val intentoDeEncender = Funcionalidades.efectivas(
            clinica(modulos = mapOf("logros" to true)), emptySet(),
        )
        assertFalse(intentoDeEncender["logros"]!!, "la clínica no puede darse de alta algo que no tiene")
    }

    @Test
    fun `sin clinica, todo apagado`() {
        // El Super Administrador opera sobre la plataforma, no dentro de
        // una clínica.
        val ninguna = Funcionalidades.efectivas(null, emptySet())
        assertEquals(Funcionalidades.CATALOGO.keys, ninguna.keys)
        assertTrue(ninguna.values.none { it })
    }
}
