package cz.smarteon.loxmcp

/**
 * Application-wide constants.
 */
object Constants {
    /**
     * The version of the Loxone MCP Server.
     * This should match the version in build.gradle.kts (without -SNAPSHOT suffix).
     */
    const val VERSION = "0.1.0"

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
