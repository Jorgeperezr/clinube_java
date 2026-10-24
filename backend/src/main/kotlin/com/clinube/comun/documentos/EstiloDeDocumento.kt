package com.clinube.comun.documentos

import com.clinube.comun.archivos.AlmacenDeArchivos
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.ColumnText
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfPageEventHelper
import com.lowagie.text.pdf.PdfWriter
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * La apariencia de los documentos de una clínica (`apps/common/document_style.py`):
 * hoja, márgenes, tipografía, paleta, membrete y pie, según su
 * `DocumentAppearance` y el color de su marca. Solo las piezas que usan los
 * documentos con Platypus (membrete como contenido, título con sus datos de
 * control, secciones, cuadrícula de datos, tablas, firmas y pie).
 *
 * El PDF no sale byte a byte igual que el de reportlab —es otro motor—, pero
 * sí con el mismo texto, en el mismo orden y con las mismas medidas y
 * colores. Ver docs/DIFERENCIAS.md.
 */
@Component
class EstilosDeDocumento(
    private val jdbc: NamedParameterJdbcTemplate,
    private val json: ObjectMapper,
    private val almacen: AlmacenDeArchivos,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        /** Los valores de serie de `DocumentAppearance`, grupo a grupo. */
        val POR_DEFECTO: Map<String, Map<String, Any?>> = mapOf(
            "header" to mapOf(
                "enabled" to true, "show_logo" to true, "show_clinic_name" to true, "show_professional" to true,
                "show_specialty" to true, "show_ruc" to true, "show_address" to true, "show_phone" to true, "show_email" to true,
                "show_website" to false, "align" to "left", "height_mm" to 26, "background" to "", "text_color" to "",
            ),
            "footer" to mapOf(
                "enabled" to true, "text" to "", "show_page_numbers" to true, "show_date" to true, "show_time" to false,
                "show_clinic" to true, "legal_text" to "", "align" to "center", "text_color" to "",
            ),
            "typography" to mapOf(
                "family" to "Helvetica", "size_pt" to 10, "weight" to "normal", "letter_spacing" to 0, "line_height" to 1.35,
                "color" to "#1f2937", "title_size_pt" to 16, "subtitle_size_pt" to 12,
            ),
            "palette" to mapOf(
                "primary" to "", "secondary" to "#6b7280", "accent" to "#0e5c63", "title" to "#111827", "subtitle" to "#374151",
                "table_header_bg" to "#0e5c63", "table_header_text" to "#ffffff", "icon" to "#0e5c63", "highlight" to "#fef3c7",
                "alert" to "#b91c1c", "separator" to "#d1d5db",
            ),
            "tables" to mapOf(
                "header_bg" to "", "border_color" to "", "border_width" to 0.6, "rounded" to false, "shaded" to true,
                "zebra" to true, "zebra_color" to "#f9fafb", "cell_padding_mm" to 2, "row_spacing_mm" to 0,
            ),
            "page" to mapOf(
                "size" to "A4", "orientation" to "portrait", "prescription_size" to "A5", "margin_top_mm" to 20,
                "margin_bottom_mm" to 18, "margin_left_mm" to 20, "margin_right_mm" to 20, "columns" to 1, "block_spacing_mm" to 5,
            ),
            "logo" to mapOf("position" to "header_left", "width_mm" to 26, "height_mm" to 20, "opacity" to 1.0, "gap_mm" to 4),
            "watermark" to mapOf("enabled" to false, "text" to "", "opacity" to 0.08, "position" to "center", "size_pt" to 60, "rotation" to 45),
        )

        /** `apps/configuration/temas.py`: el color principal de cada tema predefinido. */
        val TEMAS = mapOf(
            "default" to "#0e7490", "sin_color" to "#404040", "oceano" to "#0f4c81", "petroleo" to "#0e5c63",
            "bosque" to "#1d6b3c", "vino" to "#7b1e3c", "grafito" to "#374151", "arena" to "#8a5a2b",
        )
        const val PRIMARIO_DE_RESERVA = "#0e5c63"
    }

    /** `clinic_snapshot`: los datos de la clínica para el membrete y el pie. */
    data class Clinica(val nombre: String, val ruc: String, val direccion: String, val telefono: String, val correo: String, val logo: String?)

    /** `get_document_style(tenant)` y `clinic_snapshot(tenant)` juntos. */
    fun de(clinica: UUID?): EstiloDeDocumento {
        val ajustes = POR_DEFECTO.mapValues { LinkedHashMap(it.value) }
        jdbc.query(
            "select * from configuration_documentappearance where tenant_id = :clinica order by id limit 1", mapOf("clinica" to clinica),
        ) { f, _ ->
            for (grupo in ajustes.keys) {
                val guardado = f.getString(grupo)?.let { json.readTree(it) } ?: continue
                if (guardado.isObject) guardado.fields().forEach { (k, v) -> ajustes.getValue(grupo)[k] = json.treeToValue(v, Any::class.java) }
            }
        }
        val marca = jdbc.query(
            """select b.*, b.theme::text as tema, t.name as nombre_clinica, t.ruc from common_tenant t
               left join configuration_clinicbranding b on b.tenant_id = t.id where t.id = :clinica""",
            mapOf("clinica" to clinica),
        ) { f, _ ->
            val tema = f.getString("tema")?.let { json.readTree(it) }
            val datos = Clinica(
                nombre = f.getString("display_name")?.takeIf { it.isNotEmpty() } ?: f.getString("nombre_clinica"),
                ruc = f.getString("ruc") ?: "",
                direccion = f.getString("address") ?: "",
                telefono = f.getString("phone") ?: "",
                correo = f.getString("email") ?: "",
                logo = f.getString("logo")?.takeIf { it.isNotEmpty() }?.let { almacen.abrir(it)?.toString() },
            )
            datos to primarioDelTema(tema)
        }.firstOrNull()
        return EstiloDeDocumento(ajustes, marca?.second, marca?.first ?: Clinica("", "", "", "", "", null), zona)
    }

    /** `temas.resolver(theme)[0]`. */
    private fun primarioDelTema(tema: JsonNode?): String {
        if (tema == null || !tema.isObject) return TEMAS.getValue("default")
        val preset = tema.get("preset")?.takeIf { it.isTextual && it.textValue().isNotEmpty() }?.textValue() ?: "default"
        TEMAS[preset]?.let { return it }
        val propio = tema.get("primary")?.asText()?.trim().orEmpty()
        return if (propio.length == 7 && propio.startsWith("#") && propio.drop(1).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) propio
        else TEMAS.getValue("default")
    }
}

