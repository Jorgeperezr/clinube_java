package com.clinube.comun.tiempo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Cómo lee las fechas DRF, que es como las lee Python. Lo que el panel
 * manda (`toISOString()`) lo lee cualquiera; lo que importa es lo demás,
 * comprobado contra Django:
 *
 *   · `DateField` acepta solo `AAAA-MM-DD`: con hora es «formato erróneo».
 *   · `DateTimeField` usa `datetime.fromisoformat` de Python 3.12: el
 *     separador entre fecha y hora puede ser CUALQUIER carácter
 *     (`2030-01-02X10:00`), la zona puede ir `Z`, `+05:30` o `+0530`, los
 *     decimales se truncan a seis y vale incluso solo la fecha (medianoche).
 *   · Sin zona, la hora es de la clínica (`make_aware` con `TIME_ZONE`).
 *
 * Los formatos ISO «básicos» sin guiones (`20300102T1000`) que también
 * acepta Python no se aceptan aquí: ni el panel ni la app los mandan. Ver
 * docs/DIFERENCIAS.md.
 */
object FechasDePython {

    private val FECHA = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val FECHA_HORA = Regex(
        """^(\d{4})-(\d{2})-(\d{2})(?:.(\d{2})(?::(\d{2})(?::(\d{2})(?:[.,](\d+))?)?)?""" +
            """(Z|[+-]\d{2}(?::?\d{2}(?::?\d{2}(?:\.\d{1,6})?)?)?)?)?$""",
    )

    /** `DateField` de DRF. Nula si no es una fecha válida. */
    fun fecha(texto: String): LocalDate? {
        val m = FECHA.matchEntire(texto) ?: return null
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
            .getOrNull()
    }

    /** `DateTimeField` de DRF. Nulo si no es válida. */
    fun fechaHora(texto: String, zonaDeLaClinica: ZoneId): OffsetDateTime? {
        val m = FECHA_HORA.matchEntire(texto) ?: return null
        val g = m.groupValues
        return runCatching {
            val nanos = g[7].takeIf { it.isNotEmpty() }?.take(6)?.padEnd(9, '0')?.toInt() ?: 0
            val local = LocalDateTime.of(
                g[1].toInt(), g[2].toInt(), g[3].toInt(),
                g[4].ifEmpty { "0" }.toInt(), g[5].ifEmpty { "0" }.toInt(), g[6].ifEmpty { "0" }.toInt(), nanos,
            )
            val zona = g[8]
            if (zona.isEmpty()) local.atZone(zonaDeLaClinica).toOffsetDateTime()
            else local.atOffset(desplazamiento(zona))
        }.getOrNull()
    }

    private fun desplazamiento(texto: String): ZoneOffset {
        if (texto == "Z") return ZoneOffset.UTC
        val signo = if (texto[0] == '-') -1 else 1
        val cifras = texto.substring(1).replace(":", "").substringBefore('.')
        val horas = cifras.substring(0, 2).toInt()
        val minutos = cifras.getOrNull(2)?.let { cifras.substring(2, 4).toInt() } ?: 0
        val segundos = if (cifras.length >= 6) cifras.substring(4, 6).toInt() else 0
        return ZoneOffset.ofHoursMinutesSeconds(signo * horas, signo * minutos, signo * segundos)
    }
}
