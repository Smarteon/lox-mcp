# Quick Start Guide

## For Developers: Adding New Capabilities

### Adding a New Tool

1. **Define in YAML** (`src/main/resources/mcp-config.yaml`):

```yaml
tools:
  - name: my_new_tool
    description: |
      Clear description of what this tool does.
      Use multiple lines if needed.
    parameters:
      - name: param1
        type: string
        description: Description of first parameter
        required: true
        enum: [option1, option2, option3]  # Optional
      - name: param2
        type: number
        description: Description of second parameter
        required: false
        default: "10"  # Optional
    handler:
      type: my_handler_type  # Reference to handler implementation
```

2. **Implement Handler** (`src/main/kotlin/server/DynamicToolHandler.kt`):

```kotlin
// Add to when expression in handle() method:
when (toolConfig.handler.type) {
    "my_handler_type" -> handleMyHandlerType(arguments)
    // ... existing cases
}

// Implement the handler method:
private suspend fun handleMyHandlerType(arguments: JsonObject): CallToolResult {
    // Extract parameters
    val param1 = arguments["param1"]?.jsonPrimitive?.content
        ?: return errorResult("Missing required parameter: param1")
    
    val param2 = arguments["param2"]?.jsonPrimitive?.intOrNull ?: 10
    
    // Your logic here
    try {
        val result = adapter.sendCommand(param1, "someCommand")
        return successResult("Success: $result")
    } catch (e: Exception) {
        return errorResult("Error: ${e.message}")
    }
}
```

3. **Test**: Restart the server and the new tool will be available!

### Adding a New Resource

1. **Define in YAML** (`src/main/resources/mcp-config.yaml`):

```yaml
resources:
  - uri: loxone://my/resource
    name: My Resource
    description: |
      What this resource provides.
      Can include examples and usage hints.
    mimeType: application/json  # or text/plain, etc.
    handler:
      type: my_resource_handler
```

2. **Implement Handler** (`src/main/kotlin/server/DynamicResourceHandler.kt`):

```kotlin
// Add to when expression in handle() method:
when (resourceConfig.handler.type) {
    "my_resource_handler" -> handleMyResourceHandler(uri)
    // ... existing cases
}

// Implement the handler method:
private suspend fun handleMyResourceHandler(uri: String): ReadResourceResult {
    val model = adapter.getSemanticModel()
    
    // Build your response
    val data = buildJsonObject {
        put("key", "value")
        putJsonArray("items") {
            add("item1")
            add("item2")
        }
    }
    
    val content = json.encodeToString(JsonObject.serializer(), data)
    return successResult(uri, content, "application/json")
}
```

3. **Test**: Restart the server and the new resource will be available!

### Pattern-Based URIs

For dynamic URIs like `loxone://rooms/{roomName}/devices`:

```kotlin
private suspend fun handleRoomDevices(uri: String): ReadResourceResult {
    // Extract parameter from URI
    val roomName = uri.substringAfter("rooms/").substringBefore("/devices")
    
    if (roomName.isBlank()) {
        return errorResult(uri, "Room name not found in URI")
    }
    
    // Use the parameter
    val model = adapter.getSemanticModel()
    val devices = StructureParser.findDevicesByRoom(model, roomName)
    
    // ... build response
}
```

## Common Patterns

### Querying the Semantic Model

```kotlin
val model = adapter.getSemanticModel()

// Get all rooms
model.rooms.values  // Map<String, RoomModel>

// Get all devices
model.devices.values  // Map<String, DeviceModel>

// Get all categories
model.categories.values  // Map<String, CategoryModel>

// Find devices by room
StructureParser.findDevicesByRoom(model, "Kitchen")

// Find devices by type
StructureParser.findDevicesByType(model, "Switch")

// Find devices by category
StructureParser.findDevicesByCategory(model, "Lights")

// Find device by name
StructureParser.findDeviceByName(model, "Main Light")
```

### Sending Commands

```kotlin
// Send command to a specific device
adapter.sendCommand(uuid, "On")
adapter.sendCommand(uuid, "Off")
adapter.sendCommand(uuid, "50")  // For dimmers, etc.

// Control multiple devices
val devices = StructureParser.findDevicesByRoom(model, "Kitchen")
devices.forEach { device ->
    adapter.sendCommand(device.uuid, "Off")
}
```

### Building JSON Responses

```kotlin
// Simple object
val data = buildJsonObject {
    put("name", "Kitchen")
    put("deviceCount", 5)
    put("hasLights", true)
}

// Array of objects
val devices = listOf(...)
val jsonArray = buildJsonArray {
    devices.forEach { device ->
        add(buildJsonObject {
            put("uuid", device.uuid)
            put("name", device.name)
            put("type", device.type)
        })
    }
}

// Encode to string
val content = json.encodeToString(JsonObject.serializer(), data)
```

