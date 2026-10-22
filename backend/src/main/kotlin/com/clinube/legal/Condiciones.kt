package com.clinube.legal

import com.clinube.auditoria.DireccionDelCliente
import com.clinube.auditoria.RegistroDeAuditoria
import com.clinube.auditoria.RepositorioDeAuditoria
import com.clinube.cuentas.dominio.Rol
import com.clinube.cuentas.dominio.Usuario
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.InetAddress
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Constancia de que una clínica aceptó una versión concreta de las
 * condiciones (`common.AceptacionCondiciones`). La acepta una persona con
 * rol de administración, y queda quién, cuándo y desde qué dirección.
 *
 * El nombre y el correo se copian al aceptar: si la cuenta se borra algún
 * día, la constancia tiene que seguir diciendo quién fue.
 */
@Entity
@Table(name = "common_aceptacioncondiciones")
class AceptacionDeCondiciones(
    @Id @Column(name = "id", nullable = false) var id: UUID = UUID.randomUUID(),
    @Column(name = "tenant_id", nullable = false) var clinicaId: UUID = UUID.randomUUID(),
    @Column(name = "version", nullable = false, length = 20) var version: String = "",
    @Column(name = "aceptada_por_id") var aceptadaPorId: UUID? = null,
    @Column(name = "nombre", nullable = false, length = 255) var nombre: String = "",
    @Column(name = "correo", nullable = false, length = 254) var correo: String = "",
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip") var ip: InetAddress? = null,
    @Column(name = "created_at", nullable = false) var creadaEn: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var actualizadaEn: Instant = Instant.now(),
)

interface RepositorioDeAceptaciones : JpaRepository<AceptacionDeCondiciones, UUID> {
    fun findByClinicaIdAndVersion(clinicaId: UUID, version: String): AceptacionDeCondiciones?
    fun existsByClinicaIdAndVersion(clinicaId: UUID, version: String): Boolean
}

/**
 * Las condiciones de uso que la clínica tiene que aceptar
 * (`apps/common/legal_views.py`).
 *
 * CAMBIAR UN TEXTO ES CAMBIAR SU VERSIÓN: al subir la versión, todas las
 * clínicas vuelven a tener que aceptarlas.
 */
@Service
class Condiciones(
    private val aceptaciones: RepositorioDeAceptaciones,
    private val auditoria: RepositorioDeAuditoria,
) {

    companion object {
        /** Igual que `CONDICIONES_VERSION` en `apps/common/legal.py`. */
        const val VERSION = TextosLegales.CONDICIONES_VERSION
    }

    /**
     * ¿Tiene esta persona que aceptar las condiciones antes de seguir?
     *
     * Solo la administración de una clínica puede aceptarlas, y basta con
     * que lo haga una de sus administradoras: la que acepta lo hace en
     * nombre de la clínica.
     */
    @Transactional(readOnly = true)
    fun pendientesPara(usuario: Usuario): Boolean {
        val clinicaId = usuario.clinicaId
        if (usuario.rol != Rol.ADMIN || clinicaId == null) return false
        return !aceptaciones.existsByClinicaIdAndVersion(clinicaId, VERSION)
    }

    /**
     * Acepta la versión vigente en nombre de la clínica. Si ya estaba
     * aceptada, devuelve la constancia que había y no apunta nada más: el
     * `get_or_create` de Django.
     *
     * La fila de auditoría explícita lleva la IP vacía —Django no la
     * pone— y el id de la constancia. La del middleware (con IP y código de
     * estado) la escribe después `FiltroDeAuditoria`.
     */
    @Transactional
    fun aceptar(usuario: Usuario, clinicaId: UUID, ip: String?): Resultado {
        aceptaciones.findByClinicaIdAndVersion(clinicaId, VERSION)?.let { return Resultado(it, creada = false) }

        val ahora = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val nueva = aceptaciones.save(
            AceptacionDeCondiciones(
                clinicaId = clinicaId,
                version = VERSION,
                aceptadaPorId = usuario.id,
                nombre = usuario.nombreCompleto,
                correo = usuario.correo.orEmpty(),
                ip = DireccionDelCliente.comoInet(ip),
                creadaEn = ahora,
                actualizadaEn = ahora,
            ),
        )
        auditoria.save(
            RegistroDeAuditoria(
                clinicaId = clinicaId,
                usuarioId = usuario.id,
                accion = "accept_terms",
                tipoDeEntidad = "AceptacionCondiciones",
                idDeEntidad = nueva.id.toString(),
                datos = mutableMapOf("version" to nueva.version),
                creadoEn = ahora,
            ),
        )
        return Resultado(nueva, creada = true)
    }

    data class Resultado(val aceptacion: AceptacionDeCondiciones, val creada: Boolean)
}
