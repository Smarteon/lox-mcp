# Loxone MCP Server

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server that connects AI assistants to Loxone Miniserver smart home systems.

[![License](https://img.shields.io/badge/License-AGPL_3.0-blue.svg)](LICENSE)
[![Commercial License](https://img.shields.io/badge/Commercial_License-Available-red.svg)](COMMERCIAL_LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)](https://kotlinlang.org/)
[![MCP SDK](https://img.shields.io/badge/MCP_SDK-0.7.2-green.svg)](https://modelcontextprotocol.io/)

## What it does

- Control Loxone devices (lights, blinds, scenes, …) from any MCP-capable AI assistant
- Read real-time device states via WebSocket
- Browse built-in Loxone structure file documentation
- Works with Claude Desktop, Cursor, VS Code/Copilot, OpenCode, Zed, and more

## Prerequisites

- **Java 21+**
- **Loxone Miniserver** (Gen 1 or Gen 2)
- An AI assistant that supports MCP

## Quick Start

### Option A — Configurator (recommended for non-technical users)

Download the pre-built desktop app from the [Releases](https://github.com/Smarteon/lox-mcp/releases) page (`configurator-v*` tag). It will check Java, download the JAR, and configure your MCP clients automatically.

> **macOS:** The DMG is not code-signed. On first open, right-click the app → **Open**, then confirm. Or run `xattr -cr /Applications/Lox-MCP\ Configurator.app`.

### Option B — Manual JAR

1. Download the latest `lox-mcp-*-all.jar` from [Releases](https://github.com/Smarteon/lox-mcp/releases).
2. Set credentials as environment variables:
   ```bash
   export LOXONE_HOST=http://192.168.1.77
   export LOXONE_USER=your_username
   export LOXONE_PASS=your_password
   ```
3. Add to your MCP client config:
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

> Use `--resources-as-tools` if your client has limited resource support (recommended for most clients).

For HTTP/SSE transport, custom config files, Bitwarden credentials, and per-client setup details see [docs/SETUP.md](docs/SETUP.md).

## Available Tools

| Tool | Description |
|------|-------------|
| `control_device` | Control a device by UUID (on, off, toggle, up, down, stop) |
| `control_devices_by_room` | Control all devices in a room (optional type filter) |
| `control_devices_by_type` | Control all devices of a specific type system-wide |
| `control_devices_by_category` | Control all devices in a category |
| `send_command` | Send a raw Loxone command |

## Available Resources

| URI | Description |
|-----|-------------|
| `loxone://structure/summary` | Overview of rooms, devices, and categories |
| `loxone://rooms` | All rooms with device counts |
| `loxone://rooms/{roomName}/devices` | Devices in a specific room |
| `loxone://devices/all` | All devices |
| `loxone://devices/type/{type}` | Devices by type (Switch, Dimmer, …) |
| `loxone://devices/category/{name}` | Devices by category |
| `loxone://devices/states` | Real-time state of all devices |
| `loxone://devices/{uuid}/state` | State of a specific device |
| `loxone://docs` | Loxone docs table of contents |
| `loxone://docs/controls` | All documented control types |
| `loxone://docs/topic/{name}` | Full docs for a control or section |

## License

Dual-licensed under **AGPL-3.0** (open source) and a **commercial license** for proprietary use.

- Free for personal, internal, and open-source use under AGPL-3.0 terms.
- For closed-source or SaaS products, contact **info@smarteon.cz** or see [COMMERCIAL_LICENSE](COMMERCIAL_LICENSE).

## Related Projects

- [loxone-client-kotlin](https://github.com/Smarteon/loxone-client-kotlin) — Kotlin client library for Loxone Miniserver
- [Model Context Protocol](https://modelcontextprotocol.io/) — Protocol specification
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) — Kotlin SDK for MCP
