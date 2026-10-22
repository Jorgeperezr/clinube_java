package com.clinube.legal

import com.clinube.PruebaDeContrato
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Textos legales, aceptación de las condiciones y configuración de Google,
 * comparados con las respuestas de Django capturadas por
 * `scripts/capturar-condiciones.sh`, y el rastro que dejan en la base
 * comparado con el que dejó Django (`estado-tras-aceptar.json`).
 */
class ContratoLegalTest : PruebaDeContrato() {

    /** `aceptada_en` es el momento de aceptar: cambia en cada ejecución. */
    override val volatiles = super.volatiles + "aceptada_en"

    private val ACEPTAR = "/api/v1/legal/condiciones/aceptar/"
    private val RECEPCION = "recepcion@clinube.local"

    private fun filasDeConfiguracion() =
        jdbc.queryForObject("select count(*) from common_platformconfiguration", Long::class.java)

    @Test
    fun `los textos legales son los de Django, y leerlos crea la configuracion como get_solo`() {
        // La pantalla de entrada consulta Google antes que nada: `first()`
        // no crea la fila.
        comoDjango("google-config", pedir(HttpMethod.GET, "/api/v1/auth/google/config/"))
        assertEquals(0L, filasDeConfiguracion())

        // `get_solo()` sí: comprobado contra Django, 0 filas antes y 1 después.
        comoDjango("legal-privacidad", pedir(HttpMethod.GET, "/api/v1/legal/privacidad/"))
        assertEquals(1L, filasDeConfiguracion())
        comoDjango("legal-condiciones", pedir(HttpMethod.GET, "/api/v1/legal/condiciones/"))
        assertEquals(1L, filasDeConfiguracion())
    }

    @Test
    fun `con el titular configurado y el canal por WhatsApp, los textos cambian como en Django`() {
        pedir(HttpMethod.GET, "/api/v1/legal/privacidad/")
        // Lo mismo que hace el guion de captura, con espacios alrededor del
        // ID de Google para comprobar que se recortan.
        jdbc.update(
            """
            update common_platformconfiguration set titular_nombre = 'Servicios Clínicos Pérez S.A.S.',
              titular_ruc = '1790000000001', titular_correo = 'privacidad@clinube.com',
              proveedor_correo = 'Brevo', canal_codigo_paciente = 'whatsapp',
              google_client_id = '  123-abc.apps.googleusercontent.com  '
            """,
        )
        comoDjango("legal-privacidad-con-titular", pedir(HttpMethod.GET, "/api/v1/legal/privacidad/"))
        comoDjango("legal-condiciones-con-titular", pedir(HttpMethod.GET, "/api/v1/legal/condiciones/"))
        comoDjango("google-config-con-id", pedir(HttpMethod.GET, "/api/v1/auth/google/config/"))
    }

    @Test
    fun `los textos son publicos aunque llegue un token que no vale`() {
        val (estado, _) = pedir(HttpMethod.GET, "/api/v1/legal/privacidad/", cabeceraCruda = "Bearer basura")
        assertEquals(200, estado)
    }

