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
    jvmToolchain(17)
}

dependencies {
    implementation(libs.arrow)
}

tasks.withType(Test::class) {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("ajrouvoet-parsec") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url  = project.uri("https://maven.pkg.github.com/ajrouvoet/parsec.kt")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
