package com.clinube.configuracion.marca

import com.clinube.comun.json.FormatoDeDrf
import com.clinube.comun.seguridad.ContextoDeLaPeticion
import com.clinube.comun.seguridad.Permisos
import com.clinube.comun.sql.Filas.instante
import com.clinube.comun.sql.Filas.uuid
import com.clinube.comun.tiempo.ZonaDeLaClinica
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.UUID

/**
 * La identidad visual de la clínica (`configuration.ClinicBranding`): logos,
 * paleta, temas y nombre comercial. La pide el panel en todas las pantallas
 * para pintarse con los colores y el nombre de la clínica.
 *
 * De momento solo la lectura; el PATCH (subir logos, regenerar los temas
 * desde la paleta) llega con el módulo de Configuración.
 */
@RestController
class ControladorDeMarca(
    private val permisos: Permisos,
    private val contexto: ContextoDeLaPeticion,
    private val jdbc: NamedParameterJdbcTemplate,
    private val zona: ZonaDeLaClinica,
    private val json: ObjectMapper,
) {

    /**
     * GET /api/v1/config/branding/ — cualquiera del personal de la clínica:
     * el tema se aplica a toda la interfaz, no solo a la administración.
     *
     * Como en Django (`get_or_create`), la primera lectura CREA la fila con
     * los valores por defecto.
     */
    @GetMapping("/api/v1/config/branding/")
    @Transactional
    fun marca(): Map<String, Any?> {
        permisos.exigir(Permisos.PERSONAL)
        val clinica = contexto.clinica
        val parametros = mapOf("clinica" to clinica?.id)

        if (jdbc.queryForList("select 1 from configuration_clinicbranding where tenant_id = :clinica", parametros).isEmpty()) {
            val ahora = Timestamp.from(zona.ahora())
            jdbc.update(
                """
                insert into configuration_clinicbranding
                  (id, created_at, updated_at, logo, theme, tenant_id, display_name, short_name,
                   address, email, phone, icono, logo_oscuro, paleta, temas)
                values (:id, :ahora, :ahora, null, cast(:tema as jsonb), :clinica, '', '', '', '', '', null, null,
                        '[]'::jsonb, '[]'::jsonb)
                """,
                parametros + mapOf("id" to UUID.randomUUID(), "ahora" to ahora, "tema" to TEMA_POR_DEFECTO),
            )
        }

        return jdbc.queryForObject(
            """
            select id, logo, logo_oscuro, icono, theme::text as theme, paleta::text as paleta,
                   temas::text as temas, display_name, short_name, address, phone, email, updated_at
            from configuration_clinicbranding where tenant_id = :clinica
            """,
            parametros,
        ) { fila, _ ->
            linkedMapOf(
                "id" to fila.uuid("id").toString(),
                "logo_url" to urlDeArchivo(fila.getString("logo")),
                "logo_oscuro_url" to urlDeArchivo(fila.getString("logo_oscuro")),
                "icono_url" to urlDeArchivo(fila.getString("icono")),
                "theme" to json.readTree(fila.getString("theme")),
                "paleta" to json.readTree(fila.getString("paleta")),
                "temas" to json.readTree(fila.getString("temas")),
                "display_name" to fila.getString("display_name"),
                "short_name" to fila.getString("short_name"),
                // El nombre que enseñar SIEMPRE: el comercial si lo hay y,
                // si no, el del alta. Sin él, una clínica que no rellenó el
                // nombre comercial aparecía en el panel como «Clínica».
                "nombre_clinica" to fila.getString("display_name").trim().ifEmpty { clinica?.nombre ?: "" },
                "address" to fila.getString("address"),
                "phone" to fila.getString("phone"),
                "email" to fila.getString("email"),
                "updated_at" to FormatoDeDrf.deDrf(fila.instante("updated_at"), zona.zona),
            )
        }!!
    }

    companion object {
        /** `_default_theme()` del modelo de Django. */
        const val TEMA_POR_DEFECTO = """{"preset": "default", "primary": "", "secondary": ""}"""

        /**
         * La dirección RELATIVA del archivo (`/media/branding/logos/x.png`),
         * como `FieldFile.url`: el panel la resuelve contra su propio host.
         * Construirla absoluta con el host interno del proxy hacía que el
         * navegador la bloqueara por contenido mixto.
         *
         * Se codifica como `filepath_to_uri` de Django: todo menos `/~!*()'`.
         */
        fun urlDeArchivo(nombre: String?): String? {
            if (nombre.isNullOrEmpty()) return null
            val codificado = URLEncoder.encode(nombre.replace("\\", "/"), StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/").replace("%7E", "~").replace("%21", "!")
                .replace("%2A", "*").replace("%28", "(").replace("%29", ")").replace("%27", "'")
            return "/media/$codificado"
        }
    }
}
