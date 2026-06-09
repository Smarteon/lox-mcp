package cz.smarteon.loxmcp

import kotlinx.coroutines.runBlocking
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File

actual fun getEnv(name: String): String? = System.getenv(name)

actual fun platformStdinSource(): Source = System.`in`.asSource().buffered()

actual fun platformStdoutSink(): Sink = System.out.asSink().buffered()

actual fun registerShutdownCallback(block: suspend () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { block() }
    })
}

actual fun readResourceBytes(path: String): ByteArray? {
    val name = if (path.startsWith("/")) path.trimStart('/') else path
    return ReadResourceMarker::class.java.classLoader?.getResourceAsStream(name)?.use { it.readBytes() }
}

private class ReadResourceMarker

actual fun readFileText(path: String): String? =
    try { File(path).readText() } catch (e: Exception) { null }