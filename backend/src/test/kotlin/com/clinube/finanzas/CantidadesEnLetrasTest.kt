package com.clinube.finanzas

import com.clinube.finanzas.DocumentosDeFinanzas.Companion.dinero
import com.clinube.finanzas.DocumentosDeFinanzas.Companion.enLetras
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/** `en_letras` y `dinero` de `documentos.py`: lo que va escrito en los recibos. */
class CantidadesEnLetrasTest {

    @Test
    fun `las cantidades en letras de los recibos`() {
        mapOf(
            "0" to "Cero con 00/100 dólares",
            "1" to "Un con 00/100 dólares",
            "21" to "Veintiún con 00/100 dólares",
            "100" to "Cien con 00/100 dólares",
            "101" to "Ciento un con 00/100 dólares",
            "121.50" to "Ciento veintiún con 50/100 dólares",
            "345.67" to "Trescientos cuarenta y cinco con 67/100 dólares",
            "1000" to "Mil con 00/100 dólares",
            "1001" to "Mil un con 00/100 dólares",
            "21000.05" to "Veintiún mil con 05/100 dólares",
            "31500" to "Treinta y un mil quinientos con 00/100 dólares",
            "999999.99" to "Novecientos noventa y nueve mil novecientos noventa y nueve con 99/100 dólares",
            "-45" to "Cuarenta y cinco con 00/100 dólares",
            // `quantize` sin redondeo propio: a la par, como el contexto de Python.
            "2.345" to "Dos con 34/100 dólares",
        ).forEach { (cifra, letras) -> assertEquals(letras, enLetras(BigDecimal(cifra)), cifra) }
    }

    @Test
    fun `los importes se escriben como en Ecuador`() {
        assertEquals("$1.234,56", dinero(BigDecimal("1234.56")))
        assertEquals("$1.234.567,00", dinero(BigDecimal("1234567")))
        assertEquals("-$5,00", dinero(BigDecimal("-5")))
        assertEquals("$0,00", dinero(null as BigDecimal?))
        assertEquals("$999,00", dinero("999"))
    }
}
