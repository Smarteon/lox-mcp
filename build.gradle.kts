import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import io.gitlab.arturbosch.detekt.Detekt
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
    alias(libs.plugins.axion.release)
    application
}

scmVersion {
    tag {
        prefix.set("v")
    }
}

group = "cz.smarteon.lox.mcp"
version = scmVersion.version

repositories {
    mavenCentral()
}

dependencies {
    implementation("cz.smarteon.loxone:loxone-client-kotlin-jvm:0.6.0")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.2")

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)

    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

detekt {
    config.setFrom("$projectDir/config/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

application {
    mainClass.set("cz.smarteon.loxmcp.ApplicationKt")
}

tasks {
    val generateVersionFile by registering {
        val versionValue = version.toString()
        val outputDir = layout.buildDirectory.dir("generated/sources/version")

        inputs.property("version", versionValue)
        outputs.dir(outputDir)

        doLast {
            val versionFile = outputDir.get().file("version.txt").asFile
            versionFile.parentFile.mkdirs()
            versionFile.writeText(inputs.properties["version"] as String)
        }
    }

    withType<ShadowJar>().configureEach {
        dependsOn(generateVersionFile)
        archiveBaseName.set("lox-mcp")
        archiveClassifier.set("all")
        mergeServiceFiles()
        manifest {
            attributes["Main-Class"] = application.mainClass.get()
        }
    }

    withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
        jvmTarget = "21"
    }

    jar {
        enabled = false
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        dependsOn(generateVersionFile)
        from(generateVersionFile) {
            into(".")
        }
    }
}
