package com.clinube.clinica

import com.fasterxml.jackson.databind.JsonNode
import java.text.Normalizer

/**
 * Alertas clínicas a partir de los antecedentes del paciente, como
 * `apps/clinical/alertas.py`. Dos usos:
 *
 *   · [deAntecedentes]: lo que hay que tener presente con ESTE paciente
 *     (alergias, anticoagulantes, bifosfonatos, embarazo…). Se enseña
 *     arriba de su ficha.
 *   · [revisarReceta]: cruza el texto de una receta con esas alertas.
 *
 * Avisa, no bloquea; sin IA ni servicios externos; y una frase que empieza
 * negando («niega alergias», «sin…», «no toma…») no dispara. Es seguridad
 * del paciente, así que el resultado tiene que ser EL MISMO que en Django
 * para cualquier texto: lo comprueba `ReglasDeAlertasTest` contra miles de
 * casos calculados con el `alertas.py` de Django
 * (`scripts/casos-de-alertas.py`).
 *
 * Por eso aquí no hay expresiones regulares de Java para `\b`, `\w` y `\s`:
 * en Python son las de `str.isalnum()` y `str.isspace()`, que no coinciden
 * del todo con ninguna clase de Java («²» es una letra o cifra en Python y
 * no en Java; «\u001c» es un espacio en Python y no en Java). Se escriben a
 * mano con la definición de Python.
 */
object ReglasDeAlertas {

    const val ALTO = "alto"
    const val MEDIO = "medio"

    /** Los antecedentes (`MedicalBackground`). */
    data class Antecedentes(val alergias: String, val medicacion: String, val condiciones: String, val embarazada: Boolean?)

    /** Lo que se usa del último formulario 033: la sección D y el embarazo. */
    data class Formulario033(val antecedentesPersonales: JsonNode?, val embarazada: Boolean?)

    // ── Texto como en Python ─────────────────────────────────────────

    /** `str.isspace()` de Python, que es también su `\s`. */
    fun esEspacio(c: Int): Boolean = c in 0x09..0x0d || c in 0x1c..0x20 || c == 0x85 || c == 0xa0 ||
        c == 0x1680 || c in 0x2000..0x200a || c == 0x2028 || c == 0x2029 || c == 0x202f || c == 0x205f || c == 0x3000

    /** `\w` de Python: `str.isalnum()` (letras y cualquier número) o el guion bajo. */
    fun esPalabra(c: Int): Boolean = c == '_'.code || Character.isLetter(c) || when (Character.getType(c)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }

    /** `str.strip()` de Python. */
    fun recortar(texto: String): String = texto.trim { esEspacio(it.code) }

    /** En minúsculas y sin tildes: NFD y fuera las marcas que no ocupan espacio. */
    fun normalizar(texto: String?): String {
        val descompuesto = Normalizer.normalize((texto ?: "").lowercase(), Normalizer.Form.NFD)
        val salida = StringBuilder(descompuesto.length)
        descompuesto.codePoints().forEach { if (Character.getType(it) != Character.NON_SPACING_MARK.toInt()) salida.appendCodePoint(it) }
        return salida.toString()
    }

    /** Dónde empieza `\b<raíz>` en el texto, o -1. Todas las raíces empiezan por una letra. */
    private fun posicion(texto: String, raiz: String): Int {
        var i = texto.indexOf(raiz)
        while (i >= 0) {
            if (i == 0 || !esPalabra(Character.codePointBefore(texto, i))) return i
            i = texto.indexOf(raiz, i + 1)
        }
        return -1
    }

    private val NEGACIONES = listOf("niega", "no", "sin", "ningun", "ninguna", "descarta", "negativo", "nada")

    /** `^\s*(niega|no\b|sin\b|ningun|ninguna|descarta|negativo|nada)`. */
    private fun niega(normalizado: String): Boolean {
        var i = 0
        while (i < normalizado.length && esEspacio(normalizado[i].code)) i++
        return NEGACIONES.any { palabra ->
            normalizado.startsWith(palabra, i) && (palabra != "no" && palabra != "sin" ||
                i + palabra.length == normalizado.length || !esPalabra(normalizado.codePointAt(i + palabra.length)))
        }
    }

    private val SEPARADORES = Regex("[.;,\n]+")

