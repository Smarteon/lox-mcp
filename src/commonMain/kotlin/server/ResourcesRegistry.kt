package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.McpServerProperties
import cz.smarteon.loxmcp.config.ResourceConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.Server

private val logger = KotlinLogging.logger {}

/**
 * Registers all MCP resources that expose Loxone data to AI assistants.
 * Resources are loaded from YAML configuration for easy customization.
 */
fun registerResources(server: Server, adapter: LoxoneAdapter) {
    val mcpConfig = McpServerProperties.loadConfig()

    if (mcpConfig.resources.isEmpty()) {
        logger.warn { "No resources defined in configuration" }
    } else {
        logger.info { "Registering ${mcpConfig.resources.size} resources from configuration" }
        mcpConfig.resources.forEach { resourceConfig ->
            registerResource(server, adapter, resourceConfig)
        }
    }
}

/**
 * Register a single resource from configuration.
 * Resources with URI patterns like {roomName} will be matched dynamically.
 */
private fun registerResource(server: Server, adapter: LoxoneAdapter, resourceConfig: ResourceConfig) {
    val handler = DynamicResourceHandler(adapter, resourceConfig)

    server.addResource(
        uri = resourceConfig.uri,
        name = resourceConfig.name,
        description = resourceConfig.description,
        mimeType = resourceConfig.mimeType
    ) { request ->
        handler.handle(request.uri)
    }

    logger.debug { "Registered resource: ${resourceConfig.uri}" }
}
