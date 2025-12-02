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
            try {
                logger.info { "Loading MCP configuration from $filePath" }
                val yamlContent = file.readText()
                Yaml.default.decodeFromString(McpConfig.serializer(), yamlContent)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load configuration from $filePath, using defaults" }
                McpConfig()
            }
        } else {
            logger.warn { "Configuration file not found at $filePath, using defaults" }
            McpConfig()
        }
    }

    /**
     * Load configuration from classpath resources.
     */
    fun loadFromResources(resourcePath: String = "mcp-config.yaml"): McpConfig {
        return try {
            val resourceStream = ConfigLoader::class.java.classLoader.getResourceAsStream(resourcePath)
            if (resourceStream != null) {
                logger.info { "Loading MCP configuration from resources: $resourcePath" }
                val yamlContent = resourceStream.bufferedReader().use { it.readText() }
                Yaml.default.decodeFromString(McpConfig.serializer(), yamlContent)
            } else {
                logger.warn { "Configuration resource not found: $resourcePath, using defaults" }
                McpConfig()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load configuration from resources: $resourcePath, using defaults" }
            McpConfig()
        }
    }
}

