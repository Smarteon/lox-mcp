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
     * Load configuration from a YAML file.
     * If the file doesn't exist, returns a default empty configuration.
     */
    fun load(filePath: String = "config/mcp-config.yaml"): McpConfig {
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
    fun loadFromResources(resourcePath: String = "mcp-config.yaml"): McpConfig {
        val resourceStream = ConfigLoader::class.java.classLoader.getResourceAsStream(resourcePath)

        return if (resourceStream != null) {
            val yamlContent = resourceStream.bufferedReader().use { it.readText() }
            parseYaml(yamlContent, "resources: $resourcePath")
        } else {
            logger.warn { "Configuration resource not found: $resourcePath, using defaults" }
            McpConfig()
        }
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
