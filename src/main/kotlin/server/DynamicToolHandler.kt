package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxmcp.Constants.HandlerTypes
import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.ToolConfig
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findCategoryByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findRoomByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsByType
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsForCategory
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsForRoom
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val logger = KotlinLogging.logger {}

/**
 * Dynamic tool handler that executes tools based on YAML configuration.
 */
class DynamicToolHandler(
    private val adapter: LoxoneAdapter,
    private val toolConfig: ToolConfig
) {

    suspend fun handle(arguments: JsonObject): CallToolResult {
        return try {
            when (toolConfig.handler.type) {
                HandlerTypes.CONTROL_DEVICE -> handleControlDevice(arguments)
                HandlerTypes.CONTROL_DEVICES_BY_ROOM -> handleControlDevicesByRoom(arguments)
                HandlerTypes.CONTROL_DEVICES_BY_TYPE -> handleControlDevicesByType(arguments)
                HandlerTypes.CONTROL_DEVICES_BY_CATEGORY -> handleControlDevicesByCategory(arguments)
                HandlerTypes.GENERIC_COMMAND -> handleGenericCommand(arguments)
                else -> CallToolResult(
                    content = listOf(TextContent("Unknown handler type: ${toolConfig.handler.type}")),
                    isError = true
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error executing tool ${toolConfig.name}" }
            CallToolResult(
                content = listOf(TextContent("Error: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun handleControlDevice(arguments: JsonObject): CallToolResult {
        val deviceId = getRequiredStringArg(arguments, "device_id") ?: return errorResult("Missing required parameter: device_id")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val value = getOptionalStringArg(arguments, "value")

        val command = if (value != null) "$action/$value" else action
        return executeCommand(deviceId, command, "Device $deviceId $action")
    }

    private suspend fun handleControlDevicesByRoom(arguments: JsonObject): CallToolResult {
        val roomName = getRequiredStringArg(arguments, "room") ?: return errorResult("Missing required parameter: room")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val deviceType = getOptionalStringArg(arguments, "device_type")

        val app = adapter.getApp()
        val room = app.findRoomByName(roomName)
            ?: return errorResult("Room not found: $roomName")

        val controls = app.getVisibleControlsForRoom(room.uuid)
            .filter { deviceType == null || it.type.equals(deviceType, ignoreCase = true) }

        if (controls.isEmpty()) {
            return errorResult("No devices found in room: $roomName")
        }

        return executeCommandOnMultipleControls(controls, action, "Controlled ${controls.size} devices in $roomName")
    }

    private suspend fun handleControlDevicesByType(arguments: JsonObject): CallToolResult {
        val deviceType = getRequiredStringArg(arguments, "device_type") ?: return errorResult("Missing required parameter: device_type")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")

        val app = adapter.getApp()
        val controls = app.getVisibleControlsByType(deviceType)

        if (controls.isEmpty()) {
            return errorResult("No devices found of type: $deviceType")
        }

        return executeCommandOnMultipleControls(controls, action, "Controlled ${controls.size} devices of type $deviceType")
    }

    private suspend fun handleControlDevicesByCategory(arguments: JsonObject): CallToolResult {
        val categoryName = getRequiredStringArg(arguments, "category") ?: return errorResult("Missing required parameter: category")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")

        val app = adapter.getApp()
        val category = app.findCategoryByName(categoryName)
            ?: return errorResult("Category not found: $categoryName")

        val controls = app.getVisibleControlsForCategory(category.uuid)

        if (controls.isEmpty()) {
            return errorResult("No devices found in category: $categoryName")
        }

        return executeCommandOnMultipleControls(controls, action, "Controlled ${controls.size} devices in category $categoryName")
    }

    private suspend fun handleGenericCommand(arguments: JsonObject): CallToolResult {
        val commandTemplate = toolConfig.handler.commandTemplate
            ?: return errorResult("Generic command handler requires 'commandTemplate'")

        val command = buildCommandFromTemplate(commandTemplate, arguments)
        val response = adapter.sendRawCommand(command)

        return successResult("Command executed: $command\nResponse: $response")
    }

    private suspend fun executeCommand(uuid: String, command: String, successMessage: String): CallToolResult {
        val response = adapter.sendCommand(uuid, command)
        return successResult("$successMessage: $response")
    }

    private suspend fun executeCommandOnMultipleControls(
        controls: List<Control>,
        action: String,
        summaryMessage: String
    ): CallToolResult {
        val results = controls.map { control ->
            try {
                adapter.sendCommand(control.uuidAction, action)
                "${control.name}: OK"
            } catch (e: Exception) {
                "${control.name}: ${e.message}"
            }
        }

        return successResult("$summaryMessage:\n${results.joinToString("\n")}")
    }

    private fun buildCommandFromTemplate(template: String, arguments: JsonObject): String {
        var command = template

        // Find all placeholders in the template
        val placeholderPattern = "\\{([^}]+)}".toRegex()
        val placeholders = placeholderPattern.findAll(template).map { it.groupValues[1] }.toList()

        // Replace each placeholder with its value from arguments or default
        for (placeholder in placeholders) {
            val paramConfig = toolConfig.parameters.find { it.name == placeholder }
            val argValue = arguments[placeholder]?.jsonPrimitive?.contentOrNull

            val value = when {
                argValue != null -> argValue
                paramConfig?.default != null -> paramConfig.default!!
                paramConfig?.required == true -> throw IllegalArgumentException(
                    "Missing required parameter '$placeholder' for command template"
                )
                else -> throw IllegalArgumentException(
                    "Missing value for placeholder '$placeholder' and no default configured"
                )
            }

            command = command.replace("{$placeholder}", value)
        }

        return command
    }

    private fun getRequiredStringArg(arguments: JsonObject, key: String): String? =
        arguments[key]?.jsonPrimitive?.content

    private fun getOptionalStringArg(arguments: JsonObject, key: String): String? =
        arguments[key]?.jsonPrimitive?.contentOrNull

    private fun successResult(message: String) = CallToolResult(
        content = listOf(TextContent(message)),
        isError = false
    )

    private fun errorResult(message: String) = CallToolResult(
        content = listOf(TextContent(message)),
        isError = true
    )
}
