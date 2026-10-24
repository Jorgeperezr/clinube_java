package com.clinube.finanzas

import com.clinube.comun.python.Python
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.finanzas.Finanzas.Companion.CERO
import com.clinube.finanzas.Finanzas.Companion.CIEN
import com.clinube.finanzas.Finanzas.Companion.FORMAS_COBRO
import com.clinube.finanzas.Finanzas.Companion.ORIGENES_CALCULADOS
import com.clinube.finanzas.Finanzas.Companion.PY
import com.clinube.finanzas.Finanzas.Companion.anioMes
import com.clinube.finanzas.Finanzas.Companion.centavos
import com.clinube.finanzas.Finanzas.Companion.cifras
import com.clinube.finanzas.Finanzas.Companion.pct
import com.clinube.finanzas.Finanzas.Companion.porcentaje
import com.clinube.finanzas.Finanzas.Companion.texto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * `apps/finanzas/calculos.py`: las cuentas, separadas de las rutas.
 *
 * Todo en `BigDecimal` con el contexto de Python (28 cifras, a la mitad
 * hacia el par) en CADA operación, como hace `decimal`, y redondeado a
 * céntimos «a la mitad hacia arriba» donde Django llama a `centavos`. Una
 * multiplicación sin el contexto de Python acaba, tarde o temprano, en un
 * rol que no cuadra por un centavo con el que calculó Django.
 */
