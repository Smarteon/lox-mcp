package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxkt.app.LoxoneApp
import cz.smarteon.loxkt.app.getVisibleControls
import cz.smarteon.loxkt.state.TextState
import cz.smarteon.loxkt.state.ValueState
import cz.smarteon.loxmcp.Constants.HandlerTypes
import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.ResourceConfig
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.buildDeviceJson
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.countVisibleControlsInCategory
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.countVisibleControlsInRoom
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findCategoryByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findRoomByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsByType
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsForCategory
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.getVisibleControlsForRoom
import cz.smarteon.loxmcp.loxonedocs.LoxoneDocsProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLDecoder.decode

private val logger = KotlinLogging.logger {}

/**
 * Dynamic resource handler that provides resources based on YAML configuration.
 */
class DynamicResourceHandler(
    private val adapter: LoxoneAdapter,
    private val resourceConfig: ResourceConfig
) {

    private val json = Json { prettyPrint = true }

    suspend fun handle(uri: String): ReadResourceResult {
        return try {
            when (resourceConfig.handler.type) {
                HandlerTypes.ROOMS_LIST -> handleRoomsList()
                HandlerTypes.ROOM_DEVICES -> handleRoomDevices(uri)
                HandlerTypes.DEVICES_ALL -> handleDevicesAll()
                HandlerTypes.DEVICES_BY_TYPE -> handleDevicesByType(uri)
                HandlerTypes.DEVICES_BY_CATEGORY -> handleDevicesByCategory(uri)
                HandlerTypes.CATEGORIES_LIST -> handleCategoriesList()
                HandlerTypes.STRUCTURE_SUMMARY -> handleStructureSummary()
                HandlerTypes.STRUCTURE_FILE_LIST -> LoxoneDocsProvider.handleControlsList(uri)
                HandlerTypes.STRUCTURE_FILE_OBJECT -> LoxoneDocsProvider.handleControlDetails(uri)
                HandlerTypes.ALL_DEVICE_STATES -> handleAllDeviceStates(uri)
                HandlerTypes.DEVICE_STATE -> handleDeviceState(uri)
                else -> errorResult(uri, "Unknown handler type: ${resourceConfig.handler.type}")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling resource ${resourceConfig.uri}" }
            errorResult(uri, "Error: ${e.message}")
        }
    }

    private suspend fun handleRoomsList(): ReadResourceResult {
        val app = adapter.getApp()
        val roomsList = app.rooms.values.map { room ->
            buildJsonObject {
                put("uuid", room.uuid)
                put("name", room.name)
                put("deviceCount", app.countVisibleControlsInRoom(room.uuid))
            }
        }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(roomsList))
        return successResult(resourceConfig.uri, content, "application/json")
    }

    private suspend fun handleRoomDevices(uri: String): ReadResourceResult {
        val roomName = uri.substringAfter("rooms/").substringBefore("/devices")
        if (roomName.isBlank()) {
            return errorResult(uri, "Room name not found in URI")
        }

        val app = adapter.getApp()
        val room = app.findRoomByName(roomName)
            ?: return errorResult(uri, "Room not found: $roomName")

        val controls = app.getVisibleControlsForRoom(room.uuid)
        val devicesList = controls.map { app.buildDeviceJson(it, includeRoom = false) }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(devicesList))
        return successResult(uri, content, "application/json")
    }

    private suspend fun handleDevicesAll(): ReadResourceResult {
        val app = adapter.getApp()
        val controls = app.getVisibleControls()
        val devicesList = controls.map { app.buildDeviceJson(it) }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(devicesList))
        return successResult(resourceConfig.uri, content, "application/json")
    }

    private suspend fun handleDevicesByType(uri: String): ReadResourceResult {
        val deviceType = uri.substringAfter("type/")
        if (deviceType.isBlank()) {
            return errorResult(uri, "Device type not found in URI")
        }

        val app = adapter.getApp()
        val controls = app.getVisibleControlsByType(deviceType)
        val devicesList = controls.map { app.buildDeviceJson(it) }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(devicesList))
        return successResult(uri, content, "application/json")
    }

    private suspend fun handleDevicesByCategory(uri: String): ReadResourceResult {
        val categoryName = uri.substringAfter("category/")
        if (categoryName.isBlank()) {
            return errorResult(uri, "Category name not found in URI")
        }

        val app = adapter.getApp()
        val category = app.findCategoryByName(categoryName)
            ?: return errorResult(uri, "Category not found: $categoryName")

        val controls = app.getVisibleControlsForCategory(category.uuid)
        val devicesList = controls.map { app.buildDeviceJson(it, includeCategory = false) }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(devicesList))
        return successResult(uri, content, "application/json")
    }

    private suspend fun handleCategoriesList(): ReadResourceResult {
        val app = adapter.getApp()
        val categoriesList = app.cats.values.map { category ->
            buildJsonObject {
                put("uuid", category.uuid)
                put("name", category.name)
                put("type", category.type ?: "unknown")
                put("deviceCount", app.countVisibleControlsInCategory(category.uuid))
            }
        }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(categoriesList))
        return successResult(resourceConfig.uri, content, "application/json")
    }

    private suspend fun handleStructureSummary(): ReadResourceResult {
        val app = adapter.getApp()

        val summary = buildJsonObject {
            put("rooms", app.rooms.size)
            put("devices", app.getVisibleControls().size)
            put("categories", app.cats.size)
            putJsonArray("roomList") {
                app.rooms.values.forEach { room ->
                    add(buildJsonObject {
                        put("name", room.name)
                        put("deviceCount", app.countVisibleControlsInRoom(room.uuid))
                    })
                }
            }
            putJsonArray("categoryList") {
                app.cats.values.forEach { cat ->
                    add(buildJsonObject {
                        put("name", cat.name)
                        put("type", cat.type ?: "unknown")
                        put("deviceCount", app.countVisibleControlsInCategory(cat.uuid))
                    })
                }
            }
        }

        val content = json.encodeToString(JsonObject.serializer(), summary)
        return successResult(resourceConfig.uri, content, "application/json")
    }

    private suspend fun handleAllDeviceStates(uri: String): ReadResourceResult {
        val app = adapter.getApp()
        val queryParams = parseQueryParams(uri.substringAfter("?", ""))

        val filteredControls = applyDeviceFilters(app, queryParams)
        if (filteredControls.isFailure) {
            return errorResult(uri, filteredControls.errorMessage)
        }

        val deviceJsons = filteredControls.controls.map { control ->
            buildDeviceStateJson(control, app.rooms[control.room]?.name)
        }

        val statesResult = buildJsonObject {
            put("totalDevices", filteredControls.controls.size)
            put("stateCount", adapter.getState().size())
            if (queryParams.isNotEmpty()) {
                putJsonObject("filters") { queryParams.forEach { (k, v) -> put(k, v) } }
            }
            putJsonArray("devices") { deviceJsons.forEach { add(it) } }
        }

        return successResult(uri, json.encodeToString(JsonObject.serializer(), statesResult), "application/json")
    }

    private data class FilterResult(
        val controls: List<Control> = emptyList(),
        val isFailure: Boolean = false,
        val errorMessage: String = ""
    )

    private fun applyDeviceFilters(app: LoxoneApp, queryParams: Map<String, String>): FilterResult {
        val roomUuid = queryParams["room"]?.let { name ->
            app.findRoomByName(name)?.uuid
                ?: return FilterResult(isFailure = true, errorMessage = "Room not found: $name")
        }
        val categoryUuid = queryParams["category"]?.let { name ->
            app.findCategoryByName(name)?.uuid
                ?: return FilterResult(isFailure = true, errorMessage = "Category not found: $name")
        }

        val controls = app.getVisibleControls()
            .asSequence()
            .filter { queryParams["filter"]?.let { f -> it.name.contains(f, ignoreCase = true) } ?: true }
            .filter { roomUuid?.equals(it.room) ?: true }
            .filter { queryParams["type"]?.let { t -> it.type.equals(t, ignoreCase = true) } ?: true }
            .filter { categoryUuid?.equals(it.cat) ?: true }
            .toList()

        return FilterResult(controls)
    }

    private suspend fun buildDeviceStateJson(control: Control, roomName: String?) = buildJsonObject {
        put("uuid", control.uuidAction)
        put("name", control.name)
        put("type", control.type)
        roomName?.let { put("room", it) }

        val states = adapter.getControlStates(control)
        put("statesAvailable", states.isNotEmpty())
        if (states.isNotEmpty()) {
            putJsonObject("states") {
                states.forEach { (name, value) -> putStateValue(name, value) }
            }
        }
    }

    private suspend fun handleDeviceState(uri: String): ReadResourceResult {
        val deviceUuid = uri.substringAfter("devices/").substringBefore("/state")
            .takeIf { it.isNotBlank() } ?: return errorResult(uri, "Device UUID not found in URI")

        val app = adapter.getApp()
        val control = app.controls[deviceUuid]
            ?: return errorResult(uri, "Device not found with UUID: $deviceUuid")

        val states = adapter.getControlStates(control)

        val stateResult = buildJsonObject {
            put("uuid", deviceUuid)
            put("name", control.name)
            put("type", control.type)
            control.room?.let { put("room", app.rooms[it]?.name) }
            control.cat?.let { put("category", app.cats[it]?.name) }
            put("statesAvailable", states.isNotEmpty())

            if (states.isEmpty()) {
                put("message", "No state values available yet. State updates may not be enabled.")
                control.states?.keys?.let { keys ->
                    putJsonArray("expectedStates") { keys.forEach { add(JsonPrimitive(it)) } }
                }
            } else {
                putJsonObject("states") { states.forEach { (name, value) -> putStateValue(name, value) } }
            }
        }

        return successResult(uri, json.encodeToString(JsonObject.serializer(), stateResult), "application/json")
    }

    /**
     * Parse query parameters from a query string.
     */
    private fun parseQueryParams(queryString: String): Map<String, String> =
        queryString.takeIf { it.isNotBlank() }
            ?.split("&")
            ?.mapNotNull {
                it.split("=", limit = 2).takeIf { p -> p.size == 2 && p[0].isNotBlank() }?.let { p ->
                    decode(p[0], "UTF-8") to decode(p[1], "UTF-8")
                }
            }
            ?.toMap()
            ?: emptyMap()

    private fun JsonObjectBuilder.putStateValue(name: String, value: Any) {
        when (value) {
            is ValueState -> put(name, value.value)
            is TextState -> put(name, value.text)
            else -> put(name, value.toString())
        }
    }

    private fun successResult(uri: String, content: String, mimeType: String) = ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                uri = uri,
                mimeType = mimeType,
                text = content
            )
        )
    )

    private fun errorResult(uri: String, message: String) = ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                uri = uri,
                mimeType = "text/plain",
                text = "Error: $message"
            )
        )
    )
}
