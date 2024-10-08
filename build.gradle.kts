plugins {
    kotlin("jvm")
    application
    `maven-publish`
}

group   = "ajrouvoet"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.arrow)
}

tasks.withType(Test::class) {
    useJUnitPlatform()
}
