package com.clinube.finanzas

import com.clinube.comun.documentos.EstiloDeDocumento
import com.clinube.comun.documentos.EstiloDeDocumento.Campo
import com.clinube.comun.documentos.EstilosDeDocumento
import com.clinube.comun.python.Python
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.finanzas.Finanzas.Companion.FORMAS_COBRO
import com.clinube.finanzas.Finanzas.Companion.MESES
import com.clinube.finanzas.Finanzas.Companion.cifras
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lowagie.text.Element
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Los papeles de finanzas (`apps/finanzas/documentos.py`): el comprobante de
 * cobro, la liquidación de honorarios y el rol de pagos, con el membrete,
 * los colores y el pie de cada clínica.
 *
 * El comprobante de cobro NO es una factura: lo dice en el propio papel. La
 * factura válida en Ecuador es la electrónica del SRI.
 */
@Component
class DocumentosDeFinanzas(
    private val estilos: EstilosDeDocumento,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: ObjectMapper,
) {

    companion object {
        /** `_pdf(contenido, nombre)`: se abre en el navegador, no se descarga. */
        fun respuesta(contenido: ByteArray, nombre: String): ResponseEntity<ByteArray> = ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$nombre\"")
            .body(contenido)

        /** «$1.234,56» — como se escribe en Ecuador. */
        fun dinero(valor: BigDecimal?): String {
            val v = (valor ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN)
            val (entero, decimales) = v.abs().toPlainString().split(".")
            val miles = entero.reversed().chunked(3).joinToString(".").reversed()
            return "${if (v.signum() < 0) "-" else ""}$$miles,$decimales"
        }

        fun dinero(texto: String?): String = dinero(if (texto.isNullOrEmpty()) BigDecimal.ZERO else BigDecimal(texto))

        private val UNIDADES = listOf("", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
            "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte", "veintiuno",
            "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve")
        private val DECENAS = listOf("", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
        private val CENTENAS = listOf("", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos",
            "setecientos", "ochocientos", "novecientos")

        private fun hastaMil(n: Int): String {
            if (n == 0) return ""
            if (n == 100) return "cien"
            val c = n / 100
            val resto = n % 100
            val partes = mutableListOf<String>()
            if (c != 0) partes += CENTENAS[c]
            if (resto < 30) partes += UNIDADES[resto]
            else partes += DECENAS[resto / 10] + if (resto % 10 != 0) " y ${UNIDADES[resto % 10]}" else ""
            return partes.filter { it.isNotEmpty() }.joinToString(" ")
        }

        /** «uno» → «un» y «veintiuno» → «veintiún» delante de «mil» o de «dólares». */
        private fun apocope(texto: String): String = when {
            texto.endsWith("veintiuno") -> texto.dropLast("veintiuno".length) + "veintiún"
            texto == "uno" || texto.endsWith(" uno") -> texto.dropLast(3) + "un"
            else -> texto
        }

        /** «Ciento veintiún con 50/100 dólares»: la cantidad en letras de los recibos de aquí. Hasta 999.999,99. */
        fun enLetras(valor: BigDecimal?): String {
            val v = (valor ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN).abs()
            val entero = v.toBigInteger().toInt()
            val centavos = v.subtract(BigDecimal(entero)).movePointRight(2).toInt()
            val miles = entero / 1000
            val unidades = entero % 1000
            // Django solo sabe hasta 999.999: con un millón se sale de la tabla de centenas y da 500.
            if (miles >= 1000) Finanzas.errorInterno("Cantidad en letras fuera de rango: $v")
            val partes = mutableListOf<String>()
            if (miles != 0) partes += if (miles == 1) "mil" else "${apocope(hastaMil(miles))} mil"
            if (unidades != 0) partes += apocope(hastaMil(unidades))
            val texto = partes.joinToString(" ").ifEmpty { "cero" }
            return "${texto.substring(0, 1).uppercase()}${texto.substring(1)} con ${"%02d".format(centavos)}/100 dólares"
        }

        fun enLetras(texto: String?) = enLetras(if (texto.isNullOrEmpty()) BigDecimal.ZERO else BigDecimal(texto))

        private val FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        /** `fecha_documento`: 30/09/2026; sin fecha, «—»; un texto que no es fecha, tal cual. */
        fun fecha(valor: Any?): String = when (valor) {
            null, "" -> "—"
            is LocalDate -> valor.format(FECHA)
            is String -> runCatching { LocalDate.parse(valor).format(FECHA) }.getOrDefault(valor)
            else -> valor.toString()
        }

        /** `str(Decimal).replace(".", ",")`. */
        private fun conComa(valor: BigDecimal) = valor.toPlainString().replace(".", ",")
    }

    private fun estilo(): EstiloDeDocumento = estilos.de(finanzas.clinica)

    // ── Comprobante de cobro ─────────────────────────────────────────

    fun comprobanteDeCobro(pago: UUID, numero: Long): ByteArray {
        data class Datos(val paciente: String, val cedula: String, val fecha: LocalDate, val metodo: String, val profesional: String?,
                         val cuota: Long?, val monto: BigDecimal)
        val d = jdbc.query(
            """select p.*, pa.first_name, pa.last_name, pa.national_id, u.full_name as profesional_nombre, i.number as cuota
               from billing_payment p inner join patients_patient pa on (p.patient_id = pa.id)
               left outer join accounts_user u on (p.profesional_id = u.id) left outer join billing_installment i on (p.installment_id = i.id)
               where p.id = :id""",
            mapOf("id" to pago),
        ) { f, _ ->
            Datos("${f.getString("first_name")} ${f.getString("last_name")}", f.getString("national_id"), f.fecha("date"), f.getString("method"),
                f.uuidONulo("profesional_id")?.let { f.getString("profesional_nombre") }, f.uuidONulo("installment_id")?.let { f.getLong("cuota") },
                f.getBigDecimal("amount"))
        }.first()
        return estilo().enHoja("A5").componer("Comprobante de cobro", listOf("N.º" to cifras(numero, 6))) { s ->
            val concepto = if (d.cuota != null) "Abono a su plan de pago (cuota ${d.cuota})" else "Pago por servicios odontológicos"
            listOf(
                s.campos(listOf(
                    listOf(Campo("Recibí de", d.paciente, 2), Campo("Cédula / RUC", d.cedula)),
                    listOf(Campo("Fecha", fecha(d.fecha)), Campo("Forma de pago", FORMAS_COBRO[d.metodo] ?: d.metodo), Campo("Atendido por", d.profesional ?: "")),
                )),
                s.espacioMm(4.0),
                s.tabla(listOf("Concepto", "Valor"), listOf(listOf(concepto, dinero(d.monto))), listOf(0.75, 0.25),
                    totales = listOf("TOTAL RECIBIDO", dinero(d.monto))),
                s.espacioMm(3.0),
                s.parrafo(enLetras(d.monto), negritaDelante = "Son: "),
            ) + s.firmas("Recibido por", "Paciente") + listOf(
                s.espacioMm(4.0),
                s.pequeno("Este comprobante acredita el pago recibido. No es una factura: la factura electrónica la emite la clínica por el sistema del SRI."),
            )
        }
    }

    // ── Liquidación de honorarios ────────────────────────────────────

    fun liquidacion(id: UUID): ByteArray {
        data class Liq(val numero: Long, val profesional: UUID, val nombre: String, val desde: LocalDate, val hasta: LocalDate, val estado: String,
                       val base: BigDecimal, val porcentaje: BigDecimal, val honorario: BigDecimal, val descuentos: BigDecimal,
                       val ajustes: BigDecimal, val retencionPorcentaje: BigDecimal, val retencion: BigDecimal, val aPagar: BigDecimal,
                       val nota: String, val detalle: JsonNode)
        val q = jdbc.query(
            """select l.*, l.detalle::text as detalle_texto, u.full_name from finanzas_liquidacion l
               inner join accounts_user u on (l.profesional_id = u.id) where l.id = :id""",
            mapOf("id" to id),
        ) { f, _ ->
            Liq(f.getLong("numero"), f.getObject("profesional_id", UUID::class.java), f.getString("full_name"), f.fecha("desde"), f.fecha("hasta"),
                f.getString("estado"), f.getBigDecimal("base"), f.getBigDecimal("porcentaje"), f.getBigDecimal("honorario"),
                f.getBigDecimal("descuentos"), f.getBigDecimal("ajustes"), f.getBigDecimal("retencion_porcentaje"), f.getBigDecimal("retencion"),
                f.getBigDecimal("a_pagar"), f.getString("nota"), json.readTree(f.getString("detalle_texto")))
        }.first()
        val cedula = calculos.perfil(q.profesional).cedula
        val lista = { clave: String -> q.detalle.get(clave)?.takeIf { it.isArray }?.toList() ?: emptyList() }
        val cobros = lista("cobros")
        val descuentos = lista("descuentos")
        fun atrasado(x: JsonNode) = Python.verdad(x.get("atrasado"))
        return estilo().componer("Liquidación de honorarios", listOf("N.º" to cifras(q.numero, 5))) { s ->
            val partes = mutableListOf<Element>(s.campos(listOf(
                listOf(Campo("Profesional", q.nombre, 2), Campo("Cédula / RUC", cedula)),
                listOf(Campo("Período", "${fecha(q.desde)} al ${fecha(q.hasta)}", 2), Campo("Estado", ControladorDeHonorarios.ESTADOS[q.estado] ?: q.estado)),
            )))
            if (cobros.isNotEmpty()) {
                partes += s.seccion("Cobros a sus pacientes", 1)
                partes += s.tabla(
                    listOf("Fecha", "Paciente", "Forma", "Monto"),
                    cobros.map { listOf(fecha(it.get("fecha")?.asText()) + if (atrasado(it)) " *" else "", it.get("paciente").asText(),
                        it.get("forma").asText(), dinero(it.get("monto").asText())) },
                    listOf(0.16, 0.48, 0.16, 0.20), totales = listOf("", "Total cobrado", "", dinero(q.base)),
                )
            }
            if (descuentos.isNotEmpty()) {
                partes += s.seccion("Descuentos", if (cobros.isNotEmpty()) 2 else 1)
                partes += s.tabla(
                    listOf("Fecha", "Concepto", "Monto"),
                    descuentos.map { listOf(fecha(it.get("fecha")?.asText()) + if (atrasado(it)) " *" else "", it.get("concepto").asText(),
                        dinero(it.get("monto").asText())) },
                    listOf(0.16, 0.64, 0.20),
                )
            }
            if ((cobros + descuentos).any(::atrasado)) partes += s.pequeno("* De antes del período: se registró después de liquidarlo.")

            val lineaHonorario = if (q.detalle.get("modalidad")?.asText() == "fijo") "Honorario fijo del período"
            else "Honorario (${conComa(q.porcentaje)} % de ${dinero(q.base)})"
            val filas = mutableListOf(listOf(lineaHonorario, dinero(q.honorario)))
            if (q.descuentos.signum() != 0) filas += listOf("(−) Descuentos", dinero(q.descuentos.negate()))
            if (q.ajustes.signum() != 0) filas += listOf("(±) Ajustes" + if (q.nota.isNotEmpty()) ": ${q.nota}" else "", dinero(q.ajustes))
            if (q.retencion.signum() != 0) filas += listOf("(−) Retención en la fuente ${conComa(q.retencionPorcentaje)} %", dinero(q.retencion.negate()))
            partes += s.seccion("Resumen")
            partes += s.tabla(listOf("Concepto", "Valor"), filas, listOf(0.75, 0.25), totales = listOf("VALOR A PAGAR", dinero(q.aPagar)))
            partes += s.espacioMm(3.0)
            partes += s.parrafo(enLetras(q.aPagar), negritaDelante = "Son: ")
            if (q.retencion.signum() != 0) {
                partes += s.pequeno("La retención se declara al SRI y se entrega al profesional su comprobante de retención electrónico.")
            }
            partes + s.firmas("Elaborado por", "Recibí conforme")
        }
    }

    // ── Rol de pagos ─────────────────────────────────────────────────

    fun rol(id: UUID): ByteArray {
        data class Rol(val empleado: UUID, val nombre: String, val mes: LocalDate, val calculo: JsonNode)
        val r = jdbc.query(
            """select r.empleado_id, r.mes, r.calculo::text as calculo, u.full_name from finanzas_rolpagos r
               inner join accounts_user u on (r.empleado_id = u.id) where r.id = :id""",
            mapOf("id" to id),
        ) { f, _ -> Rol(f.getObject("empleado_id", UUID::class.java), f.getString("full_name"), f.fecha("mes"), json.readTree(f.getString("calculo"))) }.first()
        val perfil = calculos.perfil(r.empleado)
        val c = r.calculo
        val mes = "${MESES[r.mes.monthValue - 1].replaceFirstChar { it.uppercase() }} ${r.mes.year}"
        fun texto(clave: String) = c.get(clave)?.takeUnless { it.isNull }?.asText()
        fun filas(clave: String) = c.get(clave)?.takeIf { it.isArray }?.toList() ?: emptyList()
        return estilo().componer("Rol de pagos", listOf("Mes" to mes)) { s ->
            val ingresos = filas("ingresos")
            val descuentos = filas("descuentos")
            val alto = maxOf(ingresos.size, descuentos.size)
            val tabla = (0 until alto).map { i ->
                val a = ingresos.getOrNull(i)
                val b = descuentos.getOrNull(i)
                listOf(a?.get("concepto")?.asText() ?: "", a?.let { dinero(it.get("valor").asText()) } ?: "",
                    b?.get("concepto")?.asText() ?: "", b?.let { dinero(it.get("valor").asText()) } ?: "")
            }
            val provisiones = filas("provisiones")
            listOf(
                s.campos(listOf(
                    listOf(Campo("Trabajador", r.nombre, 2), Campo("Cédula", perfil.cedula)),
                    listOf(Campo("Cargo", perfil.cargo), Campo("Mes", mes), Campo("Días trabajados", texto("dias_trabajados"))),
                )),
                s.espacioMm(4.0),
                s.tabla(listOf("Ingresos", "Valor", "Descuentos", "Valor"), tabla, listOf(0.32, 0.18, 0.32, 0.18),
                    totales = listOf("Total ingresos", dinero(texto("total_ingresos")), "Total descuentos", dinero(texto("total_descuentos"))),
                    derecha = setOf(1, 3)),
                s.espacioMm(3.0),
                s.banda("NETO A RECIBIR", dinero(texto("neto"))),
                s.espacioMm(2.0),
                s.parrafo(enLetras(texto("neto")), negritaDelante = "Son: "),
                s.seccion("Lo que aporta y provisiona la clínica"),
                s.tabla(
                    listOf("Concepto", "Valor"),
                    listOf(listOf("Aporte patronal IESS (${texto("aporte_patronal_porcentaje").orEmpty().replace(".", ",")} %)", dinero(texto("aporte_patronal")))) +
                        provisiones.map { listOf(it.get("concepto").asText() + " (provisión)", dinero(it.get("valor").asText())) },
                    listOf(0.75, 0.25), totales = listOf("Costo total para la clínica", dinero(texto("costo_empleador"))),
                ),
                s.pequeno("Las provisiones no se descuentan al trabajador: se le pagan en su fecha (décimos), al IESS (fondos de reserva) o al salir de vacaciones."),
            ) + s.firmas("Empleador", "Recibí conforme — ${r.nombre}")
        }
    }
}