    @Test
    fun `aceptar las condiciones responde y deja en la base lo mismo que Django`() {
        // El guion de captura le pone este nombre a la administradora.
        jdbc.update("update accounts_user set full_name = 'Marta Ruiz' where id = ?::uuid", ID_ADMIN)
        // En Django, la captura leyó antes los textos, que crearon la fila
        // de configuración; aquí también, para comparar el mismo estado.
        pedir(HttpMethod.GET, "/api/v1/legal/privacidad/")

        val admin = entrar()
        val recepcion = entrar(RECEPCION)

        comoDjango("aceptar-sin-token", pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10")))
        comoDjango("aceptar-recepcion", pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10"), token = recepcion))
        comoDjango("aceptar-version-vieja", pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "1999.01"), token = admin))
        comoDjango("aceptar-sin-cuerpo", pedirCrudo(ACEPTAR, null, autorizacion = "Bearer $admin"))
        comoDjango("aceptar-version-numero", pedirCrudo(ACEPTAR, """{"version":2026.10}""", autorizacion = "Bearer $admin"))
        comoDjango("aceptar-metodo-get", pedir(HttpMethod.GET, ACEPTAR, token = admin))
        comoDjango("aceptar-primera", pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10"), token = admin))
        comoDjango("aceptar-segunda", pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10"), token = admin))
        comoDjango("perfil-tras-aceptar", pedir(HttpMethod.GET, "/api/v1/auth/me/", token = admin))

        compararEstadoConDjango()
    }

    @Test
    fun `aceptada_en va en UTC con Z y la misma las dos veces`() {
        val admin = entrar()
        val primera = json.readTree(pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10"), token = admin).second)
        val segunda = json.readTree(pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10"), token = admin).second)
        val fecha = primera["aceptada_en"].asText()
        assertTrue(
            Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{6})?Z$""").matches(fecha),
            "Django la escribe en UTC con Z y seis decimales; Kotlin dio $fecha",
        )
        assertEquals(fecha, segunda["aceptada_en"].asText(), "la segunda vez es la constancia que ya había")
    }

    @Test
    fun `lo que acepta una clinica no vale para otra`() {
        pedir(HttpMethod.POST, ACEPTAR, mapOf("version" to "2026.10"), token = entrar())
        val vecina = entrar("vecina@clinube.local")
        val perfil = json.readTree(pedir(HttpMethod.GET, "/api/v1/auth/me/", token = vecina).second)
        assertTrue(perfil["condiciones_pendientes"].asBoolean())
    }

    /**
     * La constancia, la auditoría y la configuración, comparadas con lo que
     * dejó Django. La IP de la conexión no se compara por valor: Django vio
     * 127.0.0.1 y aquí el cliente de pruebas puede llegar por ::1. Sí se
     * comprueba que está o falta en las mismas filas, porque eso no es
     * casual: la fila explícita `accept_terms` va sin IP en Django.
     */
    private fun compararEstadoConDjango() {
        val django = json.readTree(javaClass.getResource("/contrato/estado-tras-aceptar.json")!!)
        val mio = json.readTree(
            jdbc.queryForObject(
                """
                select json_build_object(
                  'aceptaciones', (select json_agg(json_build_object('version', version, 'nombre', nombre, 'correo', correo,
                                     'ip', host(ip), 'aceptada_por', aceptada_por_id::text, 'tenant', tenant_id::text))
                                   from common_aceptacioncondiciones),
                  'auditoria', (select json_agg(json_build_object('action', action, 'entity_type', entity_type,
                                     'entity_id_vacio', entity_id = '', 'ip', host(ip_address), 'metadata', metadata,
                                     'user', user_id::text, 'tenant', tenant_id::text) order by created_at)
                                from accounts_auditlog),
                  'configuracion', (select row_to_json(c)::jsonb - 'id' - 'updated_at' from common_platformconfiguration c)
                )::text
                """,
                String::class.java,
            ),
        )

        // La IP: presente o ausente en las mismas filas.
        fun ips(nodo: com.fasterxml.jackson.databind.JsonNode) =
            nodo["auditoria"].map { !it["ip"].isNull } + nodo["aceptaciones"].map { !it["ip"].isNull }
        assertEquals(ips(django), ips(mio), "la IP tiene que estar en las mismas filas que en Django")

        val sinIp = { nodo: com.fasterxml.jackson.databind.JsonNode ->
            (nodo.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).also { copia ->
                (copia["auditoria"] + copia["aceptaciones"]).forEach {
                    (it as com.fasterxml.jackson.databind.node.ObjectNode).remove("ip")
                }
            }
        }
        // jsonb ordena las claves a su manera; se comparan como árboles.
        assertEquals(sinIp(django), sinIp(mio), "la base no quedó como la dejó Django")
    }
}
