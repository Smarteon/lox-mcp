package cz.smarteon.lox.mcp.mcp

import cz.smarteon.lox.mcp.Constants
import cz.smarteon.lox.mcp.loxone.LoxoneAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
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

    logger.info { "Loxone MCP Server started in STDIO mode" }
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
