package com.clinube

import com.clinube.configuracion.Ajustes
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(Ajustes::class)
@EnableScheduling
@EnableAsync
class Aplicacion

fun main(args: Array<String>) {
    runApplication<Aplicacion>(*args)
}
