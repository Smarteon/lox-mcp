package cz.smarteon.lox.mcp.mcp

import cz.smarteon.lox.mcp.loxone.LoxoneAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server

private val logger = KotlinLogging.logger {}

/**
 * Registers all MCP resources that expose Loxone data to AI assistants.
 */
fun registerResources(server: Server, adapter: LoxoneAdapter) {
    server.addResource(
        uri = "lox://status", name = "Loxone Server Status", description = """
            Basic status information about the Loxone Miniserver connection.
            Use this to verify the system is accessible before calling tools.
        """.trimIndent(), mimeType = "text/plain"
    ) { request ->
        try {
            val apiVersion = adapter.getApiVersion()

            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        uri = request.uri, mimeType = "text/plain", text = """
                                Loxone Miniserver Status
                                ========================
                                
                                Connection: Active
                                API Version: $apiVersion
                                
                                The Miniserver is online and responding to requests.
                            """.trimIndent()
                    )
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get server status" }

            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        uri = request.uri, mimeType = "text/plain", text = """
                                Loxone Miniserver Status
                                ========================
                                
                                Connection: Error
                                Message: ${e.message}
                                
                                The Miniserver is not accessible.
                            """.trimIndent()
                    )
                )
            )
        }
    }
}
