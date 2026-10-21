package com.clinube.cuentas.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Un token de refresco emitido. Es el `OutstandingToken` de SimpleJWT y usa
 * SU tabla: así la lista de sesiones vivas de una persona es la misma vista
 * desde los dos servidores.
 */
@Entity
@Table(name = "token_blacklist_outstandingtoken")
class TokenVigente(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "user_id")
    var usuarioId: UUID? = null,

    @Column(name = "jti", nullable = false, length = 255)
    var jti: String = "",

    @Column(name = "token", nullable = false, columnDefinition = "text")
    var token: String = "",

    @Column(name = "created_at")
    var creadoEn: Instant? = null,

    @Column(name = "expires_at", nullable = false)
    var caducaEn: Instant = Instant.now(),
)
