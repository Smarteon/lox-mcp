package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.Constants
import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.McpServerProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private val logger = KotlinLogging.logger {}

/**
 * Creates and runs the MCP server with STDIO transport.
 * This mode is used by MCP clients like Claude Desktop that communicate via standard input/output.
 *
 * @param adapter The Loxone adapter for communication with Miniserver
 */
suspend fun createStdioMcpServer(adapter: LoxoneAdapter) {
    val server = buildMcpServer(adapter)

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    val sessionClosed = CompletableDeferred<Unit>()

    val session = server.createSession(transport)

    session.onClose {
        logger.debug { "STDIO MCP session closed" }
        sessionClosed.complete(Unit)
    }

    sessionClosed.await()

    logger.info { "STDIO server shutting down" }
}

/**
 * Creates and configures the MCP server with both Streamable HTTP and legacy SSE transports for HTTP mode.
 * Streamable HTTP (recommended) is served at `/mcp`.
 * Legacy SSE (deprecated, for backward compatibility) is served at the root path.
 *
 * Note: mcpStreamableHttp (Application-level) is called first because it internally installs
 * the Ktor SSE and ContentNegotiation plugins. The legacy SSE transport then uses Route.mcp()
 * which expects SSE to be already installed, avoiding DuplicatePluginException.
 *
 * @param adapter The Loxone adapter for communication with Miniserver
 */
fun Application.createMcpServer(adapter: LoxoneAdapter) {
    // Streamable HTTP transport (MCP 2025-03-26 spec, recommended)
    // This also installs SSE and ContentNegotiation plugins internally
    mcpStreamableHttp(path = "/mcp") {
        buildMcpServer(adapter)
    }

    // Legacy SSE transport (MCP 2024-11-05 spec, deprecated but kept for backward compatibility)
    // Uses Route.mcp() which expects SSE to be already installed (done by mcpStreamableHttp above)
    routing {
        mcp {
            buildMcpServer(adapter)
        }
    }

    logger.info { "MCP transports registered: Streamable HTTP at /mcp, Legacy SSE at /" }
}

/**
 * Builds and configures an MCP [Server] instance with tools and resources.
 * This is shared between all transport modes (STDIO, Streamable HTTP, Legacy SSE).
 *
 * @param adapter The Loxone adapter for communication with Miniserver
 * @return A fully configured [Server] instance
 */
private fun buildMcpServer(adapter: LoxoneAdapter): Server {
    val server = Server(
        serverInfo = Implementation(
            name = Constants.SERVER_NAME,
            version = Constants.VERSION
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = if (McpServerProperties.resourcesAsTools) null else ServerCapabilities.Resources(
                    subscribe = false,
                    listChanged = false
                ),
                tools = ServerCapabilities.Tools(
                    listChanged = false
                )
            )
        )
    )

    registerTools(server, adapter)
    if (McpServerProperties.resourcesAsTools) {
        logger.info { "Registering resources as tools (--resources-as-tools mode)" }
        registerResourcesAsTools(server, adapter)
    } else {
        registerResources(server, adapter)
    }

    return server
}
