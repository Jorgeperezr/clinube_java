package com.clinube.finanzas

import com.clinube.comun.documentos.EstilosDeDocumento
import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.uuidONulo
import com.clinube.finanzas.Finanzas.Companion.FORMAS_COBRO
import com.clinube.finanzas.Finanzas.Companion.FORMAS_PAGO
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.anioMes
import com.clinube.finanzas.Finanzas.Companion.cifras
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate

/**
 * El Excel del mes para el contador (`apps/finanzas/exportar.py`): una hoja
 * por cosa, con lo que necesita para las declaraciones. Los importes van
 * como número, no como texto: el contador los suma.
 */
@RestController
class ControladorDeExportacion(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val estilos: EstilosDeDocumento,
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: ObjectMapper,
) {

    private val clinica get() = finanzas.clinica

    /** `xlsx_theme()`: los colores y la letra de los documentos, en lo que entiende una hoja de cálculo. */
    private data class Tema(val fondoCabecera: String, val letraCabecera: String, val letraCuerpo: String, val fuente: String, val cuerpo: Double)

    private fun tema(): Tema {
        val fila = jdbc.query(
            "select tables::text as t, palette::text as p, typography::text as ty from configuration_documentappearance where tenant_id = :clinica order by id limit 1",
            mapOf("clinica" to clinica),
        ) { f, _ -> Triple(json.readTree(f.getString("t")), json.readTree(f.getString("p")), json.readTree(f.getString("ty"))) }.firstOrNull()
        fun hex6(valor: String?, reserva: String): String {
            val crudo = valor?.trim().orEmpty().ifEmpty { reserva }.trimStart('#')
            return if (crudo.length == 6) crudo.uppercase() else reserva.trimStart('#').uppercase()
        }
        val tablas = fila?.first
        val paleta = fila?.second
        val letra = fila?.third
        fun de(n: com.fasterxml.jackson.databind.JsonNode?, clave: String) = n?.get(clave)?.takeIf { it.isTextual }?.textValue().orEmpty()
        val primario = de(paleta, "primary").ifEmpty { estilos.de(clinica).let { "#%06x".format(it.primario.rgb and 0xFFFFFF) } }
        val familia = de(letra, "family").ifEmpty { "Helvetica" }
        return Tema(
            fondoCabecera = hex6(de(tablas, "header_bg").ifEmpty { if (paleta?.has("table_header_bg") == true) de(paleta, "table_header_bg") else "#0e5c63" }
                .ifEmpty { primario }, EstilosDeDocumento.PRIMARIO_DE_RESERVA),
            letraCabecera = hex6(de(paleta, "table_header_text").ifEmpty { "#ffffff" }, "#FFFFFF"),
            letraCuerpo = hex6(de(letra, "color").ifEmpty { "#1f2937" }, "#1F2937"),
            fuente = mapOf("Helvetica" to "Arial", "Times" to "Times New Roman", "Courier" to "Courier New")[familia] ?: "Calibri",
            cuerpo = letra?.get("size_pt")?.asDouble() ?: 10.0,
        )
    }

    /** Un número de la hoja: como `float(Decimal)`. */
    private class Numero(val valor: Double)

    private fun n(valor: BigDecimal) = Numero(valor.toDouble())

    /** `_hoja`: cabecera de color, cuerpo, totales en negrita, anchos según el texto y la primera fila fija. */
    private fun hoja(libro: XSSFWorkbook, titulo: String, encabezado: List<String>, filas: List<List<Any>>, t: Tema, totales: List<Any>? = null) {
        val hoja = libro.createSheet(titulo.take(31))
        fun fuente(gruesa: Boolean, color: String?) = libro.createFont().apply {
            fontName = t.fuente; fontHeightInPoints = t.cuerpo.toInt().toShort(); bold = gruesa
            color?.let { setColor(XSSFColor(java.awt.Color(it.toInt(16)), null)) }
        }
        val miles = libro.createDataFormat().getFormat("#,##0.00")
        val cabecera = libro.createCellStyle().apply {
            setFont(fuente(true, t.letraCabecera))
            setFillForegroundColor(XSSFColor(java.awt.Color(t.fondoCabecera.toInt(16)), null))
            fillPattern = FillPatternType.SOLID_FOREGROUND
            verticalAlignment = VerticalAlignment.CENTER
        }
        val cuerpo = libro.createCellStyle().apply { setFont(fuente(false, t.letraCuerpo)) }
        val cuerpoNumero = libro.createCellStyle().apply { setFont(fuente(false, t.letraCuerpo)); dataFormat = miles }
        val total = libro.createCellStyle().apply { setFont(fuente(true, null)) }
        val totalNumero = libro.createCellStyle().apply { setFont(fuente(true, null)); dataFormat = miles }

        fun escribir(fila: Int, valores: List<Any>, texto: XSSFCellStyle?, numero: XSSFCellStyle?) {
            val r = hoja.createRow(fila)
            valores.forEachIndexed { i, v ->
                val celda = r.createCell(i)
                when (v) {
                    is Numero -> { celda.setCellValue(v.valor); numero?.let { celda.cellStyle = it } }
                    is Int -> { celda.setCellValue(v.toDouble()); texto?.let { celda.cellStyle = it } }
                    is Long -> { celda.setCellValue(v.toDouble()); texto?.let { celda.cellStyle = it } }
                    else -> { celda.setCellValue(v.toString()); texto?.let { celda.cellStyle = it } }
                }
            }
        }
        escribir(0, encabezado, cabecera, cabecera)
        filas.forEachIndexed { i, f -> escribir(i + 1, f, cuerpo, cuerpoNumero) }
        totales?.let { escribir(filas.size + 1, it, total, totalNumero) }
        anchos(hoja, listOf(encabezado) + filas + listOfNotNull(totales))
        hoja.createFreezePane(0, 1)
    }

    /** `min(max(len(str(valor or "")) + 3, 10), 50)` por columna. */
    private fun anchos(hoja: XSSFSheet, filas: List<List<Any>>) {
        val columnas = filas.maxOfOrNull { it.size } ?: 0
        for (c in 0 until columnas) {
            val largo = filas.maxOf { f -> f.getOrNull(c)?.let { textoDePython(it) }?.let(Python::longitud) ?: 0 }
            hoja.setColumnWidth(c, (minOf(maxOf(largo + 3, 10), 50) * 256))
        }
    }

    /** `str(valor or "")` de lo que se escribió en la celda. */
    private fun textoDePython(v: Any): String = when (v) {
        is Numero -> if (v.valor == 0.0) "" else Python.reprFloat(v.valor)
        is Int, is Long -> if (v.toString() == "0") "" else v.toString()
        else -> v.toString()
    }

    private val ESTADOS_EGRESO = mapOf("pagado" to "Pagado", "por_pagar" to "Por pagar")

    /** GET /api/v1/finanzas/exportar/?mes=2026-10 — `finanzas-2026-10.xlsx`, para descargar. */
    @GetMapping("/api/v1/finanzas/exportar/")
    @Transactional
    fun exportar(peticion: HttpServletRequest): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val (desde, hasta) = Finanzas.mesDe(consulta(peticion).get("mes"), finanzas.hoy()) ?: return detalle("El mes va como 2026-10.")
        val contenido = libroDelMes(desde, hasta)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finanzas-${anioMes(desde)}.xlsx\"")
            .body(contenido)
    }

    private fun libroDelMes(desde: LocalDate, hasta: LocalDate): ByteArray {
        val t = tema()
        val libro = XSSFWorkbook()
        val p = mapOf("clinica" to clinica, "desde" to desde, "hasta" to hasta)

        val cobros = jdbc.query(
            """select p.*, pa.first_name, pa.last_name, pa.national_id, u.full_name as profesional_nombre from billing_payment p
               inner join patients_patient pa on (p.patient_id = pa.id) left outer join accounts_user u on (p.profesional_id = u.id)
               where p.tenant_id = :clinica and p.date >= :desde and p.date <= :hasta order by p.date asc""",
            p,
        ) { f, _ ->
            f.getBigDecimal("amount") to listOf<Any>(
                f.fecha("date").toString(), (f.getObject("numero_comprobante") as Number?)?.toLong()?.takeIf { it != 0L } ?: "",
                "${f.getString("first_name")} ${f.getString("last_name")}", f.getString("national_id"),
                FORMAS_COBRO[f.getString("method")] ?: f.getString("method"),
                if (f.uuidONulo("profesional_id") != null) f.getString("profesional_nombre") else "", n(f.getBigDecimal("amount")),
            )
        }
        hoja(libro, "Cobros", listOf("Fecha", "Comprobante", "Paciente", "Cédula", "Forma de pago", "Profesional", "Monto"), cobros.map { it.second }, t,
            totales = listOf("Total", "", "", "", "", "", n(cobros.fold(BigDecimal.ZERO) { s, c -> s + c.first })))

        data class Egreso(val anulado: Boolean, val monto: BigDecimal, val fila: List<Any>)
        val egresos = jdbc.query(
            """select e.*, c.nombre as categoria_nombre from finanzas_egreso e inner join finanzas_categoriaegreso c on (e.categoria_id = c.id)
               where e.tenant_id = :clinica and e.fecha >= :desde and e.fecha <= :hasta order by e.fecha asc""",
            p,
        ) { f, _ ->
            val anulado = f.getBoolean("anulado")
            Egreso(anulado, f.getBigDecimal("monto"), listOf(
                f.fecha("fecha").toString(), f.getString("categoria_nombre"), f.getString("concepto"), f.getString("proveedor"),
                f.getString("proveedor_ruc"), f.getString("numero_factura"), FORMAS_PAGO[f.getString("forma_pago")] ?: f.getString("forma_pago"),
                ESTADOS_EGRESO[f.getString("estado")] ?: f.getString("estado"),
                if (anulado) "ANULADO: " + f.getString("motivo_anulacion") else "", n(f.getBigDecimal("monto")),
            ))
        }
        hoja(libro, "Egresos", listOf("Fecha", "Categoría", "Concepto", "Proveedor", "RUC / Cédula", "N.º factura", "Forma de pago", "Estado",
            "Observación", "Monto"), egresos.map { it.fila }, t,
            totales = listOf("Total (sin anulados)", "", "", "", "", "", "", "", "",
                n(egresos.filterNot { it.anulado }.fold(BigDecimal.ZERO) { s, e -> s + e.monto })))

        val liquidaciones = jdbc.query(
            """select l.*, u.full_name, (select pl.cedula from finanzas_perfillaboral pl where pl.usuario_id = l.profesional_id) as cedula
               from finanzas_liquidacion l inner join accounts_user u on (l.profesional_id = u.id)
               where l.tenant_id = :clinica and l.hasta >= :desde and l.hasta <= :hasta and not (l.estado = 'anulada') order by l.numero asc""",
            p,
        ) { f, _ ->
            listOf<Any>(
                cifras(f.getLong("numero"), 5), f.getString("full_name"), f.getString("cedula") ?: "",
                "${f.fecha("desde")} a ${f.fecha("hasta")}", n(f.getBigDecimal("base")), n(f.getBigDecimal("honorario")),
                n(f.getBigDecimal("descuentos")), n(f.getBigDecimal("ajustes")), n(f.getBigDecimal("retencion_porcentaje")),
                n(f.getBigDecimal("retencion")), n(f.getBigDecimal("a_pagar")),
                ControladorDeHonorarios.ESTADOS[f.getString("estado")] ?: f.getString("estado"),
            )
        }
        hoja(libro, "Honorarios y retenciones", listOf("N.º", "Profesional", "Cédula / RUC", "Período", "Cobrado", "Honorario", "Descuentos",
            "Ajustes", "% Retención IR", "Retención IR", "A pagar", "Estado"), liquidaciones, t)

        val roles = jdbc.query(
            """select r.*, r.calculo::text as calculo_texto, u.full_name,
               (select pl.cedula from finanzas_perfillaboral pl where pl.usuario_id = r.empleado_id) as cedula
               from finanzas_rolpagos r inner join accounts_user u on (r.empleado_id = u.id)
               where r.tenant_id = :clinica and r.mes = :desde and not (r.estado = 'anulado') order by r.mes desc""",
            p,
        ) { f, _ ->
            val c = json.readTree(f.getString("calculo_texto"))
            fun d(clave: String) = n(BigDecimal(c.get(clave)?.asText() ?: "0"))
            val aporte = c.get("descuentos")?.firstOrNull { it.get("concepto").asText().startsWith("Aporte") }?.get("valor")?.asText() ?: "0"
            listOf<Any>(
                f.getString("full_name"), f.getString("cedula") ?: "", f.getInt("dias_trabajados"), d("total_ingresos"), n(BigDecimal(aporte)),
                d("total_descuentos"), n(f.getBigDecimal("neto")), d("aporte_patronal"), d("total_provisiones"), n(f.getBigDecimal("costo_empleador")),
                ControladorDeRoles.ESTADOS[f.getString("estado")] ?: f.getString("estado"),
            )
        }
        hoja(libro, "Rol de pagos", listOf("Trabajador", "Cédula", "Días", "Total ingresos", "Aporte personal IESS", "Total descuentos",
            "Neto a recibir", "Aporte patronal", "Provisiones", "Costo para la clínica", "Estado"), roles, t)

        // El resumen va delante de todas.
        val r = calculos.resumen(desde, hasta)
        val resumen = libro.createSheet("Resumen")
        libro.setSheetOrder("Resumen", 0)
        libro.setActiveSheet(0)
        libro.setSelectedTab(0)
        val filas = mutableListOf<List<Any>>(
            listOf("Mes", anioMes(desde)), listOf("Ingresos cobrados", n(BigDecimal(r["ingresos"] as String))),
            listOf("Egresos pagados", n(BigDecimal(r["egresos"] as String))), listOf("Utilidad", n(BigDecimal(r["utilidad"] as String))),
            listOf("Por cobrar a pacientes", n(BigDecimal(r["por_cobrar"] as String))), listOf("Por pagar", n(BigDecimal(r["por_pagar"] as String))),
        )
        // Lo que se debe al IESS y al SRI por este mes, y si ya se pagó.
        for (o in calculos.obligaciones(desde)) {
            filas += listOf("${o["nombre"]} (${if (o["pagado"] == true) "pagada" else "vence " + o["vence"]})", n(BigDecimal(o["monto"] as String)))
        }
        val negrita = libro.createCellStyle().apply { setFont(libro.createFont().apply { bold = true }) }
        val miles = libro.createCellStyle().apply { dataFormat = libro.createDataFormat().getFormat("#,##0.00") }
        filas.forEachIndexed { i, (a, b) ->
            val fila = resumen.createRow(i)
            fila.createCell(0).apply { setCellValue(a as String); cellStyle = negrita }
            fila.createCell(1).apply {
                if (b is Numero) setCellValue(b.valor) else setCellValue(b as String)
                if (i > 0) cellStyle = miles
            }
        }
        resumen.setColumnWidth(0, 28 * 256)
        resumen.setColumnWidth(1, 16 * 256)

        val salida = ByteArrayOutputStream()
        libro.use { it.write(salida) }
        return salida.toByteArray()
    }
}
