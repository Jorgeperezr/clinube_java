package com.clinube.comun

import com.clinube.comun.json.DecimalComoTexto
import com.clinube.comun.json.FechaDeDrf
import com.clinube.configuracion.Ajustes
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.module.SimpleModule
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

/**
 * El formato de salida de DRF. Cada cosa de aquí se comprobó contra un
 * Django real: las respuestas están en
 * `backend/src/test/resources/contrato/`.
 */
class FormatoDeDrfTest {

    private val ajustes = Ajustes(secreto = "cualquiera", zona = "America/Guayaquil")

    private val mapper = ObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        registerModule(
            SimpleModule().apply {
                addSerializer(BigDecimal::class.java, DecimalComoTexto())
                addSerializer(OffsetDateTime::class.java, FechaDeDrf(ajustes))
            },
        )
    }

    data class Fila(val nombreCompleto: String, val creadoEn: OffsetDateTime, val importe: BigDecimal)

    @Test
    fun `las fechas salen en la zona de la clinica, con microsegundos y desplazamiento`() {
        // Esto es lo que más fácil se escapa. Django respondió:
        //   "created_at": "2026-10-20T23:53:46.378048-05:00"
        // para un instante que en UTC es 2026-10-21T04:53:46.378048Z.
        // Jackson, por su cuenta, habría escrito la versión en UTC con
        // «Z» y truncada a milisegundos. Es la misma hora y no es la
        // misma respuesta.
        val instante = OffsetDateTime.of(2026, 10, 21, 4, 53, 46, 378_048_000, ZoneOffset.UTC)
        val json = mapper.writeValueAsString(
            Fila("Ana Pérez", instante, BigDecimal("125.50")),
        )
        assertEquals(
            """{"nombre_completo":"Ana Pérez","creado_en":"2026-10-20T23:53:46.378048-05:00","importe":"125.50"}""",
            json,
        )
    }

    @Test
    fun `los decimales salen como texto y no pierden los centimos`() {
        // DRF trae COERCE_DECIMAL_TO_STRING=True. Como número, 125.50 se
        // escribiría 125.5 y el panel enseñaría «125.5» en una factura.
        assertEquals(""""125.50"""", mapper.writeValueAsString(BigDecimal("125.50")))
        assertEquals(""""0.00"""", mapper.writeValueAsString(BigDecimal("0.00")))
        assertEquals(""""1000000.00"""", mapper.writeValueAsString(BigDecimal("1000000.00")))
        // Sin notación científica, que es lo que haría `toString()`.
        assertEquals(""""0.0000001"""", mapper.writeValueAsString(BigDecimal("1E-7")))
    }

    @Test
    fun `un instante sin fraccion de segundo no escribe el punto`() {
        // Python no escribe «.000000»: si no hay microsegundos, no hay
        // punto. `appendFraction(..., 0, 6, true)` hace lo mismo.
        val enPunto = OffsetDateTime.of(2026, 10, 21, 5, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(
            """"2026-10-21T00:00:00-05:00"""",
            mapper.writeValueAsString(enPunto),
        )
    }

    @Test
    fun `la fraccion de segundo lleva seis cifras o ninguna, nunca tres`() {
        // El fallo que esto evita: `appendFraction` de Java recorta los
        // ceros finales, así que 378000 microsegundos habría salido como
        // «.378». Python escribe siempre seis cifras o ninguna, y
        // PostgreSQL guarda microsegundos: uno de cada diez valores acaba
        // en cero. Comprobado contra `datetime.isoformat()`.
        fun enGuayaquil(micros: Int) = mapper.writeValueAsString(
            OffsetDateTime.of(2026, 10, 21, 4, 53, 46, micros * 1_000, ZoneOffset.UTC),
        )

        assertEquals(""""2026-10-20T23:53:46.378048-05:00"""", enGuayaquil(378048))
        assertEquals(""""2026-10-20T23:53:46.378000-05:00"""", enGuayaquil(378000))
        assertEquals(""""2026-10-20T23:53:46.500000-05:00"""", enGuayaquil(500000))
        assertEquals(""""2026-10-20T23:53:46.120000-05:00"""", enGuayaquil(120000))
        assertEquals(""""2026-10-20T23:53:46.000001-05:00"""", enGuayaquil(1))
        // Sin microsegundos, Python no escribe ni el punto.
        assertEquals(""""2026-10-20T23:53:46-05:00"""", enGuayaquil(0))
    }

    @Test
    fun `con la clinica en UTC el desplazamiento se escribe Z`() {
        // DRF sustituye un «+00:00» final por «Z». Si algún día una
        // clínica estuviera en UTC, la respuesta tendría que llevar Z.
        val enUtc = ObjectMapper().apply {
            registerModule(
                SimpleModule().apply {
                    addSerializer(
                        OffsetDateTime::class.java,
                        FechaDeDrf(Ajustes(secreto = "cualquiera", zona = "UTC")),
                    )
                },
            )
        }
        val instante = OffsetDateTime.of(2026, 10, 21, 4, 53, 46, 378_000_000, ZoneOffset.UTC)
        assertEquals(""""2026-10-21T04:53:46.378000Z"""", enUtc.writeValueAsString(instante))
    }

    @Test
    fun `una fecha de verano sigue en menos cinco`() {
        // Ecuador no tiene horario de verano: el desplazamiento es -05:00
        // todo el año. Si algún día se cambiara la zona de la clínica a
        // una que sí lo tenga, esta prueba avisaría.
        val julio = OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(""""2026-07-15T07:00:00-05:00"""", mapper.writeValueAsString(julio))
    }
}
