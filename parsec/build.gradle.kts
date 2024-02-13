plugins {
    kotlin("multiplatform") version "1.9.22"
    application
    `maven-publish`
}

group   = "ajrouvoet"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    js(IR) {
        browser { }
    }

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.arrow-kt:arrow-core:1.2.0")
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType(Test::class) {
    useJUnitPlatform()
}
