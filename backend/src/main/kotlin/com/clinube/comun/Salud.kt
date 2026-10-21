package com.clinube.comun

import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Sondas de salud, públicas y sin autenticación: quien las consulta
 * (Docker, un balanceador, un monitor) no tiene credenciales.
 *
 *   · `health` dice que el proceso responde.
 *   · `ready` dice que además puede trabajar, y devuelve 503 cuando no,
 *     que es lo que un balanceador entiende como «no me mandes tráfico».
 *     Con la base caída, responder «listo» hace que le llegue tráfico que
 *     solo puede fallar.
 *
 * **Sin límite de peticiones.** Un balanceador sondea cada diez segundos
 * desde una sola IP y caería en el cupo de anónimo, que comparte con todo
 * el tráfico sin autenticar. Un 429 no es «estoy sano»: el balanceador
 * saca de rotación un servidor que estaba perfectamente bien, que es justo
 * el desastre que esta ruta existe para evitar.
 */
@RestController
@RequestMapping("/api/v1")
class Salud(private val em: EntityManager) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/health/")
    fun salud(): Map<String, String> = mapOf("status" to "ok")

    @GetMapping("/ready/")
    fun listo(): ResponseEntity<Map<String, String>> = try {
        em.createNativeQuery("SELECT 1").singleResult
        ResponseEntity.ok(linkedMapOf("status" to "ok", "database" to "ok"))
    } catch (e: Exception) {
        // El detalle va al registro, no a la respuesta: el mensaje de
        // error de la base de datos lleva host, puerto y usuario.
        log.error("Comprobación de disponibilidad fallida", e)
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(linkedMapOf("status" to "unavailable", "database" to "error"))
    }
}
