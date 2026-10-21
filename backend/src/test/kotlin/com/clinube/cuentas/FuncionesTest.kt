package com.clinube.cuentas

import com.clinube.cuentas.dominio.Funciones
import com.clinube.cuentas.dominio.Rol
import com.clinube.cuentas.dominio.Usuario
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Portada de `apps/accounts/tests_funciones.py`. */
class FuncionesTest {

    private fun usuario(rol: String, funciones: Map<String, Any?> = emptyMap(), superusuario: Boolean = false) =
        Usuario(rol = rol, funciones = funciones.toMutableMap(), esSuperusuario = superusuario)

    @Test
    fun `la administracion tiene todas, siempre`() {
        val todas = Funciones.de(usuario(Rol.ADMIN))
        assertEquals(Funciones.CATALOGO.keys, todas.keys)
        assertTrue(todas.values.all { it })
        // Y no se le pueden quitar guardando falsos.
        assertTrue(Funciones.de(usuario(Rol.ADMIN, mapOf("cobros" to false))).values.all { it })
    }

    @Test
    fun `un superusuario tambien`() {
        assertTrue(Funciones.de(usuario(Rol.DOCTOR, superusuario = true)).values.all { it })
    }

    @Test
    fun `quien no tiene nada guardado hereda lo de su rol`() {
        // La razón de que existan las HEREDADAS: actualizar no le quita
        // ni le da nada a nadie.
        val recepcion = Funciones.de(usuario(Rol.RECEPCION))
        assertTrue(recepcion["agenda"]!!)
        assertTrue(recepcion["cobros"]!!)
        assertTrue(recepcion["mensajes_app"]!!)
        assertFalse(recepcion["inventario"]!!)
        assertFalse(recepcion["finanzas"]!!)

        val doctor = Funciones.de(usuario(Rol.DOCTOR))
        assertTrue(doctor["mensajes_app"]!!)
        assertTrue(doctor["respaldo"]!!)
        assertFalse(doctor["agenda"]!!, "la agenda NO es heredada del doctor, solo se propone al crear")
    }

    @Test
    fun `lo guardado manda sobre lo heredado`() {
        val doctor = Funciones.de(usuario(Rol.DOCTOR, mapOf("agenda" to true, "respaldo" to false)))
        assertTrue(doctor["agenda"]!!)
        assertFalse(doctor["respaldo"]!!)
        // Y una clave que no está sigue tomando el valor heredado.
        assertTrue(doctor["mensajes_app"]!!)
    }

    @Test
    fun `una funcion que no aplica al rol queda apagada aunque se guarde`() {
        // «respaldo» lleva la historia clínica de sus pacientes: no tiene
        // sentido en recepción, que no ve datos clínicos.
        val recepcion = Funciones.de(usuario(Rol.RECEPCION, mapOf("respaldo" to true)))
        assertFalse(recepcion["respaldo"]!!)
        assertFalse(Funciones.aplica("respaldo", Rol.RECEPCION))
        assertTrue(Funciones.aplica("respaldo", Rol.DOCTOR))
    }

    @Test
    fun `un paciente no tiene ninguna funcion de gestion`() {
        assertTrue(Funciones.de(usuario(Rol.PACIENTE)).values.none { it })
        assertTrue(Funciones.de(usuario(Rol.SUPERADMIN)).values.none { it })
    }

    @Test
    fun `al crear se propone lo de AL_CREAR, no lo heredado`() {
        val doctor = Funciones.alCrear(Rol.DOCTOR)
        assertTrue(doctor["agenda"]!!, "en la mayoría de consultas el doctor agenda sus propias citas")
        assertTrue(doctor["logros"]!!)
        assertTrue(doctor["respaldo"]!!)
        assertFalse(doctor["finanzas"]!!)

        val recepcion = Funciones.alCrear(Rol.RECEPCION)
        assertFalse(recepcion["respaldo"]!!, "no aplica a recepción")
    }

    @Test
    fun `el orden del catalogo es el de Django`() {
        // El panel lo pinta en el orden en que llega.
        assertEquals(
            listOf("agenda", "cobros", "mensajes_app", "logros", "inventario",
                   "reportes", "finanzas", "whatsapp", "respaldo"),
            Funciones.CATALOGO.keys.toList(),
        )
        assertEquals(Funciones.CATALOGO.keys.toList(), Funciones.de(usuario(Rol.DOCTOR)).keys.toList())
    }
}
