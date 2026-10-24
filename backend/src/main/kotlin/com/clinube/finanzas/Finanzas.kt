package com.clinube.finanzas

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.json.CamposDrf
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Lo que comparten las rutas de `apps/finanzas`: la configuración de ley
 * de la clínica, sus categorías de egreso, el equipo al que se le paga y
 * las lecturas sueltas de `views.py` (`_decimal`, `_fecha`, `mes_de`), que
 * no son las de un serializador y tienen sus propios casos.
 */
@Component
class Finanzas(
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val json: ObjectMapper,
) {

    companion object {
        /** `PERMISO` de `views.py`: la función «Finanzas» (y con ella, la funcionalidad contratada). */
        val PERMISO = Permisos.funcion("finanzas")

        /** El contexto de `decimal` de Python: 28 cifras, a la mitad hacia el par. */
        val PY = MathContext(28, RoundingMode.HALF_EVEN)
        val CERO: BigDecimal = BigDecimal("0.00")
        val CIEN = BigDecimal(100)

        val FORMAS_PAGO = linkedMapOf("efectivo" to "Efectivo", "transferencia" to "Transferencia", "tarjeta" to "Tarjeta", "cheque" to "Cheque")
        val FORMAS_COBRO = linkedMapOf("cash" to "Efectivo", "transfer" to "Transferencia", "card" to "Tarjeta")
        val REGIONES = linkedMapOf(
            "sierra" to "Sierra y Amazonía (décimo cuarto hasta el 15 de agosto)",
            "costa" to "Costa y Galápagos (décimo cuarto hasta el 15 de marzo)",
        )
        val ROLES = mapOf(
            "superadmin" to "Super Administrador", "admin" to "Administrador", "reception" to "Recepción",
            "doctor" to "Doctor", "auxiliary" to "Auxiliar", "patient" to "Paciente",
        )
        val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
            "octubre", "noviembre", "diciembre")

        /** Las de una clínica dental en Ecuador, en el orden en que se suelen mirar (`CATEGORIAS_INICIALES`). */
        val CATEGORIAS_INICIALES = listOf(
            "honorarios" to "Honorarios profesionales",
            "sueldos" to "Sueldos y beneficios de ley",
            "laboratorio" to "Laboratorio dental",
            "insumos" to "Insumos y materiales",
            "arriendo" to "Arriendo del local",
            "servicios" to "Luz, agua, internet y teléfono",
            "equipos" to "Equipos y mantenimiento",
            "publicidad" to "Publicidad",
            "contador" to "Contador y servicios profesionales",
            "impuestos" to "Impuestos, permisos y tasas",
            "otros" to "Otros gastos",
        )

        /** Egresos que crea un cálculo: no se corrigen a mano ni se descuentan a nadie. */
        val ORIGENES_CALCULADOS = listOf("liquidacion", "rol", "iess", "sri", "decimo_tercero", "decimo_cuarto")
        /** De estos, los pagos a terceros sí se anulan desde Egresos. */
        val ORIGENES_OBLIGACION = listOf("iess", "sri", "decimo_tercero", "decimo_cuarto")

        /** `centavos`: a céntimos, «a la mitad hacia arriba», como redondea un contador a mano. */
        fun centavos(valor: BigDecimal): BigDecimal = valor.setScale(2, RoundingMode.HALF_UP)

        /** `pct(base, porcentaje)`: el porcentaje de una base, en céntimos. */
        fun pct(base: BigDecimal, porcentaje: BigDecimal): BigDecimal =
            centavos(base.multiply(porcentaje, PY).divide(CIEN, PY))

        /** Un `DecimalField(decimal_places=2)` de DRF al escribirse: a céntimos, a la mitad hacia el par. */
        fun dinero(valor: BigDecimal): String = valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString()

        /** `str(Decimal)`, para los importes que ya llegan con sus dos decimales. */
        fun texto(valor: BigDecimal): String = valor.toPlainString()

        /** `f"{Decimal(v).normalize():f}"` con coma: «10,5», «100». */
        fun numero(valor: BigDecimal): String = valor.round(PY).stripTrailingZeros().toPlainString().replace(".", ",")

        /** `_p`: «9,45 %». */
        fun porcentaje(valor: BigDecimal): String = numero(valor) + " %"

        /** `f"{n:05d}"`. */
        fun cifras(n: Long, ancho: Int): String = n.toString().padStart(ancho, '0')

        /** Un 500, como el que da Django en los casos que no previó (ver docs/DIFERENCIAS.md). */
        fun errorInterno(motivo: String): Nothing = throw IllegalStateException(motivo)

        /** `Response({"detail": texto}, status=400)`: el cuerpo va tal cual, sin el sobre de los errores. */
        fun detalle(texto: String, estado: HttpStatus = HttpStatus.BAD_REQUEST): ResponseEntity<Any> =
            ResponseEntity.status(estado).body(mapOf("detail" to texto))

        /** `raise _error(texto)`: un `ValidationError({"detail": …})`. */
        fun error(texto: String): Nothing = throw ExcepcionApi.validacionConDetalle(texto)

        /** `request.query_params` como un diccionario de textos, con el último valor de cada clave. */
        fun consulta(peticion: HttpServletRequest): ObjectNode = JsonNodeFactory.instance.objectNode().apply {
            peticion.parameterMap.forEach { (clave, valores) -> valores.lastOrNull()?.let { put(clave, it) } }
        }

        /** `fuente.get(campo)`: nulo si no está. */
        fun valor(fuente: JsonNode, campo: String): JsonNode? = fuente.get(campo)?.takeUnless { it.isNull || it.isMissingNode }

        /**
         * `_decimal(valor, defecto)`: `Decimal(str(valor))`, y con `None` o ""
         * el defecto. Nulo si no es un número. NaN e infinito sí son
         * `Decimal` en Python, pero cualquier cuenta o comparación con ellos
         * acaba en 500 allí: aquí también.
         */
        fun decimal(valor: JsonNode?, defecto: String? = "0"): BigDecimal? {
            val texto = when {
                valor == null || valor.isNull || (valor.isTextual && valor.textValue().isEmpty()) -> defecto ?: return null
                else -> Python.str(valor)
            }
            return when (val d = Python.decimal(Python.recortar(texto))) {
                is Python.Decimal.Numero -> d.valor()
                Python.Decimal.Especial -> errorInterno("Decimal especial: $texto")
                null -> null
            }
        }

        /**
         * `_fecha(valor, defecto)`: `parse_date(str(valor))` si el valor es
         * verdadero en Python, y si no el defecto. Nulo si no es una fecha;
         * un día que no existe (31 de febrero) hace saltar a Django con 500.
         */
        fun fecha(valor: JsonNode?, defecto: LocalDate?): LocalDate? {
            if (!Python.verdad(valor)) return defecto
            val texto = Python.str(valor!!)
            if (Python.fechaQueLanza(texto)) errorInterno("parse_date: $texto")
            return Python.fecha(texto)
        }

        /**
         * `mes_de(texto)`: «2026-10» → del 1 al 31 de octubre; vacío, el mes
         * en curso. Nulo donde Python da `ValueError` (las vistas deciden si
         * eso es un 400 o un 500).
         */
        fun mesDe(texto: JsonNode?, hoy: LocalDate): Pair<LocalDate, LocalDate>? {
            val mes = if (Python.verdad(texto)) {
                val partes = Python.str(texto!!).split("-").take(2)
                if (partes.size < 2) return null
                val (anio, numero) = partes.map { Python.entero(it) ?: return null }
                if (anio !in BigInteger.ONE..BigInteger.valueOf(9999) || numero !in BigInteger.ONE..BigInteger.valueOf(12)) return null
                YearMonth.of(anio.toInt(), numero.toInt())
            } else YearMonth.from(hoy)
            return mes.atDay(1) to mes.atEndOfMonth()
        }

        /** `strftime("%Y-%m")`. */
        fun anioMes(dia: LocalDate): String = "%04d-%02d".format(dia.year, dia.monthValue)
    }

    data class Configuracion(
        val id: UUID,
        val salarioBasico: BigDecimal,
        val aportePersonalIess: BigDecimal,
        val aportePatronalIess: BigDecimal,
        val aporteIeceSecap: BigDecimal,
        val fondosReserva: BigDecimal,
        val retencionHonorarios: BigDecimal,
        val siguienteComprobante: Long,
        val region: String,
        val recargoSuplementarias: BigDecimal,
        val recargoExtraordinarias: BigDecimal,
    )

    val clinica: UUID? get() = contexto.clinica?.id

    /**
     * `ConfiguracionFinanzas.de(tenant)`: la de la clínica, y si no la
     * tiene, se crea con los valores de ley de 2026 (`get_or_create`).
     */
    fun configuracion(bloquear: Boolean = false): Configuracion {
        leerConfiguracion(bloquear)?.let { return it }
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into finanzas_configuracionfinanzas (id, created_at, updated_at, salario_basico, aporte_personal_iess,
               aporte_patronal_iess, aporte_iece_secap, fondos_reserva, retencion_honorarios, siguiente_comprobante, tenant_id,
               recargo_extraordinarias, recargo_suplementarias, region)
               values (:id, :ahora, :ahora, 482.00, 9.45, 11.15, 1.00, 8.33, 10.00, 1, :clinica, 100.00, 50.00, 'sierra')
               on conflict (tenant_id) do nothing""",
            mapOf("id" to UUID.randomUUID(), "ahora" to ahora, "clinica" to clinica),
        )
        return leerConfiguracion(bloquear)!!
    }

    private fun leerConfiguracion(bloquear: Boolean): Configuracion? = jdbc.query(
        "select * from finanzas_configuracionfinanzas where tenant_id = :clinica" + if (bloquear) " for update" else "",
        mapOf("clinica" to clinica),
    ) { f, _ ->
        Configuracion(
            f.uuid("id"), f.getBigDecimal("salario_basico"), f.getBigDecimal("aporte_personal_iess"),
            f.getBigDecimal("aporte_patronal_iess"), f.getBigDecimal("aporte_iece_secap"), f.getBigDecimal("fondos_reserva"),
            f.getBigDecimal("retencion_honorarios"), f.getLong("siguiente_comprobante"), f.getString("region"),
            f.getBigDecimal("recargo_suplementarias"), f.getBigDecimal("recargo_extraordinarias"),
        )
    }.firstOrNull()

    /** `categorias_de(tenant)`: si la clínica no tiene ninguna, se le crean las de serie. */
    fun asegurarCategorias() {
        val hay = jdbc.queryForObject(
            "select exists(select 1 from finanzas_categoriaegreso where tenant_id = :clinica)", mapOf("clinica" to clinica), Boolean::class.java,
        )!!
        if (hay) return
        CATEGORIAS_INICIALES.forEachIndexed { i, (clave, nombre) ->
            val ahora = Timestamp.from(zona.ahora())
            jdbc.update(
                """insert into finanzas_categoriaegreso (id, created_at, updated_at, nombre, clave, activa, orden, tenant_id)
                   values (:id, :ahora, :ahora, :nombre, :clave, true, :orden, :clinica)""",
                mapOf("id" to UUID.randomUUID(), "ahora" to ahora, "nombre" to nombre, "clave" to clave, "orden" to i * 10, "clinica" to clinica),
            )
        }
    }

    /** `categoria(tenant, clave)`: la primera de la clínica con esa clave, por orden y nombre. */
    fun categoria(clave: String): UUID? {
        asegurarCategorias()
        return jdbc.query(
            "select id from finanzas_categoriaegreso where tenant_id = :clinica and clave = :clave order by orden, nombre limit 1",
            mapOf("clinica" to clinica, "clave" to clave),
        ) { f, _ -> f.uuid("id") }.firstOrNull()
    }

    /** `_equipo(tenant)`: el personal activo de la clínica, sin pacientes ni superadministradores. */
    val EQUIPO = """from accounts_user u where u.tenant_id = :clinica and u.is_active and u.role not in ('patient', 'superadmin')"""

    data class Persona(val id: UUID, val nombre: String, val correo: String?, val rol: String)

    fun equipo(): List<Persona> = jdbc.query("select u.* $EQUIPO order by u.full_name", mapOf("clinica" to clinica)) { f, _ -> persona(f) }

    private fun persona(f: ResultSet) = Persona(f.uuid("id"), f.getString("full_name"), f.getString("email"), f.getString("role"))

    /**
     * `generics.get_object_or_404(_equipo(tenant), pk=valor)` con lo que
     * llegue: un id que no es un UUID para Django (un decimal, una lista, un
     * negativo) es el `Http404` a secas; uno válido que no está, el de User.
     */
    fun delEquipo(valor: JsonNode?): Persona {
        val id: UUID? = when {
            valor == null || valor.isNull || valor.isMissingNode -> null
            valor.isTextual -> CamposDrf.uuidDePython(valor.textValue()) ?: throw ExcepcionApi.http404SinModelo()
            valor.isBoolean -> UUID(0, if (valor.booleanValue()) 1 else 0)
            valor.isIntegralNumber -> valor.bigIntegerValue().let {
                if (it.signum() < 0 || it.bitLength() > 128) throw ExcepcionApi.http404SinModelo()
                UUID(it.shiftRight(64).toLong(), it.toLong())
            }
            else -> throw ExcepcionApi.http404SinModelo()
        }
        return id?.let {
            jdbc.query("select u.* $EQUIPO and u.id = :id", mapOf("clinica" to clinica, "id" to it)) { f, _ -> persona(f) }.firstOrNull()
        } ?: throw ExcepcionApi.http404("User")
    }

    /** `_auditar(request, accion, objeto, **datos)`. */
    fun auditar(accion: String, tipo: String, id: UUID, datos: Map<String, Any?> = emptyMap()) {
        jdbc.update(
            """insert into accounts_auditlog (id, action, entity_type, entity_id, ip_address, metadata, created_at, tenant_id, user_id)
               values (:aid, :accion, :tipo, :eid, null, :datos::jsonb, :ahora, :clinica, :usuario)""",
            mapOf(
                "aid" to UUID.randomUUID(), "accion" to accion, "tipo" to tipo, "eid" to id.toString(),
                "datos" to json.writeValueAsString(datos), "ahora" to Timestamp.from(zona.ahora()), "clinica" to clinica,
                "usuario" to contexto.usuario?.id,
            ),
        )
    }

    /** Un egreso que crea Clinube al pagar algo (liquidación, rol, obligación, décimo). */
    fun crearEgreso(
        fecha: LocalDate, categoria: UUID?, concepto: String, monto: BigDecimal, forma: String,
        profesional: UUID? = null, origen: String = "", periodo: LocalDate? = null, proveedor: String = "",
        proveedorRuc: String = "", numeroFactura: String = "",
    ): UUID {
        // Sin la categoría (una clínica que creó las suyas antes de abrir la
        // lista de serie), Django intenta guardar un nulo y la base lo rechaza.
        categoria ?: errorInterno("Sin categoría para el egreso")
        val id = UUID.randomUUID()
        val ahora = Timestamp.from(zona.ahora())
        jdbc.update(
            """insert into finanzas_egreso (id, created_at, updated_at, fecha, concepto, monto, forma_pago, estado, vence, proveedor,
               proveedor_ruc, numero_factura, comprobante, descontar_al_profesional, origen, anulado, motivo_anulacion, categoria_id,
               profesional_id, registrado_por_id, tenant_id, periodo)
               values (:id, :ahora, :ahora, :fecha, :concepto, :monto, :forma, 'pagado', null, :proveedor, :ruc, :factura, '', false, :origen,
               false, '', :categoria, :profesional, :usuario, :clinica, :periodo)""",
            mapOf(
                "id" to id, "ahora" to ahora, "fecha" to fecha, "concepto" to concepto, "monto" to monto, "forma" to forma,
                "proveedor" to proveedor, "ruc" to proveedorRuc, "factura" to numeroFactura, "origen" to origen, "categoria" to categoria, "profesional" to profesional,
                "usuario" to contexto.usuario?.id, "clinica" to clinica, "periodo" to periodo,
            ),
        )
        return id
    }

    fun hoy(): LocalDate = zona.hoy()
}
