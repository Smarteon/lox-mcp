import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
    alias(libs.plugins.axion.release)
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

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xsuppress-kotlin-version-compatibility-check")
        }
    }
    linuxX64()
    linuxArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/version/commonMain"))
            resources.srcDir(layout.buildDirectory.dir("generated/resources"))
        }
        commonMain.dependencies {
            implementation("cz.smarteon.loxone:loxone-client-kotlin:0.7.1")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("io.modelcontextprotocol:kotlin-sdk:0.13.0")

            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.kaml)
            implementation(libs.ksoup)
            implementation(libs.kotlinx.io)
            implementation(libs.kmp.zip)

            implementation(libs.kotlin.logging)
        }

        jvmMain.dependencies {
            implementation(libs.slf4j.simple)
        }

        linuxMain.dependencies {
        }

        val linuxMain by sourceSets.getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/embeddedResources/linuxMain"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
            implementation(libs.mockk)
            implementation(libs.ktor.server.test.host)
        }
    }
}

detekt {
    config.setFrom("$projectDir/config/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

tasks {
    val generateVersionFile by registering {
        val versionValue = version.toString()
        val outputDir = layout.buildDirectory.dir("generated/sources/version/commonMain")

        inputs.property("version", versionValue)
        outputs.dir(outputDir)

        doLast {
            val srcFile = outputDir.get().file("cz/smarteon/loxmcp/Version.kt").asFile
            srcFile.parentFile.mkdirs()
            srcFile.writeText(
                """
                package cz.smarteon.loxmcp

                internal const val SERVER_VERSION = "$versionValue"
                """.trimIndent()
            )
        }
    }

    val copyLoxoneDocs by registering(Copy::class) {
        from("loxone-docs") {
            include("*.json")
        }
        into(layout.buildDirectory.dir("generated/resources/loxone-docs"))
    }

    val embedResources by registering {
        val outputDir = layout.buildDirectory.dir("generated/sources/embeddedResources/linuxMain")

        inputs.files(
            layout.projectDirectory.file("loxone-docs/versions.json"),
            layout.projectDirectory.file("loxone-docs/structure-file-16.0.json"),
            layout.projectDirectory.file("src/commonMain/resources/mcp-config.yaml")
        )
        outputs.dir(outputDir)

        doLast {
            val resources = mapOf(
                "mcp-config.yaml" to layout.projectDirectory.file("src/commonMain/resources/mcp-config.yaml").asFile,
                "/loxone-docs/versions.json" to layout.projectDirectory.file("loxone-docs/versions.json").asFile,
                "/loxone-docs/structure-file-16.0.json" to layout.projectDirectory.file("loxone-docs/structure-file-16.0.json").asFile
            )

            val sb = StringBuilder()
            sb.appendLine("package cz.smarteon.loxmcp")
            sb.appendLine()
            sb.appendLine("internal fun embeddedResourceBytes(path: String): ByteArray? = when(path) {")
            for ((name, file) in resources) {
                val hex = file.readBytes().joinToString("") { "%02x".format(it) }
                sb.appendLine("    \"$name\" -> hexToBytes(\"$hex\")")
            }
            sb.appendLine("    else -> null")
            sb.appendLine("}")
            sb.appendLine()
            sb.appendLine("private fun hexToBytes(hex: String): ByteArray {")
            sb.appendLine("    val len = hex.length / 2")
            sb.appendLine("    val result = ByteArray(len)")
            sb.appendLine("    for (i in 0 until len) {")
            sb.appendLine("        result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()")
            sb.appendLine("    }")
            sb.appendLine("    return result")
            sb.appendLine("}")

            val srcFile = outputDir.get().file("cz/smarteon/loxmcp/ResourcesEmbedded.kt").asFile
            srcFile.parentFile.mkdirs()
            srcFile.writeText(sb.toString())
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

    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
        dependsOn(generateVersionFile, copyLoxoneDocs)
        archiveBaseName.set("lox-mcp")
        archiveClassifier.set("all")
        mergeServiceFiles()
        manifest {
            attributes("Main-Class" to "cz.smarteon.loxmcp.ApplicationKt")
        }
    }
}

tasks.named("compileKotlinJvm") { dependsOn("generateVersionFile") }
tasks.named("compileKotlinLinuxX64") { dependsOn("generateVersionFile", "embedResources") }
tasks.named("compileKotlinLinuxArm64") { dependsOn("generateVersionFile", "embedResources") }

tasks.named("jvmProcessResources") { dependsOn("copyLoxoneDocs") }
tasks.named("linuxX64ProcessResources") { dependsOn("copyLoxoneDocs") }
tasks.named("linuxArm64ProcessResources") { dependsOn("copyLoxoneDocs") }
