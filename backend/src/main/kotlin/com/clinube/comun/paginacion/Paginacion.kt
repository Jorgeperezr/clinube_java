package com.clinube.comun.paginacion

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.errores.MensajesDeDjango
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.web.util.UriComponentsBuilder

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

        private fun enlace(peticion: HttpServletRequest, pagina: Int?): String {
            val base = StringBuilder(peticion.requestURL)
            peticion.queryString?.let { base.append('?').append(it) }
            val constructor = UriComponentsBuilder.fromUriString(base.toString())
            if (pagina == null) {
                constructor.replaceQueryParam("page")
            } else {
                constructor.replaceQueryParam("page", pagina)
            }
            return constructor.build().toUriString()
        }
    }
}
