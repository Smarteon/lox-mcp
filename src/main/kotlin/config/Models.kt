package cz.smarteon.loxmcp.config

import kotlinx.serialization.Serializable

/**
 * Root configuration for MCP server tools and resources.
 */
@Serializable
data class McpConfig(
    val tools: List<ToolConfig> = emptyList(),
    val resources: List<ResourceConfig> = emptyList()
)

/**
 * Configuration for MCP tools loaded from YAML.
 */
@Serializable
data class ToolConfig(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val handler: ToolHandler
)

@Serializable
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: String? = null,
    val enum: List<String>? = null
)

@Serializable
data class ToolHandler(
    val type: String,
    val scope: String? = null,
    val target: String? = null,
    val action: String? = null,
    val valueParam: String? = null
)

/**
 * Configuration for MCP resources loaded from YAML.
 */
@Serializable
data class ResourceConfig(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String,
    val handler: ResourceHandler
)

@Serializable
data class ResourceHandler(
    val type: String
)
