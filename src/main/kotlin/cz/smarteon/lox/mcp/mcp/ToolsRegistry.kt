package cz.smarteon.lox.mcp.mcp

import cz.smarteon.lox.mcp.loxone.LoxoneAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server

private val logger = KotlinLogging.logger {}

/**
 * Registers all MCP tools that expose Loxone functionality to AI assistants.
 */
fun registerTools(server: Server, adapter: LoxoneAdapter) {
    server.addTool(
        name = "lox_get_api_version",
        description = """
            Get the API version of the Loxone Miniserver.
            This is a safe read-only operation that returns basic system information.
        """.trimIndent(),
        inputSchema = Tool.Input(EmptyJsonObject)
    ) { request ->
        try {
            val apiVersion = adapter.getApiVersion()

            logger.info { "Successfully retrieved API version: $apiVersion" }

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = """
                            API Version: $apiVersion
                            
                            This indicates the Miniserver is responding and accessible.
                        """.trimIndent()
                    )
                ),
                isError = false
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get API version" }

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Error getting API version: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    logger.info { "Registered 1 MCP tool(s)" }
}
