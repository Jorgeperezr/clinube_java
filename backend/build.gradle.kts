plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Los PDF (comprobantes, liquidaciones, roles) y el Excel del contador.
    // OpenPDF es LGPL/MPL y POI, Apache: nada de iText 7, que es AGPL.
    implementation("com.github.librepdf:openpdf:2.0.3")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Las fechas de Clinube se guardan en UTC y se presentan en la zona de
    // la clínica. Si la JVM de las pruebas corriera en la zona de la
    // clínica, un error de conversión no se vería: coincidirían por
    // casualidad. Con UTC la suite distingue las dos cosas, y la segunda
    // pasada (scripts/probar.sh) la repite en Pacific/Kiritimati (UTC+14),
    // al otro lado de la línea de fecha.
    //
    // Es una propiedad de Gradle (-PzonaPruebas=…) y no `-Duser.timezone`:
    // `System.getProperty("user.timezone")` se lee en el demonio de Gradle,
    // donde siempre vale la zona de la máquina, y la Mac de desarrollo está
    // justo en America/Guayaquil. Las pruebas habrían corrido en la zona de
    // la clínica sin que nada lo dijera. `ZonaDeLaJvmTest` lo comprueba.
    val zona = providers.gradleProperty("zonaPruebas").getOrElse("UTC")
    systemProperty("user.timezone", zona)
    systemProperty("clinube.zonaPruebas", zona)
    // Cambiar de zona vuelve a ejecutar las pruebas, sin --rerun-tasks.
    inputs.property("zonaPruebas", zona)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
