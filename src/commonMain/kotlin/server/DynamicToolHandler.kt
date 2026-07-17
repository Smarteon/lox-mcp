package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxkt.state.TextState
import cz.smarteon.loxkt.state.ValueState
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val logger = KotlinLogging.logger {}

private val json = Json { prettyPrint = true }

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
                HandlerTypes.OPERATE_CONTROL -> handleOperateControl(arguments)
                HandlerTypes.OPERATE_CONTROLS_BY_ROOM -> handleOperateControlsByRoom(arguments)
                HandlerTypes.OPERATE_CONTROLS_BY_TYPE -> handleOperateControlsByType(arguments)
                HandlerTypes.OPERATE_CONTROLS_BY_CATEGORY -> handleOperateControlsByCategory(arguments)
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

    private suspend fun handleOperateControl(arguments: JsonObject): CallToolResult {
        val controlId = getRequiredStringArg(arguments, "control_id") ?: return errorResult("Missing required parameter: control_id")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val value = getOptionalStringArg(arguments, "value")

        val command = if (value != null) "$action/$value" else action
        return executeCommand(controlId, command, "Control $controlId $action")
    }

    private suspend fun handleOperateControlsByRoom(arguments: JsonObject): CallToolResult {
        val roomName = getRequiredStringArg(arguments, "room") ?: return errorResult("Missing required parameter: room")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val controlType = getOptionalStringArg(arguments, "control_type")
        val includeStates = getOptionalBooleanArg(arguments, "include_states") ?: false

        val app = adapter.getApp()
        val room = app.findRoomByName(roomName)
            ?: return errorResult("Room not found: $roomName")

        val controls = app.getVisibleControlsForRoom(room.uuid)
            .filter { controlType == null || it.type.equals(controlType, ignoreCase = true) }

        if (controls.isEmpty()) {
            return errorResult("No controls found in room: $roomName")
        }

        return executeCommandOnMultipleControls(controls, action, "Operated ${controls.size} controls in $roomName", includeStates)
    }

    private suspend fun handleOperateControlsByType(arguments: JsonObject): CallToolResult {
        val controlType = getRequiredStringArg(arguments, "control_type") ?: return errorResult("Missing required parameter: control_type")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val includeStates = getOptionalBooleanArg(arguments, "include_states") ?: false

        val app = adapter.getApp()
        val controls = app.getVisibleControlsByType(controlType)

        if (controls.isEmpty()) {
            return errorResult("No controls found of type: $controlType")
        }

        return executeCommandOnMultipleControls(controls, action, "Operated ${controls.size} controls of type $controlType", includeStates)
    }

    private suspend fun handleOperateControlsByCategory(arguments: JsonObject): CallToolResult {
        val categoryName = getRequiredStringArg(arguments, "category") ?: return errorResult("Missing required parameter: category")
        val action = getRequiredStringArg(arguments, "action") ?: return errorResult("Missing required parameter: action")
        val includeStates = getOptionalBooleanArg(arguments, "include_states") ?: false

        val app = adapter.getApp()
        val category = app.findCategoryByName(categoryName)
            ?: return errorResult("Category not found: $categoryName")

        val controls = app.getVisibleControlsForCategory(category.uuid)

        if (controls.isEmpty()) {
            return errorResult("No controls found in category: $categoryName")
        }

        return executeCommandOnMultipleControls(
            controls,
            action,
            "Operated ${controls.size} controls in category $categoryName",
            includeStates
        )
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
        summaryMessage: String,
        includeStates: Boolean = false
    ): CallToolResult {
        val results = controls.map { control ->
            val status = try {
                adapter.sendCommand(control.uuidAction, action)
                "OK"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            buildControlResult(control, status, includeStates)
        }

        return if (includeStates) {
            val response = buildJsonObject {
                put("message", summaryMessage)
                putJsonArray("controls") {
                    results.forEach { add(it.jsonObject) }
                }
            }
            successResult(json.encodeToString(JsonObject.serializer(), response))
        } else {
            successResult("$summaryMessage:\n${results.joinToString("\n") { it.text }}")
        }
    }

    private suspend fun buildControlResult(
        control: Control,
        status: String,
        includeStates: Boolean
    ): ControlResult {
        val jsonObject = buildJsonObject {
            put("name", control.name)
            put("uuid", control.uuidAction)
            put("type", control.type)
            put("status", status)

            if (includeStates) {
                val states = adapter.getControlStates(control)
                if (states.isNotEmpty()) {
                    putJsonObject("states") {
                        states.forEach { (name, value) ->
                            when (value) {
                                is ValueState -> put(name, value.value)
                                is TextState -> put(name, value.text)
                                else -> put(name, value.toString())
                            }
                        }
                    }
                }
            }
        }

        val text = "  ${control.name} (${control.type}): $status"

        return ControlResult(text, jsonObject)
    }

    private data class ControlResult(val text: String, val jsonObject: JsonObject)

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
                paramConfig?.default != null -> paramConfig.default
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

    private fun getOptionalBooleanArg(arguments: JsonObject, key: String): Boolean? =
        arguments[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun successResult(message: String) = CallToolResult(
        content = listOf(TextContent(message)),
        isError = false
    )

    private fun errorResult(message: String) = CallToolResult(
        content = listOf(TextContent(message)),
        isError = true
    )
}
