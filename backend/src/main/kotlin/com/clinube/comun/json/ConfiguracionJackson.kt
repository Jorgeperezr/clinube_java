package com.clinube.comun.json

import com.clinube.configuracion.Ajustes
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/**
 * Jackson configurado para escribir lo que escribe DRF. Los tres puntos
 * que importan, y que no son opinión: se comprobaron contra un servidor
 * Django real.
 *
 *   1. **snake_case.** `full_name`, `must_change_password`, `created_at`.
 *   2. **Los decimales, como texto.** DRF trae
 *      `COERCE_DECIMAL_TO_STRING=True`, así que un importe sale `"125.50"`
 *      y no `125.5`. Mandar un número rompería el panel en los sitios
 *      donde compara cadenas, y perdería los ceros de los céntimos.
 *   3. **Las fechas, en la zona de la clínica y con microsegundos.** Esto
 *      es lo que más fácil se escapa: DRF no emite UTC ni `Z`, emite la
 *      hora local de la clínica con su desplazamiento y SEIS decimales,
 *      porque PostgreSQL guarda microsegundos:
 *
 *          "2026-10-20T23:53:46.378048-05:00"
 *
 *      Jackson, por su cuenta, habría escrito `2026-10-21T04:53:46.378Z`
 *      o un número de epoch. Las dos cosas son la misma hora y ninguna es
 *      la misma respuesta.
 */
@Configuration
class ConfiguracionJackson(private val ajustes: Ajustes) {

    @Bean
    fun comoDrf(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { constructor ->
            constructor.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            constructor.featuresToDisable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            )
            constructor.modulesToInstall({ modulos ->
                modulos.add(
                    SimpleModule("drf").apply {
                        addSerializer(BigDecimal::class.java, DecimalComoTexto())
                        addSerializer(OffsetDateTime::class.java, FechaDeDrf(ajustes))
                        addSerializer(Instant::class.java, InstanteDeDrf(ajustes))
                    },
                )
            })
        }
}

/** `"125.50"`, no `125.5`: como `COERCE_DECIMAL_TO_STRING` de DRF. */
class DecimalComoTexto : JsonSerializer<BigDecimal>() {
    override fun serialize(valor: BigDecimal, gen: JsonGenerator, proveedor: SerializerProvider) {
        gen.writeString(valor.toPlainString())
    }
}

/**
 * El formato de `DateTimeField` de DRF: ISO-8601 en la zona de la clínica.
 *
 * La fracción de segundo se escribe a mano y no con `appendFraction`, y la
 * razón es un fallo que habría salido en producción y no en las pruebas:
 * `appendFraction(MICRO_OF_SECOND, 0, 6, true)` **recorta los ceros
 * finales**, así que para 378000 microsegundos habría escrito `.378`.
 * `isoformat()` de Python —y por tanto DRF— escribe siempre seis cifras o
 * ninguna:
 *
 *     micros = 378048  →  2026-10-20T23:53:46.378048-05:00
 *     micros = 378000  →  2026-10-20T23:53:46.378000-05:00
 *     micros =      0  →  2026-10-20T23:53:46-05:00
 *
 * Como PostgreSQL guarda microsegundos, uno de cada diez valores acaba en
 * cero y habría salido distinto. No es una diferencia que se vea mirando:
 * se ve comparando.
 */
object FormatoDeDrf {

    /** Sin fracción: la parte que siempre está igual. */
    private val HASTA_LOS_SEGUNDOS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** `Z` cuando el desplazamiento es cero, como hace DRF al sustituir `+00:00`. */
    private val DESPLAZAMIENTO = DateTimeFormatterBuilder().appendOffset("+HH:MM", "Z").toFormatter()

    fun escribir(momento: OffsetDateTime): String {
        val micros = momento.nano / 1_000
        val fraccion = if (micros == 0) "" else ".%06d".format(micros)
        return momento.format(HASTA_LOS_SEGUNDOS) + fraccion + momento.format(DESPLAZAMIENTO)
    }
}

class FechaDeDrf(private val ajustes: Ajustes) : JsonSerializer<OffsetDateTime>() {
    override fun serialize(valor: OffsetDateTime, gen: JsonGenerator, proveedor: SerializerProvider) {
        gen.writeString(FormatoDeDrf.escribir(valor.atZoneSameInstant(ajustes.zonaDeLaClinica).toOffsetDateTime()))
    }
}

class InstanteDeDrf(private val ajustes: Ajustes) : JsonSerializer<Instant>() {
    override fun serialize(valor: Instant, gen: JsonGenerator, proveedor: SerializerProvider) {
        gen.writeString(FormatoDeDrf.escribir(valor.atZone(ajustes.zonaDeLaClinica).toOffsetDateTime()))
    }
}
