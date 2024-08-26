plugins {
    kotlin("plugin.serialization") version "1.9.21" apply false
    kotlin("jvm") version "1.9.22" apply false
}

rootProject.name = "Parsec"

include("parsec")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("arrow", "io.arrow-kt:arrow-core:1.2.0")
        }
    }
}

