package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.getVisibleControls
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
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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
                "rooms_list" -> handleRoomsList()
                "room_devices" -> handleRoomDevices(uri)
                "devices_all" -> handleDevicesAll()
                "devices_by_type" -> handleDevicesByType(uri)
                "devices_by_category" -> handleDevicesByCategory(uri)
                "categories_list" -> handleCategoriesList()
                "structure_summary" -> handleStructureSummary()
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
