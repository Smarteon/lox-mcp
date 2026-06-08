package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Category
import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxkt.app.LoxoneApp
import cz.smarteon.loxkt.app.Room
import cz.smarteon.loxkt.app.getCategoryName
import cz.smarteon.loxkt.app.getControlsByType
import cz.smarteon.loxkt.app.getControlsForCategory
import cz.smarteon.loxkt.app.getControlsForRoom
import cz.smarteon.loxkt.app.getRoomName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Helper utilities for querying and formatting Loxone data.
 * Eliminates code duplication between tool and resource handlers.
 */
object LoxoneQueryHelper {

    /**
     * Find a room by name (case-insensitive).
     */
    fun LoxoneApp.findRoomByName(roomName: String): Room? {
        return rooms.values.firstOrNull { it.name.equals(roomName, ignoreCase = true) }
    }

    /**
     * Find a category by name (case-insensitive).
     */
    fun LoxoneApp.findCategoryByName(categoryName: String): Category? {
        return cats.values.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
    }

    /**
     * Get visible controls for a room (controls with non-empty type).
     */
    fun LoxoneApp.getVisibleControlsForRoom(roomUuid: String): List<Control> {
        return getControlsForRoom(roomUuid).filter { it.type.isNotEmpty() }
    }

    /**
     * Get visible controls for a category (controls with non-empty type).
     */
    fun LoxoneApp.getVisibleControlsForCategory(categoryUuid: String): List<Control> {
        return getControlsForCategory(categoryUuid).filter { it.type.isNotEmpty() }
    }

    /**
     * Get visible controls by type (controls with non-empty type).
     */
    fun LoxoneApp.getVisibleControlsByType(type: String): List<Control> {
        return getControlsByType(type).filter { it.type.isNotEmpty() }
    }

    /**
     * Build a JSON object representing a device/control with common fields.
     */
    fun LoxoneApp.buildDeviceJson(control: Control, includeRoom: Boolean = true, includeCategory: Boolean = true): JsonObject {
        return buildJsonObject {
            put("uuid", control.uuidAction)
            put("name", control.name)
            put("type", control.type)
            if (includeRoom) {
                getRoomName(control)?.let { put("room", it) }
            }
            if (includeCategory) {
                getCategoryName(control)?.let { put("category", it) }
            }
        }
    }

    /**
     * Count visible controls for a room.
     */
    fun LoxoneApp.countVisibleControlsInRoom(roomUuid: String): Int {
        return getVisibleControlsForRoom(roomUuid).size
    }

    /**
     * Count visible controls for a category.
     */
    fun LoxoneApp.countVisibleControlsInCategory(categoryUuid: String): Int {
        return getVisibleControlsForCategory(categoryUuid).size
    }
}
