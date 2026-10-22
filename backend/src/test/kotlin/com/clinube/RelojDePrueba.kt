package com.clinube

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Un reloj que las pruebas pueden fijar. Las de Django simulan «hoy» para
 * probar el 29 de febrero, el cruce de año o la medianoche; estas, igual.
 * Sin fijar, da la hora real.
 */
class RelojDePrueba : Clock() {
    @Volatile
    var fijo: Instant? = null

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = fijo ?: Instant.now()

    /** Fija el reloj a esa hora de Guayaquil. */
    fun fijarEnGuayaquil(dia: LocalDate, hora: Int = 12, minuto: Int = 0) {
        fijo = dia.atTime(LocalTime.of(hora, minuto)).atZone(GUAYAQUIL).toInstant()
    }

    companion object {
        val GUAYAQUIL: ZoneId = ZoneId.of("America/Guayaquil")
    }
}

@TestConfiguration
class ConfiguracionDePrueba {
    @Bean
    @Primary
    fun relojDePrueba(): RelojDePrueba = RelojDePrueba()
}
