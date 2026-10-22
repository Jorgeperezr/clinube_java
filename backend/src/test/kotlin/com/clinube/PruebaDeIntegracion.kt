package com.clinube

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base de las pruebas que necesitan el servidor entero y una base de datos
 * de verdad.
 *
 * La base es **PostgreSQL 16 en un contenedor**, no una en memoria, y
 * Flyway le aplica la MISMA línea base que a producción. Una base en
 * memoria (H2) no serviría: el esquema lleva `jsonb`, `inet`, `uuid` e
 * índices con `varchar_pattern_ops`, y lo que se quiere comprobar es
 * precisamente que las entidades cuadran con el esquema de Django, que es
 * de PostgreSQL. Hibernate corre en `validate`: si no cuadra, el contexto
 * no arranca y la prueba falla antes de empezar.
 *
 * Hace falta Docker en marcha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prueba")
@Testcontainers
@Import(ConfiguracionDePrueba::class)
abstract class PruebaDeIntegracion {

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Autowired
    protected lateinit var jdbc: JdbcTemplate

    @LocalServerPort
    protected var puerto: Int = 0

    @Autowired
    protected lateinit var reloj: RelojDePrueba

    /** Para sembrar filas de prueba con las columnas obligatorias de cada tabla. */
    protected val sembrar: Sembrador by lazy { Sembrador(jdbc) { reloj.instant() } }

    @BeforeEach
    fun prepararBase() {
        // Cada prueba empieza con la hora real; la que necesite otra, la fija.
        reloj.fijo = null
        // Se vacía y se vuelve a sembrar en cada prueba: una prueba que
        // dependa de lo que dejó la anterior pasa sola y falla en el CI.
        jdbc.execute(
            """
            truncate token_blacklist_blacklistedtoken, token_blacklist_outstandingtoken,
                     common_aceptacioncondiciones, accounts_auditlog, common_platformconfiguration,
                     accounts_user, common_tenant cascade
            """,
        )
        val semilla = javaClass.getResource("/semilla.sql")!!.readText()
        jdbc.execute(semilla)
    }

    companion object {
        const val CORREO_ADMIN = "admin@clinube.local"
        const val CLAVE = "OdontoDemo2026"
        const val ID_ADMIN = "a35f7a96-7c8b-4954-8f61-0596bd5118ad"
        const val ID_CLINICA = "88a4cbeb-8604-4c10-b8fd-5bb844cd9362"
        const val ID_CLINICA_VECINA = "11111111-1111-1111-1111-111111111111"

        private val postgres = ContenedorPostgres().apply {
            withDatabaseName("clinica")
            withUsername("clinica")
            withPassword("clinica")
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun propiedades(registro: DynamicPropertyRegistry) {
            registro.add("spring.datasource.url") { postgres.jdbcUrl }
            registro.add("spring.datasource.username") { postgres.username }
            registro.add("spring.datasource.password") { postgres.password }
        }
    }
}

/** En Kotlin, `PostgreSQLContainer<SELF>` necesita una subclase para fijar SELF. */
class ContenedorPostgres : PostgreSQLContainer<ContenedorPostgres>("postgres:16-alpine")
