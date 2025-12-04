# Loxone MCP Server

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server that connects AI assistants to Loxone Miniserver smart home systems.

[![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)](https://kotlinlang.org/)
[![MCP SDK](https://img.shields.io/badge/MCP_SDK-0.7.2-green.svg)](https://modelcontextprotocol.io/)

## 🎯 Project Status: M0 (Minimal Viable Connection)

This is the **M0 milestone** release - establishing the foundation for AI-driven smart home control through a clean, extensible architecture.

### What M0 Delivers
- ✅ Reliable connection to Loxone Miniserver via HTTP API
- ✅ Authentication using username/password
- ✅ Automatic parsing and caching of Loxone structure file
- ✅ Direct usage of LoxoneApp with extension functions
- ✅ YAML-based configuration for tools and resources
- ✅ Dynamic tool and resource registration from config
- ✅ Support for both STDIO and HTTP/SSE transport modes
- ✅ Graceful shutdown with proper resource cleanup
- ✅ Clean, maintainable Kotlin codebase following best practices

### Architecture Highlights
- **Configuration-driven**: Define tools and resources in YAML without code changes
- **Direct LoxoneApp usage**: Leverages loxone-client-kotlin's native API and extension functions
- **Type-safe**: Kotlin's type system ensures correctness at compile time
- **Extensible**: Simple pattern for adding new handler types
- **Production-ready**: Proper error handling, logging, and resource management

## 📋 Prerequisites

- **Java 21** or higher
- **Loxone Miniserver** (Gen 1 or Gen 2)
- AI assistant that supports MCP (e.g., Claude Desktop, Cline, GitHub Copilot Chat with MCP)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/smarteon/lox-mcp.git
cd lox-mcp
```

### 2. Configure Environment Variables

Set your Loxone connection details:

**Linux/macOS:**
```bash
export LOXONE_HOST=http://192.168.1.77
export LOXONE_USER=your_username
export LOXONE_PASS=your_password
```

**Windows PowerShell:**
```powershell
$env:LOXONE_HOST="http://192.168.1.77"
$env:LOXONE_USER="your_username"
$env:LOXONE_PASS="your_password"
```

> **Note**: Never commit credentials to version control. Use environment variables or `.env` files (ensure `.env` is in `.gitignore`).

### 3. Build the Project

```bash
./gradlew build
```

### 4. Define Your Tools and Resources

Edit `src/main/resources/mcp-config.yaml` to define the tools and resources you want to expose:

```yaml
tools:
  - name: control_light
    description: Turn a light on or off
    parameters:
      - name: device_id
        type: string
        description: UUID of the light device
        required: true
      - name: action
        type: string
        description: Action to perform
        required: true
        enum: ["on", "off"]
    handler:
      type: control_device

resources:
  - uri: loxone://rooms
    name: All Rooms
    description: List of all rooms in the Loxone system
    mimeType: application/json
    handler:
      type: rooms_list
```

See `DEVELOPER_GUIDE.md` for detailed documentation on creating tools and resources.

### 5. Run the Server

**STDIO Mode (for Claude Desktop, Cline):**
```bash
./gradlew run --args="--stdio"
```

**HTTP/SSE Mode (for web clients):**
```bash
./gradlew run --args="--sse 3001"
```

Or using the distribution:
```bash
./gradlew installDist
./build/install/lox-mcp/bin/lox-mcp --stdio
```

## 🔌 Integration with AI Assistants

### Claude Desktop

Add to your Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "loxone": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/lox-mcp/build/libs/lox-mcp-0.1.0-SNAPSHOT.jar",
        "--stdio"
      ],
      "env": {
        "LOXONE_HOST": "http://192.168.1.77",
        "LOXONE_USER": "your_username",
        "LOXONE_PASS": "your_password"
      }
    }
  }
}
```

### GitHub Copilot Chat (VS Code/JetBrains)

Add to your MCP settings:

```json
{
  "mcpServers": {
    "loxone": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/lox-mcp/build/libs/lox-mcp-0.1.0-SNAPSHOT.jar",
        "--stdio"
      ],
      "env": {
        "LOXONE_HOST": "http://192.168.1.77",
        "LOXONE_USER": "your_username",
        "LOXONE_PASS": "your_password"
      }
    }
  }
}
```

### Cline (VS Code Extension)

Configure in Cline's MCP settings similarly to the above examples.

## 📂 Project Structure

```
lox-mcp/
├── src/main/kotlin/
│   ├── Application.kt              # Entry point, command-line parsing
│   ├── Constants.kt                # Application constants (version, name)
│   ├── LoxoneAdapter.kt            # Wraps Loxone HTTP client
│   ├── config/
│   │   ├── Models.kt               # Config data classes
│   │   └── ConfigLoader.kt         # YAML config loading
│   └── server/
│       ├── McpServer.kt            # MCP server setup (STDIO & HTTP/SSE)
│       ├── ToolsRegistry.kt        # Registers tools from config
│       ├── ResourcesRegistry.kt    # Registers resources from config
│       ├── DynamicToolHandler.kt   # Executes tool logic
│       └── DynamicResourceHandler.kt # Provides resource content
├── src/main/resources/
│   └── mcp-config.yaml             # Tools and resources configuration
├── build.gradle.kts                # Gradle build configuration
├── gradle/libs.versions.toml       # Dependency versions
└── README.md                       # This file
```

## 🛠️ Configuration

### Tools Configuration

Tools represent actions the AI can perform. Each tool definition includes:

- **name**: Unique identifier for the tool
- **description**: Clear explanation of what the tool does
- **parameters**: List of input parameters with types and constraints
- **handler**: Handler configuration specifying the implementation type

**Supported handler types:**
- `control_device` - Control a single device by UUID
- `control_devices_by_room` - Control all devices in a room
- `control_devices_by_type` - Control all devices of a specific type
- `control_devices_by_category` - Control all devices in a category
- `send_command` - Send raw command to a device

### Resources Configuration

Resources represent information the AI can read. Each resource definition includes:

- **uri**: Unique URI for the resource (e.g., `loxone://rooms`)
- **name**: Human-readable name
- **description**: Clear explanation of the resource content
- **mimeType**: Content type (typically `application/json`)
- **handler**: Handler configuration specifying the implementation type

**Supported handler types:**
- `rooms_list` - List all rooms
- `room_devices` - List devices in a specific room
- `devices_all` - List all devices
- `devices_by_type` - List devices of a specific type
- `devices_by_category` - List devices in a category
- `categories_list` - List all categories
- `structure_summary` - Overview of the entire system

### URI Patterns

Resources support URI patterns with placeholders:
- `loxone://rooms/{roomName}/devices` - Room name extracted from URI
- `loxone://devices/type/{deviceType}` - Device type extracted from URI
- `loxone://devices/category/{categoryName}` - Category name extracted from URI

## 🧪 Development

### Build Commands

```bash
# Full build
./gradlew build

# Run tests (when implemented)
./gradlew test

# Run in STDIO mode
./gradlew run --args="--stdio"

# Run in HTTP/SSE mode on port 3001
./gradlew run --args="--sse 3001"

# Create distribution
./gradlew installDist

# Build JAR
./gradlew jar
```

### Code Style

This project follows:
- Kotlin coding conventions
- Conventional Commits for commit messages
- KDoc for public API documentation

See `.github/copilot-instructions.md` for detailed coding guidelines.

## 🔍 Debugging

Enable debug logging by setting the log level:

```bash
# Application logs at INFO level by default
# Check console output for connection status and errors
```

Common issues:
- **Connection timeout**: Verify `LOXONE_HOST` is correct and accessible
- **Authentication failed**: Check `LOXONE_USER` and `LOXONE_PASS`
- **MCP connection closed**: Ensure Java 21+ is installed and JAR is built correctly

## 📄 License

This project is licensed under the BSD 3-Clause License - see the [LICENSE](LICENSE) file for details.

## 🔗 Related Projects

- [loxone-client-kotlin](https://github.com/Smarteon/loxone-client-kotlin) - Kotlin client library for Loxone Miniserver
- [Model Context Protocol](https://modelcontextprotocol.io/) - Protocol specification
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) - Kotlin SDK for MCP

## 📞 Support

For issues and questions:
- Open an issue on [GitHub](https://github.com/smarteon/lox-mcp/issues)
- Check the [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for detailed documentation
- Review [loxone-app-parsing.md](loxone-app-parsing.md) for structure file details

