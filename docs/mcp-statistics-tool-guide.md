# Guide: Implementing a Statistics Tool in the Loxone MCP Server

This guide explains how to use the `loxone-client-kotlin` library to implement a statistics-reading tool in the MCP server.

## Overview of the Statistics API

All statistics functionality is exposed as **extension functions on `Control`**, found in `cz.smarteon.loxkt.app.StatisticExtensions`. You need a `LoxoneApp` to find the control and a `LoxoneClient` (specifically `KtorHttpLoxoneClient`) to execute the requests.

### Key types

| Type | Package | Purpose |
|---|---|---|
| `Control` | `cz.smarteon.loxkt.app` | A single Loxone control — the receiver for all stat calls |
| `StatisticEntry` | `cz.smarteon.loxkt.app` | One data point: `timestamp: UInt` + `values: List<Double>` |
| `StatisticUnit` | `cz.smarteon.loxkt.app` | Resolution enum: `ALL`, `HOUR`, `DAY`, `MONTH`, `YEAR` |
| `LoxoneApp` | `cz.smarteon.loxkt.app` | The full app config — contains all controls |

### Timestamp semantics

- **V2 controls** (`control.statisticV2 != null`): timestamps are **Unix UTC seconds**
- **V1 controls** (`control.statistic != null`): timestamps are **seconds since Loxone epoch (2009-01-01 00:00:00)** in local Miniserver time

### Transport

All statistics use **HTTP only** — use `KtorHttpLoxoneClient`, not `KtorWebsocketLoxoneClient` (calling `callRawForData` on the WS client throws `UnsupportedOperationException`).

---

## The three extension functions

### 1. `fetchStatistics` — unified, recommended for the MCP tool

```kotlin
suspend fun Control.fetchStatistics(
    client: LoxoneClient,
    from: Long,          // Unix UTC timestamp, inclusive (V2 only)
    until: Long,         // Unix UTC timestamp, inclusive (V2 only)
    unit: StatisticUnit = StatisticUnit.DAY,  // V2 only
    date: String? = null // V1 only, format "YYYYMM" or "YYYYMMDD", defaults to current month
): List<StatisticEntry>
```

Auto-detects V1 vs V2 and dispatches appropriately. **Use this for the MCP tool.** Returns `emptyList()` if the control has no statistics configured at all.

### 2. `fetchV1Http` — explicit V1

```kotlin
suspend fun Control.fetchV1Http(client: LoxoneClient, date: String): List<StatisticEntry>
```

Fetches XML data for a specific month (`"202501"`) or day (`"20250115"`). Only works on controls with `control.statistic != null`.

### 3. `fetchV2Raw` / `fetchV2Diff` — explicit V2

```kotlin
suspend fun Control.fetchV2Raw(client, from, until, groupId?, unit?, outputName?): List<StatisticEntry>
suspend fun Control.fetchV2Diff(client, from, until, groupId?, unit?, outputName?): List<StatisticEntry>
```

- `fetchV2Raw` — every recorded data point in the time range
- `fetchV2Diff` — one aggregated entry per `unit` period (sum of differences); this is what `fetchStatistics` uses internally

---

## Step-by-step: implementing the MCP tool

### Step 1 — Get the app and find the control

The MCP server likely already has a `LoxoneApp` loaded. Find the control by name or UUID:

```kotlin
val app: LoxoneApp = // already available in the MCP server
val control = app.controls.values.firstOrNull { it.name == controlName }
    ?: return "Control '$controlName' not found"

// Check it actually has statistics
if (control.statistic == null && control.statisticV2 == null) {
    return "Control '$controlName' has no statistics configured"
}
```

### Step 2 — Determine the time range

For V2 controls you need Unix UTC timestamps. For V1 controls you need a date string.

```kotlin
import kotlinx.datetime.*

// For V2: last 30 days
val until = Clock.System.now().epochSeconds
val from = until - 30 * 24 * 3600L

// For V1: current month (fetchStatistics handles this automatically when date = null)
// Or specify explicitly:
val date = "202501" // January 2025
```

