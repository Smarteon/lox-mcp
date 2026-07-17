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
        const val OPERATE_CONTROL = "operate_control"
        const val OPERATE_CONTROLS_BY_ROOM = "operate_controls_by_room"
        const val OPERATE_CONTROLS_BY_TYPE = "operate_controls_by_type"
        const val OPERATE_CONTROLS_BY_CATEGORY = "operate_controls_by_category"
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
        const val WEBSERVICES = "webservices"
        const val DIAGNOSTIC_SCENARIOS = "diagnostic_scenarios"
        const val SYSTEM_LOG = "system_log"
        const val DEVICES_WITH_STATISTICS = "devices_with_statistics"
        const val SYSTEM_STATUS = "system_status"
        const val PHYSICAL_DEVICES = "physical_devices"
        const val SYSTEM_STATS = "system_stats"
    }
}
