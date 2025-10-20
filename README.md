# Loxone MCP Server

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server that connects AI assistants to Loxone Miniserver smart home systems.

[![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)](https://kotlinlang.org/)
[![MCP SDK](https://img.shields.io/badge/MCP_SDK-0.7.2-green.svg)](https://modelcontextprotocol.io/)

## 🎯 Project Status: M0 (Minimal Viable Connection)

This is the **M0 milestone** release - a proof-of-concept that establishes basic connectivity between AI assistants and Loxone Miniserver. The primary goal is to validate the integration architecture.

### Current Capabilities
- ✅ Connect to Loxone Miniserver via HTTP API
- ✅ Basic authentication (username/password)
- ✅ Read Miniserver API version
- ✅ Expose system status as MCP resource
- ✅ Support both STDIO and HTTP/SSE transport modes

## 📋 Prerequisites

- **Java 17** or higher
- **Loxone Miniserver** (Gen 1, Gen 2)
- AI assistant that supports MCP (e.g., Claude Desktop, Cline)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/lox-mcp.git
cd lox-mcp
```

### 2. Configure Environment

Create a `.env` file from the example:

```bash
cp .env.example .env
```

Edit `.env` with your Loxone credentials:

```env
LOXONE_HOST=http://192.168.1.77
LOXONE_USER=your_username
LOXONE_PASS=your_password
```

> ⚠️ **Security Note**: The `.env` file contains sensitive credentials. It's already in `.gitignore` - never commit it to version control.

### 3. Build the Project

```bash
./gradlew build
```

On Windows:
```cmd
gradlew.bat build
```

### 4. Run the Server

#### STDIO Mode (for Claude Desktop, Cline, etc.)

```bash
./gradlew run --args="--stdio"
```

#### HTTP/SSE Mode (for testing or web clients)

```bash
./gradlew run --args="--sse 3001"
```

The server will start on `http://127.0.0.1:3001`

## 🔧 Configuration

### Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `LOXONE_HOST` | Yes | Miniserver URL or IP | `http://192.168.1.77` |
| `LOXONE_USER` | Yes | Loxone username | `admin` |
| `LOXONE_PASS` | Yes | Loxone password | `your_password` |

### MCP Client Configuration

#### Claude Desktop

Add to your Claude Desktop config file:

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "loxone": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/lox-mcp/build/libs/lox-mcp-0.1.0-SNAPSHOT.jar",
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

#### Cline (VS Code Extension)

Add to Cline MCP settings:

```json
{
  "loxone": {
    "command": "java",
    "args": ["-jar", "/path/to/lox-mcp/build/libs/lox-mcp-0.1.0-SNAPSHOT.jar", "--stdio"],
    "env": {
      "LOXONE_HOST": "http://192.168.1.77",
      "LOXONE_USER": "admin",
      "LOXONE_PASS": "password"
    }
  }
}
```

## 📚 Available MCP Features

### Tools

#### `loxone_get_api_version`
Get the API version of the Loxone Miniserver to verify connectivity.

**Parameters**: None

**Example Usage** (via AI assistant):
```
"Can you check if my Loxone system is online?"
```

### Resources

#### `loxone://status`
Read-only resource providing Miniserver connection status and API information.

**Example Usage** (via AI assistant):
```
"Show me my Loxone system status"
```

## 🏗️ Project Structure

```
lox-mcp/
├── src/main/kotlin/cz/smarteon/lox/mcp/
│   ├── Application.kt              # Main entry point, server initialization
│   ├── loxone/
│   │   └── LoxoneAdapter.kt        # Loxone Miniserver client wrapper
│   └── mcp/
│       ├── McpServer.kt            # MCP server setup (STDIO & HTTP/SSE)
│       ├── ToolsRegistry.kt        # MCP tools registration
│       └── ResourcesRegistry.kt    # MCP resources registration
├── build.gradle.kts                # Gradle build configuration
├── gradle/libs.versions.toml       # Dependency version catalog
└── .env.example                    # Environment variables template
```

## 🔨 Development

### Building from Source

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Creating Distribution

```bash
./gradlew installDist
```

The distribution will be available in `build/install/lox-mcp/`

## 🧪 Testing the Server

### Manual Testing (HTTP/SSE Mode)

1. Start the server in HTTP mode:
```bash
./gradlew run --args="--sse 3001"
```

2. The MCP server exposes standard MCP endpoints at `http://127.0.0.1:3001`

3. You can test with MCP-compatible clients or tools

### Testing with AI Assistant

After configuring Claude Desktop or Cline:

1. Restart your AI assistant
2. Ask: *"Can you check my Loxone system status?"*
3. The assistant should use the `loxone_get_api_version` tool

## 🛠️ Technologies Used

- **[Kotlin](https://kotlinlang.org/)** 2.2.20 - Modern JVM language
- **[Ktor](https://ktor.io/)** 3.3.0 - HTTP server framework
- **[MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk)** 0.7.2 - Model Context Protocol implementation
- **[loxone-client-kotlin](https://github.com/Smarteon/loxone-client-kotlin)** 0.5.1 - Loxone API client library
- **[Gradle](https://gradle.org/)** - Build system with Kotlin DSL

## 📄 License

This project is licensed under the BSD 3-Clause License - see the [LICENSE](LICENSE) file for details.
