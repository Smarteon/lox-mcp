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
 */
suspend fun createStdioMcpServer(adapter: LoxoneAdapter) {
    val server = Server(
        serverInfo = Implementation(
            name = Constants.SERVER_NAME,
            version = Constants.VERSION
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(
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
    registerResources(server, adapter)

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
 */
fun Application.createMcpServer(adapter: LoxoneAdapter) {
    mcp {
        val server = Server(
            serverInfo = Implementation(
                name = Constants.SERVER_NAME,
                version = Constants.VERSION
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(
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
        registerResources(server, adapter)

        server
    }
}
