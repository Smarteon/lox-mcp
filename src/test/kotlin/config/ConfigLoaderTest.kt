package cz.smarteon.loxmcp.config

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files

class ConfigLoaderTest : ShouldSpec({

    context("loadFromResources") {
        should("load valid configuration from resources") {
            val config = ConfigLoader.load()

            config shouldNotBe null
            config.tools shouldHaveSize 5
            config.resources shouldHaveSize 12
        }
    }

    context("load from file") {
        should("load valid configuration from file") {
            val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
            tempFile.writeText(
                """
                tools:
                  - name: test_tool
                    description: Test tool
                    handler:
                      type: send_command
                resources: []
                """.trimIndent()
            )

            try {
                val config = ConfigLoader.load(customConfigPath = tempFile.absolutePath, overrideInternalConfig = true)

                config shouldNotBe null
                config.tools shouldHaveSize 1
                config.tools[0].name shouldBe "test_tool"
                config.tools[0].description shouldBe "Test tool"
                config.tools[0].handler.type shouldBe "send_command"
            } finally {
                tempFile.delete()
            }
        }

        should("return internal config when file not found") {
            val config = ConfigLoader.load("nonexistent-file.yaml")

            config shouldNotBe null
            config.tools shouldHaveSize 5
            config.resources shouldHaveSize 12
        }

        should("handle malformed YAML gracefully") {
            val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
            tempFile.writeText("{ invalid yaml content [[[")

            try {
                val config = shouldNotThrowAny {
                    ConfigLoader.load(customConfigPath = tempFile.absolutePath, overrideInternalConfig = true)
                }

                config shouldNotBe null
                config.tools shouldHaveSize 0
                config.resources shouldHaveSize 0
            } finally {
                tempFile.delete()
            }
        }
    }

    context("load with custom config") {
        should("load only internal config when no custom config provided") {
            val config = ConfigLoader.load()

            config shouldNotBe null
            config.tools shouldHaveSize 5
            config.resources shouldHaveSize 12
        }

        should("merge custom config with internal config") {
            val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
            tempFile.writeText(
                """
                tools:
                  - name: custom_tool
                    description: Custom tool
                    handler:
                      type: generic_command
                resources:
                  - uri: loxone://custom
                    name: Custom Resource
                    description: Custom resource
                    mimeType: application/json
                    handler:
                      type: custom_handler
                """.trimIndent()
            )

            try {
                val config = ConfigLoader.load(customConfigPath = tempFile.absolutePath, overrideInternalConfig = false)

                config shouldNotBe null
                config.tools shouldHaveSize 6  // 5 internal + 1 custom
                config.resources shouldHaveSize 13  // 12 internal + 1 custom

                // Verify custom tool is present
                config.tools.any { it.name == "custom_tool" } shouldBe true
                // Verify internal tools are still present
                config.tools.any { it.name == "send_control_command" } shouldBe true
            } finally {
                tempFile.delete()
            }
        }

        should("override internal config when override flag is true") {
            val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
            tempFile.writeText(
                """
                tools:
                  - name: custom_tool_only
                    description: Only custom tool
                    handler:
                      type: send_control_command
                resources:
                  - uri: loxone://custom_only
                    name: Only Custom Resource
                    description: Only custom resource
                    mimeType: application/json
                    handler:
                      type: custom_handler
                """.trimIndent()
            )

            try {
                val config = ConfigLoader.load(customConfigPath = tempFile.absolutePath, overrideInternalConfig = true)

                config shouldNotBe null
                config.tools shouldHaveSize 1
                config.resources shouldHaveSize 1

                config.tools[0].name shouldBe "custom_tool_only"
                config.resources[0].uri shouldBe "loxone://custom_only"
            } finally {
                tempFile.delete()
            }
        }

        should("custom tool with same name overrides internal tool") {
            val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
            tempFile.writeText(
                """
                tools:
                  - name: send_control_command
                    description: Overridden control command tool
                    handler:
                      type: custom_handler
                resources: []
                """.trimIndent()
            )

            try {
                val config = ConfigLoader.load(customConfigPath = tempFile.absolutePath, overrideInternalConfig = false)

                config shouldNotBe null
                config.tools shouldHaveSize 5  // Still 5 tools, one replaced

                val sendCommandTool = config.tools.find { it.name == "send_control_command" }
                sendCommandTool shouldNotBe null
                sendCommandTool?.description shouldBe "Overridden control command tool"
                sendCommandTool?.handler?.type shouldBe "custom_handler"
            } finally {
                tempFile.delete()
            }
        }

        should("custom resource with same URI overrides internal resource") {
            val tempFile = Files.createTempFile("test-config", ".yaml").toFile()
            tempFile.writeText(
                """
                tools: []
                resources:
                  - uri: loxone://rooms
                    name: Custom Rooms List
                    description: Overridden rooms list
                    mimeType: text/plain
                    handler:
                      type: custom_handler
                """.trimIndent()
            )

            try {
                val config = ConfigLoader.load(customConfigPath = tempFile.absolutePath, overrideInternalConfig = false)

                config shouldNotBe null
                config.resources shouldHaveSize 12

                val roomsResource = config.resources.find { it.uri == "loxone://rooms" }
                roomsResource shouldNotBe null
                roomsResource?.name shouldBe "Custom Rooms List"
                roomsResource?.handler?.type shouldBe "custom_handler"
            } finally {
                tempFile.delete()
            }
        }
    }

    context("YAML serialization") {
        should("correctly deserialize tool configuration") {
            val yaml = """
                tools:
                  - name: control_device
                    description: Control a specific device
                    parameters:
                      - name: device_id
                        type: string
                        description: Device UUID
                        required: true
                      - name: action
                        type: string
                        description: Action to perform
                        required: true
                        enum: ["On", "Off", "pulse"]
                    handler:
                      type: control_device
                resources: []
            """.trimIndent()

            val config = Yaml.default.decodeFromString(McpConfig.serializer(), yaml)

            config.tools shouldHaveSize 1
            config.tools[0].name shouldBe "control_device"
            config.tools[0].parameters shouldHaveSize 2
            config.tools[0].parameters[0].name shouldBe "device_id"
            config.tools[0].parameters[0].required shouldBe true
            config.tools[0].parameters[1].name shouldBe "action"
            config.tools[0].parameters[1].enum shouldNotBe null
        }

        should("correctly deserialize resource configuration") {
            val yaml = """
                tools: []
                resources:
                  - uri: loxone://rooms
                    name: All Rooms
                    description: List all rooms
                    mimeType: application/json
                    handler:
                      type: rooms_list
            """.trimIndent()

            val config = Yaml.default.decodeFromString(McpConfig.serializer(), yaml)

            config.resources shouldHaveSize 1
            config.resources[0].uri shouldBe "loxone://rooms"
            config.resources[0].name shouldBe "All Rooms"
            config.resources[0].mimeType shouldBe "application/json"
            config.resources[0].handler.type shouldBe "rooms_list"
        }

        should("handle optional parameters correctly") {
            val yaml = """
                tools:
                  - name: test_tool
                    description: Test
                    parameters:
                      - name: optional_param
                        type: string
                        description: Optional parameter
                        required: false
                        default: "default_value"
                    handler:
                      type: send_command
                resources: []
            """.trimIndent()

            val config = Yaml.default.decodeFromString(McpConfig.serializer(), yaml)

            config.tools[0].parameters[0].required shouldBe false
            config.tools[0].parameters[0].default shouldBe "default_value"
        }
    }
})
