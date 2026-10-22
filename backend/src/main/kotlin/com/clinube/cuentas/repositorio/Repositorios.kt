package com.clinube.cuentas.repositorio

import com.clinube.cuentas.dominio.TokenEnListaNegra
import com.clinube.cuentas.dominio.TokenVigente
import com.clinube.cuentas.dominio.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RepositorioDeUsuarios : JpaRepository<Usuario, UUID> {

    /**
     * Búsqueda por correo EXACTA, distinguiendo mayúsculas.
     *
     * Así es como entra Django: `USERNAME_FIELD = "email"` y
     * `get_by_natural_key` hace una igualdad exacta. Buscar sin distinguir
     * mayúsculas dejaría entrar a quien hoy no entra, que es un cambio de
     * comportamiento aunque parezca una mejora.
     */
    fun findByCorreo(correo: String): Usuario?

    fun countByClinicaId(clinicaId: UUID): Long
}

interface RepositorioDeTokensVigentes : JpaRepository<TokenVigente, Long> {
    fun findByJti(jti: String): TokenVigente?
}

interface RepositorioDeTokensEnListaNegra : JpaRepository<TokenEnListaNegra, Long> {

    fun existsByTokenId(tokenId: Long): Boolean

    /**
     * Cuántas veces está en la lista negra el token con ese `jti`: cero o
     * una. Es `BlacklistedToken.objects.filter(token__jti=jti).exists()`.
     *
     * Devuelve un número y no un booleano porque `select count(n) > 0` no
     * es HQL portable entre versiones de Hibernate; comparar en Kotlin sí
     * lo es.
     */
    @Query(
        """
        select count(n) from TokenEnListaNegra n, TokenVigente v
        where n.tokenId = v.id and v.jti = :jti
        """,
    )
    fun contarEnListaNegra(@Param("jti") jti: String): Long
}
