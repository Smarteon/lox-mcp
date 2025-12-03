package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.Constants.HandlerTypes
import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.ToolConfig
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findCategoryByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findRoomByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsByType
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsForCategory
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsForRoom
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
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
                HandlerTypes.SEND_COMMAND -> handleSendCommand(arguments)
                HandlerTypes.CONTROL_DEVICE -> handleControlDevice(arguments)
                HandlerTypes.CONTROL_DEVICES_BY_ROOM -> handleControlDevicesByRoom(arguments)
                HandlerTypes.CONTROL_DEVICES_BY_TYPE -> handleControlDevicesByType(arguments)
                HandlerTypes.CONTROL_DEVICES_BY_CATEGORY -> handleControlDevicesByCategory(arguments)
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

    private suspend fun handleSendCommand(arguments: JsonObject): CallToolResult {
        val uuid = getRequiredStringArg(arguments, "uuid") ?: return errorResult("Missing required parameter: uuid")
        val command = getRequiredStringArg(arguments, "command") ?: return errorResult("Missing required parameter: command")

        val response = adapter.sendCommand(uuid, command)
        return successResult("Command sent successfully: $response")
    }

    private suspend fun handleControlDevice(arguments: JsonObject): CallToolResult {
        val deviceId = getRequiredStringArg(arguments, "device_id") ?: return errorResult("Missing required parameter: device_id")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val value = getOptionalStringArg(arguments, "value")

        val response = if (value != null) {
            adapter.sendCommand(deviceId, "$action/$value")
        } else {
            adapter.sendCommand(deviceId, action)
        }

        return successResult("Device $deviceId $action: $response")
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

        val results = controls.map { control ->
            try {
                adapter.sendCommand(control.uuidAction, action)
                "${control.name}: OK"
            } catch (e: Exception) {
                "${control.name}: ${e.message}"
            }
        }

        return successResult("Controlled ${controls.size} devices in $roomName:\n${results.joinToString("\n")}")
    }

    private suspend fun handleControlDevicesByType(arguments: JsonObject): CallToolResult {
        val deviceType = getRequiredStringArg(arguments, "device_type") ?: return errorResult("Missing required parameter: device_type")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")

        val app = adapter.getApp()
        val controls = app.getVisibleControlsByType(deviceType)

        if (controls.isEmpty()) {
            return errorResult("No devices found of type: $deviceType")
        }

        val results = controls.map { control ->
            try {
                adapter.sendCommand(control.uuidAction, action)
                "${control.name}: OK"
            } catch (e: Exception) {
                "${control.name}: ${e.message}"
            }
        }

        return successResult("Controlled ${controls.size} devices of type $deviceType:\n${results.joinToString("\n")}")
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

        val results = controls.map { control ->
            try {
                adapter.sendCommand(control.uuidAction, action)
                "${control.name}: OK"
            } catch (e: Exception) {
                "${control.name}: ${e.message}"
            }
        }

        return successResult("Controlled ${controls.size} devices in category $categoryName:\n${results.joinToString("\n")}")
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
