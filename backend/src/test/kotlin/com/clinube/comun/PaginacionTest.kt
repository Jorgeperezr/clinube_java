package com.clinube.comun

import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.paginacion.Pagina
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PaginacionTest {

    private fun peticion(consulta: String? = null) = MockHttpServletRequest("GET", "/api/v1/users/").apply {
        scheme = "http"
        serverName = "127.0.0.1"
        serverPort = 8000
        queryString = consulta
    }

    @Test
    fun `una sola pagina no tiene siguiente ni anterior`() {
        val p = Pagina.de(5, listOf("a"), 1, peticion())
        assertEquals(5, p.count)
        assertNull(p.next)
        assertNull(p.previous)
    }

    @Test
    fun `los enlaces son direcciones absolutas`() {
        // El panel sigue el enlace tal cual, así que una dirección
        // relativa no le serviría.
        val p = Pagina.de(45, (1..20).map { "x" }, 1, peticion())
        assertEquals("http://127.0.0.1:8000/api/v1/users/?page=2", p.next)
    }

    @Test
    fun `al volver a la primera pagina se quita el parametro, no se pone a uno`() {
        // Es lo que hace DRF: `?page=1` no aparece nunca en `previous`.
        val p = Pagina.de(45, (1..20).map { "x" }, 2, peticion("page=2"))
        assertEquals("http://127.0.0.1:8000/api/v1/users/", p.previous)
        assertEquals("http://127.0.0.1:8000/api/v1/users/?page=3", p.next)
    }

    @Test
    fun `los enlaces conservan los demas filtros, ordenados como DRF`() {
        // Si se perdiera el filtro al pasar de página, la página 2
        // enseñaría otra lista. Y DRF ORDENA los parámetros por nombre:
        // `page` va delante de `search`.
        val p = Pagina.de(45, (1..20).map { "x" }, 2, peticion("search=P%C3%A9rez&page=2"))
        assertEquals("http://127.0.0.1:8000/api/v1/users/?page=3&search=P%C3%A9rez", p.next)
        assertEquals("http://127.0.0.1:8000/api/v1/users/?search=P%C3%A9rez", p.previous)
    }

    @Test
    fun `la consulta se reconstruye exactamente como replace_query_param de DRF`() {
        // Cada esperado salió de ejecutar `replace_query_param` de DRF
        // 3.17 con la misma entrada.
        val base = "http://127.0.0.1:8000/api/v1/users/"
        val casos = listOf(
            "zona=a&nombre=Ana%20P%C3%A9rez" to "?nombre=Ana+P%C3%A9rez&page=2&zona=a",
            "q=a+b" to "?page=2&q=a+b",
            "q=~x*y/z:w" to "?page=2&q=~x%2Ay%2Fz%3Aw",
            "estado=a&estado=b" to "?estado=a&estado=b&page=2",
            "solo&page=1" to "?page=2&solo=",
            "q=100%zz" to "?page=2&q=100%25zz",
            "a=1&&b=2" to "?a=1&b=2&page=2",
        )
        casos.forEach { (entrada, esperado) ->
            val p = Pagina.de(45, (1..20).map { "x" }, 1, peticion(entrada))
            assertEquals(base + esperado, p.next, "con la consulta «$entrada»")
        }
    }

    @Test
    fun `al quitar page sin mas parametros no queda interrogacion`() {
        val p = Pagina.de(45, (1..20).map { "x" }, 2, peticion("page=2"))
        assertEquals("http://127.0.0.1:8000/api/v1/users/", p.previous)
    }

    @Test
    fun `la ultima pagina no tiene siguiente`() {
        val p = Pagina.de(45, (1..5).map { "x" }, 3, peticion("page=3"))
        assertNull(p.next)
        assertEquals("http://127.0.0.1:8000/api/v1/users/?page=2", p.previous)
    }

    @Test
    fun `una pagina fuera de rango es un 404 con el texto de Django`() {
        val e = assertFailsWith<ExcepcionApi> { Pagina.numeroDePagina("2", 5) }
        assertEquals(404, e.estado.value())
        assertEquals("not_found", e.codigo)
        assertEquals("Página inválida.", e.mensaje)
    }

    @Test
    fun `lo que no es un numero tambien es pagina invalida`() {
        listOf("0", "-1", "abc", "1.5").forEach { malo ->
            assertFailsWith<ExcepcionApi>("debería fallar con «$malo»") {
                Pagina.numeroDePagina(malo, 100)
            }
        }
    }

    @Test
    fun `sin parametro es la primera, y last es la ultima`() {
        assertEquals(1, Pagina.numeroDePagina(null, 45))
        assertEquals(1, Pagina.numeroDePagina("", 45))
        assertEquals(3, Pagina.numeroDePagina("last", 45))
        assertEquals(1, Pagina.numeroDePagina("last", 0))
    }

    @Test
    fun `una lista vacia sigue teniendo una pagina`() {
        // Django no responde 404 a la página 1 de una lista vacía.
        assertEquals(1, Pagina.numeroDePagina("1", 0))
        val p = Pagina.de(0, emptyList<String>(), 1, peticion())
        assertEquals(0, p.count)
        assertNull(p.next)
    }
}
