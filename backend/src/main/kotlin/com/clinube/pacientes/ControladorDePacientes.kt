package com.clinube.pacientes

import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.paginacion.Pagina
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.fecha
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.instanteONulo
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.LocalDate
import java.time.Year

/** Pacientes (`apps/patients`): de momento, la lista y los cumpleaños. */
@RestController
class ControladorDePacientes(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
) {

    companion object {
        /**
         * Los órdenes de la lista (`ORDERING_MAP` de Django). Un valor
         * desconocido cae en `name_asc`. Los que ordenan por atenciones o
         * por próxima cita necesitan esos valores ANTES de paginar; los
         * demás, no.
         */
        val ORDENES = mapOf(
            "created_desc" to "p.created_at desc",
            "created_asc" to "p.created_at asc",
            "name_asc" to "p.last_name asc, p.first_name asc",
            "name_desc" to "p.last_name desc, p.first_name desc",
            "attention_desc" to "$ATENCIONES desc, p.last_name asc",
            "attention_asc" to "$ATENCIONES asc, p.last_name asc",
            // Los pacientes sin cita futura, al final.
            "next_appt_asc" to "$PROXIMA asc nulls last, p.last_name asc",
        )

        private const val ATENCIONES =
            "(select count(*) from agenda_appointment a where a.patient_id = p.id)"
        private const val PROXIMA =
            "(select min(a.scheduled_start) from agenda_appointment a where a.patient_id = p.id and a.scheduled_start >= :ahora)"

        /**
         * `icontains` de Django en PostgreSQL: `UPPER(col) LIKE UPPER('%valor%')`
         * con `\`, `%` y `_` escapados, para que «50%» busque «50%» y no
         * «50 lo que sea».
         */
        fun patronContiene(texto: String): String =
            "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }

    /**
     * GET /api/v1/patients/ — RF-PAC-01, RF-PAC-06. Búsqueda por nombre,
     * apellido, cédula o teléfono con `?search=`, orden con `?ordering=` y
     * paginación de 20.
     *
     * Las atenciones y la próxima cita se calculan con una subconsulta POR
     * FILA y DESPUÉS de cortar la página, no con un JOIN y un GROUP BY sobre
     * todas las citas de la clínica, que es lo que hace Django y lo que el
     * plan pide no repetir: así la lista hace el mismo trabajo con 3
     * pacientes que con 30.000. Solo si se ordena por uno de esos dos
     * valores hace falta calcularlo antes, y entonces va en el ORDER BY.
     */
    @GetMapping("/api/v1/patients/")
    fun lista(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) ordering: String?,
        @RequestParam(required = false) page: String?,
        peticion: HttpServletRequest,
    ): Pagina<Map<String, Any?>> {
        permisos.exigir(Permisos.PERSONAL)
        val ahora = Timestamp.from(zona.ahora())
        val parametros = mutableMapOf<String, Any?>("clinica" to contexto.clinica?.id, "ahora" to ahora)

        var filtro = "p.tenant_id = :clinica and p.is_active"
        if (!search.isNullOrEmpty()) {
            parametros["busqueda"] = patronContiene(search)
            filtro += """ and (upper(p.first_name::text) like upper(:busqueda)
                          or upper(p.last_name::text) like upper(:busqueda)
                          or upper(p.national_id::text) like upper(:busqueda)
                          or upper(p.phone::text) like upper(:busqueda))"""
        }
        val orden = ORDENES[ordering] ?: ORDENES.getValue("name_asc")

        val total = jdbc.queryForObject("select count(*) from patients_patient p where $filtro", parametros, Long::class.java)!!
        val pagina = Pagina.numeroDePagina(page, total)
        parametros["desde"] = (pagina - 1) * Pagina.POR_PAGINA
        parametros["cuantos"] = Pagina.POR_PAGINA

        val filas = jdbc.query(
            """
            with pagina as (
              select p.id, p.first_name, p.last_name, p.national_id, p.phone, p.created_at,
                     row_number() over (order by $orden) as posicion
              from patients_patient p
              where $filtro
              order by $orden
              limit :cuantos offset :desde
            )
            select p.*, $ATENCIONES as atenciones, $PROXIMA as proxima
            from pagina p
            order by p.posicion
            """,
            parametros,
        ) { fila, _ ->
            linkedMapOf<String, Any?>(
                "id" to fila.uuid("id").toString(),
                "full_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
                "national_id" to fila.getString("national_id"),
                "phone" to fila.getString("phone"),
                "created_at" to FormatoDeDrf.deDrf(fila.instante("created_at"), zona.zona),
                "attention_count" to fila.getLong("atenciones"),
                "next_appointment" to fila.instanteONulo("proxima")?.let { FormatoDeDrf.deDrf(it, zona.zona) },
            )
        }
        return Pagina.de(total, filas, pagina, peticion)
    }

    /**
     * GET /api/v1/patients/birthdays/?days=7 — quién cumple años hoy y en los
     * próximos días (0 a 60), para recordarlo desde el inicio.
     *
     * Con la fecha LOCAL de la clínica, no la del servidor: con el servidor
     * en UTC, entre medianoche y las 05:00 de Guayaquil se listaba el
     * cumpleaños de mañana y se perdía el de hoy.
     */
    @GetMapping("/api/v1/patients/birthdays/")
    fun cumpleanos(@RequestParam(required = false) days: String?): List<Map<String, Any?>> {
        permisos.exigir(Permisos.PERSONAL)
        // `int(...)` de Python: admite espacios alrededor y signo; si no es
        // un entero, 7.
        val dias = (if (days == null) 7 else days.trim().toIntOrNull() ?: 7).coerceIn(0, 60)
        val hoy = zona.hoy()

        // (mes, día) de la ventana → (cuántos días faltan, año en que cae)
        val buscados = LinkedHashMap<Pair<Int, Int>, Pair<Int, Int>>()
        for (desfase in 0..dias) {
            val d = hoy.plusDays(desfase.toLong())
            buscados.putIfAbsent(d.monthValue to d.dayOfMonth, desfase to d.year)
            // Quien nació un 29 de febrero no tiene cumpleaños los años no
            // bisiestos: se le felicita el 28. Si no, desaparecía de la
            // lista tres de cada cuatro años.
            if (d.monthValue == 2 && d.dayOfMonth == 28 && !Year.isLeap(d.year.toLong())) {
                buscados.putIfAbsent(2 to 29, desfase to d.year)
            }
        }

        val resultado = jdbc.query(
            """
            select id, first_name, last_name, birth_date, phone from patients_patient
            where tenant_id = :clinica and is_active and birth_date is not null
            """,
            mapOf("clinica" to contexto.clinica?.id),
        ) { fila, _ ->
            val nacimiento: LocalDate = fila.fecha("birth_date")
            val (desfase, anio) = buscados[nacimiento.monthValue to nacimiento.dayOfMonth] ?: return@query null
            linkedMapOf<String, Any?>(
                "id" to fila.uuid("id").toString(),
                "full_name" to "${fila.getString("first_name")} ${fila.getString("last_name")}",
                "phone" to fila.getString("phone"),
                "birth_date" to nacimiento.toString(),
                "in_days" to desfase,
                "is_today" to (desfase == 0),
                // Los años que cumple, sobre el año en que cae ESE cumpleaños:
                // con la ventana cruzando el fin de año salía uno de menos.
                "turns" to anio - nacimiento.year,
            )
        }.filterNotNull()

        // Python ordena por (días, nombre) comparando puntos de código; el
        // orden es estable, como `sorted`.
        return resultado.sortedWith(
            compareBy<Map<String, Any?>> { it["in_days"] as Int }.thenComparator { a, b ->
                compararComoPython(a["full_name"] as String, b["full_name"] as String)
            },
        )
    }

    /** Comparación de cadenas por punto de código, como `str < str` en Python. */
    private fun compararComoPython(a: String, b: String): Int {
        val pa = a.codePoints().toArray()
        val pb = b.codePoints().toArray()
        for (i in 0 until minOf(pa.size, pb.size)) if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        return pa.size.compareTo(pb.size)
    }
}
