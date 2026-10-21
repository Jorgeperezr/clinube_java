package com.clinube.clinicas

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Una clínica. En Django es `common.Tenant` y la tabla sigue llamándose
 * `common_tenant`: el nombre de la tabla es parte del contrato con la base
 * de datos, aunque en Kotlin la clase se llame como se llama la cosa.
 */
@Entity
@Table(name = "common_tenant")
class Clinica(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 255)
    var nombre: String = "",

    @Column(name = "ruc", nullable = false, length = 20)
    var ruc: String = "",

    @Column(name = "address", nullable = false, length = 255)
    var direccion: String = "",

    @Column(name = "phone", nullable = false, length = 30)
    var telefono: String = "",

    @Column(name = "email", nullable = false, length = 254)
    var correo: String = "",

    @Column(name = "is_active", nullable = false)
    var activa: Boolean = true,

    /** Lo que la PLATAFORMA le ha dado de alta. Ver `Funcionalidades`. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "funcionalidades", nullable = false)
    var funcionalidades: MutableMap<String, Any?> = mutableMapOf(),

    /** Lo que la PROPIA clínica decide no usar de lo que tiene contratado. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modulos_clinica", nullable = false)
    var modulosDeLaClinica: MutableMap<String, Any?> = mutableMapOf(),

    /** Recibe las funciones nuevas antes que las demás. */
    @Column(name = "es_prueba", nullable = false)
    var esDePruebas: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var creadaEn: Instant = Instant.now(),
)
