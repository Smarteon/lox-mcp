# GitHub Copilot Instructions for lox-mcp

## Project Overview

This is a Kotlin/JVM application implementing a Model Context Protocol (MCP) server that connects AI assistants to Loxone Miniserver smart home systems. The server exposes Loxone functionality through the standardized MCP interface, enabling AI assistants like Claude to interact with Loxone smart home devices.

**Key Characteristics:**
- Kotlin/JVM-based MCP server (not multiplatform)
- Uses Ktor server for HTTP/SSE transport
- Supports both STDIO and HTTP/SSE transport modes
- Integrates with Loxone Miniserver via loxone-client-kotlin library
- Exposes Loxone features as MCP tools and resources

## Conventional Commits

**ALWAYS** follow the Conventional Commits specification for all commit messages.

**Format:** `<type>: <description>`

**Required types:**
- `feat:` - New features
- `fix:` - Bug fixes
- `chore:` - Maintenance tasks (dependencies, build config)
- `docs:` - Documentation changes
- `test:` - Test additions or modifications
- `refactor:` - Code refactoring without changing functionality
- `style:` - Code style changes (formatting, missing semi-colons, etc.)
- `perf:` - Performance improvements
- `ci:` - CI/CD pipeline changes
- `build:` - Build system changes

**Examples:**
- `feat: add tool for controlling Loxone outputs`
- `fix: handle connection timeout gracefully`
- `chore: upgrade kotlin to latest version`
- `docs: update README with configuration examples`

## Build System

### Gradle with Kotlin DSL

The project uses Gradle with Kotlin DSL (`.gradle.kts` files) and modern Gradle features:

**Gradle Version Catalog:** Dependencies are managed via `gradle/libs.versions.toml`
- Do NOT hardcode version numbers in build files
- Add new dependencies to the version catalog first
- Use `libs.` prefix to reference catalog dependencies

**JVM Toolchain:** Project requires JDK 17 for builds and runtime
- Build requires: JDK 17
- Runtime target: JVM 17
- Application packaged as executable JAR

### Common Build Commands

```bash
# Full build
./gradlew clean build

# Run tests
./gradlew test

# Run the application in STDIO mode
./gradlew run --args="--stdio"

# Run the application in HTTP/SSE mode
./gradlew run --args="--sse 3001"

# Create distribution
./gradlew installDist

# Build executable JAR
./gradlew jar
```

### Source Structure

**Single JVM target:**
- `src/main/kotlin` - Application source code
- `src/test/kotlin` - Test code (if present)

**Package structure:**
- `cz.smarteon.lox.mcp` - Main package
- `cz.smarteon.lox.mcp.loxone` - Loxone integration adapters
- `cz.smarteon.lox.mcp.mcp` - MCP server implementation (tools, resources, server setup)

## Testing Approach

### Testing Framework: Kotest

The project uses **Kotest** as the testing framework when tests are present.

**Preferred Test Style:** `ShouldSpec`
```kotlin
class MyTest : ShouldSpec({
    context("given some context") {
        should("test something") {
            // test code
        }
    }
})
```

**Common Test Patterns:**
- Use `should` for test cases, not `test` or other keywords
- Use `context` for grouping related tests
- Use Kotest matchers: `shouldBe`, `shouldNotBeNull`, `shouldBeGreaterThan`, etc.

### Coroutines Testing

- Use `kotlinx-coroutines-test` for testing async code when needed
- Tests are suspending functions by nature in Kotest

## Code Style and Linting

### Kotlin Code Conventions

- Use Kotlin idiomatic code style
- Prefer Kotlin stdlib functions over manual implementations
- Use Kotlin coroutines for async operations
- Use data classes for DTOs and messages
- Use sealed classes/interfaces for type hierarchies
- Prefer immutable data structures

### Serialization

- Use **kotlinx.serialization** for JSON serialization
- Annotate serializable classes with `@Serializable`
- Custom serializers should inherit from `KSerializer`
- Use `@SerialName` for JSON field mapping when needed

## Architecture Patterns

### MCP Server Architecture

The application implements the Model Context Protocol server pattern:

**Transport Modes:**
- **STDIO**: Standard input/output for local AI assistant integration (Claude Desktop, Cline)
- **HTTP/SSE**: HTTP server with Server-Sent Events for web-based clients

**MCP Components:**
- **Tools** (`ToolsRegistry.kt`): Exposed MCP tools that AI assistants can call
- **Resources** (`ResourcesRegistry.kt`): Static or dynamic resources AI assistants can read
- **Server** (`McpServer.kt`): Core MCP server setup for both transport modes

### Loxone Integration

