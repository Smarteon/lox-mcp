package cz.smarteon.loxmcp

import cz.smarteon.loxmcp.credentials.CredentialResolver
import cz.smarteon.loxmcp.credentials.LoxoneCredentials
import cz.smarteon.loxmcp.server.createMcpServer
import cz.smarteon.loxmcp.server.createStdioMcpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Parsed command-line arguments.
 */
private data class AppArgs(
    val mode: String = "--sse",
    val port: Int = DEFAULT_PORT,
    val resourcesAsTools: Boolean = false,
    val originalArgs: Array<String> = emptyArray()
) {
    companion object {
        const val DEFAULT_PORT = 3001

        fun parse(args: Array<String>): AppArgs {
            var mode = "--sse"
            var port = DEFAULT_PORT
            var resourcesAsTools = false

            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--stdio" -> mode = "--stdio"
                    "--sse", "--http" -> {
                        mode = "--sse"
                        args.getOrNull(i + 1)?.toIntOrNull()?.let {
                            port = it
                            i++
                        }
                    }
                    "--resources-as-tools" -> resourcesAsTools = true
                }
                i++
            }

            return AppArgs(mode, port, resourcesAsTools, args)
        }
    }
}

fun main(args: Array<String>) {
    val appArgs = AppArgs.parse(args)

    when (appArgs.mode) {
        "--stdio" -> runStdioMode(appArgs.originalArgs, appArgs.resourcesAsTools)
        "--sse", "--http" -> runHttpMode(appArgs.port, appArgs.originalArgs, appArgs.resourcesAsTools)
        else -> {
            logger.error { "Invalid mode: ${appArgs.mode}. Use '--stdio' or '--http'" }
        }
    }
}

private fun runStdioMode(args: Array<String>, resourcesAsTools: Boolean) = runBlocking {
    logger.info { "Starting Loxone MCP Server in STDIO mode" }

    val adapter = initAdapter(args)
    registerShutdownHook(adapter)

    createStdioMcpServer(adapter, resourcesAsTools)
}

private fun runHttpMode(port: Int, args: Array<String>, resourcesAsTools: Boolean) {
    logger.info { "Starting Loxone MCP Server in HTTP/SSE mode" }

    embeddedServer(
        factory = Netty,
        port = port,
        host = "0.0.0.0",
        module = { module(args, resourcesAsTools) }
    ).start(wait = true)
}

fun Application.module(args: Array<String> = emptyArray(), resourcesAsTools: Boolean = false) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    val adapter = initAdapter(args)
    registerShutdownHook(adapter)

    createMcpServer(adapter, resourcesAsTools)

    logger.info { "Loxone MCP Server started successfully" }
}

private fun initAdapter(args: Array<String>): LoxoneAdapter {
    val source = CredentialResolver.fromArgs(args)

    val credentials: LoxoneCredentials = try {
        source.get()
    } catch (e: Exception) {
        logger.error { e.message }
        exitProcess(1)
    }

    return LoxoneAdapter(
        address = credentials.address,
        username = credentials.username,
        password = credentials.password
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