    /** Frases del campo, sin las que empiezan negando. */
    private fun fragmentos(texto: String): List<String> =
        texto.split(SEPARADORES).filter { recortar(it).isNotEmpty() && !niega(normalizar(it)) }.map(::recortar)

    /** La frase original de la primera coincidencia, o null. */
    private fun buscar(texto: String, raices: List<String>): String? {
        for (frase in fragmentos(texto)) {
            val n = normalizar(frase)
            if (raices.any { posicion(n, it) >= 0 }) return frase
        }
        return null
    }

    // ── Vocabulario ──────────────────────────────────────────────────
    val PENICILINAS = listOf("penicilin", "amoxicilin", "ampicilin", "augmentin", "betalactam", "dicloxacilin")
    val CEFALOSPORINAS = listOf("cefalexin", "cefadroxil", "cefuroxim", "ceftriaxon", "cefazolin", "cefalosporin")
    val OTROS_ANTIBIOTICOS = listOf("clindamicin", "azitromicin", "eritromicin", "claritromicin", "metronidazol",
        "doxiciclin", "tetraciclin", "minociclin", "ciprofloxacin", "levofloxacin")
    val TETRACICLINAS = listOf("doxiciclin", "tetraciclin", "minociclin")
    val AINE = listOf("ibuprofen", "naproxen", "diclofenac", "ketorolac", "aspirin", "acido acetilsalicilico",
        "ketoprofen", "dexketoprofen", "meloxicam", "celecoxib", "etoricoxib", "nimesulid",
        "piroxicam", "aine")
    val ANESTESICOS = listOf("lidocain", "articain", "mepivacain", "prilocain", "bupivacain", "anestesi")
    val ANTICOAGULANTES = listOf("warfarin", "acenocumarol", "sintrom", "rivaroxaban", "xarelto", "apixaban",
        "eliquis", "dabigatran", "pradaxa", "edoxaban", "heparin", "enoxaparin",
        "clopidogrel", "plavix", "ticagrelor", "prasugrel", "anticoagul", "antiagreg")
    val BIFOSFONATOS = listOf("alendron", "risedron", "ibandron", "zoledron", "pamidron", "bifosfonat",
        "denosumab", "prolia", "xgeva", "fosamax")
    val DIABETES = listOf("diabet", "metformin", "insulin", "glibenclamid")
    val CARDIOVASCULAR = listOf("hipertens", "cardiopat", "infarto", "arritmia", "angina", "insuficiencia cardiaca",
        "enf. cardiaca", "enfermedad cardiaca")
    val ENDOCARDITIS = listOf("endocarditis", "protesis valvular", "valvula protesica", "valvula artificial",
        "cardiopatia congenita")
    val HEMORRAGICOS = listOf("hemofilia", "hemorrag", "coagulopat", "von willebrand", "trombocitopen")

    /** La verdad de Python de un valor JSON: `0`, `""`, `[]`, `{}` y `null` son falsos. */
    private fun verdad(v: JsonNode?): Boolean = when {
        v == null || v.isNull -> false
        v.isBoolean -> v.booleanValue()
        v.isNumber -> v.decimalValue().signum() != 0
        v.isTextual -> v.textValue().isNotEmpty()
        v.isContainerNode -> v.size() > 0
        else -> true
    }

    /**
     * Casillas marcadas en el formulario 033 (sección D) con su detalle
     * escrito: {nombre de casilla: detalle}.
     *
     * Django da un 500 si la sección no es un objeto o si el detalle de una
     * casilla que se lee no es texto; aquí no cuentan (docs/DIFERENCIAS.md).
     */
    private fun marcadas(form033: Formulario033?): Map<String, String> {
        val seccion = form033?.antecedentesPersonales?.takeIf { it.isObject } ?: return emptyMap()
        val salida = LinkedHashMap<String, String>()
        for ((casilla, valor) in seccion.properties()) {
            if (valor.isObject && verdad(valor.get("checked"))) {
                salida[casilla] = valor.get("detail")?.takeIf { it.isTextual }?.textValue() ?: ""
            }
        }
        return salida
    }

