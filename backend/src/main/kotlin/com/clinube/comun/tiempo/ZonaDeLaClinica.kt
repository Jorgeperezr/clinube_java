package com.clinube.comun.tiempo

import com.clinube.configuracion.Ajustes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Todo se guarda en UTC; «hoy», los meses y los filtros por día son los de
 * la clínica (`America/Guayaquil`).
 *
 * **Se filtra por intervalos de instantes, nunca convirtiendo cada fila.**
 * La diferencia no es de estilo: `WHERE fecha::date = '2026-10-20'` obliga
 * a PostgreSQL a calcular una conversión por fila —así no usa el índice— y
 * además la hace en la zona de la sesión, que no tiene por qué ser la de la
 * clínica. Con `WHERE fecha >= inicio AND fecha < inicio_del_siguiente` se
 * usa el índice y el día es el día de la clínica.
 *
 * De aquí salieron varios fallos en Clinube: una cita de las 20:00 en
 * Guayaquil es del día siguiente en UTC, así que «las citas de hoy»
 * calculado en UTC se deja fuera la última consulta de la tarde.
 *
 * «Ahora» sale de un `Clock` y no de `Instant.now()`: las pruebas lo
 * fijan, igual que las de Django simulan la fecha de hoy.
 */
@Component
class ZonaDeLaClinica(private val ajustes: Ajustes, private val reloj: Clock) {

    val zona: ZoneId get() = ajustes.zonaDeLaClinica

    /** Con microsegundos, como los guarda PostgreSQL y los da Python. */
    fun ahora(): Instant = reloj.instant().truncatedTo(ChronoUnit.MICROS)

    fun hoy(): LocalDate = LocalDate.ofInstant(reloj.instant(), zona)

    fun mesActual(): YearMonth = YearMonth.from(hoy())

    /** El instante en que empieza ese día en la clínica. */
    fun inicioDelDia(dia: LocalDate): Instant = dia.atStartOfDay(zona).toInstant()

    /** Intervalo `[desde, hasta)` que cubre exactamente ese día en la clínica. */
    fun elDia(dia: LocalDate): Intervalo = Intervalo(inicioDelDia(dia), inicioDelDia(dia.plusDays(1)))

    /** Intervalo `[desde, hasta)` que cubre ese mes en la clínica. */
    fun elMes(mes: YearMonth): Intervalo =
        Intervalo(inicioDelDia(mes.atDay(1)), inicioDelDia(mes.plusMonths(1).atDay(1)))

    /** Intervalo `[desde, hasta)` entre dos días, ambos incluidos. */
    fun entre(desde: LocalDate, hasta: LocalDate): Intervalo =
        Intervalo(inicioDelDia(desde), inicioDelDia(hasta.plusDays(1)))

    data class Intervalo(val desde: Instant, val hasta: Instant)
}

@Configuration
class ConfiguracionDelReloj {
    @Bean
    fun reloj(): Clock = Clock.systemUTC()
}
