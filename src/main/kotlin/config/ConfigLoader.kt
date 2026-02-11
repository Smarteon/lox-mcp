package cz.smarteon.loxmcp.config

import com.charleskorn.kaml.Yaml
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Loads MCP configuration from YAML files.
 */
object ConfigLoader {

    /**
     * Load configuration with optional custom config file.
     *
     * @param customConfigPath Path to custom config file. If null, only internal config is loaded.
     * @param overrideInternalConfig If true and customConfigPath is provided, only use custom config;
     *                               if false, merge custom config with internal config
     * @return Loaded configuration (internal, custom, or merged)
     */
    fun load(customConfigPath: String? = null, overrideInternalConfig: Boolean = false): McpConfig {
        val internalConfig = if (overrideInternalConfig && customConfigPath != null) {
            logger.info { "Override mode enabled, skipping internal configuration" }
            McpConfig()
        } else {
            loadFromResources()
        }

        val customConfig = customConfigPath?.let { path ->
            logger.info { "Loading custom configuration from: $path" }
            loadFromFile(path)
        } ?: McpConfig()

        return if (customConfigPath == null) {
            internalConfig
        } else {
            merge(internalConfig, customConfig)
        }
    }

    /**
     * Load configuration from a YAML file.
     * If the file doesn't exist, returns a default empty configuration.
     */
    private fun loadFromFile(filePath: String): McpConfig {
        val file = File(filePath)

        return if (file.exists()) {
            parseYaml(file.readText(), "file $filePath")
        } else {
            logger.warn { "Configuration file not found at $filePath, using defaults" }
            McpConfig()
        }
    }

    /**
     * Load configuration from classpath resources.
     */
    private fun loadFromResources(resourcePath: String = "mcp-config.yaml"): McpConfig {
        val resourceStream = ConfigLoader::class.java.classLoader.getResourceAsStream(resourcePath)

        return if (resourceStream != null) {
            val yamlContent = resourceStream.bufferedReader().use { it.readText() }
            parseYaml(yamlContent, "resources: $resourcePath")
        } else {
            logger.warn { "Configuration resource not found: $resourcePath, using defaults" }
            McpConfig()
        }
    }

    /**
     * Merge two configurations. Custom config takes precedence for duplicates.
     * Tools and resources with the same name from custom config override internal ones.
     */
    private fun merge(internal: McpConfig, custom: McpConfig): McpConfig {
        val customToolNames = custom.tools.map { it.name }.toSet()
        val mergedTools = internal.tools.filterNot { it.name in customToolNames } + custom.tools

        val customResourceUris = custom.resources.map { it.uri }.toSet()
        val mergedResources = internal.resources.filterNot { it.uri in customResourceUris } + custom.resources

        return McpConfig(tools = mergedTools, resources = mergedResources)
    }

    private fun parseYaml(yamlContent: String, source: String): McpConfig {
        return try {
            logger.info { "Loading MCP configuration from $source" }
            Yaml.default.decodeFromString(McpConfig.serializer(), yamlContent)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load configuration from $source, using defaults" }
            McpConfig()
        }
    }
}
