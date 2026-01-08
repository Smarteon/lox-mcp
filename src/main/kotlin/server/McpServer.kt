package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.Constants
import cz.smarteon.loxmcp.LoxoneAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private val logger = KotlinLogging.logger {}

/**
 * Creates and runs the MCP server with STDIO transport.
 * This mode is used by MCP clients like Claude Desktop that communicate via standard input/output.
 *
 * @param adapter The Loxone adapter for communication with Miniserver
 * @param resourcesAsTools If true, resources will be registered as tools instead of MCP resources.
 *                         This is useful for MCP clients that don't support resources well.
 */
suspend fun createStdioMcpServer(adapter: LoxoneAdapter, resourcesAsTools: Boolean = false) {
    val server = Server(
        serverInfo = Implementation(
            name = Constants.SERVER_NAME,
            version = Constants.VERSION
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = if (resourcesAsTools) null else ServerCapabilities.Resources(
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
    if (resourcesAsTools) {
        logger.info { "Registering resources as tools (--resources-as-tools mode)" }
        registerResourcesAsTools(server, adapter)
    } else {
        registerResources(server, adapter)
    }

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
    server.connect(transport)

    try {
        logger.info { "Loxone MCP Server started in STDIO mode" }
        awaitCancellation()
    } catch (e: CancellationException) {
        logger.info { "STDIO server cancelled, shutting down" }
        throw e
    }
}

/**
 * Creates and configures the MCP server with SSE transport for HTTP mode.
 *
 * @param adapter The Loxone adapter for communication with Miniserver
 * @param resourcesAsTools If true, resources will be registered as tools instead of MCP resources.
 *                         This is useful for MCP clients that don't support resources well.
 */
fun Application.createMcpServer(adapter: LoxoneAdapter, resourcesAsTools: Boolean = false) {
    mcp {
        val server = Server(
            serverInfo = Implementation(
                name = Constants.SERVER_NAME,
                version = Constants.VERSION
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    resources = if (resourcesAsTools) null else ServerCapabilities.Resources(
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
        if (resourcesAsTools) {
            logger.info { "Registering resources as tools (--resources-as-tools mode)" }
            registerResourcesAsTools(server, adapter)
        } else {
            registerResources(server, adapter)
        }

        server
    }
}
