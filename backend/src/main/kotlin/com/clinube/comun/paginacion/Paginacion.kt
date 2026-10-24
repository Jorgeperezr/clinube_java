package com.clinube.comun.paginacion

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * La paginación de DRF: `{count, next, previous, results}`, 20 por página
 * y `?page=N` empezando en 1.
 *
 * `next` y `previous` son direcciones ABSOLUTAS, como las de Django
 * (`request.build_absolute_uri()`), y conservan los demás parámetros de
 * la consulta: el panel sigue el enlace tal cual, así que un filtro que
 * se perdiera al pasar de página haría que la página 2 enseñara otra
 * lista. Cuando la anterior es la 1, el parámetro `page` se quita en vez
 * de ponerse a 1, igual que hace DRF.
 */
data class Pagina<T>(
    val count: Long,
    val next: String?,
    val previous: String?,
    val results: List<T>,
) {
    companion object {
        const val POR_PAGINA = 20

        /** Donde Django no pagina, la respuesta es un arreglo plano: eso no usa esta clase. */
        fun <T> de(
            total: Long,
            elementos: List<T>,
            pagina: Int,
            peticion: HttpServletRequest,
        ): Pagina<T> {
            val paginas = if (total == 0L) 1 else ((total + POR_PAGINA - 1) / POR_PAGINA).toInt()
            return Pagina(
                count = total,
                next = if (pagina < paginas) enlace(peticion, pagina + 1) else null,
                previous = when {
                    pagina <= 1 -> null
                    pagina - 1 == 1 -> enlace(peticion, null)
                    else -> enlace(peticion, pagina - 1)
                },
                results = elementos,
            )
        }

        /**
         * El número de página pedido. Django responde 404 «Página
         * inválida.» a lo que no sea un número o esté fuera de rango, y
         * acepta `?page=last`.
         */
        fun numeroDePagina(crudo: String?, total: Long): Int {
            val paginas = if (total == 0L) 1 else ((total + POR_PAGINA - 1) / POR_PAGINA).toInt()
            val pedida = when {
                crudo.isNullOrBlank() -> 1
                crudo == "last" -> paginas
                else -> crudo.toIntOrNull() ?: throw paginaInvalida()
            }
            if (pedida < 1 || pedida > paginas) throw paginaInvalida()
            return pedida
        }

        fun pageable(pagina: Int, orden: Sort = Sort.unsorted()): Pageable =
            PageRequest.of(pagina - 1, POR_PAGINA, orden)

        private fun paginaInvalida() =
            ExcepcionApi.noEncontrado(MensajesDeDjango.PAGINA_INVALIDA)

        /**
         * El enlace a otra página, construido como lo construye DRF
         * (`replace_query_param` y `remove_query_param`): decodifica la
         * consulta, cambia o quita `page`, **ordena los parámetros por
         * nombre** y la vuelve a codificar con `quote_plus`. Por eso
         * `?search=Pérez&page=2` da `?page=3&search=P%C3%A9rez`, con `page`
         * delante, y un espacio sale como `+`.
         */
        private fun enlace(peticion: HttpServletRequest, pagina: Int?): String {
            val base = peticion.requestURL.toString()
            val parametros = leerConsulta(peticion.queryString)
            if (pagina == null) parametros.remove("page") else parametros["page"] = mutableListOf(pagina.toString())
            val consulta = parametros.entries
                .sortedBy { it.key }
                .flatMap { (clave, valores) -> valores.map { "${codificar(clave)}=${codificar(it)}" } }
                .joinToString("&")
            return if (consulta.isEmpty()) base else "$base?$consulta"
        }

        /** `parse_qs(consulta, keep_blank_values=True)` de Python. */
        fun leerConsulta(consulta: String?): LinkedHashMap<String, MutableList<String>> {
            val parametros = LinkedHashMap<String, MutableList<String>>()
            consulta?.split("&")?.filter { it.isNotEmpty() }?.forEach { campo ->
                val partes = campo.split("=", limit = 2)
                val clave = decodificar(partes[0])
                val valor = if (partes.size == 2) decodificar(partes[1]) else ""
                parametros.getOrPut(clave) { mutableListOf() }.add(valor)
            }
            return parametros
        }

        /**
         * `unquote_plus` de Python: `+` es un espacio y `%XX` un byte. Un `%`
         * que no va seguido de dos cifras hexadecimales se deja tal cual, en
         * vez de fallar como `URLDecoder`.
         */
        private fun decodificar(texto: String): String {
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < texto.length) {
                val c = texto[i]
                when {
                    c == '+' -> { bytes.write(' '.code); i++ }
                    c == '%' && i + 2 < texto.length &&
                        texto[i + 1].digitToIntOrNull(16) != null && texto[i + 2].digitToIntOrNull(16) != null -> {
                        bytes.write(texto.substring(i + 1, i + 3).toInt(16)); i += 3
                    }
                    else -> { bytes.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
                }
            }
            return String(bytes.toByteArray(), Charsets.UTF_8)
        }

        /**
         * `quote_plus` de Python: se dejan letras, cifras y `_.-~`; el
         * espacio es `+`; todo lo demás, `%XX` en mayúsculas sobre los bytes
         * UTF-8. No es `URLEncoder`, que deja `*` y codifica `~`.
         */
        private fun codificar(texto: String): String {
            val salida = StringBuilder()
            texto.toByteArray(Charsets.UTF_8).forEach { b ->
                val c = b.toInt() and 0xFF
                when {
                    c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                        c == '_'.code || c == '.'.code || c == '-'.code || c == '~'.code -> salida.append(c.toChar())
                    c == ' '.code -> salida.append('+')
                    else -> salida.append('%').append("%02X".format(c))
                }
            }
            return salida.toString()
        }
    }
}
