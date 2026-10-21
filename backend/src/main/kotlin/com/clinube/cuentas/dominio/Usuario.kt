package com.clinube.cuentas.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Una cuenta: el personal entra con correo y contraseña; el paciente, solo
 * con el código que se le envía (su contraseña queda inutilizable).
 *
 * `clinicaId` es una columna, no una relación perezosa, a propósito: la
 * clínica de la petición se resuelve una vez y de forma explícita (ver
 * `ContextoDeLaPeticion`). Con una relación perezosa, cada vista que
 * tocara `usuario.clinica` lanzaría su propia consulta sin que se vea en
 * el código, que es justo de donde salen los N+1.
 */
@Entity
@Table(name = "accounts_user")
class Usuario(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    /** El hash de Django: `pbkdf2_sha256$<iteraciones>$<sal>$<hash>`. */
    @Column(name = "password", nullable = false, length = 128)
    var contrasena: String = "",

    @Column(name = "last_login")
    var ultimoAcceso: Instant? = null,

    @Column(name = "is_superuser", nullable = false)
    var esSuperusuario: Boolean = false,

    @Column(name = "email", length = 254)
    var correo: String? = null,

    @Column(name = "phone", length = 20)
    var telefono: String? = null,

    @Column(name = "full_name", nullable = false, length = 150)
    var nombreCompleto: String = "",

    @Column(name = "role", nullable = false, length = 20)
    var rol: String = Rol.DOCTOR,

    /** Baja lógica: una cuenta desactivada no entra, pero no se borra. */
    @Column(name = "is_active", nullable = false)
    var activo: Boolean = true,

    @Column(name = "is_staff", nullable = false)
    var accesoAlAdmin: Boolean = false,

    /** Nula SOLO para el Super Administrador, que opera sobre la plataforma. */
    @Column(name = "tenant_id")
    var clinicaId: UUID? = null,

    /**
     * Mientras esté marcada, la cuenta solo puede hacer una cosa: elegir
     * su propia contraseña. Se pone cuando la contraseña la puso otra
     * persona (el alta de una clínica, o un restablecimiento).
     */
    @Column(name = "must_change_password", nullable = false)
    var debeCambiarContrasena: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferencias", nullable = false)
    var preferencias: MutableMap<String, Any?> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "funciones", nullable = false)
    var funciones: MutableMap<String, Any?> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var creadoEn: Instant = Instant.now(),
) {
    val esPaciente: Boolean get() = rol == Rol.PACIENTE
}
