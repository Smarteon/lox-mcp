package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.McpServerProperties
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
 * Includes both YAML-configured dynamic tools and built-in hardcoded tools.
 */
fun registerTools(server: Server, adapter: LoxoneAdapter) {
    val mcpConfig = McpServerProperties.loadConfig()

    if (mcpConfig.tools.isEmpty()) {
        logger.warn { "No tools defined in configuration" }
    } else {
        logger.info { "Registering ${mcpConfig.tools.size} tools from configuration" }
        mcpConfig.tools.forEach { toolConfig ->
            registerTool(server, adapter, toolConfig)
        }
    }

    registerGetLoxoneXmlTool(server, adapter)
}

/**
 * Registers the built-in `get_loxone_xml` tool.
 * Fetches, decompresses, and optionally slims the Loxone project XML.
 */
private fun registerGetLoxoneXmlTool(server: Server, adapter: LoxoneAdapter) {
    val handler = GetLoxoneXmlHandler(adapter)

    server.addTool(
        name = "get_loxone_xml",
        description = "Fetch the Loxone Miniserver project configuration as XML. " +
            "Returns the complete automation logic: all rooms, devices, programs, users, " +
            "categories, schedules, and wiring. " +
            "UI-only elements and attributes are stripped to keep the XML within LLM context limits.",
        inputSchema = ToolSchema()
    ) { _ ->
        handler.handle()
    }

    logger.info { "Registered built-in tool 'get_loxone_xml'" }
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
