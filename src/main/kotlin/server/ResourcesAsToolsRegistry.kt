package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.McpServerProperties
import cz.smarteon.loxmcp.config.ResourceConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val logger = KotlinLogging.logger {}

/**
 * Pattern to match URI template parameters like {roomName}, {deviceType}, etc.
 */
private val URI_PARAM_PATTERN = "\\{([^}]+)}".toRegex()

/**
 * Registers all MCP resources as tools.
 * This is useful for MCP clients that don't fully support the resources feature.
 *
 * Resources are converted to tools with the following rules:
 * - Static resources (no URI parameters) become no-argument tools
 * - Parameterized resources become tools with required string parameters
 * - Tool names are derived from handler type (e.g., "rooms_list" -> "get_rooms_list")
 *
 * @param server MCP server instance
 * @param adapter Loxone adapter
 */
fun registerResourcesAsTools(server: Server, adapter: LoxoneAdapter) {
    val mcpConfig = McpServerProperties.loadConfig()

    if (mcpConfig.resources.isEmpty()) {
        logger.warn { "No resources defined in configuration to convert to tools" }
    } else {
        logger.info { "Converting ${mcpConfig.resources.size} resources to tools" }
        mcpConfig.resources.forEach { resourceConfig ->
            registerResourceAsTool(server, adapter, resourceConfig)
        }
    }
}

/**
 * Register a single resource as a tool.
 */
private fun registerResourceAsTool(server: Server, adapter: LoxoneAdapter, resourceConfig: ResourceConfig) {
    val toolName = "get_${resourceConfig.handler.type}"
    val uriParams = extractUriParameters(resourceConfig.uri)

    val handler = DynamicResourceHandler(adapter, resourceConfig)

    // Build inputSchema - use ToolSchema() for no params, or with properties/required for params
    val inputSchema = if (uriParams.isEmpty()) {
        ToolSchema()
    } else {
        val (properties, required) = buildToolSchemaParams(uriParams, resourceConfig)
        ToolSchema(properties = properties, required = required)
    }

    logger.info { "Registering resource as tool: '$toolName' with params: $uriParams" }

    server.addTool(
        name = toolName,
        description = buildToolDescription(resourceConfig),
        inputSchema = inputSchema
    ) { request ->
        try {
            // Build the URI from parameters
            val uri = buildUriFromParams(resourceConfig.uri, uriParams, request.arguments)

            // Call the resource handler
            val result = handler.handle(uri)

            // Convert ReadResourceResult to CallToolResult
            val content = result.contents.firstOrNull()
            if (content is TextResourceContents) {
                CallToolResult(
                    content = listOf(TextContent(text = content.text))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Error: Unexpected response type")),
                    isError = true
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error executing resource-as-tool: $toolName" }
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    logger.debug { "Registered resource as tool: $toolName" }
}

/**
 * Extract parameter names from URI template.
 * Example: "loxone://rooms/{roomName}/devices" -> ["roomName"]
 */
private fun extractUriParameters(uri: String): List<String> {
    return URI_PARAM_PATTERN.findAll(uri)
        .map { it.groupValues[1] }
        .toList()
}

/**
 * Build properties and required list for ToolSchema based on URI parameters.
 * ToolSchema automatically wraps with type: "object".
 */
private fun buildToolSchemaParams(
    uriParams: List<String>,
    resourceConfig: ResourceConfig
): Pair<JsonObject, List<String>> {
    val properties = buildJsonObject {
        uriParams.forEach { param ->
            val snakeCaseParam = camelToSnakeCase(param)
            put(snakeCaseParam, buildJsonObject {
                put("type", "string")
                put("description", getParameterDescription(param, resourceConfig))
            })
        }
    }
    val required = uriParams.map { camelToSnakeCase(it) }
    return Pair(properties, required)
}

/**
 * Build tool description from resource configuration.
 */
private fun buildToolDescription(resourceConfig: ResourceConfig): String {
    return """
        |${resourceConfig.description.trim()}
        |
        |Original resource URI: ${resourceConfig.uri}
    """.trimMargin()
}

/**
 * Build actual URI from template and parameters.
 */
private fun buildUriFromParams(
    uriTemplate: String,
    uriParams: List<String>,
    arguments: JsonObject?
): String {
    var uri = uriTemplate
    uriParams.forEach { param ->
        val snakeCaseParam = camelToSnakeCase(param)
        val value = arguments?.get(snakeCaseParam)?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing required parameter: $snakeCaseParam")
        uri = uri.replace("{$param}", value)
    }
    return uri
}

/**
 * Convert camelCase to snake_case.
 */
private fun camelToSnakeCase(str: String): String {
    return str.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .lowercase()
}

/**
 * Get a description for a URI parameter based on its name.
 */
@Suppress("UnusedParameter")
private fun getParameterDescription(param: String, resourceConfig: ResourceConfig): String {
    return when (param.lowercase()) {
        "roomname" -> "Name of the room (e.g., 'Kitchen', 'Living Room')"
        "devicetype" -> "Type of device (e.g., 'Switch', 'Dimmer', 'Jalousie')"
        "categoryname" -> "Name of the category (e.g., 'Lights', 'Blinds')"
        else -> "Value for $param parameter"
    }
}
