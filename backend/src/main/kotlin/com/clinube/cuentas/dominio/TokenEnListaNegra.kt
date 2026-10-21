package com.clinube.cuentas.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Un token de refresco invalidado. Es el `BlacklistedToken` de SimpleJWT.
 * Se escribe al rotar (`BLACKLIST_AFTER_ROTATION`), que es lo que hace que
 * un refresco usado dos veces falle la segunda.
 */
@Entity
@Table(name = "token_blacklist_blacklistedtoken")
class TokenEnListaNegra(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "token_id", nullable = false)
    var tokenId: Long = 0,

    @Column(name = "blacklisted_at", nullable = false)
    var invalidadoEn: Instant = Instant.now(),
)
