package cz.smarteon.loxmcp

/**
 * Application-wide constants.
 */
object Constants {
    /**
     * The version of the Loxone MCP Server.
     * Automatically loaded from the build-generated version file.
     */
    val VERSION: String by lazy {
        Constants::class.java.classLoader
            .getResourceAsStream(VERSION_FILE_NAME)
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?: "unknown"
    }

    const val VERSION_FILE_NAME = "version.txt"

    /**
     * The name of the MCP server implementation.
     */
    const val SERVER_NAME = "lox-mcp-server"

    /**
     * Handler type constants for tools and resources.
     */
    object HandlerTypes {
        // Tool handler types
        const val SEND_COMMAND = "send_command"
        const val CONTROL_DEVICE = "control_device"
        const val CONTROL_DEVICES_BY_ROOM = "control_devices_by_room"
        const val CONTROL_DEVICES_BY_TYPE = "control_devices_by_type"
        const val CONTROL_DEVICES_BY_CATEGORY = "control_devices_by_category"

        // Resource handler types
        const val ROOMS_LIST = "rooms_list"
        const val ROOM_DEVICES = "room_devices"
        const val DEVICES_ALL = "devices_all"
        const val DEVICES_BY_TYPE = "devices_by_type"
        const val DEVICES_BY_CATEGORY = "devices_by_category"
        const val CATEGORIES_LIST = "categories_list"
        const val STRUCTURE_SUMMARY = "structure_summary"
    }
}