    /**
     * Las alertas del paciente a partir de sus antecedentes y del último
     * formulario 033: las casillas marcadas y lo que se escribió en su
     * detalle («Alergia antibiótico: amoxicilina»). Cada una con clave,
     * nivel, titulo, detalle y fuente, en ese orden.
     */
    fun deAntecedentes(fondo: Antecedentes?, form033: Formulario033? = null): List<Map<String, String>> {
        val alergias = fondo?.alergias ?: ""
        val medicacion = fondo?.medicacion ?: ""
        val condiciones = fondo?.condiciones ?: ""
        val embarazada = fondo?.embarazada == true
        val marcadas = marcadas(form033)
        val embarazada033 = form033?.embarazada == true

        val salida = mutableListOf<Map<String, String>>()
        fun agregar(clave: String, nivel: String, titulo: String, detalle: String, fuente: String) {
            salida += linkedMapOf("clave" to clave, "nivel" to nivel, "titulo" to titulo, "detalle" to detalle, "fuente" to fuente)
        }

        fun deTexto(campo: String, texto: String, raices: List<String>) = buscar(texto, raices)?.let { "$campo: «$it»" }

        /** El detalle escrito en alguna de esas casillas marcadas. */
        fun de033(casillas: List<String>, raices: List<String>): String? {
            for (casilla in casillas) {
                val detalle = marcadas[casilla] ?: continue
                deTexto("Formulario 033 ($casilla)", detalle, raices)?.let { return it }
            }
            return null
        }

        fun casilla(nombre: String) = if (nombre in marcadas) "Formulario 033: «$nombre»" else null

        // Dónde puede estar escrita una alergia, una enfermedad o un fármaco.
        fun enAlergias(raices: List<String>) = deTexto("Alergias", alergias, raices)
            ?: de033(listOf("Alergia antibiótico", "Alergia anestesia", "Otro"), raices)
        fun enCondiciones(raices: List<String>) = deTexto("Condiciones", condiciones, raices) ?: de033(listOf("Otro"), raices)
        fun enMedicacion(raices: List<String>) = deTexto("Medicación", medicacion, raices) ?: de033(listOf("Otro"), raices)

        val penicilina = enAlergias(PENICILINAS)
        val antibiotico = casilla("Alergia antibiótico")
        if (penicilina != null) {
            agregar("alergia_penicilina", ALTO, "Alergia a penicilinas",
                "No recetar penicilinas (amoxicilina, ampicilina…). Precaución con cefalosporinas.", penicilina)
        } else if (antibiotico != null) {
            val detalle = recortar(marcadas.getValue("Alergia antibiótico"))
            agregar("alergia_antibiotico", ALTO, "Alergia a antibióticos",
                if (detalle.isNotEmpty()) "El formulario 033 la marca ($detalle): confirmar cuál antes de recetar."
                else "El formulario 033 la marca sin especificar cuál: confirmarlo antes de recetar.", antibiotico)
        }
        enAlergias(AINE)?.let {
            agregar("alergia_aine", ALTO, "Alergia a antiinflamatorios (AINE)",
                "No recetar ibuprofeno, aspirina, diclofenaco ni otros AINE.", it)
        }
        (enAlergias(ANESTESICOS) ?: casilla("Alergia anestesia"))?.let {
            agregar("alergia_anestesico", ALTO, "Alergia a anestésicos locales",
                "Confirmar el anestésico implicado antes de infiltrar.", it)
        }
        enAlergias(listOf("latex"))?.let { agregar("alergia_latex", ALTO, "Alergia al látex", "Guantes y dique sin látex.", it) }

        val anticoagulado = enMedicacion(ANTICOAGULANTES)
        val hemorragico = if (anticoagulado == null) enCondiciones(HEMORRAGICOS) ?: casilla("Hemorragias") else null
        if (anticoagulado != null) {
            agregar("anticoagulado", ALTO, "Toma anticoagulantes o antiagregantes",
                "Riesgo de sangrado en extracciones y cirugía. Evitar AINE.", anticoagulado)
        } else if (hemorragico != null) {
            agregar("hemorragico", ALTO, "Trastorno de la coagulación",
                "Riesgo de sangrado en procedimientos cruentos.", hemorragico)
        }
        enMedicacion(BIFOSFONATOS)?.let {
            agregar("bifosfonatos", ALTO, "Bifosfonatos o denosumab",
                "Riesgo de osteonecrosis de los maxilares en extracciones e implantes.", it)
        }
        enCondiciones(ENDOCARDITIS)?.let {
            agregar("endocarditis", ALTO, "Riesgo de endocarditis",
                "Valorar profilaxis antibiótica antes de procedimientos que sangran.", it)
        }

        if (embarazada || embarazada033) {
            agregar("embarazo", ALTO, "Embarazo",
                "Evitar AINE y tetraciclinas; limitar radiografías a lo imprescindible.",
                if (embarazada) "Antecedentes: embarazo" else "Formulario 033: embarazada")
        }

        (enCondiciones(DIABETES) ?: deTexto("Medicación", medicacion, DIABETES) ?: casilla("Diabetes"))?.let {
            agregar("diabetes", MEDIO, "Diabetes",
                "Cicatrización y riesgo periodontal mayores; confirmar control glucémico.", it)
        }
        (enCondiciones(CARDIOVASCULAR) ?: casilla("Hipertensión arterial") ?: casilla("Enf. cardíaca"))?.let {
            agregar("cardiovascular", MEDIO, "Hipertensión o cardiopatía",
                "Limitar el vasoconstrictor (epinefrina) y tomar la presión antes de intervenir.", it)
        }
        return salida
    }

