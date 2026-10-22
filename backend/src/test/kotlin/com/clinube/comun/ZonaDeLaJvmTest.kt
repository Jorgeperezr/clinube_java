package com.clinube.comun

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * La suite pasa dos veces, en UTC y en Pacific/Kiritimati (UTC+14), para
 * que un error que confunda «instante» con «día de la clínica» no pase por
 * casualidad. Esta prueba comprueba que la JVM de las pruebas corre de
 * verdad en la zona pedida: sin ella, la segunda pasada podría estar
 * corriendo en la zona de la máquina —que en la Mac de desarrollo es la
 * misma de la clínica— y nadie lo notaría.
 */
class ZonaDeLaJvmTest {

    @Test
    fun `la JVM de las pruebas corre en la zona pedida, y no en la de la clinica`() {
        val pedida = System.getProperty("clinube.zonaPruebas")
        assumeTrue(pedida != null, "ejecutada fuera de Gradle: no hay zona pedida")
        assertEquals(pedida, TimeZone.getDefault().id)
        assertNotEquals(
            "America/Guayaquil", TimeZone.getDefault().id,
            "en la zona de la clínica, los errores de conversión de fechas pasan por casualidad",
        )
    }
}
