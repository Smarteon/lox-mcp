import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "cz.smarteon.lox.mcp"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("cz.smarteon.loxone:loxone-client-kotlin-jvm:0.5.1")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.2")

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.server.test.host)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("cz.smarteon.lox.mcp.ApplicationKt")
}
