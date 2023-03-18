plugins {
    kotlin("jvm") version "1.8.20-Beta"
    kotlin("plugin.serialization") version "1.8.20-Beta"
    application
}

group = "org.chsrobotics.scout"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-cors-jvm:2.2.3")
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-server-core:2.2.3")
    implementation("io.ktor:ktor-server-netty:2.2.3")
    implementation("io.github.oshai:kotlin-logging-jvm:4.0.0-beta-22")
    implementation("org.slf4j:slf4j-api:2.0.6")
    implementation("org.slf4j:slf4j-simple:2.0.6")
    implementation("io.github.xn32:json5k:0.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("org.dizitart:potassium-nitrite:3.4.4")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(19)
}

application {
    mainClass.set("org.chsrobotics.scout.ScoutingServerKt")
}
