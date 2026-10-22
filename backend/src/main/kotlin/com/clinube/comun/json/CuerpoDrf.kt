package com.clinube.comun.json

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import com.clinube.comun.python.Python
import com.clinube.comun.tiempo.FechasDePython
import com.clinube.comun.validacion.ValidadorDeCorreo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Lee el cuerpo de una petición con las reglas de un serializador de DRF.
 *
 * Los controladores reciben el cuerpo como `JsonNode` y no como una clase
 * de datos porque DRF distingue casos que Jackson junta en una sola
 * excepción, y cada uno tiene su respuesta. Todas se comprobaron contra
 * Django:
 *
 *     sin cuerpo        → los campos, «Este campo es requerido.»
 *     null              → non_field_errors: «No data provided»
 *     [1, 2]            → non_field_errors: «… se esperaba un diccionario pero es un list.»
 *     {"email": null}   → «Este campo no puede ser nulo.»
 *     {"email": ""}     → «Este campo no puede estar en blanco.»
 *     {"email": "   "}  → «Este campo no puede estar en blanco.» (se recorta)
 *     {"email": true}   → «No es una cadena válida.»
 *     {"email": 123}    → vale, como el texto "123"
 */
object CuerpoDrf {

    /** El cuerpo como objeto, o el error de DRF si no lo es. Sin cuerpo cuenta como `{}`. */
    fun objeto(cuerpo: JsonNode?): ObjectNode {
        if (cuerpo == null || cuerpo.isMissingNode) return JsonNodeFactory.instance.objectNode()
        if (cuerpo.isNull) throw ExcepcionApi.validacionGeneral(MensajesDeDjango.SIN_DATOS)
        if (!cuerpo.isObject) throw ExcepcionApi.validacionGeneral(MensajesDeDjango.noEsUnDiccionario(tipoEnPython(cuerpo)))
        return cuerpo as ObjectNode
    }

    /** El nombre que tendría ese valor en Python, que es lo que DRF pone en algunos mensajes. */
    fun tipoEnPython(valor: JsonNode): String = when {
        valor.isArray -> "list"
        valor.isTextual -> "str"
        valor.isBoolean -> "bool"
        valor.isIntegralNumber -> "int"
        valor.isFloatingPointNumber -> "float"
        valor.isNull -> "NoneType"
        else -> "dict"
    }

    /**
     * `repr()` de Python de un valor JSON, para los mensajes que lo
     * incluyen: un UUID que llega como lista sale «“['x']” no es un UUID
     * válido.».
     */
    fun reprDePython(valor: JsonNode): String = Python.repr(valor)
}

/**
 * Los campos de un serializador de DRF, validados todos antes de
 * responder: DRF devuelve los errores de TODOS los campos a la vez, en el
 * orden en que se declararon en el serializador, no el primero que
 * encuentra. Por eso cada vista lee sus campos en ese orden.
 *
 *     val campos = CamposDrf(CuerpoDrf.objeto(cuerpo), parcial = metodo == PATCH)
 *     val nombre = campos.texto("first_name", maximo = 100)
 *     val correo = campos.texto("email", maximo = 254, requerido = false, enBlanco = true, correo = true)
 *     campos.validar()   // 400 con los que fallaron
 *
 * Cada lectura devuelve `Leido.Ausente` si el campo no llegó (y no es
 * obligatorio, o es una actualización parcial) o `Leido.Valor(x)`, donde
 * `x` puede ser nulo si el campo admite nulos y llegó nulo.
 */
class CamposDrf(private val datos: ObjectNode, private val parcial: Boolean = false) {

    sealed interface Leido<out T> {
        data object Ausente : Leido<Nothing>
        data class Valor<T>(val valor: T?) : Leido<T>
    }

    private val errores = LinkedHashMap<String, MutableList<String>>()

    val hayErrores: Boolean get() = errores.isNotEmpty()

    fun tieneError(campo: String) = campo in errores

    /** Un error de un `validate_<campo>` propio de la vista, después de leer el campo. */
    fun error(campo: String, mensaje: String) {
        errores.getOrPut(campo) { mutableListOf() }.add(mensaje)
    }