@Component
class CalculosDeFinanzas(
    private val finanzas: Finanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: ObjectMapper,
) {

    private val clinica get() = finanzas.clinica

    private fun suma(sql: String, parametros: Map<String, Any?>): BigDecimal =
        centavos(jdbc.queryForObject(sql, mapOf("clinica" to clinica) + parametros, BigDecimal::class.java) ?: BigDecimal.ZERO)

    // ── Lo que entra y lo que sale ───────────────────────────────────

    private fun ingresos(desde: LocalDate, hasta: LocalDate, metodo: String? = null): BigDecimal = suma(
        """select sum(amount) from billing_payment where tenant_id = :clinica and date >= :desde and date <= :hasta""" +
            (if (metodo != null) " and method = :metodo" else ""),
        mapOf("desde" to desde, "hasta" to hasta, "metodo" to metodo),
    )

    private val EGRESOS_PAGADOS = """from finanzas_egreso e where e.tenant_id = :clinica and e.fecha >= :desde and e.fecha <= :hasta
        and not e.anulado and e.estado = 'pagado'"""

    private fun egresos(desde: LocalDate, hasta: LocalDate) =
        suma("select sum(e.monto) $EGRESOS_PAGADOS", mapOf("desde" to desde, "hasta" to hasta))

    /** El estado de un mes: lo cobrado, lo gastado y lo que queda pendiente. */
    fun resumen(desde: LocalDate, hasta: LocalDate): LinkedHashMap<String, Any?> {
        val p = mapOf("desde" to desde, "hasta" to hasta, "clinica" to clinica)
        val ingresos = ingresos(desde, hasta)
        val porForma = FORMAS_COBRO.map { (metodo, nombre) -> linkedMapOf("forma" to nombre, "total" to texto(ingresos(desde, hasta, metodo))) }
        val cobros = jdbc.queryForObject(
            "select count(*) from billing_payment where tenant_id = :clinica and date >= :desde and date <= :hasta", p, Long::class.java,
        )!!
        val totalEgresos = egresos(desde, hasta)
        // `values("categoria__nombre").annotate(t=Sum("monto")).order_by("-t")`.
        val porCategoria = jdbc.query(
            """select c.nombre, sum(e.monto) as t from finanzas_egreso e inner join finanzas_categoriaegreso c on (e.categoria_id = c.id)
               where e.tenant_id = :clinica and e.fecha >= :desde and e.fecha <= :hasta and not e.anulado and e.estado = 'pagado'
               group by 1 order by 2 desc""",
            p,
        ) { f, _ -> linkedMapOf("categoria" to f.getString("nombre"), "total" to texto(centavos(f.getBigDecimal("t")))) }

        val hoy = finanzas.hoy()
        val pendientes = """select sum(i.amount - coalesce((select sum(x.amount) from billing_payment x where x.installment_id = i.id), 0))
            from billing_installment i where i.tenant_id = :clinica and i.status in ('pending', 'overdue')"""
        val porCobrar = suma(pendientes, emptyMap())
        val vencido = suma("$pendientes and i.due_date < :hoy", mapOf("hoy" to hoy))
        val cuentas = "from finanzas_egreso where tenant_id = :clinica and not anulado and estado = 'por_pagar'"

        return linkedMapOf(
            "desde" to desde.toString(), "hasta" to hasta.toString(),
            "ingresos" to texto(ingresos),
            "ingresos_por_forma" to porForma,
            "cobros" to cobros,
            "egresos" to texto(totalEgresos),
            "egresos_por_categoria" to porCategoria,
            "utilidad" to texto(ingresos - totalEgresos),
            "margen" to if (ingresos.signum() != 0) {
                texto(centavos((ingresos - totalEgresos).divide(ingresos, PY).multiply(CIEN, PY)))
            } else null,
            "por_cobrar" to texto(porCobrar),
            "por_cobrar_vencido" to texto(vencido),
            "por_pagar" to texto(suma("select sum(monto) $cuentas", emptyMap())),
            "por_pagar_vencido" to texto(suma("select sum(monto) $cuentas and vence < :hoy", mapOf("hoy" to hoy))),
            "honorarios_pendientes" to texto(suma("select sum(a_pagar) from finanzas_liquidacion where tenant_id = :clinica and estado = 'borrador'", emptyMap())),
            "roles_pendientes" to texto(suma("select sum(neto) from finanzas_rolpagos where tenant_id = :clinica and estado = 'borrador'", emptyMap())),
            "obligaciones" to obligacionesPendientes(desde),
        )
    }

    /** IESS y SRI sin pagar del mes anterior (vencen en este) y del mes en curso. */
    fun obligacionesPendientes(inicio: LocalDate): List<Map<String, Any?>> =
        listOf(inicio.minusMonths(1).withDayOfMonth(1), inicio).flatMap { obligaciones(it) }.filter { it["pagado"] == false }

    /** Ingresos y egresos de los últimos meses, para el gráfico. */
    fun serie(hasta: LocalDate, meses: Int = 6): List<Map<String, Any?>> =
        (meses - 1 downTo 0).map { atras ->
            val mes = YearMonth.from(hasta).minusMonths(atras.toLong())
            val ing = ingresos(mes.atDay(1), mes.atEndOfMonth())
            val egr = egresos(mes.atDay(1), mes.atEndOfMonth())
            linkedMapOf("mes" to mes.toString(), "ingresos" to texto(ing), "egresos" to texto(egr), "utilidad" to texto(ing - egr))
        }

    // ── Caja del día ─────────────────────────────────────────────────

    /** Lo que tendría que haber en efectivo al cerrar el día. */
    fun caja(dia: LocalDate): LinkedHashMap<String, Any?> {
        val p = mapOf("clinica" to clinica, "dia" to dia)
        val anterior = jdbc.query(
            "select fecha, contado, retirado from finanzas_cierrecaja where tenant_id = :clinica and fecha < :dia order by fecha desc limit 1", p,
        ) { f, _ -> f.fecha("fecha") to centavos(f.getBigDecimal("contado") - f.getBigDecimal("retirado")) }.firstOrNull()
        val saldoInicial = anterior?.second ?: CERO
        // `select_related("patient")`: la misma consulta que Django, con el
        // mismo orden (el de la base: `Payment` no tiene uno propio).
        val cobros = jdbc.query(
            """select p.amount, pa.first_name, pa.last_name from billing_payment p inner join patients_patient pa on (p.patient_id = pa.id)
               where p.tenant_id = :clinica and p.date >= :dia and p.date <= :dia and p.method = 'cash'""",
            p,
        ) { f, _ -> linkedMapOf("tipo" to "cobro", "detalle" to "${f.getString("first_name")} ${f.getString("last_name")}", "monto" to texto(f.getBigDecimal("amount"))) }
        val salidas = jdbc.query(
            """select concepto, monto from finanzas_egreso where tenant_id = :clinica and fecha = :dia and not anulado and estado = 'pagado'
               and forma_pago = 'efectivo' order by fecha desc, created_at desc""",
            p,
        ) { f, _ -> linkedMapOf("tipo" to "egreso", "detalle" to f.getString("concepto"), "monto" to texto(f.getBigDecimal("monto"))) }
        val entra = ingresos(dia, dia, "cash")
        val sale = suma(
            """select sum(monto) from finanzas_egreso where tenant_id = :clinica and fecha = :dia and not anulado and estado = 'pagado'
               and forma_pago = 'efectivo'""",
            p,
        )
        return linkedMapOf(
            "fecha" to dia.toString(),
            "saldo_inicial" to texto(saldoInicial),
            "viene_del_cierre" to anterior?.first?.toString(),
            "cobros_efectivo" to texto(entra),
            "egresos_efectivo" to texto(sale),
            "esperado" to texto(saldoInicial + entra - sale),
            "otros_cobros" to texto(suma(
                "select sum(amount) from billing_payment where tenant_id = :clinica and date >= :dia and date <= :dia and not (method = 'cash')", p,
            )),
            "movimientos" to cobros + salidas,
        )
    }

    // ── Perfil laboral ───────────────────────────────────────────────

    /** `PerfilLaboral`, o uno sin guardar con los valores por defecto. */
    data class Perfil(
        val id: UUID?,
        val modalidad: String = "ninguna",
        val cedula: String = "",
        val cargo: String = "",
        val baseHonorarios: String = "cobrado",
        val porcentaje: BigDecimal = BigDecimal("40.00"),
        val montoFijo: BigDecimal = CERO,
        val emiteFactura: Boolean = true,
        val sueldo: BigDecimal = CERO,
        val fechaIngreso: LocalDate? = null,
        val decimoTerceroMensual: Boolean = false,
        val decimoCuartoMensual: Boolean = false,
        val fondosReservaMensual: Boolean = true,
    )

    /** `PerfilLaboral.objects.filter(usuario=…).first() or PerfilLaboral(usuario=…)`. */
    fun perfil(usuario: UUID): Perfil = jdbc.query(
        "select * from finanzas_perfillaboral where usuario_id = :usuario order by id limit 1", mapOf("usuario" to usuario),
    ) { f, _ ->
        Perfil(
            f.uuid("id"), f.getString("modalidad"), f.getString("cedula"), f.getString("cargo"), f.getString("base_honorarios"),
            f.getBigDecimal("porcentaje"), f.getBigDecimal("monto_fijo"), f.getBoolean("emite_factura"), f.getBigDecimal("sueldo"),
            f.getObject("fecha_ingreso", LocalDate::class.java), f.getBoolean("decimo_tercero_mensual"),
            f.getBoolean("decimo_cuarto_mensual"), f.getBoolean("fondos_reserva_mensual"),
        )
    }.firstOrNull() ?: Perfil(null)

    // ── Honorarios ───────────────────────────────────────────────────

    data class YaLiquidado(val cobros: Set<String>, val descuentos: Set<String>, val rangos: List<Pair<LocalDate, LocalDate>>, val inicio: LocalDate?)

    /**
     * Lo que ya entró en alguna liquidación vigente del profesional: los ids
     * de cobros y de descuentos, y desde cuándo se le liquida en Clinube. Las
     * liquidaciones antiguas sin ids se cubren por su rango de fechas.
     */
    fun yaLiquidado(usuario: UUID): YaLiquidado {
        val cobros = mutableSetOf<String>()
        val descuentos = mutableSetOf<String>()
        val rangos = mutableListOf<Pair<LocalDate, LocalDate>>()
        var inicio: LocalDate? = null
        jdbc.query(
            """select desde, hasta, detalle::text as detalle from finanzas_liquidacion
               where tenant_id = :clinica and profesional_id = :usuario and not (estado = 'anulada') order by hasta desc, numero desc""",
            mapOf("clinica" to clinica, "usuario" to usuario),
        ) { f, _ ->
            val d = json.readTree(f.getString("detalle"))
            val desde = f.fecha("desde")
            val lista = { clave: String -> d.get(clave)?.takeIf { it.isArray }?.toList() ?: emptyList() }
            if (lista("cobros").any { it.isObject && it.has("id") } || d.has("cobros_ids")) {
                cobros += ids(d.get("cobros_ids"), lista("cobros"))
                descuentos += ids(d.get("descuentos_ids"), lista("descuentos"))
            } else {
                rangos += desde to f.fecha("hasta")
            }
            if (inicio == null || desde < inicio) inicio = desde
        }
        return YaLiquidado(cobros, descuentos, rangos, inicio)
    }

    /** `d.get("cobros_ids") or [c["id"] for c in d.get("cobros", []) if "id" in c]`. */
    private fun ids(guardados: JsonNode?, filas: List<JsonNode>): List<String> =
        if (Python.verdad(guardados)) guardados!!.map { it.asText() }
        else filas.filter { it.isObject && it.has("id") }.map { it.get("id").asText() }

    private fun fuera(fecha: LocalDate, rangos: List<Pair<LocalDate, LocalDate>>) = rangos.none { (a, b) -> fecha in a..b }

    data class Liquidacion(
        val base: BigDecimal, val porcentaje: BigDecimal, val honorario: BigDecimal, val descuentos: BigDecimal,
        val ajustes: BigDecimal, val retencionPorcentaje: BigDecimal, val retencion: BigDecimal, val aPagar: BigDecimal,
        val detalle: LinkedHashMap<String, Any?>,
    )

    /**
     * Lo que le toca a un profesional por honorarios en un período:
     *
     *     honorario  = % de lo cobrado a sus pacientes (o el monto fijo)
     *   − descuentos = gastos marcados para descontarle (laboratorio…)
     *   ± ajustes    = bonos o anticipos puestos a mano
     *   − retención  = Impuesto a la Renta, si factura a la clínica
     *   = a pagar
     *
     * Entran los cobros y descuentos del período y, además, los de fechas
     * anteriores que no entraron en ninguna liquidación.
     */
    fun liquidacion(usuario: UUID, desde: LocalDate, hasta: LocalDate, ajustesPedidos: BigDecimal): Liquidacion {
        val perfil = perfil(usuario)
        val config = finanzas.configuracion()
        val ajustes = centavos(ajustesPedidos)
        val hechos = yaLiquidado(usuario)
        val piso = hechos.inicio?.let { minOf(desde, it) } ?: desde

        fun pendiente(id: String, fecha: LocalDate, ya: Set<String>): Boolean {
            if (id in ya || !fuera(fecha, hechos.rangos)) return false
            // Antes del período, solo lo que ya caía bajo liquidaciones de Clinube.
            return !(fecha < desde && (hechos.inicio == null || fecha < hechos.inicio))
        }

        data class Cobro(val id: String, val fecha: LocalDate, val paciente: String, val metodo: String, val monto: BigDecimal)
        val pagos = jdbc.query(
            """select p.id, p.date, p.method, p.amount, pa.first_name, pa.last_name from billing_payment p
               inner join patients_patient pa on (p.patient_id = pa.id)
               where p.tenant_id = :clinica and p.profesional_id = :usuario and p.date >= :piso and p.date <= :hasta
               order by p.date asc""",
            mapOf("clinica" to clinica, "usuario" to usuario, "piso" to piso, "hasta" to hasta),
        ) { f, _ ->
            Cobro(f.uuid("id").toString(), f.fecha("date"), "${f.getString("first_name")} ${f.getString("last_name")}", f.getString("method"), f.getBigDecimal("amount"))
        }.filter { pendiente(it.id, it.fecha, hechos.cobros) }

        data class Gasto(val id: String, val fecha: LocalDate, val concepto: String, val monto: BigDecimal)
        val gastos = jdbc.query(
            """select id, fecha, concepto, monto from finanzas_egreso
               where tenant_id = :clinica and profesional_id = :usuario and descontar_al_profesional and not anulado
               and fecha >= :piso and fecha <= :hasta and not (origen in (:calculados)) order by fecha asc""",
            mapOf("clinica" to clinica, "usuario" to usuario, "piso" to piso, "hasta" to hasta, "calculados" to ORIGENES_CALCULADOS),
        ) { f, _ -> Gasto(f.uuid("id").toString(), f.fecha("fecha"), f.getString("concepto"), f.getBigDecimal("monto")) }
            .filter { pendiente(it.id, it.fecha, hechos.descuentos) }

        val (base, porcentajeAplicado, honorario) = if (perfil.baseHonorarios == "fijo") {
            Triple(CERO, CERO, centavos(perfil.montoFijo))
        } else {
            val base = centavos(pagos.fold(CERO) { s, p -> s.add(p.monto, PY) })
            Triple(base, perfil.porcentaje, pct(base, perfil.porcentaje))
        }
        val descuentos = centavos(gastos.fold(CERO) { s, g -> s.add(g.monto, PY) })
        // Se retiene sobre lo que factura: su honorario y los bonos. Un
        // anticipo ya pagado no cambia lo que factura.
        val retencionPct = if (perfil.emiteFactura) config.retencionHonorarios else CERO
        val retencion = pct(honorario.add(ajustes.max(CERO), PY), retencionPct)
        val aPagar = centavos(honorario.subtract(descuentos, PY).add(ajustes, PY).subtract(retencion, PY))

        return Liquidacion(
            base, porcentajeAplicado, honorario, descuentos, ajustes, retencionPct, retencion, aPagar,
            linkedMapOf(
                "modalidad" to perfil.baseHonorarios,
                "cobros" to pagos.map {
                    linkedMapOf("id" to it.id, "fecha" to it.fecha.toString(), "paciente" to it.paciente,
                        "forma" to (FORMAS_COBRO[it.metodo] ?: it.metodo), "monto" to texto(it.monto), "atrasado" to (it.fecha < desde))
                },
                "descuentos" to gastos.map {
                    linkedMapOf("id" to it.id, "fecha" to it.fecha.toString(), "concepto" to it.concepto, "monto" to texto(it.monto),
                        "atrasado" to (it.fecha < desde))
                },
                "cobros_ids" to pagos.map { it.id },
                "descuentos_ids" to gastos.map { it.id },
            ),
        )
    }

    // ── Rol de pagos ─────────────────────────────────────────────────

    data class ValoresDelRol(
        val diasTrabajados: Int = 30,
        val horasSuplementarias: BigDecimal = CERO,
        val horasExtraordinarias: BigDecimal = CERO,
        val horasExtra: BigDecimal = CERO,
        val otrosIngresos: BigDecimal = CERO,
        val anticipos: BigDecimal = CERO,
        val prestamosIess: BigDecimal = CERO,
        val otrosDescuentos: BigDecimal = CERO,
    )

    private fun cumplioUnAnio(ingreso: LocalDate?, finDeMes: LocalDate): Boolean =
        // `replace(year=+1)`, y el 29 de febrero pasa al 28: lo mismo que `plusYears`.
        ingreso != null && !ingreso.plusYears(1).isAfter(finDeMes)

    /**
     * El rol de pagos de un mes, con el formato de siempre en Ecuador:
     * ingresos (sueldo proporcional, horas suplementarias y extraordinarias
     * sobre el valor hora, bonos y, si lo pidió, décimos y fondos), los
     * descuentos (aporte personal, anticipos, préstamos) y, aparte, lo que le
     * cuesta a la clínica.
     */
    fun rol(empleado: UUID, mes: LocalDate, v: ValoresDelRol = ValoresDelRol()): LinkedHashMap<String, Any?> {
        val config = finanzas.configuracion()
        val perfil = perfil(empleado)
        val inicio = mes.withDayOfMonth(1)
        val fin = YearMonth.from(mes).atEndOfMonth()
        val dias = v.diasTrabajados.coerceIn(0, 30)
        val diasD = BigDecimal(dias)

        val sueldo = centavos(perfil.sueldo.multiply(diasD, PY).divide(BigDecimal(30), PY))
        val horasExtra = centavos(v.horasExtra)
        val otrosIngresos = centavos(v.otrosIngresos)
        // Valor hora: sueldo mensual / 240 (30 días de 8 horas).
        val valorHora = perfil.sueldo.divide(BigDecimal(240), PY)
        val h50 = v.horasSuplementarias
        val h100 = v.horasExtraordinarias
        fun recargo(horas: BigDecimal, porcentaje: BigDecimal) =
            centavos(valorHora.multiply(horas, PY).multiply(CIEN.add(porcentaje, PY), PY).divide(CIEN, PY))
        val suplementarias = recargo(h50, config.recargoSuplementarias)
        val extraordinarias = recargo(h100, config.recargoExtraordinarias)
        // Lo que aporta al IESS: sueldo, horas extra y bonos.
        val aportable = sueldo.add(suplementarias, PY).add(extraordinarias, PY).add(horasExtra, PY).add(otrosIngresos, PY)

        val decimoTercero = centavos(aportable.divide(BigDecimal(12), PY))
        val decimoCuarto = centavos(config.salarioBasico.divide(BigDecimal(12), PY).multiply(diasD, PY).divide(BigDecimal(30), PY))
        val conFondos = cumplioUnAnio(perfil.fechaIngreso, fin)
        val fondos = if (conFondos) pct(aportable, config.fondosReserva) else CERO

        val ingresos = mutableListOf(("Sueldo" + if (dias < 30) " ($dias días)" else "") to sueldo)
        if (suplementarias.signum() != 0) {
            ingresos += "Horas suplementarias (${Finanzas.numero(h50)} h al ${porcentaje(config.recargoSuplementarias)})" to suplementarias
        }
        if (extraordinarias.signum() != 0) {
            ingresos += "Horas extraordinarias (${Finanzas.numero(h100)} h al ${porcentaje(config.recargoExtraordinarias)})" to extraordinarias
        }
        if (horasExtra.signum() != 0) ingresos += "Otras horas extra" to horasExtra
        if (otrosIngresos.signum() != 0) ingresos += "Bonos y comisiones" to otrosIngresos
        val provisiones = mutableListOf<Pair<String, BigDecimal>>()
        for ((nombre, valor, mensual) in listOf(
            Triple("Décimo tercer sueldo", decimoTercero, perfil.decimoTerceroMensual),
            Triple("Décimo cuarto sueldo", decimoCuarto, perfil.decimoCuartoMensual),
            Triple("Fondos de reserva", fondos, perfil.fondosReservaMensual),
        )) {
            if (valor.signum() == 0) continue
            (if (mensual) ingresos else provisiones).add(nombre to valor)
        }
        provisiones += "Vacaciones" to centavos(aportable.divide(BigDecimal(24), PY))

        val aportePersonal = pct(aportable, config.aportePersonalIess)
        val descuentos = mutableListOf("Aporte personal IESS ${porcentaje(config.aportePersonalIess)}" to aportePersonal)
        val entregados = anticiposEntregados(empleado, inicio, fin)
        if (entregados.signum() != 0) descuentos += "Anticipos entregados en el mes" to entregados
        for ((nombre, valor) in listOf("Otros anticipos" to v.anticipos, "Préstamos IESS" to v.prestamosIess, "Otros descuentos" to v.otrosDescuentos)) {
            if (centavos(valor).signum() != 0) descuentos += nombre to centavos(valor)
        }

        val totalIngresos = centavos(ingresos.fold(CERO) { s, (_, x) -> s.add(x, PY) })
        val totalDescuentos = centavos(descuentos.fold(CERO) { s, (_, x) -> s.add(x, PY) })
        val neto = totalIngresos - totalDescuentos

        val patronalPct = config.aportePatronalIess.add(config.aporteIeceSecap, PY)
        val aportePatronal = pct(aportable, patronalPct)
        val totalProvisiones = centavos(provisiones.fold(CERO) { s, (_, x) -> s.add(x, PY) })
        val costo = totalIngresos.add(aportePatronal, PY).add(totalProvisiones, PY)

        fun filas(lista: List<Pair<String, BigDecimal>>) = lista.map { (n, x) -> linkedMapOf("concepto" to n, "valor" to texto(x)) }

        return linkedMapOf(
            "mes" to anioMes(inicio),
            "dias_trabajados" to dias,
            "ingresos" to filas(ingresos),
            "descuentos" to filas(descuentos),
            "total_ingresos" to texto(totalIngresos),
            "total_descuentos" to texto(totalDescuentos),
            "neto" to texto(neto),
            "aporte_patronal" to texto(aportePatronal),
            "aporte_patronal_porcentaje" to texto(patronalPct),
            "provisiones" to filas(provisiones),
            "total_provisiones" to texto(totalProvisiones),
            "costo_empleador" to texto(costo),
            "fondos_reserva_desde" to if (conFondos) null else "Desde el segundo año de trabajo",
            // Lo que se le debe al IESS por este rol (planilla del mes):
            "iess" to linkedMapOf(
                "aporte_personal" to texto(aportePersonal),
                "aporte_patronal" to texto(aportePatronal),
                "fondos_reserva" to texto(if (!perfil.fondosReservaMensual) fondos else CERO),
                "prestamos" to texto(centavos(v.prestamosIess)),
            ),
            // Lo que se acumula para pagarle después (si no se mensualiza):
            "acumula" to linkedMapOf(
                "decimo_tercero" to texto(if (perfil.decimoTerceroMensual) CERO else decimoTercero),
                "decimo_cuarto" to texto(if (perfil.decimoCuartoMensual) CERO else decimoCuarto),
            ),
        )
    }

    /** Egresos del mes marcados para descontárselos (anticipos, préstamos de la clínica). */
    fun anticiposEntregados(empleado: UUID, desde: LocalDate, hasta: LocalDate): BigDecimal = suma(
        """select sum(monto) from finanzas_egreso where tenant_id = :clinica and profesional_id = :empleado and descontar_al_profesional
           and not anulado and fecha >= :desde and fecha <= :hasta and not (origen in (:calculados))""",
        mapOf("empleado" to empleado, "desde" to desde, "hasta" to hasta, "calculados" to ORIGENES_CALCULADOS),
    )

    // ── Obligaciones: lo que se paga a terceros ──────────────────────

    private fun siguienteMes(inicio: LocalDate, dia: Int): LocalDate {
        val mes = YearMonth.from(inicio).plusMonths(1)
        return mes.atDay(minOf(dia, mes.lengthOfMonth()))
    }

    private fun decimalDe(valor: JsonNode?): BigDecimal {
        if (!Python.verdad(valor)) return BigDecimal.ZERO
        return (Python.decimal(Python.str(valor!!)) as? Python.Decimal.Numero)?.valor() ?: Finanzas.errorInterno("Decimal: $valor")
    }

    /** `_de_calculo`: un valor del cálculo de un rol; los guardados antes de tener `grupo` se leen de la lista. */
    private fun deCalculo(calculo: JsonNode, grupo: String, clave: String, lista: String, prefijo: String): BigDecimal {
        if (calculo.has(grupo)) return decimalDe(calculo.get(grupo).get(clave))
        return (calculo.get(lista)?.toList() ?: emptyList())
            .filter { it.get("concepto").asText().startsWith(prefijo) }
            .fold(BigDecimal.ZERO) { s, f -> s.add(decimalDe(f.get("valor")), PY) }
    }

    data class Pago(val id: UUID, val fecha: LocalDate, val monto: BigDecimal)

    /** `_pago_registrado`: el egreso que salda esa obligación, si lo hay. */
    private fun pagoRegistrado(origen: String, periodo: LocalDate, profesional: UUID? = null): Pago? = jdbc.query(
        """select id, fecha, monto from finanzas_egreso where tenant_id = :clinica and origen = :origen and periodo = :periodo and not anulado""" +
            (if (profesional != null) " and profesional_id = :profesional" else "") + " order by fecha desc, created_at desc limit 1",
        mapOf("clinica" to clinica, "origen" to origen, "periodo" to periodo, "profesional" to profesional),
    ) { f, _ -> Pago(f.uuid("id"), f.fecha("fecha"), f.getBigDecimal("monto")) }.firstOrNull()

    /**
     * Lo que la clínica debe pagar por el mes que empieza en `inicio`: la
     * planilla del IESS (vence el 15 del mes siguiente) y las retenciones al
     * SRI de las liquidaciones pagadas ese mes (formulario 103).
     */
    fun obligaciones(inicio: LocalDate): List<Map<String, Any?>> {
        val fin = YearMonth.from(inicio).atEndOfMonth()
        val resultado = mutableListOf<Map<String, Any?>>()

        val calculos = jdbc.query(
            """select calculo::text as calculo from finanzas_rolpagos where tenant_id = :clinica and mes = :inicio and not (estado = 'anulado')""",
            mapOf("clinica" to clinica, "inicio" to inicio),
        ) { f, _ -> json.readTree(f.getString("calculo")) }
        var personal = BigDecimal.ZERO
        var patronal = BigDecimal.ZERO
        var fondos = BigDecimal.ZERO
        var prestamos = BigDecimal.ZERO
        for (c in calculos) {
            personal = personal.add(deCalculo(c, "iess", "aporte_personal", "descuentos", "Aporte personal"), PY)
            patronal = patronal.add(decimalDe(c.get("aporte_patronal")), PY)
            fondos = fondos.add(deCalculo(c, "iess", "fondos_reserva", "provisiones", "Fondos de reserva"), PY)
            prestamos = prestamos.add(deCalculo(c, "iess", "prestamos", "descuentos", "Préstamos IESS"), PY)
        }
        val total = centavos(personal.add(patronal, PY).add(fondos, PY).add(prestamos, PY))
        if (total.signum() != 0) {
            resultado += obligacion(
                "iess", "Planilla del IESS", inicio, total, siguienteMes(inicio, 15),
                listOf(
                    "Aporte personal (descontado en los roles)" to personal,
                    "Aporte patronal e IECE/SECAP" to patronal,
                    "Fondos de reserva no mensualizados" to fondos,
                    "Préstamos IESS descontados" to prestamos,
                ),
                "${calculos.size} rol" + if (calculos.size != 1) "es" else "",
            )
        }

        val retenciones = jdbc.query(
            """select l.numero, l.retencion, u.full_name from finanzas_liquidacion l
               inner join finanzas_egreso e on (l.egreso_id = e.id) inner join accounts_user u on (l.profesional_id = u.id)
               where l.tenant_id = :clinica and l.estado = 'pagada' and e.fecha >= :inicio and e.fecha <= :fin and l.retencion > 0
               order by l.hasta desc, l.numero desc""",
            mapOf("clinica" to clinica, "inicio" to inicio, "fin" to fin),
        ) { f, _ -> "${f.getString("full_name")} — liquidación N.º ${cifras(f.getLong("numero"), 5)}" to f.getBigDecimal("retencion") }
        val retenido = centavos(retenciones.fold(CERO) { s, (_, x) -> s.add(x, PY) })
        if (retenido.signum() != 0) {
            resultado += obligacion(
                "sri", "Retenciones en la fuente al SRI", inicio, retenido, siguienteMes(inicio, 28), retenciones,
                "Formulario 103; la fecha exacta depende del noveno dígito del RUC",
            )
        }
        return resultado
    }

    private fun obligacion(
        tipo: String, nombre: String, inicio: LocalDate, monto: BigDecimal, vence: LocalDate,
        detalle: List<Pair<String, BigDecimal>>, nota: String,
    ): Map<String, Any?> {
        val pago = pagoRegistrado(tipo, inicio)
        return linkedMapOf(
            "tipo" to tipo, "nombre" to nombre, "periodo" to anioMes(inicio), "monto" to texto(monto),
            "vence" to vence.toString(), "nota" to nota,
            "detalle" to detalle.filter { centavos(it.second).signum() != 0 }
                .map { linkedMapOf("concepto" to it.first, "valor" to texto(centavos(it.second))) },
            "pagado" to (pago != null), "egreso" to pago?.id?.toString(),
            "fecha_pago" to pago?.fecha?.toString(),
            "diferencia" to pago?.let { texto(centavos(monto.subtract(it.monto, PY))) },
        )
    }

    // ── Décimos acumulados ───────────────────────────────────────────

    /** (desde, hasta, vence) del décimo tercero o cuarto que se paga en `anio`. */
    fun periodoDecimo(tipo: String, anio: Int, region: String): Triple<LocalDate, LocalDate, LocalDate> = when {
        tipo == "tercero" -> Triple(LocalDate.of(anio - 1, 12, 1), LocalDate.of(anio, 11, 30), LocalDate.of(anio, 12, 24))
        region == "costa" -> Triple(LocalDate.of(anio - 1, 3, 1), YearMonth.of(anio, 2).atEndOfMonth(), LocalDate.of(anio, 3, 15))
        else -> Triple(LocalDate.of(anio - 1, 8, 1), LocalDate.of(anio, 7, 31), LocalDate.of(anio, 8, 15))
    }

    /**
     * Lo acumulado de un décimo para cada persona que no lo cobra cada mes,
     * sumando lo provisionado en sus roles del período legal.
     */
    fun decimos(tipo: String, anio: Int): LinkedHashMap<String, Any?> {
        val region = finanzas.configuracion().region
        val (desde, hasta, vence) = periodoDecimo(tipo, anio, region)
        val nombre = if (tipo == "tercero") "Décimo tercer sueldo" else "Décimo cuarto sueldo"

        data class Acumulado(val empleado: UUID, val nombre: String, var meses: Int = 0, var monto: BigDecimal = CERO)
        val porPersona = LinkedHashMap<UUID, Acumulado>()
        jdbc.query(
            """select r.empleado_id, r.calculo::text as calculo, u.full_name from finanzas_rolpagos r
               inner join accounts_user u on (r.empleado_id = u.id)
               where r.tenant_id = :clinica and r.mes >= :desde and r.mes <= :hasta and not (r.estado = 'anulado') order by r.mes desc""",
            mapOf("clinica" to clinica, "desde" to desde, "hasta" to hasta),
        ) { f, _ ->
            val valor = deCalculo(json.readTree(f.getString("calculo")), "acumula", "decimo_$tipo", "provisiones", nombre)
            if (valor.signum() != 0) {
                val fila = porPersona.getOrPut(f.uuid("empleado_id")) { Acumulado(f.uuid("empleado_id"), f.getString("full_name")) }
                fila.meses += 1
                fila.monto = fila.monto.add(valor, PY)
            }
        }
        val periodo = LocalDate.of(anio, 1, 1)
        // `sorted(…, key=full_name)`: por el texto en Python, no por la intercalación de la base.
        val filas = porPersona.values.sortedWith { a, b -> Python.compararTextos(a.nombre, b.nombre) }.map { f ->
            val pago = pagoRegistrado("decimo_$tipo", periodo, f.empleado)
            linkedMapOf(
                "empleado" to f.empleado.toString(), "nombre" to f.nombre, "meses" to f.meses, "monto" to texto(centavos(f.monto)),
                "pagado" to (pago != null), "egreso" to pago?.id?.toString(), "fecha_pago" to pago?.fecha?.toString(),
            )
        }
        val total = centavos(filas.fold(CERO) { s, f -> s.add(BigDecimal(f["monto"] as String), PY) })
        val pendiente = centavos(filas.filter { it["pagado"] == false }.fold(CERO) { s, f -> s.add(BigDecimal(f["monto"] as String), PY) })
        return linkedMapOf(
            "tipo" to tipo, "nombre" to nombre, "anio" to anio, "region" to region,
            "desde" to desde.toString(), "hasta" to hasta.toString(), "vence" to vence.toString(),
            "filas" to filas, "total" to texto(total), "pendiente" to texto(pendiente),
        )
    }

    // ── Lecturas para las rutas ──────────────────────────────────────

    /** El instante de una fila, para los serializadores. */
    fun instanteDe(f: java.sql.ResultSet, columna: String): Instant = f.instante(columna)

    fun uuidONulo(f: java.sql.ResultSet, columna: String): UUID? = f.uuidONulo(columna)
}
