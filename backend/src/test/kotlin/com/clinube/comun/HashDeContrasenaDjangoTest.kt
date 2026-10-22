package com.clinube.comun

import com.clinube.comun.seguridad.HashDeContrasenaDjango
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La prueba que decide si los usuarios que YA existen pueden entrar.
 *
 * El hash de `HASH_DE_DJANGO` no está inventado: lo generó Django 5.2.17
 * para la contraseña de abajo, en la base de la clínica demo. Si esta
 * prueba falla, nadie creado por Django entra en el servidor de Kotlin, y
 * eso hace inútil todo lo demás.
 */
class HashDeContrasenaDjangoTest {

    private val hash = HashDeContrasenaDjango()

    companion object {
        /** Creado por `manage.py bootstrap` de clinube. Un millón de iteraciones. */
        const val HASH_DE_DJANGO =
            "pbkdf2_sha256\$1000000\$5nzb8mbtEDCsQIIMppAK3o\$G1XYgABbEZjed831acNhDO/p6AI6bTWbm+Y9o1vjVns="
        const val CONTRASENA = "OdontoDemo2026"
    }

    @Test
    fun `verifica un hash hecho por Django`() {
        assertTrue(hash.verificar(CONTRASENA, HASH_DE_DJANGO))
    }

    @Test
    fun `rechaza la contraseña equivocada`() {
        assertFalse(hash.verificar("OdontoDemo2027", HASH_DE_DJANGO))
        assertFalse(hash.verificar("", HASH_DE_DJANGO))
        assertFalse(hash.verificar(CONTRASENA.uppercase(), HASH_DE_DJANGO))
    }

    @Test
    fun `la contrasena vacia se calcula como en Python, sin reventar`() {
        // `SecretKeySpec` de Java rechaza una clave vacía, y esta prueba
        // reventaba con «Empty key». El hash es de `hashlib.pbkdf2_hmac`
        // con contraseña vacía: tiene que verificar, no fallar.
        val deContrasenaVacia =
            "pbkdf2_sha256\$1000\$salDePrueba0123456789a\$y6nuYAOxmwA37d5qMJOfH8lOLqQ82n6Puu3saPvEqcM="
        assertTrue(hash.verificar("", deContrasenaVacia))
        assertFalse(hash.verificar("x", deContrasenaVacia))
    }

    @Test
    fun `lo que genera lo vuelve a verificar`() {
        val generado = hash.generar("otra contraseña larga")
        assertTrue(hash.verificar("otra contraseña larga", generado))
        assertFalse(hash.verificar("otra contrasena larga", generado))
    }

    @Test
    fun `genera el formato que Django sabe leer`() {
        val partes = hash.generar("cualquiera que sirva").split("$")
        assertEquals(4, partes.size)
        assertEquals("pbkdf2_sha256", partes[0])
        assertEquals(HashDeContrasenaDjango.ITERACIONES.toString(), partes[1])
        assertEquals(22, partes[2].length, "la sal de Django tiene 22 caracteres")
        // 32 bytes en base64: 44 caracteres con el relleno.
        assertEquals(44, partes[3].length)
    }

    @Test
    fun `respeta las iteraciones de cada hash y no las suyas`() {
        // Conviven hashes de versiones distintas de Django: uno creado
        // hace dos años puede llevar 390.000 iteraciones en vez del
        // millón de ahora. Si se usaran siempre las de ahora, esa persona
        // no entraría nunca. Los dos hashes son de verdad, calculados con
        // `hashlib.pbkdf2_hmac` para la MISMA contraseña.
        val deMilIteraciones =
            "pbkdf2_sha256\$1000\$sal12345678901234567x\$OmDg/TX/wFD33xtqiaFQyjWJ6DYUzlQDZd1a1iLd0nU="
        val deTrescientasNoventaMil =
            "pbkdf2_sha256\$390000\$otraSal0123456789abcde\$MG921d6yVYPnuOkvDLklLJ8cUTvCisK3DHWOGZTStxM="

        assertTrue(hash.verificar(CONTRASENA, deMilIteraciones))
        assertTrue(hash.verificar(CONTRASENA, deTrescientasNoventaMil))
        assertTrue(hash.verificar(CONTRASENA, HASH_DE_DJANGO))

        // Y la contraseña equivocada sigue sin entrar en ninguno.
        assertFalse(hash.verificar("otra", deMilIteraciones))
        assertFalse(hash.verificar("otra", deTrescientasNoventaMil))
    }

    @Test
    fun `una contraseña inutilizable nunca entra`() {
        // Django guarda `!` y 40 caracteres al azar en las cuentas que
        // solo entran por otro camino: los pacientes, con su código.
        val inutilizable = hash.inutilizable()
        assertTrue(inutilizable.startsWith("!"))
        assertTrue(hash.esInutilizable(inutilizable))
        assertFalse(hash.verificar(inutilizable.substring(1), inutilizable))
        assertFalse(hash.verificar("", inutilizable))
    }

    @Test
    fun `no revienta con un hash malformado`() {
        assertFalse(hash.verificar(CONTRASENA, null))
        assertFalse(hash.verificar(CONTRASENA, ""))
        assertFalse(hash.verificar(CONTRASENA, "basura"))
        assertFalse(hash.verificar(CONTRASENA, "pbkdf2_sha256\$abc\$sal\$hash"))
        assertFalse(hash.verificar(CONTRASENA, "argon2\$argon2id\$v=19\$m=102400"))
    }
}
