package com.clinube.configuracion

import com.clinube.comun.errores.CuerpoDeError
import com.clinube.comun.errores.ExcepcionApi
import com.clinube.comun.seguridad.FiltroDeAutenticacionJwt
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * La API es sin estado y se autentica solo por token: no hay sesión, ni
 * CSRF, ni formulario de inicio de sesión, igual que en DRF.
 *
 * Por omisión TODO exige autenticación —como el
 * `DEFAULT_PERMISSION_CLASSES: IsAuthenticated` de Django— y las rutas
 * públicas se enumeran una a una. Hacerlo al revés (abierto por defecto y
 * cerrar lo sensible) es como se publican datos clínicos sin querer.
 */
@Configuration
class ConfiguracionDeSeguridad(
    private val ajustes: Ajustes,
    private val filtroJwt: FiltroDeAutenticacionJwt,
    private val json: ObjectMapper,
) {

    @Bean
    fun cadenaDeFiltros(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(origenesPermitidos()) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { rutas ->
                rutas
                    // Las sondas de salud: quien las consulta (Docker, un
                    // balanceador, un monitor) no tiene credenciales.
                    .requestMatchers("/api/v1/health/", "/api/v1/ready/").permitAll()
                    // Entrar, renovar y los flujos de acceso del paciente.
                    // Sin método: a un GET en /auth/login/ Django responde 405,
                    // no 401, porque la vista no exige autenticación.
                    .requestMatchers(
                        "/api/v1/auth/login/",
                        "/api/v1/auth/token/refresh/",
                        "/api/v1/auth/google/",
                        "/api/v1/auth/otp/request/",
                        "/api/v1/auth/otp/verify/",
                        "/api/v1/auth/recovery/request/",
                        "/api/v1/auth/recovery/confirm/",
                    ).permitAll()
                    .requestMatchers(
                        "/api/v1/auth/google/config/",
                        "/api/v1/auth/otp/canal/",
                    ).permitAll()
                    // Los logotipos de cada clínica son públicos a propósito:
                    // salen en los documentos y en la pantalla de acceso.
                    .requestMatchers("/media/branding/**").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { manejo ->
                manejo.authenticationEntryPoint { _, respuesta, _ ->
                    escribir(respuesta, ExcepcionApi.noAutenticado())
                }
                manejo.accessDeniedHandler { _, respuesta, _ ->
                    escribir(respuesta, ExcepcionApi.sinPermiso())
                }
            }
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    /**
     * El filtro es un @Component y Spring Boot lo registraría TAMBIÉN como
     * filtro del contenedor, delante de Spring Security, que después
     * limpiaría la autenticación. Solo debe correr dentro de la cadena.
     */
    @Bean
    fun noRegistrarElFiltroDosVeces(filtro: FiltroDeAutenticacionJwt) =
        org.springframework.boot.web.servlet.FilterRegistrationBean(filtro).apply { isEnabled = false }

    /**
     * En producción el panel y la API comparten origen tras el proxy, pero
     * en desarrollo el panel corre en otro puerto y el navegador oculta
     * las cabeceras de respuesta que no se declaren. Sin `Content-Disposition`,
     * una descarga generada por la API llega sin su nombre de archivo.
     */
    @Bean
    fun origenesPermitidos(): CorsConfigurationSource {
        val configuracion = CorsConfiguration().apply {
            allowedOrigins = ajustes.cors.origenes
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            exposedHeaders = listOf(
                "Content-Disposition",
                "X-Backup-Records",
                "X-Backup-Archivos",
                "X-Backup-Archivos-Omitidos",
            )
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuracion) }
    }

    private fun escribir(respuesta: jakarta.servlet.http.HttpServletResponse, e: ExcepcionApi) {
        respuesta.status = e.estado.value()
        respuesta.contentType = MediaType.APPLICATION_JSON_VALUE
        respuesta.characterEncoding = "UTF-8"
        respuesta.writer.write(
            json.writeValueAsString(CuerpoDeError(CuerpoDeError.Detalle(e.codigo, e.mensaje, e.detalles))),
        )
    }
}
