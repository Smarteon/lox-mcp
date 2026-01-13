package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.ConfigLoader
import cz.smarteon.loxmcp.config.ToolConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val logger = KotlinLogging.logger {}

/**
 * Registers all MCP tools that expose Loxone functionality to AI assistants.
 * Tools are loaded from YAML configuration for easy customization.
 */
fun registerTools(server: Server, adapter: LoxoneAdapter) {
    val config = ConfigLoader.loadFromResources()

    if (config.tools.isEmpty()) {
        logger.warn { "No tools defined in configuration" }
    } else {
        logger.info { "Registering ${config.tools.size} tools from configuration" }
        config.tools.forEach { toolConfig ->
            registerTool(server, adapter, toolConfig)
        }
    }
}

/**
 * Register a single tool from configuration.
 */
private fun registerTool(server: Server, adapter: LoxoneAdapter, toolConfig: ToolConfig) {
    val handler = DynamicToolHandler(adapter, toolConfig)

    // Build inputSchema based on whether tool has parameters
    val inputSchema = if (toolConfig.parameters.isEmpty()) {
        ToolSchema()
    } else {
        val properties = buildJsonObject {
            toolConfig.parameters.forEach { param ->
                put(param.name, buildJsonObject {
                    put("type", param.type)
                    put("description", param.description)
                    param.enum?.let { enumValues ->
                        putJsonArray("enum") {
                            enumValues.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                    param.default?.let { defaultValue ->
                        put("default", JsonPrimitive(defaultValue))
                    }
                })
            }
        }
        val required = toolConfig.parameters.filter { it.required }.map { it.name }
        ToolSchema(properties = properties, required = required)
    }

    logger.info { "Registering tool '${toolConfig.name}'" }

    server.addTool(
        name = toolConfig.name,
        description = toolConfig.description,
        inputSchema = inputSchema
    ) { request ->
        handler.handle(request.arguments
            ?: throw IllegalArgumentException("Missing required arguments for tool '${toolConfig.name}'"))
    }

    logger.debug { "Registered tool: ${toolConfig.name}" }
}
