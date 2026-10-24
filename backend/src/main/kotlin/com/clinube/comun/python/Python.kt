package com.clinube.comun.python

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Lo que hace Python con un valor que llega en un JSON, cuando DRF lo pasa
 * por `str()`, `int()`, `Decimal()` o `date.fromisoformat()`. No coincide
 * con Java en los bordes, y los bordes son lo que se ve en un 400:
 *
 *   · `str(1e16)` es «1e+16» (Java: «1.0E16»); `str(-0.0)`, «-0.0».
 *   · `int("٣")` es 3, `int("1_000")` es 1000 y `int("\u001c7")` falla.
 *   · `Decimal("1__0")` es 10: se ignoran TODOS los guiones bajos.
 *   · `str.strip()` quita «\u001c» y « »; `String.trim()` de Kotlin,
 *     no exactamente lo mismo.
 *
 * Cada cosa de aquí la comprueba `CamposDrfTest` contra miles de casos
 * calculados con el DRF de clinube (`scripts/casos-de-campos.py`).
 */
object Python {

    // ── Texto ────────────────────────────────────────────────────────

    /** `str.isspace()`, que es también el `\s` de las expresiones regulares. */
    fun esEspacio(c: Int): Boolean = c in 0x09..0x0d || c in 0x1c..0x20 || c == 0x85 || c == 0xa0 ||
        c == 0x1680 || c in 0x2000..0x200a || c == 0x2028 || c == 0x2029 || c == 0x202f || c == 0x205f || c == 0x3000

