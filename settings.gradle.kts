plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"

    kotlin("jvm") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
}

rootProject.name = "parsec"

include("parsec")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("arrow", "io.arrow-kt:arrow-core:1.2.0")
        }
    }
}