### Error Handling

```kotlin
// For tools
try {
    // your code
    return successResult("Operation successful")
} catch (e: Exception) {
    logger.error(e) { "Error in tool" }
    return errorResult("Error: ${e.message}")
}

// For resources
try {
    // your code
    return successResult(uri, content, mimeType)
} catch (e: Exception) {
    logger.error(e) { "Error in resource" }
    return errorResult(uri, "Error: ${e.message}")
}
```

## Testing Your Changes

### 1. Build
```bash
./gradlew clean build
```

### 2. Run in STDIO mode
```bash
./gradlew run --args="--stdio"
```

### 3. Run in HTTP mode
```bash
./gradlew run --args="--sse 3001"
```

### 4. Test with curl (HTTP mode only)
```bash
# List available tools
curl http://localhost:3001/mcp/tools

# List available resources
curl http://localhost:3001/mcp/resources
```

### 5. Test with MCP client
- Configure Claude Desktop or Cline
- Try natural language commands that should trigger your tools

## Tips & Tricks

### Use Descriptive Names
- Tool names: `verb_noun` format (e.g., `control_device`, `set_temperature`)
- Resource URIs: hierarchical paths (e.g., `loxone://rooms/all`, `loxone://devices/type/Switch`)

### Provide Good Descriptions
The AI assistant reads these descriptions to understand what to use:

```yaml
description: |
  This tool controls all devices in a specific room.
  
  Examples:
  - Turn off all lights in the kitchen
  - Open all blinds in the bedroom
  
  The tool will find all devices in the specified room and send
  the requested action to each one.
```

### Use Enums for Valid Values
```yaml
parameters:
  - name: action
    type: string
    enum: [on, off, toggle, up, down, stop]
```

This helps the AI choose valid values.

### Provide Defaults
```yaml
parameters:
  - name: timeout
    type: number
    default: "30"
    required: false
```

### Log Everything
```kotlin
logger.info { "Processing request with param: $param" }
logger.debug { "Intermediate result: $result" }
logger.error(e) { "Failed to process" }
```

### Keep It Simple
- One tool = one responsibility
- Prefer composition over complexity
- Let the AI handle the orchestration

## Example: Complete Tool Implementation

Here's a complete example of adding a "set dimmer brightness" tool:

**1. YAML** (`mcp-config.yaml`):
```yaml
tools:
  - name: set_dimmer_brightness
    description: |
      Sets the brightness of a dimmer device.
      Brightness is specified as a percentage (0-100).
    parameters:
      - name: device_id
        type: string
        description: UUID of the dimmer device
        required: true
      - name: brightness
        type: number
        description: Brightness level (0-100)
        required: true
    handler:
      type: set_dimmer
```

**2. Handler** (`DynamicToolHandler.kt`):
```kotlin
when (toolConfig.handler.type) {
    "set_dimmer" -> handleSetDimmer(arguments)
    // ... existing cases
}

private suspend fun handleSetDimmer(arguments: JsonObject): CallToolResult {
    val deviceId = arguments["device_id"]?.jsonPrimitive?.content
        ?: return errorResult("Missing device_id")
    
    val brightness = arguments["brightness"]?.jsonPrimitive?.intOrNull
        ?: return errorResult("Missing or invalid brightness")
    
    if (brightness !in 0..100) {
        return errorResult("Brightness must be between 0 and 100")
    }
    
    try {
        adapter.sendCommand(deviceId, brightness.toString())
        return successResult("Dimmer set to $brightness%")
    } catch (e: Exception) {
        logger.error(e) { "Failed to set dimmer" }
        return errorResult("Error: ${e.message}")
    }
}
```

**3. Test**:
```bash
./gradlew build run --args="--stdio"
```

Done! The AI can now use:
- "Set the living room dimmer to 50%"
- "Dim the bedroom light to 25%"

## Need Help?

- Check `IMPLEMENTATION_SUMMARY.md` for architecture overview
- Check `README.md` for general documentation
- Check existing handlers for examples
- Look at `tools_reference.md` and `resource_quick_reference.md` for inspiration

## Common Issues

### "Unresolved reference" errors in IDE
- Run `./gradlew build` first
- The IDE needs to index the built artifacts
- Try "File → Invalidate Caches / Restart" in IntelliJ

### YAML parsing errors
- Check YAML syntax (indentation matters!)
- Use a YAML validator online
- Check the logs for parsing error details

### Tool not showing up
- Check YAML syntax
- Restart the server
- Check logs for registration messages

### Resource returns empty data
- Verify the semantic model is loaded
- Check if structure file is accessible
- Add debug logging to your handler