    /**
     * Ausente, nulo o con valor: la parte común de todos los tipos
     * (`validate_empty_values` de DRF). Devuelve el nodo si hay que seguir
     * mirándolo, o el resultado ya decidido.
     */
    private fun vacio(campo: String, requerido: Boolean, nulo: Boolean): Pair<JsonNode?, Leido<Nothing>?> {
        val valor = datos.get(campo)
        if (valor == null || valor.isMissingNode) {
            if (requerido && !parcial) error(campo, MensajesDeDjango.CAMPO_REQUERIDO)
            return null to Leido.Ausente
        }
        if (valor.isNull) {
            if (!nulo) {
                error(campo, MensajesDeDjango.CAMPO_NULO)
                return null to Leido.Ausente
            }
            return null to Leido.Valor(null)
        }
        return valor to null
    }

    /**
     * Un `CharField`: recorta los espacios, rechaza listas, objetos y
     * booleanos, convierte los números en texto, y comprueba el largo y,
     * si es un `EmailField`, el formato con el validador de Django.
     */
    fun texto(
        campo: String,
        maximo: Int? = null,
        requerido: Boolean = true,
        enBlanco: Boolean = false,
        nulo: Boolean = false,
        correo: Boolean = false,
    ): Leido<String> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        valor!!
        // `str(data).strip() == ''` va antes que el tipo: «[]» no está en blanco, es inválido.
        val texto = Python.recortar(Python.str(valor))
        if (texto.isEmpty()) {
            if (!enBlanco) {
                error(campo, MensajesDeDjango.CAMPO_EN_BLANCO)
                return Leido.Ausente
            }
            return Leido.Valor("")
        }
        if (valor.isBoolean || !(valor.isTextual || valor.isNumber)) {
            // `EmailField` cambia el mensaje «invalid» de `CharField` por el suyo.
            error(campo, if (correo) MensajesDeDjango.CORREO_INVALIDO else MensajesDeDjango.CADENA_INVALIDA)
            return Leido.Ausente
        }
        // Los validadores de DRF, en su orden y todos a la vez: largo, nulos,
        // sustitutos sueltos y, en un `EmailField`, el formato.
        val antes = errores[campo]?.size ?: 0
        if (maximo != null && Python.longitud(texto) > maximo) error(campo, MensajesDeDjango.maximoDeCaracteres(maximo))
        if ('\u0000' in texto) error(campo, MensajesDeDjango.CARACTERES_NULOS)
        texto.indices.firstOrNull { texto[it].isSurrogate() && !esParDeSustitutos(texto, it) }?.let {
            error(campo, MensajesDeDjango.caracterSustituto(texto[it].code))
        }
        if (correo && !ValidadorDeCorreo.esValido(texto)) error(campo, MensajesDeDjango.CORREO_INVALIDO)
        return if ((errores[campo]?.size ?: 0) > antes) Leido.Ausente else Leido.Valor(texto)
    }

    private fun esParDeSustitutos(texto: String, i: Int): Boolean =
        (texto[i].isHighSurrogate() && i + 1 < texto.length && texto[i + 1].isLowSurrogate()) ||
            (texto[i].isLowSurrogate() && i > 0 && texto[i - 1].isHighSurrogate())

    /** Un `DecimalField` con su número de cifras y de decimales, ya redondeado como DRF. */
    data class Decimal(val valor: BigDecimal, val texto: String)

    /**
     * Un `DecimalField`: `Decimal()` de Python sobre `str(dato).strip()`,
     * y la precisión contada como DRF (`"36.50"` tiene dos decimales; el
     * número `36.50` es el float 36.5 y tiene uno). Un "" o "  " es nulo si
     * el campo admite nulos. El texto conserva el «-0.0» de Python, que la
     * base guarda como 0.0.
     */
    fun decimal(campo: String, maxDigitos: Int, decimales: Int, requerido: Boolean = false, nulo: Boolean = false): Leido<Decimal> {
        val crudo = datos.get(campo)
        if (nulo && crudo != null && !crudo.isMissingNode && !crudo.isNull && Python.recortar(Python.str(crudo)).isEmpty()) {
            return Leido.Valor(null)
        }
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        val texto = Python.recortar(Python.str(valor!!))
        if (Python.longitud(texto) > 1000) return fallo(campo, MensajesDeDjango.CADENA_DEMASIADO_LARGA)
        val numero = Python.decimal(texto) as? Python.Decimal.Numero ?: return fallo(campo, MensajesDeDjango.NUMERO_INVALIDO)
        val n = numero.cifras.length.toLong()
        val e = numero.exponente
        val (total, enteros, posiciones) = when {
            e >= 0 -> Triple(n + e, n + e, 0L)
            n > -e -> Triple(n, n + e, -e)
            else -> Triple(-e, 0L, -e)
        }
        if (total > maxDigitos) return fallo(campo, MensajesDeDjango.maximoDeDigitos(maxDigitos))
        if (posiciones > decimales) return fallo(campo, MensajesDeDjango.maximoDeDecimales(decimales))
        if (enteros > maxDigitos - decimales) return fallo(campo, MensajesDeDjango.maximoDeDigitosEnteros(maxDigitos - decimales))
        val redondeado = numero.valor().setScale(decimales)
        val escrito = (if (numero.negativo && redondeado.signum() == 0) "-" else "") + redondeado.toPlainString()
        return Leido.Valor(Decimal(redondeado, escrito))
    }

    /**
     * Un `IntegerField` con su mínimo y su máximo: `int()` de Python sobre
     * `str(dato)` sin un «.0» final (así 12.0 y "12.00 " valen 12).
     */
    fun entero(campo: String, minimo: Long? = null, maximo: Long? = null, requerido: Boolean = false, nulo: Boolean = false): Leido<Long> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        valor!!
        if (valor.isTextual && Python.longitud(valor.textValue()) > 1000) return fallo(campo, MensajesDeDjango.CADENA_DEMASIADO_LARGA)
        val numero = Python.entero(sinPuntoCero(Python.str(valor))) ?: return fallo(campo, MensajesDeDjango.ENTERO_INVALIDO)
        if (maximo != null && numero > maximo.toBigInteger()) return fallo(campo, MensajesDeDjango.menorOIgual(maximo))
        if (minimo != null && numero < minimo.toBigInteger()) return fallo(campo, MensajesDeDjango.mayorOIgual(minimo))
        return Leido.Valor(numero.toLong())
    }

    /** `re.sub(r'\.0*\s*$', '', texto)`: el primer punto desde el que solo quedan ceros y espacios. */
    private fun sinPuntoCero(texto: String): String {
        var i = texto.indexOf('.')
        while (i >= 0) {
            var j = i + 1
            while (j < texto.length && texto[j] == '0') j++
            if (texto.substring(j).all { Python.esEspacio(it.code) }) return texto.substring(0, i)
            i = texto.indexOf('.', i + 1)
        }
        return texto
    }

    /**
     * Un `JSONField`: cualquier valor JSON, salvo un número que en Python es
     * infinito (`1e400`). Se devuelve tal cual llegó; para escribirlo o
     * guardarlo como lo haría Python, [JsonDePython.comoPython].
     */
    fun json(campo: String, requerido: Boolean = false, nulo: Boolean = false): Leido<JsonNode> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        if (JsonDePython.tieneInfinitos(valor!!)) return fallo(campo, MensajesDeDjango.JSON_INVALIDO)
        return Leido.Valor(valor)
    }

    /** Un `ChoiceField`: la clave es `str(dato)`, así que el número 1 vale por la opción "1". */
    fun opcion(campo: String, opciones: Collection<String>, requerido: Boolean = false, nulo: Boolean = false,
               enBlanco: Boolean = false): Leido<String> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        valor!!
        if (enBlanco && valor.isTextual && valor.textValue().isEmpty()) return Leido.Valor("")
        val clave = Python.str(valor)
        if (clave !in opciones) return fallo(campo, MensajesDeDjango.eleccionNoValida(clave))
        return Leido.Valor(clave)
    }

    private fun fallo(campo: String, mensaje: String): Leido<Nothing> {
        error(campo, mensaje)
        return Leido.Ausente
    }

    /** Un `DateField`: `parse_date` de Django (ver [Python.fecha]). */
    fun fecha(campo: String, requerido: Boolean = true, nulo: Boolean = false): Leido<LocalDate> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        val fecha = valor!!.takeIf { it.isTextual }?.let { Python.fecha(it.textValue()) }
        if (fecha == null) {
            error(campo, MensajesDeDjango.FECHA_INVALIDA)
            return Leido.Ausente
        }
        return Leido.Valor(fecha)
    }

    /** Un `DateTimeField`: `fromisoformat` de Python; sin zona, la hora es de la clínica. */
    fun fechaHora(campo: String, zona: ZoneId, requerido: Boolean = true, nulo: Boolean = false): Leido<OffsetDateTime> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        val momento = valor!!.takeIf { it.isTextual }?.let { FechasDePython.fechaHora(it.textValue(), zona) }
        if (momento == null) {
            error(campo, MensajesDeDjango.FECHA_HORA_INVALIDA)
            return Leido.Ausente
        }
        return Leido.Valor(momento)
    }

    /**
     * Un `BooleanField` de DRF, con los textos que acepta: «y», «on»,
     * «1»… En uno que admite nulos, «null» o "" es nulo.
     */
    fun booleano(campo: String, requerido: Boolean = false, nulo: Boolean = false): Leido<Boolean> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        valor!!
        if (valor.isBoolean) return Leido.Valor(valor.booleanValue())
        if (valor.isIntegralNumber || valor.isFloatingPointNumber) {
            when (valor.asDouble()) {
                1.0 -> return Leido.Valor(true)
                0.0 -> return Leido.Valor(false)
            }
        }
        if (valor.isTextual) {
            val t = valor.textValue()
            if (t in VERDADEROS) return Leido.Valor(true)
            if (t in FALSOS) return Leido.Valor(false)
            if (nulo && t in NULOS) return Leido.Valor(null)
        }
        error(campo, MensajesDeDjango.BOOLEANO_INVALIDO)
        return Leido.Ausente
    }

    /**
     * Un `PrimaryKeyRelatedField` con clave UUID. `existe` dice si el
     * objeto está en el conjunto que admite el campo; si no, «Clave
     * primaria "…" inválida - objeto no existe.».
     */
    fun clave(
        campo: String,
        requerido: Boolean = true,
        nulo: Boolean = false,
        existe: (UUID) -> Boolean,
    ): Leido<UUID> {
        val (valor, decidido) = vacio(campo, requerido, nulo)
        if (decidido != null) return decidido
        valor!!
        if (valor.isBoolean) {
            error(campo, "Tipo incorrecto. Se esperaba valor de clave primaria y se recibió bool.")
            return Leido.Ausente
        }
        val texto = if (valor.isTextual) valor.textValue() else if (valor.isNumber) valor.asText() else CuerpoDrf.reprDePython(valor)
        // Un número lo convierte Django en UUID (`UUID(int=n)`), así que no
        // es «UUID inválido» sino «no existe».
        if (valor.isIntegralNumber) {
            error(campo, MensajesDeDjango.claveInexistente(texto))
            return Leido.Ausente
        }
        val id = uuidDePython(texto)
        if (id == null) {
            error(campo, MensajesDeDjango.uuidInvalido(texto))
            return Leido.Ausente
        }
        if (!existe(id)) {
            error(campo, MensajesDeDjango.claveInexistente(texto))
            return Leido.Ausente
        }
        return Leido.Valor(id)
    }

    /** 400 con los errores de todos los campos, si hubo alguno. */
    fun validar() {
        if (errores.isNotEmpty()) throw ExcepcionApi.validacion(errores)
    }

    companion object {
        val VERDADEROS = setOf("t", "T", "y", "Y", "yes", "Yes", "YES", "true", "True", "TRUE", "on", "On", "ON", "1")
        val FALSOS = setOf("f", "F", "n", "N", "no", "No", "NO", "false", "False", "FALSE", "off", "Off", "OFF", "0")
        val NULOS = setOf("null", "Null", "NULL", "")

        /**
         * `uuid.UUID(texto)` de Python: admite el UUID con o sin guiones,
         * entre llaves y con prefijo «urn:uuid:». `UUID.fromString` de Java
         * es más permisivo con los ceros a la izquierda, así que se
         * comprueba también la forma.
         */
        private val UUID_DE_PYTHON = Regex("""^(urn:uuid:)?\{?[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}}?$""")

        /** El UUID como lo lee Python, o nulo si no lo es. */
        fun uuidDePython(texto: String): UUID? {
            if (!UUID_DE_PYTHON.matches(texto)) return null
            val hex = texto.removePrefix("urn:uuid:").removePrefix("{").removeSuffix("}").replace("-", "")
            return UUID.fromString("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20)}")
        }
    }

    /**
     * El `CharField` obligatorio de las rutas de autenticación, con la
     * forma antigua de esta clase (nulo si falló). Recorta también la
     * contraseña, como `PasswordField` de SimpleJWT.
     */
    fun texto(campo: String): String? = (texto(campo, maximo = null) as? Leido.Valor)?.valor
}