**LoxoneAdapter Pattern:**
- `LoxoneAdapter` wraps the loxone-client-kotlin library
- Provides high-level interface for MCP server to interact with Loxone
- Handles connection management and resource cleanup

### Ktor Server Usage

HTTP/SSE mode uses Ktor server:
```kotlin
embeddedServer(Netty, port = port) {
    install(ContentNegotiation) {
        json()
    }
    // MCP server routes
}
```

### Configuration

**Environment Variables:**
- `LOXONE_HOST` - Loxone Miniserver URL (required)
- `LOXONE_USER` - Loxone username (required)
- `LOXONE_PASS` - Loxone password (required)

All configuration is loaded from environment variables at startup.

## Dependencies Management

### Core Dependencies

- **Kotlin Standard Library** - Core language features
- **Ktor Server** - HTTP/SSE server implementation
- **MCP Kotlin SDK** - Model Context Protocol implementation
- **loxone-client-kotlin** - Loxone Miniserver client library
- **kotlinx.serialization** - JSON serialization
- **kotlinx.coroutines** - Async programming
- **kotlin-logging** - Logging facade
- **slf4j-simple** - Simple logging implementation

### Test Dependencies

- **Kotest** - Testing framework
- **ktor-server-test-host** - Testing Ktor applications

### Adding Dependencies

1. Add version to `[versions]` section in `gradle/libs.versions.toml`
2. Add library to `[libraries]` section
3. Reference in `build.gradle.kts` dependencies block using `libs.` prefix
4. Since this is JVM-only, add directly to main dependencies section

**Example:**
```kotlin
// In gradle/libs.versions.toml
[versions]
newlib = "1.2.3"

[libraries]
newlib-core = { module = "com.example:newlib", version.ref = "newlib" }

// In build.gradle.kts
dependencies {
    implementation(libs.newlib.core)
}
```

## MCP Development Guidelines

### Adding New Tools

When adding new MCP tools:

1. Define the tool in `ToolsRegistry.kt`
2. Implement the tool handler with proper error handling
3. Use descriptive names and provide clear descriptions
4. Define input schema using JSON Schema
5. Return structured responses

**Tool Pattern:**
```kotlin
tool(
    name = "tool_name",
    description = "Clear description of what the tool does",
    inputSchema = /* JSON Schema */
) { request ->
    // Implementation
    CallToolResult.success(result)
}
```

### Adding New Resources

When adding new MCP resources:

1. Define the resource in `ResourcesRegistry.kt`
2. Provide descriptive URI scheme (e.g., `loxone://resource`)
3. Implement resource content provider
4. Support both text and binary content as appropriate

**Resource Pattern:**
```kotlin
resource(
    uri = "loxone://resource",
    name = "Resource Name",
    description = "Resource description",
    mimeType = "application/json"
) {
    // Return resource content
}
```

### Error Handling

- Always handle errors gracefully in tool and resource handlers
- Log errors appropriately using kotlin-logging
- Return meaningful error messages to the AI assistant
- Use proper exception types

## Application Lifecycle

### Startup

1. Parse command-line arguments (--stdio or --sse with port)
2. Load environment variables for Loxone configuration
3. Initialize LoxoneAdapter
4. Register shutdown hook for graceful cleanup
5. Start MCP server in appropriate mode

### Shutdown

- Shutdown hook ensures LoxoneAdapter is properly closed
- Connection to Loxone Miniserver is gracefully terminated
- Resources are cleaned up

## Documentation

- Use KDoc for public API documentation
- Follow Kotlin documentation conventions
- Keep README.md updated with:
  - Configuration instructions
  - Available tools and resources
  - Integration examples
  - Troubleshooting guides

## Security Considerations

- **Never commit credentials** - Use environment variables only
- `.env` files must be in `.gitignore`
- Validate all environment variables at startup
- Log security-relevant events appropriately
- Handle authentication errors gracefully

## Common Pitfalls

1. **Don't add dependencies without using version catalog**
2. **Don't hardcode configuration** - Always use environment variables
3. **Don't use blocking code** - Use coroutines for async operations
4. **Don't forget error handling** - MCP tools must handle errors gracefully
5. **Don't skip graceful shutdown** - Always clean up resources properly
6. **Don't expose sensitive data in logs** - Mask credentials and tokens

## Project Patterns to Follow

Since this project is part of the Smarteon ecosystem (like loxone-client-kotlin):

- **Package naming**: Start with `cz.smarteon.lox.mcp`
- **Code style**: Follow Kotlin conventions and idiomatic patterns
- **Commit messages**: Use conventional commits format
- **Documentation**: Keep documentation clear and updated
- **Dependencies**: Use version catalog for all dependencies
- **Testing**: Use Kotest when adding tests
