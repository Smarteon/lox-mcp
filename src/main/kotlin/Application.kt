package cz.smarteon.loxmcp

import cz.smarteon.loxmcp.config.McpServerProperties
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Parsed command-line arguments.
 */
private data class AppArgs(
    val mode: String,
    val port: Int,
    val resourcesAsTools: Boolean,
    val configPath: String?,
    val configOverride: Boolean,
    val originalArgs: Array<String>
) {
    companion object {
        const val DEFAULT_PORT = 3001

        fun parse(args: Array<String>): AppArgs {
            var mode = "--sse"
            var port = DEFAULT_PORT
            var resourcesAsTools = false
            var configPath: String? = null
            var configOverride = false

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
                    "-c", "--config" -> {
                        configPath = args.getOrNull(i + 1)
                        if (configPath == null) {
                            logger.error { "Missing config file path after ${args[i]}" }
                        } else {
                            i++
                        }
                    }
                    "-o", "--override" -> configOverride = true
                }
                i++
            }

            return AppArgs(mode, port, resourcesAsTools, configPath, configOverride, args)
        }
    }
}

fun main(args: Array<String>) {
    val appArgs = AppArgs.parse(args)

    McpServerProperties.initialize(
        mode = appArgs.mode,
        port = appArgs.port,
        resourcesAsTools = appArgs.resourcesAsTools,
        customConfigPath = appArgs.configPath,
        overrideInternalConfig = appArgs.configOverride
    )

    when (appArgs.mode) {
        "--stdio" -> runStdioMode(appArgs.originalArgs)
        "--sse", "--http" -> runHttpMode(appArgs.originalArgs)
        else -> {
            logger.error { "Invalid mode: ${appArgs.mode}. Use '--stdio' or '--http'" }
        }
    }
}

private fun runStdioMode(args: Array<String>) = runBlocking {
    logger.info { "Starting Loxone MCP Server in STDIO mode" }

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val adapter = initAdapter(args, scope)

    runCatching {
        adapter.enableStateUpdates()
    }.onFailure { e ->
        logger.error(e) { "Failed to enable state updates during startup" }
    }

    registerShutdownHook(adapter, scope)

    createStdioMcpServer(adapter)
}

private fun runHttpMode(args: Array<String>) {
    logger.info { "Starting Loxone MCP Server in HTTP/SSE mode" }

    embeddedServer(
        factory = Netty,
        port = McpServerProperties.port,
        host = "0.0.0.0",
        module = { module(args) }
    ).start(wait = true)
}

fun Application.module(args: Array<String> = emptyArray()) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    val adapterScope = CoroutineScope(coroutineContext + SupervisorJob())
    val adapter = initAdapter(args, adapterScope)

    launch {
        runCatching {
            adapter.enableStateUpdates()
        }.onFailure { e ->
            logger.error(e) { "Failed to enable state updates" }
        }
    }

    registerShutdownHook(adapter, adapterScope)
    createMcpServer(adapter)

    logger.info { "Loxone MCP Server started successfully" }
}

private fun initAdapter(args: Array<String>, scope: CoroutineScope): LoxoneAdapter {
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
        password = credentials.password,
        scope = scope
    )
}

/**
 * Registers a shutdown hook to gracefully close the Loxone adapter connection and cancel the scope.
 */
private fun registerShutdownHook(adapter: LoxoneAdapter, scope: CoroutineScope) {
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            runCatching {
                adapter.close()
                scope.cancel()
                logger.info { "Loxone connection closed successfully" }
            }.onFailure { e ->
                logger.error(e) { "Error during shutdown" }
            }
        }
    })
}
