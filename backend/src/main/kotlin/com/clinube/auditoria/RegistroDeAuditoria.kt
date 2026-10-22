package com.clinube.auditoria

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.servlet.http.HttpServletRequest
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import java.net.InetAddress
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Registro de auditoría (`accounts.AuditLog`, RF-USR-05): todo acceso o
 * cambio a datos clínicos o financieros, como exige la LOPDP.
 */
@Entity
@Table(name = "accounts_auditlog")
class RegistroDeAuditoria(
    @Id @Column(name = "id", nullable = false) var id: UUID = UUID.randomUUID(),
    @Column(name = "tenant_id") var clinicaId: UUID? = null,
    @Column(name = "user_id") var usuarioId: UUID? = null,
    @Column(name = "action", nullable = false, length = 50) var accion: String = "",
    @Column(name = "entity_type", nullable = false, length = 100) var tipoDeEntidad: String = "",
    @Column(name = "entity_id", nullable = false, length = 64) var idDeEntidad: String = "",
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address") var ip: InetAddress? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false) var datos: MutableMap<String, Any?> = mutableMapOf(),
    @Column(name = "created_at", nullable = false)
    var creadoEn: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS),
)

interface RepositorioDeAuditoria : JpaRepository<RegistroDeAuditoria, UUID>

/** Utilidades para la dirección de quien hace la petición. */
object DireccionDelCliente {

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val IPV6 = Regex("""^[0-9A-Fa-f:.]+$""")

    /**
     * `AuditLogMiddleware._get_client_ip` de Django: el primer valor de
     * `X-Forwarded-For` si llega, y si no, la dirección de la conexión.
     */
    fun de(peticion: HttpServletRequest): String? {
        val reenviada = peticion.getHeader("X-Forwarded-For")
        if (!reenviada.isNullOrEmpty()) return reenviada.split(",")[0].trim()
        return peticion.remoteAddr
    }

    /**
     * La dirección como `InetAddress`, solo si es una IP literal. Con
     * cualquier otra cosa, `InetAddress.getByName` haría una consulta DNS
     * con un texto que manda el cliente; se guarda nula. (Django intentaría
     * guardar el texto y la base lo rechazaría con un 500.)
     */
    fun comoInet(texto: String?): InetAddress? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (!IPV4.matches(t) && !(t.contains(':') && IPV6.matches(t))) return null
        return runCatching { InetAddress.getByName(t) }.getOrNull()
    }
}