    /** `\w`: `str.isalnum()` (letras y cualquier número) o el guion bajo. */
    fun esPalabra(c: Int): Boolean = c == '_'.code || Character.isLetter(c) || when (Character.getType(c)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    /** `\d` y `str.isdecimal()`: una cifra decimal de cualquier alfabeto. */
    fun esCifra(c: Int): Boolean = Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER.toInt()

    /** `str.strip()`. Los espacios de Python son todos del plano básico. */
    fun recortar(texto: String): String = texto.trim { esEspacio(it.code) }

    /** `len()`: caracteres, no unidades de UTF-16. */
    fun longitud(texto: String): Int = texto.codePointCount(0, texto.length)

    /** `str(x)` de lo que da `json.loads`. */
    fun str(valor: JsonNode): String = when {
        valor.isTextual -> valor.textValue()
        else -> repr(valor)
    }

    /** `bool(x)` de lo que da `json.loads`: `0`, `0.0`, `""`, `[]`, `{}` y `None` son falsos. */
    fun verdad(valor: JsonNode?): Boolean = when {
        valor == null || valor.isNull || valor.isMissingNode -> false
        valor.isBoolean -> valor.booleanValue()
        valor.isNumber -> valor.decimalValue().signum() != 0
        valor.isTextual -> valor.textValue().isNotEmpty()
        else -> valor.size() > 0
    }

    /** `repr(x)` de lo que da `json.loads`: los mensajes de DRF que lo citan. */
    fun repr(valor: JsonNode): String = when {
        valor.isNull || valor.isMissingNode -> "None"
        valor.isBoolean -> if (valor.booleanValue()) "True" else "False"
        valor.isTextual -> reprTexto(valor.textValue())
        valor.isIntegralNumber -> valor.bigIntegerValue().toString()
        valor.isNumber -> reprFloat(valor.doubleValue())
        valor.isArray -> valor.joinToString(", ", "[", "]") { repr(it) }
        else -> valor.properties().joinToString(", ", "{", "}") { (k, v) -> "${reprTexto(k)}: ${repr(v)}" }
    }

    /** `repr()` de un texto: comillas simples salvo que lleve simples y no dobles. */
    fun reprTexto(texto: String): String {
        val comilla = if ('\'' in texto && '"' !in texto) '"' else '\''
        val salida = StringBuilder().append(comilla)
        texto.codePoints().forEach { c ->
            when {
                c == comilla.code || c == '\\'.code -> salida.append('\\').appendCodePoint(c)
                c == '\t'.code -> salida.append("\\t")
                c == '\n'.code -> salida.append("\\n")
                c == '\r'.code -> salida.append("\\r")
                imprimible(c) -> salida.appendCodePoint(c)
                c <= 0xff -> salida.append("\\x%02x".format(c))
                c <= 0xffff -> salida.append("\\u%04x".format(c))
                else -> salida.append("\\U%08x".format(c))
            }
        }
        return salida.append(comilla).toString()
    }

    /** `str.isprintable()` de un carácter: el espacio sí; los demás separadores y los de control, no. */
    private fun imprimible(c: Int): Boolean = c == ' '.code || when (Character.getType(c).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
        Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SPACE_SEPARATOR -> false
        else -> true
    }

    // ── Números ──────────────────────────────────────────────────────

    /**
     * `repr(float)`: las mismas cifras que `Double.toString` de Java 21 (las
     * más cortas que identifican el número), con el formato de Python:
     * notación normal entre 1e-4 y 1e16, «1e+16» o «1e-05» fuera.
     */
    fun reprFloat(x: Double): String {
        if (x.isNaN()) return "nan"
        if (x.isInfinite()) return if (x > 0) "inf" else "-inf"
        if (x == 0.0) return if (1.0 / x < 0) "-0.0" else "0.0"
        val exacto = masCorto(Math.abs(x))
        val cifras = exacto.unscaledValue().toString()
        val punto = cifras.length - exacto.scale()
        val signo = if (x < 0) "-" else ""
        if (punto > -4 && punto <= 16) {
            return signo + when {
                punto <= 0 -> "0." + "0".repeat(-punto) + cifras
                punto >= cifras.length -> cifras + "0".repeat(punto - cifras.length) + ".0"
                else -> cifras.substring(0, punto) + "." + cifras.substring(punto)
            }
        }
        val exponente = punto - 1
        val mantisa = if (cifras.length > 1) cifras[0] + "." + cifras.substring(1) else cifras
        return signo + mantisa + "e" + (if (exponente < 0) "-" else "+") + "%02d".format(Math.abs(exponente))
    }

    /**
     * Las cifras más cortas que vuelven a dar `x`. `Double.toString` de Java
     * 21 las da, salvo que escribe al menos dos (`4.9E-324` donde Python
     * escribe `5e-324`): si hay una sola cifra que también vale, esa, y si
     * valen dos (por arriba y por abajo), la más cercana, como Python.
     */
    private fun masCorto(x: Double): BigDecimal {
        val deJava = BigDecimal(java.lang.Double.toString(x)).stripTrailingZeros()
        if (deJava.precision() != 2) return deJava
        val abajo = deJava.setScale(deJava.scale() - 1, RoundingMode.FLOOR)
        val arriba = deJava.setScale(deJava.scale() - 1, RoundingMode.CEILING)
        val exacto = BigDecimal(x)
        return listOf(abajo, arriba).filter { it.toDouble() == x }
            .minByOrNull { it.subtract(exacto).abs() }?.stripTrailingZeros() ?: deJava
    }

    /** Una cifra de cualquier alfabeto a ASCII (como hace Python antes de leer un número), o null. */
    private fun cifraAscii(c: Int): Char? = if (esCifra(c)) ('0' + Character.digit(c, 10)) else null

    /**
     * `int(texto)` en base 10, o null si Python da `ValueError`. Python pasa
     * los caracteres no ASCII a ASCII (los espacios a « » y las cifras a
     * 0-9; cualquier otro lo invalida) y después solo admite espacios ASCII
     * alrededor, un signo y cifras con un guion bajo suelto entre ellas.
     */
    fun entero(texto: String): BigInteger? {
        val ascii = StringBuilder()
        for (c in texto.codePoints()) {
            when {
                c < 128 -> ascii.append(c.toChar())
                esEspacio(c) -> ascii.append(' ')
                else -> ascii.append(cifraAscii(c) ?: return null)
            }
        }
        val limpio = ascii.trim { it == ' ' || it in '\t'..'\r' }
        val m = ENTERO.matchEntire(limpio) ?: return null
        return BigInteger(m.groupValues[1] + m.groupValues[2].replace("_", ""))
    }

    private val ENTERO = Regex("""([+-]?)([0-9](?:_?[0-9])*)""")

    /** `int(texto, 16)`, o null si Python da `ValueError`: admite «0x» y guiones bajos entre cifras. */
    fun enteroEnBase16(texto: String): BigInteger? {
        val ascii = StringBuilder()
        for (c in texto.codePoints()) {
            when {
                c < 128 -> ascii.append(c.toChar())
                esEspacio(c) -> ascii.append(' ')
                else -> ascii.append(cifraAscii(c) ?: return null)
            }
        }
        val m = HEX.matchEntire(ascii.trim { it == ' ' || it in '\t'..'\r' }) ?: return null
        val numero = BigInteger(m.groupValues[2].replace("_", ""), 16)
        return if (m.groupValues[1] == "-") numero.negate() else numero
    }

    private val HEX = Regex("""([+-]?)(?:0[xX]_?)?([0-9a-fA-F](?:_?[0-9a-fA-F])*)""")

    /** Un `Decimal` de Python: signo, cifras sin ceros a la izquierda y exponente. */
    sealed interface Decimal {
        /** NaN, sNaN o infinito: DRF los rechaza todos. */
        data object Especial : Decimal

        data class Numero(val negativo: Boolean, val cifras: String, val exponente: Long) : Decimal {
            /** El valor, si el exponente cabe (si no, ya falló la precisión antes de necesitarlo). */
            fun valor(): BigDecimal = BigDecimal(BigInteger(cifras), Math.toIntExact(-exponente)).let { if (negativo) it.negate() else it }
        }
    }

    /** `MAX_EMAX` y `MIN_ETINY` de libmpdec en 64 bits. */
    private const val EMAX = 999_999_999_999_999_999L
    private const val ETINY = -1_999_999_999_999_999_997L

    private val DECIMAL = Regex("""([+-]?)(?:([0-9]+)(?:\.([0-9]*))?|\.([0-9]+))(?:[eE]([+-]?[0-9]+))?""")
    private val ESPECIAL = Regex("""[+-]?(?:inf|infinity|s?nan[0-9]*)""", RegexOption.IGNORE_CASE)

    /**
     * `decimal.Decimal(texto)`, o null si Python da `InvalidOperation`. El
     * texto llega ya recortado (DRF hace `strip()` antes). Se quitan TODOS
     * los guiones bajos, las cifras de otros alfabetos pasan a ASCII y
     * cualquier otro carácter no ASCII lo invalida.
     */
    fun decimal(texto: String): Decimal? {
        val ascii = StringBuilder()
        for (c in texto.codePoints()) {
            when {
                c == '_'.code -> {}
                c < 128 -> ascii.append(c.toChar())
                esEspacio(c) -> ascii.append(' ')
                else -> ascii.append(cifraAscii(c) ?: return null)
            }
        }
        val s = ascii.toString()
        if (ESPECIAL.matches(s)) return Decimal.Especial
        val m = DECIMAL.matchEntire(s) ?: return null
        val entera = m.groupValues[2]
        val fraccion = if (m.groups[4] != null) m.groupValues[4] else m.groupValues[3]
        // Un exponente que no cabe en 64 bits: Python lo rechaza.
        val literal = m.groups[5]?.value?.removePrefix("+")?.toLongOrNull()
            ?: if (m.groups[5] == null) 0L else return null
        if (literal > EMAX || literal < 2 * ETINY) return null
        val cifras = (entera + fraccion).trimStart('0').ifEmpty { "0" }
        val exponente = literal - fraccion.length
        // `Decimal()` tiene que ser exacto con el contexto máximo de libmpdec:
        // fuera de [Etiny, Emax] se desborda o se redondea, y eso es inválido.
        if (exponente + cifras.length - 1 > EMAX || exponente < ETINY) return null
        return Decimal.Numero(m.groupValues[1] == "-", cifras, exponente)
    }

    // ── Fechas ───────────────────────────────────────────────────────

    /**
     * `parse_date` de Django 5.2: primero `date.fromisoformat` de Python
     * 3.12 (`AAAA-MM-DD`, `AAAAMMDD` y las semanas ISO `AAAA-Www-D`,
     * `AAAAWwwD`, `AAAA-Www`, `AAAAWww`, con cifras ASCII y por su largo en
     * UTF-8: 7, 8 o 10), y si no, `\d{4}-\d{1,2}-\d{1,2}$` con cifras de
     * cualquier alfabeto (y un salto de línea al final, por el `$`).
     */
    fun fecha(texto: String): LocalDate? = isoformat(texto) ?: run {
        val m = FECHA_DJANGO.matchEntire(texto) ?: return null
        val (a, mes, d) = (1..3).map { m.groupValues[it].codePoints().toArray().fold(0) { n, c -> n * 10 + Character.digit(c, 10) } }
        valida(a, mes, d)
    }

    private val FECHA_DJANGO = Regex("""(\p{Nd}{4})-(\p{Nd}{1,2})-(\p{Nd}{1,2})\n?""")

    private fun valida(anio: Int, mes: Int, dia: Int): LocalDate? =
        if (anio !in 1..9999) null else runCatching { LocalDate.of(anio, mes, dia) }.getOrNull()

    private fun isoformat(texto: String): LocalDate? {
        if (texto.toByteArray(Charsets.UTF_8).size !in setOf(7, 8, 10) || texto.any { it.code >= 128 }) return null
        var p = 0
        fun cifras(n: Int): Int? {
            if (p + n > texto.length || (p until p + n).any { texto[it] !in '0'..'9' }) return null
            return texto.substring(p, p + n).toInt().also { p += n }
        }
        val anio = cifras(4) ?: return null
        val guion = p < texto.length && texto[p] == '-'
        if (guion) p++
        if (p < texto.length && texto[p] == 'W') {
            p++
            val semana = cifras(2) ?: return null
            val dia = if (p < texto.length) {
                if (guion && texto[p++] != '-') return null
                cifras(1) ?: return null
            } else 1
            return semanaIso(anio, semana, dia)
        }
        val mes = cifras(2) ?: return null
        if (guion && (p >= texto.length || texto[p++] != '-')) return null
        val dia = cifras(2) ?: return null
        if (p != texto.length) return null
        return valida(anio, mes, dia)
    }

    /** `iso_to_ymd`: la semana 53 solo existe si el año la tiene. */
    private fun semanaIso(anio: Int, semana: Int, dia: Int): LocalDate? {
        if (anio !in 1..9999 || dia !in 1..7 || semana !in 1..53) return null
        val cuatroDeEnero = LocalDate.of(anio, 1, 4)
        val lunes = cuatroDeEnero.minusDays((cuatroDeEnero.dayOfWeek.value - 1).toLong())
        if (semana == 53) {
            val primero = LocalDate.of(anio, 1, 1).dayOfWeek.value
            val bisiesto = cuatroDeEnero.isLeapYear
            if (!(primero == 4 || (primero == 3 && bisiesto))) return null
        }
        val fecha = lunes.plus(((semana - 1) * 7 + dia - 1).toLong(), ChronoUnit.DAYS)
        return if (fecha.year in 1..9999) fecha else null
    }
}
