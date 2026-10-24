package com.clinube.configuracion

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.ZoneId

/**
 * Los ajustes del servidor, con los MISMOS nombres de variable de entorno
 * que usa Django (ver `application.yml`): así el `.env` de `clinube` sirve
 * sin tocarlo y los dos servidores pueden arrancar contra la misma base.
 */
@ConfigurationProperties(prefix = "clinube")
data class Ajustes(
    /** La clave con la que se firman los JWT: la de Django, para que un token valga en los dos. */
    val secreto: String,
    val entorno: String = "desarrollo",
    val depuracion: Boolean = false,
    /** Zona de la clínica. «Hoy», los meses y los filtros por día se calculan en ella. */
    val zona: String = "America/Guayaquil",
    val marca: String = "Clinube",
    val panelUrl: String = "",
    /** `MEDIA_ROOT`: la carpeta de los archivos subidos (comprobantes de egresos). */
    val media: String = "media",
    val jwt: Jwt = Jwt(),
    val cors: Cors = Cors(),
) {
    data class Jwt(
        val vidaAcceso: Duration = Duration.ofMinutes(30),
        val vidaRefresco: Duration = Duration.ofDays(7),
    )

    data class Cors(
        val origenes: List<String> = listOf("http://localhost:3000"),
    )

    val zonaDeLaClinica: ZoneId get() = ZoneId.of(zona)

    val esProduccion: Boolean get() = entorno == "produccion"
}
