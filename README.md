# Loxone MCP Server

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server that connects AI assistants to Loxone Miniserver smart home systems.

[![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)](https://kotlinlang.org/)
[![MCP SDK](https://img.shields.io/badge/MCP_SDK-0.7.2-green.svg)](https://modelcontextprotocol.io/)

## ✨ Features

- 🔌 **Reliable Connection** - Connects to Loxone Miniserver via HTTP API and WebSocket
- 📊 **Real-time State Reading** - Read device states via WebSocket event streaming
- 🔐 **Flexible Credentials** - Multiple credential sources (env vars, CLI args, Bitwarden)
- 📝 **Configuration-Driven** - Define tools and resources in YAML without code changes
- 🎯 **Dynamic Registration** - Tools and resources automatically loaded from configuration
- 🚀 **Dual Transport** - Support for both STDIO and HTTP/SSE modes
- 📚 **Built-in Loxone Docs** - Versioned Structure File documentation served as browsable resources
- 🧹 **Clean Architecture** - Type-safe Kotlin codebase with proper resource management
- 📦 **Easy Integration** - Works with Claude Desktop, Cline, GitHub Copilot Chat, and more

## 📋 Prerequisites

- **Java 21** or higher
- **Loxone Miniserver** (Gen 1 or Gen 2)
- AI assistant that supports MCP (e.g., Claude Desktop, Cline, GitHub Copilot Chat with MCP)

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/smarteon/lox-mcp.git
cd lox-mcp
./gradlew build
```

### 2. Configure Credentials

The server resolves credentials using a fixed priority: Bitwarden (if configured), then command-line arguments (if all are provided), and finally environment variables as a fallback:

#### Option A: Command-Line Arguments

```bash
java -jar build/libs/lox-mcp-*.jar --stdio \
  --address "http://192.168.1.77" \
  --username "your_username" \
  --password "your_password"
```

#### Option B: Environment Variables (Recommended for Production)

```bash
# Linux/macOS
export LOXONE_HOST=http://192.168.1.77
export LOXONE_USER=your_username
export LOXONE_PASS=your_password

# Windows PowerShell
$env:LOXONE_HOST="http://192.168.1.77"
$env:LOXONE_USER="your_username"
$env:LOXONE_PASS="your_password"
```

### 3. Run the Server

**STDIO Mode (for Claude Desktop, Cline):**
```bash
./gradlew run --args="--stdio"
```

**HTTP/SSE Mode (for web clients):**
```bash
./gradlew run --args="--sse 3001"
```

### 4. Resources as Tools Mode (Recommended)

Many MCP clients have limited or no support for MCP resources. Use the `--resources-as-tools` flag to expose all resources as callable tools instead:

```bash
# STDIO mode with resources as tools
./gradlew run --args="--stdio --resources-as-tools"

# HTTP/SSE mode with resources as tools  
./gradlew run --args="--sse 3001 --resources-as-tools"
```

This converts resources like `loxone://rooms` into tools like `get_rooms_list`, making them accessible to any MCP client.

## 🔌 Integration with AI Assistants

### Claude Desktop

Add to your Claude Desktop configuration (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "loxone": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/lox-mcp/build/libs/lox-mcp-*.jar",
        "--stdio",
        "--resources-as-tools"
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

> **Note:** The `--resources-as-tools` flag is recommended because Claude Desktop has limited support for MCP resources.

### GitHub Copilot Chat (VS Code/JetBrains)

Configure in your MCP settings using the same format as Claude Desktop.

### Cline (VS Code Extension)

Configure in Cline's MCP settings using the same format.

## 📂 Project Structure

```
lox-mcp/
├── src/main/kotlin/
│   ├── Application.kt              # Entry point, command-line parsing
│   ├── Constants.kt                # Application constants and handler type names
│   ├── LoxoneAdapter.kt            # Wraps Loxone HTTP/WebSocket client
│   ├── credentials/
│   │   ├── CredentialSource.kt     # Credential source interface and implementations
│   │   └── CredentialResolver.kt   # Resolves credentials from sources
│   ├── config/
│   │   ├── Models.kt               # Config data classes
│   │   └── ConfigLoader.kt         # YAML config loading
│   ├── loxonedocs/
│   │   ├── LoxoneDocsProvider.kt   # Loads and serves versioned docs bundles
│   │   └── Models.kt               # Docs data models (ControlDoc, DocSection, etc.)
│   └── server/
│       ├── McpServer.kt            # MCP server setup (STDIO & HTTP/SSE)
│       ├── ToolsRegistry.kt        # Registers tools from config
│       ├── ResourcesRegistry.kt    # Registers resources from config
│       ├── DynamicToolHandler.kt   # Executes tool logic
│       └── DynamicResourceHandler.kt # Provides resource content
├── src/main/resources/
│   └── mcp-config.yaml             # Tools and resources configuration
├── loxone-docs/
│   ├── versions.json               # Index of available parsed docs versions
│   └── structure-file-{ver}.json   # Pre-parsed Structure File docs (CI-generated)
├── .github/
│   ├── scripts/parse-loxone-docs.py  # Python script that parses the Loxone PDF
│   └── workflows/update-loxone-docs.yml # Weekly CI job to refresh docs
├── build.gradle.kts                # Gradle build configuration
├── gradle/libs.versions.toml       # Dependency versions
└── README.md                       # This file
```

### Available Tools

The server exposes these MCP tools for controlling Loxone devices:

| Tool | Description |
|------|-------------|
| `control_device` | Control a specific device by UUID (on, off, toggle, up, down, stop) |
| `control_devices_by_room` | Control all devices in a room (with optional type filter and state reading) |
| `control_devices_by_type` | Control all devices of a specific type system-wide |
| `control_devices_by_category` | Control all devices in a category |
| `send_command` | Send raw commands for advanced control |

### Available Resources

Resources provide read-only access to Loxone system data and documentation:

| Resource URI | Description |
|--------------|-------------|
| `loxone://structure/summary` | Overview of rooms, devices, and categories |
| `loxone://rooms` | List of all rooms with device counts |
| `loxone://rooms/{roomName}/devices` | Devices in a specific room |
| `loxone://devices/all` | Complete list of all devices |
| `loxone://devices/type/{type}` | Devices filtered by type (e.g., Switch, Dimmer) |
| `loxone://devices/category/{name}` | Devices filtered by category |
| `loxone://categories` | List of all categories |
| `loxone://devices/states` | Real-time state values for all devices |
| `loxone://devices/{uuid}/state` | State values for a specific device |
| `loxone://docs` | **Loxone docs TOC** — browse all documented sections and controls |
| `loxone://docs/controls` | **Flat list of all control types** with detail links |
| `loxone://docs/topic/{name}` | **Full docs for a control or general section** (e.g. `Switch`, `rooms`) |

### Custom Configuration Files

You can load custom configuration files using the `-c` or `--config` parameter. By default, custom configurations are **merged** with the internal configuration, allowing you to add or override specific tools and resources.

#### Merge Mode (Default)

```bash
# Custom tools/resources are added to internal ones
./gradlew run --args="--stdio -c /path/to/custom-config.yaml"
```

#### Override Mode

```bash
# Only use custom configuration, ignore internal config
./gradlew run --args="--stdio -c /path/to/custom-config.yaml -o"
```

### Tools and Resources

Define tools and resources in `src/main/resources/mcp-config.yaml`:

```yaml
tools:
  - name: control_device
    description: Control a Loxone device
    parameters:
      - name: device_id
        type: string
        required: true
      - name: action
        type: string
        required: true
    handler:
      type: control_device

resources:
  - uri: loxone://rooms
    name: All Rooms
    description: List of all rooms
    mimeType: application/json
    handler:
      type: rooms_list
```

See `docs/DEVELOPER_GUIDE.md` for detailed documentation.

## 🧪 Development

```bash
# Full build
./gradlew build

# Run tests
./gradlew test

# Run in STDIO mode
./gradlew run --args="--stdio"

# Run in HTTP/SSE mode
./gradlew run --args="--sse 3001"

# Build distribution
./gradlew installDist
```

## 📄 License

This project is licensed under the BSD 3-Clause License - see the [LICENSE](LICENSE) file for details.

## 🔗 Related Projects

- [loxone-client-kotlin](https://github.com/Smarteon/loxone-client-kotlin) - Kotlin client library for Loxone Miniserver
- [Model Context Protocol](https://modelcontextprotocol.io/) - Protocol specification
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) - Kotlin SDK for MCP
