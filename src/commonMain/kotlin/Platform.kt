package cz.smarteon.loxmcp

import kotlinx.io.Sink
import kotlinx.io.Source

expect fun getEnv(name: String): String?
expect fun platformStdinSource(): Source
expect fun platformStdoutSink(): Sink
expect fun registerShutdownCallback(block: suspend () -> Unit)
expect fun readResourceBytes(path: String): ByteArray?
expect fun readFileText(path: String): String?
