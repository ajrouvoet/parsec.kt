plugins {
    kotlin("jvm") version "1.9.21"
    application
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.arrow-kt:arrow-core:1.2.0")

}

tasks.withType(Test::class) {
    useJUnitPlatform()
}

application {
    mainClass.set("apoml.AppKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
