package com.clinube.plataforma

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * La configuración global de la plataforma (`common.PlatformConfiguration`):
 * una sola fila. Los valores por defecto son los del modelo de Django, porque
 * aquí también se crea la fila cuando falta (ver `ServicioDeConfiguracion`).
 *
 * `smtp_password` va cifrada con Fernet (`apps/common/secretos.py`) y nunca
 * sale por la API; se mapea para poder crear la fila, no para leerla.
 */
@Entity
@Table(name = "common_platformconfiguration")
class ConfiguracionPlataforma(
    @Id @Column(name = "id", nullable = false) var id: UUID = UUID.randomUUID(),
    @Column(name = "platform_name", nullable = false, length = 120) var nombrePlataforma: String = "Clinube",
    @Column(name = "logo_url", nullable = false, length = 200) var logoUrl: String = "",
    @Column(name = "smtp_host", nullable = false) var smtpHost: String = "",
    @Column(name = "smtp_port", nullable = false) var smtpPuerto: Int = 587,
    @Column(name = "smtp_user", nullable = false) var smtpUsuario: String = "",
    @Column(name = "smtp_password", nullable = false) var smtpContrasenaCifrada: String = "",
    @Column(name = "timezone", nullable = false, length = 64) var zona: String = "America/Guayaquil",
    @Column(name = "currency", nullable = false, length = 8) var moneda: String = "USD",
    @Column(name = "titular_nombre", nullable = false, length = 200) var titularNombre: String = "",
    @Column(name = "titular_ruc", nullable = false, length = 20) var titularRuc: String = "",
    @Column(name = "titular_correo", nullable = false, length = 254) var titularCorreo: String = "",
    @Column(name = "proveedor_correo", nullable = false, length = 120) var proveedorCorreo: String = "",
    @Column(name = "canal_codigo_paciente", nullable = false, length = 10) var canalCodigoPaciente: String = CANAL_CORREO,
    @Column(name = "google_client_id", nullable = false, length = 200) var googleClientId: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "funciones_publicadas", nullable = false) var funcionesPublicadas: MutableList<Any?> = mutableListOf(),
    @Column(name = "updated_at", nullable = false) var actualizadaEn: Instant = Instant.now(),
) {
    companion object {
        const val CANAL_WHATSAPP = "whatsapp"
        const val CANAL_CORREO = "correo"
    }
}

interface RepositorioDeConfiguracion : JpaRepository<ConfiguracionPlataforma, UUID> {
    /** `PlatformConfiguration.objects.first()`: sin orden propio, Django ordena por la clave primaria. */
    fun findFirstByOrderByIdAsc(): ConfiguracionPlataforma?
}

/**
 * Las dos formas en que Django lee la configuración, y la diferencia importa:
 *
 *   · `primera()` es `objects.first()`: si no hay fila, no hay configuración.
 *     Así la leen el canal del código, Google y las funciones publicadas.
 *   · `obtenerOCrear()` es `get_solo()`, que **crea la fila** con los valores
 *     por defecto si falta. Así la leen los textos legales: el primer GET a
 *     `/legal/privacidad/` de una instalación nueva escribe en la base.
 *     Comprobado contra Django (0 filas antes, 1 después). Se copia para que
 *     la base quede igual pase por el servidor que pase.
 */
@Service
class ServicioDeConfiguracion(private val repositorio: RepositorioDeConfiguracion) {

    @Transactional(readOnly = true)
    fun primera(): ConfiguracionPlataforma? = repositorio.findFirstByOrderByIdAsc()

    @Transactional
    fun obtenerOCrear(): ConfiguracionPlataforma =
        repositorio.findFirstByOrderByIdAsc()
            ?: repositorio.save(ConfiguracionPlataforma(actualizadaEn = Instant.now().truncatedTo(ChronoUnit.MICROS)))

    /** El canal elegido en Plataforma; correo si no hay configuración. */
    fun canalDelCodigo(): String = primera()?.canalCodigoPaciente?.ifEmpty { null } ?: ConfiguracionPlataforma.CANAL_CORREO

    /** Las funciones nacidas en prueba que ya se publicaron para todas las clínicas. */
    fun funcionesPublicadas(): Set<String> =
        (primera()?.funcionesPublicadas ?: emptyList())
            .filterIsInstance<String>()
            .filter { it in Funcionalidades.NACIDAS_EN_PRUEBA }
            .toSet()
}
