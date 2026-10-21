// Las versiones se declaran aquí y los módulos solo aplican los plugins,
// para que no haya dos sitios donde subir una versión.
plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    kotlin("plugin.jpa") version "2.1.20" apply false
    id("org.springframework.boot") version "3.4.4" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.clinube"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
