package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxkt.app.LoxoneApp
import cz.smarteon.loxkt.app.StatisticEntry
import cz.smarteon.loxkt.app.StatisticUnit
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
import io.ktor.http.decodeURLPart
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val logger = KotlinLogging.logger {}

/**
 * Dynamic resource handler that provides resources based on YAML configuration.
 */
class DynamicResourceHandler(
    private val adapter: LoxoneAdapter,
    private val resourceConfig: ResourceConfig
) {

    private val json = Json { prettyPrint = true }

    @Suppress("CyclomaticComplexMethod")
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
                HandlerTypes.DOCS_TOC -> withDocsVersion { LoxoneDocsProvider.handleDocsToc(uri, it) }
                HandlerTypes.DOCS_CONTROLS -> withDocsVersion { LoxoneDocsProvider.handleDocsControlsList(uri, it) }
                HandlerTypes.DOCS_TOPIC -> withDocsVersion { LoxoneDocsProvider.handleDocsTopic(uri, it) }
                HandlerTypes.ALL_DEVICE_STATES -> handleAllDeviceStates(uri)
                HandlerTypes.DEVICE_STATE -> handleDeviceState(uri)
                HandlerTypes.STATISTICS -> handleStatistics(uri)
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
     * Handle a statistics resource request.
     *
     * The URI query string encodes one or more entries of the form:
     *   ?uuid=<uuid>&from=<unixTs>&to=<unixTs>[&uuid=<uuid>&from=<unixTs>&to=<unixTs>...]
     *
     * Multiple (uuid, from, to) triples are passed as repeated parameters:
     *   loxone://statistics?uuid=aaa&from=1700000000&to=1700086400&uuid=bbb&from=1700000000&to=1700086400
     *
     * Optional per-request parameter (applies to all entries):
     *   &unit=DAY   (ALL | HOUR | DAY | MONTH | YEAR, default DAY)
     */
    private suspend fun handleStatistics(uri: String): ReadResourceResult {
        val parseResult = parseStatisticsParams(uri)
        if (parseResult is StatisticsParseResult.Error) return parseResult.result
        val params = (parseResult as StatisticsParseResult.Ok).params

        val app = adapter.getApp()
        val buildResult = buildStatisticsResults(app, params.uuids, params.froms, params.tos, params.unit)
        if (buildResult is StatisticsParseResult.Error) return buildResult.result

        val content = json.encodeToString(JsonArray.serializer(), JsonArray((buildResult as StatisticsParseResult.Ok).results))
        return successResult(uri, content, "application/json")
    }

    private fun parseStatisticsParams(uri: String): StatisticsParseResult {
        val queryString = uri.substringAfter("?", "")
        if (queryString.isBlank()) {
            return StatisticsParseResult.Error(errorResult(uri, "No query parameters provided. Required: uuid, from, to"))
        }
        return validateStatisticsQueryParams(uri, parseQueryParamsList(queryString))
    }

    @Suppress("ReturnCount")
    private fun validateStatisticsQueryParams(uri: String, params: Map<String, List<String>>): StatisticsParseResult {
        val uuids = params["uuid"]
            ?: return StatisticsParseResult.Error(errorResult(uri, "Missing required parameter: uuid"))
        val froms = params["from"]
            ?: return StatisticsParseResult.Error(errorResult(uri, "Missing required parameter: from"))
        val tos = params["to"]
            ?: return StatisticsParseResult.Error(errorResult(uri, "Missing required parameter: to"))

        if (uuids.size != froms.size || uuids.size != tos.size) {
            return StatisticsParseResult.Error(
                errorResult(
                    uri,
                    "Mismatched parameter counts: uuid=${uuids.size}, from=${froms.size}, to=${tos.size}. " +
                        "Each uuid must have a corresponding from and to."
                )
            )
        }

        val unitStr = params["unit"]?.firstOrNull()?.uppercase()
        val unit = unitStr?.let { name -> StatisticUnit.entries.firstOrNull { it.name == name } }
        if (unitStr != null && unit == null) {
            return StatisticsParseResult.Error(
                errorResult(uri, "Unknown unit '$unitStr'. Valid values: ${StatisticUnit.entries.map { it.name }}")
            )
        }

        return StatisticsParseResult.Ok(StatisticsParams(uuids, froms, tos, unit ?: StatisticUnit.DAY))
    }

    private suspend fun buildStatisticsResults(
        app: LoxoneApp,
        uuids: List<String>,
        froms: List<String>,
        tos: List<String>,
        unit: StatisticUnit
    ): StatisticsParseResult {
        val results = mutableListOf<JsonElement>()
        for (idx in uuids.indices) {
            val uuid = uuids[idx]
            val fromTs = runCatching { Instant.parse(froms[idx]) }.getOrNull()
                ?: return StatisticsParseResult.Error(
                    errorResult("", "Invalid 'from' value '${froms[idx]}' for uuid $uuid — expected ISO 8601 (e.g. 2024-11-15T00:00:00Z)")
                )
            val toTs = runCatching { Instant.parse(tos[idx]) }.getOrNull()
                ?: return StatisticsParseResult.Error(
                    errorResult("", "Invalid 'to' value '${tos[idx]}' for uuid $uuid — expected ISO 8601 (e.g. 2024-11-15T00:00:00Z)")
                )
            val control = app.controls[uuid]
            results += if (control == null) {
                buildJsonObject { put("uuid", uuid); put("error", "Control not found") }
            } else {
                buildStatisticResult(control, fromTs, toTs, unit)
            }
        }
        return StatisticsParseResult.Ok(StatisticsParams(emptyList(), emptyList(), emptyList(), unit), results)
    }

    private suspend fun buildStatisticResult(
        control: Control,
        from: Instant,
        until: Instant,
        unit: StatisticUnit
    ): JsonElement {
        val uuid = control.uuidAction
        val entries: List<StatisticEntry> = try {
            adapter.fetchControlStatistics(uuid, from, until, unit) ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch statistics for $uuid" }
            return buildJsonObject {
                put("uuid", uuid)
                put("name", control.name)
                put("error", "Failed to fetch statistics: ${e.message}")
            }
        }

        // Determine value labels
        val labels: List<String> = control.statistic?.outputs?.map { it.name }?.takeIf { it.isNotEmpty() }
            ?: control.statisticV2?.groups?.firstOrNull()?.dataPoints?.map { it.title ?: it.output ?: "value" }
            ?: emptyList()

        val isV2 = control.statisticV2 != null

        return buildJsonObject {
            put("uuid", uuid)
            put("name", control.name)
            put("type", control.type)
            put("statisticVersion", if (isV2) "v2" else "v1")
            put("unit", unit.name)
            put("entryCount", entries.size)
            putJsonArray("labels") { labels.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("entries") {
                entries.forEach { entry ->
                    add(buildJsonObject {
                        put("timestamp", entry.timestamp.toLong())
                        putJsonArray("values") { entry.values.forEach { add(JsonPrimitive(it)) } }
                    })
                }
            }
        }
    }

    private data class StatisticsParams(
        val uuids: List<String>,
        val froms: List<String>,
        val tos: List<String>,
        val unit: StatisticUnit
    )

    private sealed interface StatisticsParseResult {
        data class Ok(val params: StatisticsParams, val results: List<JsonElement> = emptyList()) : StatisticsParseResult
        data class Error(val result: ReadResourceResult) : StatisticsParseResult
    }

    /**
     * Parse query parameters from a query string, collecting all values per key (supports repeated keys).
     */
    internal fun parseQueryParamsList(queryString: String): Map<String, List<String>> =
        queryString.takeIf { it.isNotBlank() }
            ?.split("&")
            ?.mapNotNull { pair ->
                pair.split("=", limit = 2).takeIf { p -> p.size == 2 && p[0].isNotBlank() }?.let { p ->
                    p[0].decodeURLPart() to p[1].decodeURLPart()
                }
            }
            ?.groupBy({ it.first }, { it.second })
            ?: emptyMap()

    /**
     * Parse query parameters from a query string (first value wins for duplicate keys).
     */
    private fun parseQueryParams(queryString: String): Map<String, String> =
        parseQueryParamsList(queryString).mapValues { it.value.first() }

    private fun JsonObjectBuilder.putStateValue(name: String, value: Any) {
        when (value) {
            is ValueState -> put(name, value.value)
            is TextState -> put(name, value.text)
            else -> put(name, value.toString())
        }
    }

    private suspend fun withDocsVersion(block: (String?) -> ReadResourceResult): ReadResourceResult =
        block(adapter.getMiniserverVersion())

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
