# Setup Guide

Detailed setup and configuration reference for lox-mcp.

## Credentials

The server resolves credentials in this priority order: **Bitwarden → CLI args → environment variables**.

### Environment Variables (recommended)

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

### CLI Arguments

```bash
java -jar lox-mcp-all.jar --stdio \
  --address "http://192.168.1.77" \
  --username "your_username" \
  --password "your_password"
```

### Bitwarden

Set `BW_SERVER_ITEM_ID` to a Bitwarden item ID. The server will fetch credentials from the Bitwarden CLI at startup. CLI must be unlocked (`bw unlock`).

## Transport Modes

### STDIO (recommended)

Used by Claude Desktop, Cursor, VS Code, and most desktop clients.

```bash
java -jar lox-mcp-all.jar --stdio
```

### HTTP/SSE

Used by web-based or remote clients.

```bash
java -jar lox-mcp-all.jar --sse 3001
```

### Streamable HTTP

```bash
java -jar lox-mcp-all.jar --http 3001
```

## Resources as Tools

Many MCP clients have limited or no resource support. Use `--resources-as-tools` to expose all resources as callable tools (e.g., `loxone://rooms` becomes `get_rooms_list`).

```bash
java -jar lox-mcp-all.jar --stdio --resources-as-tools
```

## Client Configuration

### Claude Desktop

`~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

```json
{
  "mcpServers": {
    "loxone": {
      "command": "java",
      "args": ["-jar", "/path/to/lox-mcp-all.jar", "--stdio", "--resources-as-tools"],
      "env": {
        "LOXONE_HOST": "http://192.168.1.77",
        "LOXONE_USER": "your_username",
        "LOXONE_PASS": "your_password"
      }
    }
  }
}
```

### Cursor

`~/.cursor/mcp.json` — same `mcpServers` format as above.

### VS Code / GitHub Copilot

`~/.vscode/mcp.json`:

```json
{
  "servers": {
    "loxone": {
      "command": "java",
      "args": ["-jar", "/path/to/lox-mcp-all.jar", "--stdio", "--resources-as-tools"],
      "env": {
        "LOXONE_HOST": "http://192.168.1.77",
        "LOXONE_USER": "your_username",
        "LOXONE_PASS": "your_password"
      }
    }
  }
}
```

### OpenCode

`~/.config/opencode/opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "loxone": {
      "type": "local",
      "command": ["java", "-jar", "/path/to/lox-mcp-all.jar", "--stdio", "--resources-as-tools"],
      "environment": {
        "LOXONE_HOST": "http://192.168.1.77",
        "LOXONE_USER": "your_username",
        "LOXONE_PASS": "your_password"
      }
    }
  }
}
```

### Zed

`~/.config/zed/settings.json` — uses `context_servers` key with the standard `command`/`args`/`env` format.

### Continue.dev

`~/.continue/mcpServers/loxone.json` — standard `mcpServers` format.

### Windsurf

`~/.codeium/windsurf/mcp_config.json` — standard `mcpServers` format.

## Custom Configuration Files

You can extend or override the built-in tool/resource configuration using a YAML file.

### Merge mode (default) — adds to internal config

```bash
java -jar lox-mcp-all.jar --stdio -c /path/to/custom-config.yaml
```

### Override mode — replaces internal config entirely

```bash
java -jar lox-mcp-all.jar --stdio -c /path/to/custom-config.yaml -o
```

### Config file format

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

## Building from Source

```bash
git clone https://github.com/Smarteon/lox-mcp.git
cd lox-mcp

# Build
./gradlew build

# Run tests
./gradlew test

# Run in STDIO mode
./gradlew run --args="--stdio"

# Build distribution JAR
./gradlew installDist
```

Requires Java 21+.

## Project Structure

```
lox-mcp/
├── src/main/kotlin/
│   ├── Application.kt                 # Entry point, CLI parsing
│   ├── LoxoneAdapter.kt               # Loxone HTTP/WebSocket client wrapper
│   ├── credentials/                   # Credential sources and resolver
│   ├── config/                        # YAML config loading and models
│   ├── loxonedocs/                    # Built-in Loxone docs provider
│   └── server/                        # MCP server, tool/resource registries
├── src/main/resources/
│   └── mcp-config.yaml                # Default tools and resources
├── loxone-docs/                       # Pre-parsed Structure File docs (CI-generated)
├── configurator/                      # Tauri desktop configurator app
└── docs/                              # Additional documentation
```

For developer internals see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).
