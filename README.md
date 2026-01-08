# Loxone MCP Server

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server that connects AI assistants to Loxone Miniserver smart home systems.

[![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)](https://kotlinlang.org/)
[![MCP SDK](https://img.shields.io/badge/MCP_SDK-0.7.2-green.svg)](https://modelcontextprotocol.io/)

## ✨ Features

- 🔌 **Reliable Connection** - Connects to Loxone Miniserver via HTTP API
- 🔐 **Flexible Credentials** - Multiple credential sources (env vars, CLI args, Bitwarden)
- 📝 **Configuration-Driven** - Define tools and resources in YAML without code changes
- 🎯 **Dynamic Registration** - Tools and resources automatically loaded from configuration
- 🚀 **Dual Transport** - Support for both STDIO and HTTP/SSE modes
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
│   ├── Constants.kt                # Application constants (version, JSON config)
│   ├── LoxoneAdapter.kt            # Wraps Loxone HTTP client
│   ├── credentials/
│   │   ├── CredentialSource.kt     # Credential source interface and implementations
│   │   └── CredentialResolver.kt   # Resolves credentials from sources
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
