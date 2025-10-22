package cz.smarteon.loxmcp

import cz.smarteon.loxmcp.server.createMcpServer
import cz.smarteon.loxmcp.server.createStdioMcpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "--sse"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
        "--stdio" -> runStdioMode()
        "--sse", "--http" -> runHttpMode(port)
        else -> {
            logger.error { "Invalid mode: $command. Use '--stdio' or '--http'" }
        }
    }
}

private fun runStdioMode() = runBlocking {
    logger.info { "Starting Loxone MCP Server in STDIO mode" }

    val adapter = initAdapter()
    registerShutdownHook(adapter)

    createStdioMcpServer(adapter)
}

private fun runHttpMode(port: Int) {
    logger.info { "Starting Loxone MCP Server in HTTP/SSE mode" }

    embeddedServer(
        factory = Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    val adapter = initAdapter()
    registerShutdownHook(adapter)

    createMcpServer(adapter)

    logger.info { "Loxone MCP Server started successfully" }
}

private fun initAdapter(): LoxoneAdapter {
    return LoxoneAdapter(
        host = System.getenv("LOXONE_HOST") ?: run {
            logger.error { "LOXONE_HOST environment variable is required" }
            exitProcess(1)
        },
        username = System.getenv("LOXONE_USER") ?: run {
            logger.error { "LOXONE_USER environment variable is required" }
            exitProcess(1)
        },
        password = System.getenv("LOXONE_PASS") ?: run {
            logger.error { "LOXONE_PASS environment variable is required" }
            exitProcess(1)
        }
    )
}

/**
 * Registers a shutdown hook to gracefully close the Loxone adapter connection.
 */
private fun registerShutdownHook(adapter: LoxoneAdapter) {
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            try {
                adapter.close()
                logger.info { "Loxone connection closed successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Error during shutdown" }
            }
        }
    })
}