/** Un estilo ya resuelto y listo para componer: no vuelve a la base. */
class EstiloDeDocumento(
    private val s: Map<String, Map<String, Any?>>,
    private val primarioDeLaMarca: String?,
    val clinica: EstilosDeDocumento.Clinica,
    private val zona: ZonaDeLaClinica,
    tamano: String? = null,
) {

    private fun texto(grupo: String, clave: String): String = s[grupo]?.get(clave)?.toString().orEmpty()
    private fun numero(grupo: String, clave: String, defecto: Double): Double = (s[grupo]?.get(clave) as? Number)?.toDouble()
        ?: s[grupo]?.get(clave)?.toString()?.toDoubleOrNull() ?: defecto
    private fun si(grupo: String, clave: String, defecto: Boolean = true): Boolean = when (val v = s[grupo]?.get(clave)) {
        is Boolean -> v
        null -> defecto
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty()
        else -> true
    }

    private fun mm(valor: Double) = (valor * 72 / 25.4).toFloat()

    val pagina: Rectangle = run {
        val nombre = (tamano ?: texto("page", "size")).uppercase()
        val base = when (nombre) { "LETTER" -> PageSize.LETTER; "LEGAL" -> PageSize.LEGAL; "A5" -> PageSize.A5; else -> PageSize.A4 }
        if (texto("page", "orientation") == "landscape") base.rotate() else base
    }
    val margenArriba = mm(numero("page", "margin_top_mm", 20.0))
    val margenAbajo = mm(numero("page", "margin_bottom_mm", 18.0))
    val margenIzquierdo = mm(numero("page", "margin_left_mm", 20.0))
    val margenDerecho = mm(numero("page", "margin_right_mm", 20.0))
    private val espacioEntreBloques = mm(numero("page", "block_spacing_mm", 5.0))
    val ancho: Float get() = pagina.width - margenIzquierdo - margenDerecho

    /** El mismo estilo en otra hoja (la del comprobante es A5). */
    fun enHoja(tamano: String) = EstiloDeDocumento(s, primarioDeLaMarca, clinica, zona, tamano)

    private val familia = when (texto("typography", "family")) {
        "Times" -> Triple(BaseFont.TIMES_ROMAN, BaseFont.TIMES_BOLD, BaseFont.TIMES_ITALIC)
        "Courier" -> Triple(BaseFont.COURIER, BaseFont.COURIER_BOLD, BaseFont.COURIER_OBLIQUE)
        else -> Triple(BaseFont.HELVETICA, BaseFont.HELVETICA_BOLD, BaseFont.HELVETICA_OBLIQUE)
    }
    private val normal: BaseFont = BaseFont.createFont(familia.first, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
    private val negrita: BaseFont = BaseFont.createFont(familia.second, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
    val cuerpo = numero("typography", "size_pt", 10.0).toFloat()
    private val interlineado = cuerpo * numero("typography", "line_height", 1.35).toFloat()
    private val cuerpoTitulo = numero("typography", "title_size_pt", 16.0).toFloat()
    private val cuerpoSubtitulo = numero("typography", "subtitle_size_pt", 12.0).toFloat()

    private fun color(valor: String?, reserva: String): Color {
        val t = valor?.trim().orEmpty()
        val hex = if (t.startsWith("#")) t.drop(1) else t
        return runCatching { if (hex.length == 6) Color(hex.toInt(16)) else null }.getOrNull() ?: Color(reserva.drop(1).toInt(16))
    }

    val primario: Color = color(texto("palette", "primary").ifEmpty { primarioDeLaMarca.orEmpty() }, EstilosDeDocumento.PRIMARIO_DE_RESERVA)
    private val secundario = color(texto("palette", "secondary"), "#6b7280")
    private val colorTitulo = color(texto("palette", "title"), "#111827")
    private val tinta = color(texto("typography", "color"), "#1f2937")
    private val separador = color(texto("palette", "separator"), "#d1d5db")

    fun fuente(tamano: Float = cuerpo, gruesa: Boolean = false, tono: Color = tinta) = Font(if (gruesa) negrita else normal, tamano, Font.NORMAL, tono)

    /**
     * Los caracteres que Helvetica sin incrustar no tiene en su
     * codificación (Windows-1252) se cambian por su equivalente: el signo
     * menos «−» por el guion, que es lo que hace reportlab con él.
     */
    private fun legible(texto: String): String = texto.replace('−', '-')

    // ── Las piezas ───────────────────────────────────────────────────

    /** `header_flowables`: el nombre de la clínica en su color, los datos de contacto y el filete doble. */
    fun membrete(): List<Element> {
        if (!si("header", "enabled")) return emptyList()
        val parrafo = Paragraph()
        parrafo.leading = cuerpo + 3
        if (si("header", "show_clinic_name") && clinica.nombre.isNotEmpty()) {
            parrafo.add(Chunk(legible(clinica.nombre), fuente(cuerpoSubtitulo + 2, true, primario)))
        }
        val contacto = lineaDeContacto()
        if (contacto.isNotEmpty()) {
            if (parrafo.isNotEmpty()) parrafo.add(Chunk.NEWLINE)
            parrafo.add(Chunk(legible(contacto), fuente(cuerpo - 1, tono = secundario)))
        }
        val logo = clinica.logo?.takeIf { si("header", "show_logo") }?.let { runCatching { Image.getInstance(it) }.getOrNull() }
        val anchoLogo = mm(numero("logo", "width_mm", 26.0))
        val tabla = if (logo != null) {
            logo.scaleToFit(anchoLogo, mm(numero("logo", "height_mm", 20.0)))
            val columna = anchoLogo + mm(numero("logo", "gap_mm", 4.0))
            PdfPTable(floatArrayOf(columna, ancho - columna)).apply { addCell(celdaDeMembrete(PdfPCell(logo, false))) }
        } else PdfPTable(1)
        tabla.totalWidth = ancho
        tabla.isLockedWidth = true
        tabla.horizontalAlignment = Element.ALIGN_LEFT
        tabla.addCell(celdaDeMembrete(PdfPCell(parrafo)))
        val fino = PdfPTable(1).apply {
            totalWidth = ancho; isLockedWidth = true
            addCell(PdfPCell().apply {
                border = Rectangle.BOTTOM; borderWidthBottom = 0.5f; borderColorBottom = separador; fixedHeight = mm(1.2)
            })
        }
        return listOf(tabla, fino, espacio(espacioEntreBloques))
    }

    private fun celdaDeMembrete(celda: PdfPCell) = celda.apply {
        border = Rectangle.BOTTOM; borderWidthBottom = 1.4f; borderColorBottom = primario
        verticalAlignment = Element.ALIGN_MIDDLE; paddingLeft = 0f; paddingBottom = 6f
    }

    /** `_lineas_de_contacto` sin ancho: todo en una línea lógica, que el párrafo parte. */
    private fun lineaDeContacto(): String {
        val piezas = mutableListOf<String>()
        if (si("header", "show_ruc") && clinica.ruc.isNotEmpty()) piezas += "RUC ${clinica.ruc}"
        if (si("header", "show_address") && clinica.direccion.isNotEmpty()) piezas += clinica.direccion
        if (si("header", "show_phone") && clinica.telefono.isNotEmpty()) piezas += "Tel. ${clinica.telefono}"
        if (si("header", "show_email") && clinica.correo.isNotEmpty()) piezas += clinica.correo
        return piezas.joinToString(" · ")
    }

    /** `title_flowables`: el título en mayúsculas a la izquierda y sus datos de control a la derecha. */
    fun titulo(titulo: String, meta: List<Pair<String, String>>): List<Element> {
        val tabla = PdfPTable(floatArrayOf(0.68f, 0.32f)).apply { totalWidth = ancho; isLockedWidth = true }
        val izquierda = Paragraph(legible(titulo.uppercase()), fuente(cuerpoTitulo - 2, true, colorTitulo)).apply { leading = (cuerpoTitulo - 2) * 1.2f }
        val derecha = Paragraph().apply {
            leading = cuerpo + 2
            alignment = Element.ALIGN_RIGHT
            meta.filter { it.second.isNotEmpty() }.forEachIndexed { i, (k, v) ->
                if (i > 0) add(Chunk.NEWLINE)
                add(Chunk(legible(k), fuente(cuerpo - 2, tono = secundario)))
                add(Chunk("  "))
                add(Chunk(legible(v), fuente(cuerpo - 1, true)))
            }
        }
        for ((p, alineacion) in listOf(izquierda to Element.ALIGN_LEFT, derecha to Element.ALIGN_RIGHT)) {
            tabla.addCell(PdfPCell().apply {
                addElement(p); horizontalAlignment = alineacion; verticalAlignment = Element.ALIGN_TOP
                border = Rectangle.BOTTOM; borderWidthBottom = 1f; borderColorBottom = primario
                paddingLeft = 0f; paddingRight = 0f; paddingBottom = 5f; paddingTop = 0f
            })
        }
        return listOf(tabla, espacio(espacioEntreBloques))
    }

    /** `section_flowable`: una banda suave con la barra del color principal. */
    fun seccion(texto: String, numero: Int? = null): Element {
        val etiqueta = (if (numero != null) "$numero. $texto" else texto).uppercase()
        val fondo = Color(mezcla(primario.red), mezcla(primario.green), mezcla(primario.blue))
        return PdfPTable(floatArrayOf(mm(1.2), ancho - mm(1.2))).apply {
            totalWidth = ancho; isLockedWidth = true; setSpacingBefore(8f); setSpacingAfter(6f)
            addCell(PdfPCell().apply { backgroundColor = primario; border = Rectangle.NO_BORDER; paddingLeft = 0f })
            addCell(PdfPCell(Phrase(legible(etiqueta), fuente(cuerpo - 0.5f, true, primario))).apply {
                backgroundColor = fondo; border = Rectangle.NO_BORDER; paddingLeft = mm(2.5); paddingTop = 3f; paddingBottom = 3f
            })
        }
    }

    /** El 8 % del color sobre blanco: lo que da `alpha=0.08` en reportlab. */
    private fun mezcla(c: Int) = (255 - (255 - c) * 0.08).toInt()

    /** Una celda de `fields_table`: etiqueta, valor y cuántas columnas ocupa. */
    data class Campo(val etiqueta: String, val valor: String?, val ocupa: Int = 1)

    /** `fields_table`: la cuadrícula etiqueta/valor con bordes de un formulario formal. */
    fun campos(filas: List<List<Campo>>, columnas: Int = 3): Element {
        val tabla = PdfPTable(columnas).apply { totalWidth = ancho; isLockedWidth = true }
        for (fila in filas) {
            for (c in fila) {
                val p = Paragraph().apply {
                    leading = cuerpo + 1.5f
                    add(Chunk(legible(c.etiqueta.uppercase()), fuente(cuerpo - 3, tono = secundario)))
                    add(Chunk.NEWLINE)
                    add(Chunk(legible(if (c.valor.isNullOrEmpty()) "—" else c.valor), fuente(cuerpo - 0.5f)))
                }
                tabla.addCell(PdfPCell().apply {
                    addElement(p); colspan = c.ocupa; borderWidth = 0.5f; borderColor = separador
                    verticalAlignment = Element.ALIGN_TOP; paddingTop = 3f; paddingBottom = 4f; paddingLeft = mm(2.0)
                })
            }
        }
        return tabla
    }

    /**
     * `_tabla` de `documentos.py` con el `table_style` de la clínica:
     * cabecera de color, rejilla, filas alternas y, si los hay, los totales en
     * negrita con un filete del color principal.
     */
    fun tabla(
        encabezado: List<String>, filas: List<List<String>>, anchos: List<Double>, totales: List<String>? = null,
        derecha: Set<Int> = setOf(anchos.size - 1),
    ): Element {
        val fondo = color(texto("tables", "header_bg").ifEmpty { texto("palette", "table_header_bg") }, EstilosDeDocumento.PRIMARIO_DE_RESERVA)
        val letraCabecera = color(texto("palette", "table_header_text"), "#ffffff")
        val borde = color(texto("tables", "border_color").ifEmpty { texto("palette", "separator") }, "#d1d5db")
        val grosor = numero("tables", "border_width", 0.6).toFloat()
        val relleno = mm(numero("tables", "cell_padding_mm", 2.0))
        val vertical = relleno * 0.6f + mm(numero("tables", "row_spacing_mm", 0.0))
        val cebra = color(texto("tables", "zebra_color"), "#f9fafb")
        val tabla = PdfPTable(anchos.map { it.toFloat() }.toFloatArray()).apply {
            totalWidth = ancho; isLockedWidth = true; headerRows = 1
        }
        // Las columnas a la derecha pueden contarse desde el final, como en reportlab (-1 es la última).
        val derechaReal = derecha.map { if (it < 0) anchos.size + it else it }.toSet()
        fun celda(texto: String, gruesa: Boolean, tono: Color, columna: Int, fondoCelda: Color?, totalArriba: Boolean = false) =
            PdfPCell(Phrase(legible(texto), fuente(cuerpo - 1, gruesa, tono))).apply {
                horizontalAlignment = if (columna in derechaReal) Element.ALIGN_RIGHT else Element.ALIGN_LEFT
                verticalAlignment = Element.ALIGN_MIDDLE
                paddingLeft = relleno; paddingRight = relleno; paddingTop = vertical; paddingBottom = vertical + 2f
                if (grosor > 0) { borderWidth = grosor; borderColor = borde } else border = Rectangle.NO_BORDER
                fondoCelda?.let { backgroundColor = it }
                if (totalArriba) { borderWidthTop = 1f; borderColorTop = primario }
            }
        encabezado.forEachIndexed { i, t -> tabla.addCell(celda(t, true, letraCabecera, i, if (si("tables", "shaded")) fondo else null)) }
        val todas = filas + listOfNotNull(totales)
        todas.forEachIndexed { r, fila ->
            val esTotal = totales != null && r == todas.size - 1
            val fondoFila = if (si("tables", "zebra") && r % 2 == 1) cebra else null
            fila.forEachIndexed { i, t -> tabla.addCell(celda(t, esTotal, tinta, i, fondoFila, esTotal)) }
        }
        return tabla
    }

    /** La banda del neto del rol: el color principal de fondo y la cifra a la derecha, en blanco. */
    fun banda(izquierda: String, derecha: String): Element = PdfPTable(floatArrayOf(0.82f, 0.18f)).apply {
        totalWidth = ancho; isLockedWidth = true
        listOf(izquierda to Element.ALIGN_LEFT, derecha to Element.ALIGN_RIGHT).forEach { (t, alineacion) ->
            addCell(PdfPCell(Phrase(legible(t), fuente(cuerpo + 1, true, Color.WHITE))).apply {
                backgroundColor = primario; border = Rectangle.NO_BORDER; horizontalAlignment = alineacion
                paddingTop = 6f; paddingBottom = 6f; paddingLeft = mm(3.0); paddingRight = mm(3.0)
            })
        }
    }

    /** Un párrafo de cuerpo, con una parte en negrita delante («Son: …»). */
    fun parrafo(texto: String, negritaDelante: String? = null): Element = Paragraph().apply {
        leading = interlineado
        negritaDelante?.let { add(Chunk(legible(it), fuente(cuerpo, true))) }
        add(Chunk(legible(texto), fuente(cuerpo)))
    }

    /** `DocSmall`: la letra de las notas al pie. */
    fun pequeno(texto: String): Element = Paragraph(legible(texto), fuente(maxOf(6f, cuerpo - 2.5f), tono = secundario)).apply {
        leading = interlineado * 0.85f
    }

    fun espacio(alto: Float): Element = Paragraph(" ").apply { leading = alto; font = fuente(1f) }
    fun espacioMm(alto: Double) = espacio(mm(alto))

    /** `_firmas`: dos líneas dibujadas, con su leyenda debajo y aire encima. */
    fun firmas(izquierda: String, derecha: String): List<Element> {
        val tabla = PdfPTable(floatArrayOf(0.42f, 0.16f, 0.42f)).apply { totalWidth = ancho; isLockedWidth = true; keepTogether = true }
        listOf(izquierda, "", derecha).forEachIndexed { i, t ->
            tabla.addCell(PdfPCell(Phrase(legible(t), fuente(maxOf(6f, cuerpo - 2.5f), tono = secundario))).apply {
                horizontalAlignment = Element.ALIGN_CENTER; border = if (i == 1) Rectangle.NO_BORDER else Rectangle.TOP
                borderWidthTop = 0.6f; borderColorTop = Color(0x55, 0x55, 0x55); paddingTop = 4f
            })
        }
        return listOf(espacioMm(22.0), tabla)
    }

    /**
     * Compone el documento: membrete, título y el cuerpo, con el pie en cada
     * página (`page_furniture`): nombre y datos de la clínica, la fecha de
     * hoy en la clínica y el número de página, lo que quepa.
     */
    fun componer(titulo: String, meta: List<Pair<String, String>>, cuerpo: (EstiloDeDocumento) -> List<Element>): ByteArray {
        val salida = ByteArrayOutputStream()
        val documento = Document(pagina, margenIzquierdo, margenDerecho, margenArriba, margenAbajo)
        val escritor = PdfWriter.getInstance(documento, salida)
        escritor.pageEvent = object : PdfPageEventHelper() {
            override fun onEndPage(writer: PdfWriter, document: Document) = pie(writer, document.pageNumber)
        }
        documento.addTitle(titulo)
        documento.addCreator("Clinube")
        documento.open()
        (membrete() + titulo(titulo, meta) + cuerpo(this)).forEach { documento.add(it) }
        documento.close()
        return salida.toByteArray()
    }

    private fun pie(writer: PdfWriter, pagina: Int) {
        if (!si("footer", "enabled")) return
        val c = writer.directContent
        val y = margenAbajo
        c.setColorStroke(separador)
        c.setLineWidth(0.6f)
        c.moveTo(margenIzquierdo, y + mm(6.0))
        c.lineTo(this.pagina.width - margenDerecho, y + mm(6.0))
        c.stroke()
        val datos = if (si("footer", "show_clinic") && clinica.nombre.isNotEmpty()) {
            listOf(clinica.nombre, clinica.direccion, clinica.telefono, clinica.correo).filter { it.isNotEmpty() }.toMutableList()
        } else mutableListOf()
        val fijos = mutableListOf<String>()
        texto("footer", "text").takeIf { it.isNotEmpty() }?.let { fijos += it }
        if (si("footer", "show_date")) {
            val ahora = java.time.LocalDateTime.ofInstant(zona.ahora(), zona.zona)
            fijos += ahora.format(DateTimeFormatter.ofPattern(if (si("footer", "show_time", false)) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"))
        }
        if (si("footer", "show_page_numbers")) fijos += "Página $pagina"
        val letra = cuerpo - 2
        while (datos.isNotEmpty() && normal.getWidthPoint((datos + fijos).joinToString("  ·  "), letra) > ancho) datos.removeAt(datos.lastIndex)
        val linea = legible((datos + fijos).joinToString("  ·  "))
        val tono = if (texto("footer", "text_color").isNotEmpty()) color(texto("footer", "text_color"), "#6b7280") else secundario
        val alineacion = when (texto("footer", "align")) { "left" -> Element.ALIGN_LEFT; "right" -> Element.ALIGN_RIGHT; else -> Element.ALIGN_CENTER }
        val x = when (alineacion) { Element.ALIGN_LEFT -> margenIzquierdo; Element.ALIGN_RIGHT -> this.pagina.width - margenDerecho; else -> this.pagina.width / 2 }
        ColumnText.showTextAligned(c, alineacion, Phrase(linea, fuente(letra, tono = tono)), x, y, 0f)
        texto("footer", "legal_text").takeIf { it.isNotEmpty() }?.let {
            ColumnText.showTextAligned(c, Element.ALIGN_CENTER, Phrase(legible(it), fuente(cuerpo - 3, tono = tono)), this.pagina.width / 2, y - mm(4.0), 0f)
        }
    }
}
