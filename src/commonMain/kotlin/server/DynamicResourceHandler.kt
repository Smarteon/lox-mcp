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
import cz.smarteon.loxmcp.readResourceBytes
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                HandlerTypes.WEBSERVICES -> handleWebservices(uri)
                HandlerTypes.DIAGNOSTIC_SCENARIOS -> handleDiagnosticScenarios(uri)
                HandlerTypes.SYSTEM_LOG -> handleSystemLog(uri)
                HandlerTypes.DEVICES_WITH_STATISTICS -> handleDevicesWithStatistics(uri)
                HandlerTypes.SYSTEM_STATUS -> handleSystemStatus(uri)
                HandlerTypes.PHYSICAL_DEVICES -> handlePhysicalDevices(uri)
                HandlerTypes.SYSTEM_STATS -> handleSystemStats(uri)
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
                    put("controlCount", app.countVisibleControlsInRoom(room.uuid))
            }
        }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(roomsList))
        return successResult(resourceConfig.uri, content, "application/json")
    }

    private suspend fun handleRoomDevices(uri: String): ReadResourceResult {
        val roomName = uri.substringAfter("rooms/").substringBefore("/controls")
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
                put("controlCount", app.countVisibleControlsInCategory(category.uuid))
            }
        }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(categoriesList))
        return successResult(resourceConfig.uri, content, "application/json")
    }

    private suspend fun handleStructureSummary(): ReadResourceResult {
        val app = adapter.getApp()

        val summary = buildJsonObject {
            put("rooms", app.rooms.size)
            put("controls", app.getVisibleControls().size)
            put("categories", app.cats.size)
            putJsonArray("roomList") {
                app.rooms.values.forEach { room ->
                    add(buildJsonObject {
                        put("name", room.name)
                put("controlCount", app.countVisibleControlsInRoom(room.uuid))
                    })
                }
            }
            putJsonArray("categoryList") {
                app.cats.values.forEach { cat ->
                    add(buildJsonObject {
                        put("name", cat.name)
                        put("type", cat.type ?: "unknown")
                        put("controlCount", app.countVisibleControlsInCategory(cat.uuid))
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
            put("totalControls", filteredControls.controls.size)
            put("stateCount", adapter.getState().size())
            if (queryParams.isNotEmpty()) {
                putJsonObject("filters") { queryParams.forEach { (k, v) -> put(k, v) } }
            }
            putJsonArray("controls") { deviceJsons.forEach { add(it) } }
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
        val deviceUuid = uri.substringAfter("controls/").substringBefore("/state")
            .takeIf { it.isNotBlank() } ?: return errorResult(uri, "Control UUID not found in URI")

        val app = adapter.getApp()
        val control = app.controls[deviceUuid]
            ?: return errorResult(uri, "Control not found with UUID: $deviceUuid")

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

    private fun handleWebservices(uri: String): ReadResourceResult {
        val bytes = readResourceBytes("/loxone-docs/webservices.json")
            ?: return errorResult(uri, "webservices.json not found")
        return successResult(uri, bytes.decodeToString(), resourceConfig.mimeType)
    }

    private fun handleDiagnosticScenarios(uri: String): ReadResourceResult {
        val bytes = readResourceBytes("/loxone-docs/diagnostic-scenarios.json")
            ?: return errorResult(uri, "diagnostic-scenarios.json not found")
        return successResult(uri, bytes.decodeToString(), resourceConfig.mimeType)
    }

    private suspend fun handleSystemLog(uri: String): ReadResourceResult {
        val queryString = uri.substringAfter("?", "")
        val lines = (parseQueryParams(queryString)["lines"]?.toIntOrNull() ?: 200).coerceIn(1, 1000)

        val log = adapter.sendRawCommand("dev/fsget/log/def.log")
        val truncated = log.lines().takeLast(lines).joinToString("\n")
        return successResult(uri, truncated, resourceConfig.mimeType)
    }

    private suspend fun handleDevicesWithStatistics(uri: String): ReadResourceResult {
        val app = adapter.getApp()
        val controls = app.controls.values.filter { it.statistic != null || it.statisticV2 != null }

        val result = controls.map { control ->
            buildJsonObject {
                put("uuid", control.uuidAction)
                put("name", control.name)
                put("type", control.type)
                control.room?.let { put("room", app.rooms[it]?.name) }
                put("statisticVersion", if (control.statisticV2 != null) "v2" else "v1")
                control.statistic?.outputs?.let { outputs ->
                    putJsonArray("outputs") { outputs.forEach { o -> add(JsonPrimitive(o.name)) } }
                }
                control.statisticV2?.groups?.firstOrNull()?.dataPoints?.let { dps ->
                    putJsonArray("outputs") { dps.forEach { dp -> add(JsonPrimitive(dp.title ?: dp.output ?: "value")) } }
                }
            }
        }

        val content = json.encodeToString(JsonArray.serializer(), JsonArray(result))
        return successResult(uri, content, resourceConfig.mimeType)
    }

    private suspend fun handleSystemStatus(uri: String): ReadResourceResult {
        fun parseValue(response: String): String =
            Regex("""value="([^"]+)"""").find(response)?.groupValues?.get(1) ?: response.trim()

        val raw = coroutineScope {
            awaitAll(
                async { parseValue(adapter.sendRawCommand("dev/sps/state")) },
                async { parseValue(adapter.sendRawCommand("dev/sys/cpu")) },
                async { parseValue(adapter.sendRawCommand("dev/sys/heap")) },
                async { parseValue(adapter.sendRawCommand("dev/sps/status")) },
                async { parseValue(adapter.sendRawCommand("dev/cfg/version")) },
                async { parseValue(adapter.sendRawCommand("dev/sys/time")) },
                async { parseValue(adapter.sendRawCommand("dev/cfg/ip")) },
                async { parseValue(adapter.sendRawCommand("dev/cfg/dns1")) },
                async { parseValue(adapter.sendRawCommand("dev/cfg/ntp")) }
            )
        }
        val plcStateRaw = raw[0].toIntOrNull() ?: -1
        val cpuRaw = raw[1].toIntOrNull() ?: -1
        val heapRaw = raw[2].toLongOrNull() ?: -1L
        val cycleStatus = raw[3]
        val version = raw[4]
        val systemTime = raw[5]
        val ip = raw[6]
        val dns = raw[7]
        val ntp = raw[8]

        val plcStateLabel = when (plcStateRaw) {
            0 -> "none"; 1 -> "starting"; 2 -> "loaded"; 3 -> "started"
            4 -> "LoxLink started"; 5 -> "running"; 6 -> "changing"
            7 -> "error"; 8 -> "updating"; else -> "unknown"
        }

        val result = buildJsonObject {
            putJsonObject("plc") {
                put("state", plcStateRaw)
                put("stateLabel", plcStateLabel)
                put("healthy", plcStateRaw == 5)
                put("cycleStatus", cycleStatus)
            }
            putJsonObject("system") {
                put("cpuPercent", cpuRaw)
                put("cpuHealthy", cpuRaw in 0..79)
                put("heapBytes", heapRaw)
                put("heapHealthy", heapRaw > 10000L)
                put("firmwareVersion", version)
                put("systemTime", systemTime)
            }
            putJsonObject("network") {
                put("ip", ip)
                put("dns", dns)
                put("ntp", ntp)
            }
        }

        return successResult(uri, json.encodeToString(JsonObject.serializer(), result), resourceConfig.mimeType)
    }

    private suspend fun handlePhysicalDevices(uri: String): ReadResourceResult {
        val queryParams = parseQueryParams(uri.substringAfter("?", ""))
        val nameFilter = queryParams["name"]
        val serialFilter = queryParams["serial"]
        val versionFilter = queryParams["version"]
        val typeFilter = queryParams["type"]
        val onlineOnly = queryParams["online_only"]?.toBooleanStrictOrNull() ?: false

        val xml = adapter.getPhysicalDevices()
        val devices = parsePhysicalDeviceXml(xml)
            .filter { nameFilter == null || it["Name"]?.contains(nameFilter, ignoreCase = true) == true }
            .filter { serialFilter == null || it["Serial"] == serialFilter }
            .filter { versionFilter == null || it["Version"]?.contains(versionFilter) == true }
            .filter { typeFilter == null || it["Type"]?.equals(typeFilter, ignoreCase = true) == true }
            .filter { !onlineOnly || it["Online"]?.lowercase() == "true" || it["Offline"]?.lowercase() == "false" }

        val response = buildJsonObject {
            put("total", devices.size)
            putJsonArray("devices") {
                devices.forEach { device ->
                    add(buildJsonObject {
                        device.forEach { (k, v) -> put(k, v) }
                    })
                }
            }
        }
        return successResult(uri, json.encodeToString(JsonObject.serializer(), response), resourceConfig.mimeType)
    }

    private fun parsePhysicalDeviceXml(xml: String): List<Map<String, String>> {
        data class Frame(val tag: String, val attrs: Map<String, String>)

        val results = mutableListOf<Map<String, String>>()
        val stack = ArrayDeque<Frame>()
        val tokenPattern = Regex("""<(/)?(\w+)([^>]*?)(/?)>""")

        tokenPattern.findAll(xml).forEach { match ->
            val isClosing = match.groupValues[1] == "/"
            val isSelfClosing = match.groupValues[4] == "/"
            val tagName = match.groupValues[2]
            val tagAttrs = extractXmlAttributes(match.groupValues[3])
            val isDevice = tagAttrs.containsKey("Online") || tagAttrs.containsKey("Offline")

            if (isClosing) {
                if (stack.lastOrNull()?.tag == tagName) stack.removeLast()
            } else {
                if (isDevice) {
                    val device = tagAttrs.toMutableMap()
                    if (!device.containsKey("Type")) device["Type"] = tagName
                    // Attach immediate non-root parent context (branch/extension the device is on)
                    stack.lastOrNull()?.takeIf { it.tag != "Miniserver" }?.let { parent ->
                        device["parentType"] = parent.attrs["Type"] ?: parent.tag
                        device["parentName"] = parent.attrs["Name"] ?: parent.tag
                        parent.attrs["Serial"]?.let { device["parentSerial"] = it }
                    }
                    results.add(device.toMap())
                }
                if (!isSelfClosing) stack.addLast(Frame(tagName, tagAttrs))
            }
        }
        return results
    }

    private fun extractXmlAttributes(attrString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val attrPattern = Regex("""(\w+)="([^"]*)"""")
        attrPattern.findAll(attrString).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private suspend fun handleSystemStats(uri: String): ReadResourceResult {
        val response = adapter.sendRawCommand("stats/")
        return successResult(uri, response, resourceConfig.mimeType)
    }

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
