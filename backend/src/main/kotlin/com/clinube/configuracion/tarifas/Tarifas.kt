package com.clinube.configuracion.tarifas

import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Qué precio corresponde a un tratamiento para un paciente
 * (`apps/configuration/pricing.py`, RF-CFG-03/04). De lo más específico a lo
 * más general, gana el primero que existe:
 *
 *   1. el tarifario del convenio (`Tariff(tratamiento, convenio)`);
 *   2. el descuento porcentual del convenio, si está activo, sobre el precio
 *      de referencia;
 *   3. el tarifario general (`Tariff(tratamiento, sin convenio)`);
 *   4. el precio base del catálogo.
 *
 * El precio de referencia del punto 2 es el del 3 si existe, y si no el del
 * 4. Todo a dos decimales con redondeo comercial (`ROUND_HALF_UP`), y nunca
 * negativo.
 */
@Component
class Tarifas(private val jdbc: NamedParameterJdbcTemplate) {

    /** El convenio del paciente, con lo que cuenta para el precio. */
    data class Convenio(val id: UUID, val nombre: String, val descuento: BigDecimal?, val activo: Boolean)

    /** Los tarifarios de la clínica que hacen falta para ese convenio (`prefetch_tariffs`). */
    inner class Precios(private val convenio: Convenio?, private val filas: List<Triple<UUID, UUID?, BigDecimal>>) {

        /** `price_for(tratamiento, convenio, tariffs=…)`. */
        fun de(tratamiento: UUID, precioBase: BigDecimal): BigDecimal {
            var especifico: BigDecimal? = null
            var general: BigDecimal? = null
            for ((deTratamiento, deConvenio, precio) in filas) {
                if (deTratamiento != tratamiento) continue
                if (convenio != null && deConvenio == convenio.id) especifico = precio
                else if (deConvenio == null) general = precio
            }
            if (especifico != null) return dinero(especifico)
            val referencia = general ?: precioBase
            if (convenio != null && convenio.activo && convenio.descuento != null) {
                // Un 0 % es un convenio sin descuento; uno mal cargado con 150 % no devuelve dinero.
                val porcentaje = convenio.descuento.max(BigDecimal.ZERO).min(BigDecimal(100))
                return dinero(referencia.multiply(BigDecimal(100) - porcentaje).divide(BigDecimal(100)))
            }
            return dinero(referencia)
        }
    }

    /** Los tarifarios generales de la clínica y los del convenio, si lo hay. */
    fun precios(clinica: UUID?, convenio: Convenio?): Precios {
        val filas = jdbc.query(
            """select treatment_id, agreement_id, price from configuration_tariff
               where tenant_id = :clinica and (agreement_id is null or agreement_id = :convenio)""",
            mapOf("clinica" to clinica, "convenio" to convenio?.id),
        ) { f, _ -> Triple(f.uuid("treatment_id"), f.uuidONulo("agreement_id"), f.getBigDecimal("price")) }
        return Precios(convenio, filas)
    }

    /** El convenio de un paciente, activo o no (el descuento solo cuenta si está activo). */
    fun convenioDelPaciente(paciente: UUID): Convenio? = jdbc.query(
        """select a.id, a.name, a.discount_percentage, a.is_active from patients_patient p
           join configuration_agreement a on a.id = p.agreement_id where p.id = :paciente""",
        mapOf("paciente" to paciente),
    ) { f, _ -> Convenio(f.uuid("id"), f.getString("name"), f.getBigDecimal("discount_percentage"), f.getBoolean("is_active")) }
        .firstOrNull()

    companion object {
        /** `_money`: dos decimales, redondeo comercial, nunca negativo. */
        fun dinero(valor: BigDecimal): BigDecimal {
            val redondeado = valor.setScale(2, RoundingMode.HALF_UP)
            return if (redondeado.signum() > 0) redondeado else BigDecimal("0.00")
        }
    }
}