    // ── Receta frente a antecedentes ─────────────────────────────────

    private class Regla(val clave: String, val raices: List<String>, val nivel: String, val mensaje: String)

    private val REGLAS_RECETA = listOf(
        Regla("alergia_penicilina", PENICILINAS, ALTO, "Receta una penicilina ({x}) a un paciente alérgico a penicilinas."),
        Regla("alergia_penicilina", CEFALOSPORINAS, MEDIO, "Receta una cefalosporina ({x}): posible reacción cruzada con la alergia a penicilinas."),
        Regla("alergia_antibiotico", PENICILINAS + CEFALOSPORINAS + OTROS_ANTIBIOTICOS, ALTO,
            "Receta un antibiótico ({x}) y el formulario 033 marca alergia a antibióticos sin especificar."),
        Regla("alergia_aine", AINE, ALTO, "Receta un AINE ({x}) a un paciente alérgico a los AINE."),
        Regla("anticoagulado", AINE, ALTO, "Receta un AINE ({x}) a un paciente con anticoagulante o antiagregante: aumenta el riesgo de sangrado."),
        Regla("hemorragico", AINE, MEDIO, "Receta un AINE ({x}) a un paciente con trastorno de la coagulación."),
        Regla("embarazo", TETRACICLINAS, ALTO, "Receta una tetraciclina ({x}) durante el embarazo: contraindicada."),
        Regla("embarazo", AINE, MEDIO, "Receta un AINE ({x}) durante el embarazo: evitarlo, sobre todo en el tercer trimestre."),
    )

    /** `\b<raíz>\w*`: la palabra entera que empieza por la raíz, o null. */
    private fun palabra(texto: String, raiz: String): String? {
        val inicio = posicion(texto, raiz).takeIf { it >= 0 } ?: return null
        var fin = inicio + raiz.length
        while (fin < texto.length) {
            val c = texto.codePointAt(fin)
            if (!esPalabra(c)) break
            fin += Character.charCount(c)
        }
        return texto.substring(inicio, fin)
    }

    /**
     * Choques entre el texto de una receta y las alertas del paciente: clave,
     * nivel, titulo (el choque) y fuente (la alerta).
     */
    fun revisarReceta(texto: String, alertas: List<Map<String, String>>): List<Map<String, String>> {
        val presentes = alertas.associateBy { it.getValue("clave") }
        val n = normalizar(texto)
        val salida = mutableListOf<Map<String, String>>()
        val vistos = HashSet<Pair<String, String>>()
        for (regla in REGLAS_RECETA) {
            val alerta = presentes[regla.clave] ?: continue
            for (raiz in regla.raices) {
                val hallada = palabra(n, raiz)
                if (hallada != null && vistos.add(regla.clave to hallada)) {
                    salida += linkedMapOf(
                        "clave" to regla.clave, "nivel" to regla.nivel,
                        "titulo" to regla.mensaje.replace("{x}", hallada),
                        "fuente" to "${alerta.getValue("titulo")} — ${alerta.getValue("fuente")}",
                    )
                    break
                }
            }
        }
        return salida
    }
}