### Step 3 — Call fetchStatistics

```kotlin
val httpClient: KtorHttpLoxoneClient = // from MCP server context
val entries: List<StatisticEntry> = control.fetchStatistics(
    client = httpClient,
    from = from,
    until = until,
    unit = StatisticUnit.DAY,  // one entry per day for V2
    date = null                 // auto-defaults to current month for V1
)
```

### Step 4 — Format the result for the MCP response

Each `StatisticEntry` has:
- `entry.timestamp: UInt` — see timestamp semantics above
- `entry.values: List<Double>` — one value per configured output/data-point

```kotlin
// Get output names for labelling (V1)
val outputNames = control.statistic?.outputs?.map { it.name } ?: emptyList()
// Get data point titles for labelling (V2)
val dataPointTitles = control.statisticV2?.groups
    ?.firstOrNull()?.dataPoints?.map { it.title ?: it.output ?: "value" }
    ?: emptyList()

val labels = if (outputNames.isNotEmpty()) outputNames else dataPointTitles

val result = entries.joinToString("\n") { entry ->
    val valueStr = entry.values.mapIndexed { i, v ->
        val label = labels.getOrElse(i) { "value$i" }
        "$label=$v"
    }.joinToString(", ")
    "ts=${entry.timestamp}: $valueStr"
}
```

---

## MCP tool definition sketch

```kotlin
McpTool(
    name = "get_statistics",
    description = "Fetch historical statistics for a Loxone control",
    parameters = listOf(
        McpParam("control_name", "string", "Name of the control (e.g. 'Energy Meter')"),
        McpParam("from_days_ago", "integer", "How many days back to fetch (V2 only)", required = false),
        McpParam("unit", "string", "Grouping: ALL, HOUR, DAY, MONTH, YEAR (V2 only, default DAY)", required = false),
        McpParam("date", "string", "For V1 controls: YYYYMM or YYYYMMDD (default: current month)", required = false),
    )
) { params ->
    val controlName = params["control_name"] as String
    val daysAgo = (params["from_days_ago"] as? Int) ?: 30
    val unit = StatisticUnit.entries.firstOrNull {
        it.name == (params["unit"] as? String)?.uppercase()
    } ?: StatisticUnit.DAY
    val date = params["date"] as? String

    val control = app.controls.values.firstOrNull { it.name == controlName }
        ?: return@McpTool "Control not found"

    val until = Clock.System.now().epochSeconds
    val from = until - daysAgo * 24 * 3600L

    val entries = control.fetchStatistics(httpClient, from, until, unit, date)
    if (entries.isEmpty()) return@McpTool "No statistics data available"

    // format and return...
}
```

---

## Important caveats for the implementer

1. **Use `KtorHttpLoxoneClient`**, not the WebSocket client — `callRawForData` is HTTP only.

2. **`from`/`until`/`unit` are ignored for V1 controls** — `fetchStatistics` uses the `date` parameter instead (defaults to the current month). If you need a specific month for a V1 control, pass it as `date = "YYYYMM"`.

3. **`entry.values` may have multiple elements** — a control can record multiple outputs simultaneously (e.g. import + export on an energy meter). Use the output/data-point names from `control.statistic?.outputs` or `control.statisticV2?.groups?.first()?.dataPoints` to label them.

4. **V1 timestamps are not Unix time** — they are seconds since 2009-01-01 in local Miniserver time. If you need to display them as human-readable dates, add them to `LocalDateTime(2009, 1, 1, 0, 0, 0)` using `kotlinx.datetime` arithmetic, or just display the raw offset.

5. **`emptyList()` on no stats** — `fetchStatistics` returns an empty list both when there is no data for the period *and* when the control has no statistics configured. Check `control.statistic` and `control.statisticV2` upfront if you need to distinguish these cases.

