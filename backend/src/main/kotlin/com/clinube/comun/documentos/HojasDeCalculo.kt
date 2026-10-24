package com.clinube.comun.documentos

import com.clinube.comun.python.Python
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Las hojas de cálculo de la clínica: `xlsx_theme()` de
 * `apps/common/document_style.py` (los colores y la letra de sus documentos,
 * en lo que entiende Excel) y `build_xlsx` de `apps/billing/report_export.py`
 * (un reporte en una hoja). Un reporte en Excel es un documento de la clínica
 * y lleva sus colores.
 */
@Component
class HojasDeCalculo(
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: ObjectMapper,
    private val estilos: EstilosDeDocumento,
) {

    data class Tema(
        val fondoCabecera: String, val letraCabecera: String, val letraCuerpo: String, val fuente: String, val cuerpo: Double,
        val cebra: Boolean, val fondoCebra: String,
    )

    /** Un número en la hoja: lo que da `float(Decimal)` en Python. */
    class Numero(val valor: Double)

    companion object {
        const val TIPO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** El `.xlsx` para descargar, con su nombre. */
        fun descarga(contenido: ByteArray, nombre: String): ResponseEntity<Any> = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(TIPO))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$nombre\"")
            .body(contenido)

        /** `str(valor)` de lo que se escribe en una celda. */
        fun texto(valor: Any?): String = when (valor) {
            null -> "None"
            is Numero -> Python.reprFloat(valor.valor)
            else -> valor.toString()
        }

        fun color(hex: String) = XSSFColor(java.awt.Color(hex.toInt(16)), null)
    }

    /** `get_document_style(tenant).xlsx_theme()`. */
    fun tema(clinica: UUID?): Tema {
        val fila = jdbc.query(
            """select tables::text as t, palette::text as p, typography::text as ty from configuration_documentappearance
               where tenant_id = :clinica order by id limit 1""",
            mapOf("clinica" to clinica),
        ) { f, _ -> Triple(json.readTree(f.getString("t")), json.readTree(f.getString("p")), json.readTree(f.getString("ty"))) }.firstOrNull()
        fun hex6(valor: String?, reserva: String): String {
            val crudo = valor?.trim().orEmpty().ifEmpty { reserva }.trimStart('#')
            return if (crudo.length == 6) crudo.uppercase() else reserva.trimStart('#').uppercase()
        }
        val (tablas, paleta, letra) = fila ?: Triple(null, null, null)
        fun de(n: JsonNode?, clave: String) = n?.get(clave)?.takeIf { it.isTextual }?.textValue().orEmpty()
        val primario = de(paleta, "primary").ifEmpty { estilos.de(clinica).let { "#%06x".format(it.primario.rgb and 0xFFFFFF) } }
        val familia = de(letra, "family").ifEmpty { "Helvetica" }
        return Tema(
            fondoCabecera = hex6(de(tablas, "header_bg").ifEmpty { if (paleta?.has("table_header_bg") == true) de(paleta, "table_header_bg") else "#0e5c63" }
                .ifEmpty { primario }, EstilosDeDocumento.PRIMARIO_DE_RESERVA),
            letraCabecera = hex6(de(paleta, "table_header_text").ifEmpty { "#ffffff" }, "#FFFFFF"),
            letraCuerpo = hex6(de(letra, "color").ifEmpty { "#1f2937" }, "#1F2937"),
            fuente = mapOf("Helvetica" to "Arial", "Times" to "Times New Roman", "Courier" to "Courier New")[familia] ?: "Calibri",
            cuerpo = letra?.get("size_pt")?.asDouble() ?: 10.0,
            cebra = tablas?.get("zebra")?.let { Python.verdad(it) } ?: true,
            fondoCebra = hex6(de(tablas, "zebra_color"), "#F9FAFB"),
        )
    }

    /**
     * `build_xlsx(title, headers, rows)`: una hoja con la cabecera del color
     * de la clínica, las filas alternas sombreadas, la primera fila fija y
     * cada columna tan ancha como su texto más largo más cuatro.
     */
    fun reporte(clinica: UUID?, titulo: String, encabezado: List<String>, filas: List<List<Any?>>): ByteArray {
        val t = tema(clinica)
        val libro = XSSFWorkbook()
        val hoja = libro.createSheet(titulo.take(31))
        fun fuente(gruesa: Boolean, tono: String) = libro.createFont().apply {
            fontName = t.fuente; fontHeightInPoints = t.cuerpo.toInt().toShort(); bold = gruesa; setColor(color(tono))
        }
        val cabecera = libro.createCellStyle().apply {
            setFont(fuente(true, t.letraCabecera)); setFillForegroundColor(color(t.fondoCabecera))
            fillPattern = FillPatternType.SOLID_FOREGROUND; verticalAlignment = VerticalAlignment.CENTER
        }
        val cuerpo = libro.createCellStyle().apply { setFont(fuente(false, t.letraCuerpo)) }
        val cebra = libro.createCellStyle().apply {
            setFont(fuente(false, t.letraCuerpo)); setFillForegroundColor(color(t.fondoCebra)); fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        hoja.createRow(0).let { r -> encabezado.forEachIndexed { i, h -> r.createCell(i).apply { setCellValue(h); cellStyle = cabecera } } }
        filas.forEachIndexed { n, fila ->
            val r = hoja.createRow(n + 1)
            fila.forEachIndexed { i, v ->
                val celda = r.createCell(i)
                when (v) {
                    null -> {}
                    is Numero -> celda.setCellValue(v.valor)
                    is Int -> celda.setCellValue(v.toDouble())
                    is Long -> celda.setCellValue(v.toDouble())
                    else -> celda.setCellValue(v.toString())
                }
                if (i < encabezado.size) celda.cellStyle = if (t.cebra && n % 2 == 1) cebra else cuerpo
            }
        }
        hoja.createFreezePane(0, 1)
        encabezado.forEachIndexed { i, h ->
            val largo = (listOf(Python.longitud(h)) + filas.map { Python.longitud(texto(it.getOrNull(i))) }).max()
            hoja.setColumnWidth(i, minOf((largo + 4) * 256, 255 * 256))
        }
        val salida = ByteArrayOutputStream()
        libro.use { it.write(salida) }
        return salida.toByteArray()
    }
}
