package cz.smarteon.loxmcp.config

/**
 * Global MCP server configuration properties.
 * Initialized once at application startup from command-line arguments.
 */
object McpServerProperties {

    var mode: String = "--sse"
        private set

    var port: Int = 3001
        private set

    var resourcesAsTools: Boolean = false
        private set

    var customConfigPath: String? = null
        private set

    var overrideInternalConfig: Boolean = false
        private set

    private var cachedConfig: McpConfig? = null

    /**
     * Initialize properties from command-line arguments.
     * Should be called once at application startup.
     */
    fun initialize(
        mode: String = "--stdio",
        port: Int = 3001,
        resourcesAsTools: Boolean = false,
        customConfigPath: String? = null,
        overrideInternalConfig: Boolean = false
    ) {
        this.mode = mode
        this.port = port
        this.resourcesAsTools = resourcesAsTools
        this.customConfigPath = customConfigPath
        this.overrideInternalConfig = overrideInternalConfig
        cachedConfig = null
    }

    /**
     * Load MCP configuration (tools and resources) based on current properties.
     * Result is cached to avoid re-parsing YAML on subsequent calls.
     */
    fun loadConfig(): McpConfig {
        return cachedConfig ?: run {
            val config = ConfigLoader.load(
                customConfigPath = customConfigPath,
                overrideInternalConfig = overrideInternalConfig
            )
            cachedConfig = config
            config
        }
    }
}
