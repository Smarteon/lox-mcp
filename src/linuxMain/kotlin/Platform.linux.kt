@file:OptIn(ExperimentalForeignApi::class)

package cz.smarteon.loxmcp

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.SIGINT
import platform.posix.SIGTERM
import platform.posix.errno
import platform.posix.exit
import platform.posix.fflush
import platform.posix.getenv
import platform.posix.read
import platform.posix.signal
import platform.posix.strerror
import platform.posix.stdout
import platform.posix.write

private const val CHUNK = 8192L

class PosixStdinSource : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        val size = minOf(byteCount, CHUNK).toInt()

        return memScoped {
            val buf = allocArray<ByteVar>(size)
            val n = read(STDIN_FILENO, buf, size.toULong())

            if (n < 0L) throw RuntimeException("stdin read: ${strerror(platform.posix.errno)?.toKString()}")
            if (n == 0L) return@memScoped -1L

            sink.write(buf.readBytes(n.toInt()))
            n
        }
    }

    override fun close() {}
}

class PosixStdoutSink : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount

        while (remaining > 0L) {
            val chunkSize = minOf(remaining, CHUNK).toInt()
            val data = source.readByteArray(chunkSize)
            var written = 0

            while (written < data.size) {
                data.usePinned { pinned ->
                    val n = write(STDOUT_FILENO, pinned.addressOf(written), (data.size - written).toULong())
                    if (n < 0L)
                        throw RuntimeException("stdout write: ${strerror(errno)?.toKString()}")
                    if (n == 0L)
                        throw RuntimeException("stdout write: wrote 0 bytes")
                    written += n.toInt()
                }
            }
            remaining -= chunkSize
        }
    }

    override fun flush() {
        fflush(stdout)
    }

    override fun close() {
        flush()
    }
}

actual fun getEnv(name: String): String? = getenv(name)?.toKString()

actual fun platformStdinSource(): Source = PosixStdinSource().buffered()

actual fun platformStdoutSink(): Sink = PosixStdoutSink().buffered()

private var shutdownBlock: (suspend () -> Unit)? = null

@Suppress("UNUSED_PARAMETER")
private fun handleSignal(sig: Int) {
    val block = shutdownBlock
    if (block != null) {
        runBlocking { block() }
    }
    exit(0)
}

actual fun registerShutdownCallback(block: suspend () -> Unit) {
    shutdownBlock = block
    signal(SIGINT, staticCFunction(::handleSignal))
    signal(SIGTERM, staticCFunction(::handleSignal))
}

actual fun readResourceBytes(path: String): ByteArray? = embeddedResourceBytes(path)

actual fun readFileText(path: String): String? = null
