package com.clinube.comun.sql

import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Lectura de columnas de PostgreSQL con los tipos que usa el resto del
 * código. Las consultas de solo lectura (listados, informes) van en SQL
 * escrito a mano y no con entidades: así se puede copiar la consulta que
 * hace Django —el mismo filtro, el mismo orden— y, de paso, agrupar en la
 * base en vez de en memoria.
 */
object Filas {
    fun ResultSet.uuid(columna: String): UUID = getObject(columna, UUID::class.java)
    fun ResultSet.uuidONulo(columna: String): UUID? = getObject(columna, UUID::class.java)
    fun ResultSet.instante(columna: String): Instant = getObject(columna, OffsetDateTime::class.java).toInstant()
    fun ResultSet.instanteONulo(columna: String): Instant? =
        getObject(columna, OffsetDateTime::class.java)?.toInstant()
    fun ResultSet.fecha(columna: String): LocalDate = getObject(columna, LocalDate::class.java)
    fun ResultSet.fechaONula(columna: String): LocalDate? = getObject(columna, LocalDate::class.java)
    fun ResultSet.decimal(columna: String): BigDecimal = getBigDecimal(columna)
}
