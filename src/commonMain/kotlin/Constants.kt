package cz.smarteon.loxmcp

/**
 * Application-wide constants.
 */
object Constants {
    val VERSION: String = SERVER_VERSION

    /**
     * The name of the MCP server implementation.
     */
    const val SERVER_NAME = "lox-mcp-server"

    /**
     * Handler type constants for tools and resources.
     */
    object HandlerTypes {
        // Tool handler types
        const val CONTROL_DEVICE = "control_device"
        const val CONTROL_DEVICES_BY_ROOM = "control_devices_by_room"
        const val CONTROL_DEVICES_BY_TYPE = "control_devices_by_type"
        const val CONTROL_DEVICES_BY_CATEGORY = "control_devices_by_category"
        const val GENERIC_COMMAND = "generic_command"

        // Resource handler types
        const val ROOMS_LIST = "rooms_list"
        const val ROOM_DEVICES = "room_devices"
        const val DEVICES_ALL = "devices_all"
        const val DEVICES_BY_TYPE = "devices_by_type"
        const val DEVICES_BY_CATEGORY = "devices_by_category"
        const val CATEGORIES_LIST = "categories_list"
        const val STRUCTURE_SUMMARY = "structure_summary"
        const val DOCS_TOC = "docs_toc"
        const val DOCS_CONTROLS = "docs_controls"
        const val DOCS_TOPIC = "docs_topic"
        const val ALL_DEVICE_STATES = "all_device_states"
        const val DEVICE_STATE = "device_state"
        const val STATISTICS = "statistics"
    }
}
