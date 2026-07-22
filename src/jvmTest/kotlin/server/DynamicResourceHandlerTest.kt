package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.ResourceConfig
import cz.smarteon.loxmcp.config.ResourceHandler
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.mockk.coEvery
import io.mockk.mockk

class DynamicResourceHandlerTest : ShouldSpec({

    fun resultText(result: io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult) =
        (result.contents.first() as TextResourceContents).text

    context("parsePhysicalDeviceXml via handlePhysicalDevices") {
        val adapter = mockk<LoxoneAdapter>()
        val handler = DynamicResourceHandler(
            adapter,
            ResourceConfig(
                uri = "loxone://devices",
                name = "Physical Devices",
                description = "Physical devices",
                mimeType = "application/json",
                handler = ResourceHandler(type = "physical_devices")
            )
        )

        val sampleXml = """<?xml version="1.0"?>
            <Status>
                <Miniserver Name="TestMS" Offline="false">
                    <TreeBranch Branch="3" Name="Tree" Serial="13000001" Version="15.0.0.1" Devices="2" Errors="0">
                        <TreeDevice Name="OnlineDevice" Serial="AA000001" Version="1.0.0.0" Online="true" HwVersion="1" DummyDev="false"/>
                        <TreeDevice Name="OfflineDevice" Serial="AA000002" Version="1.0.0.0" Online="false" HwVersion="1" DummyDev="false"/>
                    </TreeBranch>
                    <Link Branch="24" Name="Link" Type="Link">
                        <Extension Type="AO Extension" Name="AO Ext" Serial="BBBBBBBB" Version="15.0.0.1" Online="false"/>
                    </Link>
                </Miniserver>
            </Status>"""

        should("return device names and serials") {
            coEvery { adapter.getPhysicalDevices() } returns sampleXml
            val text = resultText(handler.handle("loxone://devices"))
            text shouldContain "OnlineDevice"
            text shouldContain "OfflineDevice"
            text shouldContain "AA000001"
            text shouldContain "AO Ext"
        }

        should("attach parent context to child devices") {
            coEvery { adapter.getPhysicalDevices() } returns sampleXml
            val text = resultText(handler.handle("loxone://devices"))
            text shouldContain "Tree"       // parentName for TreeDevices
            text shouldContain "13000001"   // parentSerial
        }

        should("online_only=true keeps only Online=true devices") {
            coEvery { adapter.getPhysicalDevices() } returns sampleXml
            val text = resultText(handler.handle("loxone://devices?online_only=true"))
            text shouldContain "OnlineDevice"
            text shouldNotContain "OfflineDevice"
            text shouldNotContain "AO Ext"
        }

        should("name filter is case-insensitive") {
            coEvery { adapter.getPhysicalDevices() } returns sampleXml
            val text = resultText(handler.handle("loxone://devices?name=online"))
            text shouldContain "OnlineDevice"
            text shouldNotContain "AO Ext"
        }
    }

    context("handleSystemStatus") {
        val adapter = mockk<LoxoneAdapter>()
        val handler = DynamicResourceHandler(
            adapter,
            ResourceConfig(
                uri = "loxone://system/status",
                name = "System Status",
                description = "System status",
                mimeType = "application/json",
                handler = ResourceHandler(type = "system_status")
            )
        )

        fun mockAll(state: String, cpu: String, heap: String) {
            coEvery { adapter.sendRawCommand("dev/sps/state") } returns "<LL value=\"$state\"/>"
            coEvery { adapter.sendRawCommand("dev/sys/cpu") } returns "<LL value=\"$cpu\"/>"
            coEvery { adapter.sendRawCommand("dev/sys/heap") } returns "<LL value=\"$heap\"/>"
            coEvery { adapter.sendRawCommand("dev/sps/status") } returns "<LL value=\"ok\"/>"
            coEvery { adapter.sendRawCommand("dev/cfg/version") } returns "<LL value=\"17.1.0\"/>"
            coEvery { adapter.sendRawCommand("dev/sys/time") } returns "<LL value=\"1234567890\"/>"
            coEvery { adapter.sendRawCommand("dev/cfg/ip") } returns "<LL value=\"192.168.1.1\"/>"
            coEvery { adapter.sendRawCommand("dev/cfg/dns1") } returns "<LL value=\"8.8.8.8\"/>"
            coEvery { adapter.sendRawCommand("dev/cfg/ntp") } returns "<LL value=\"pool.ntp.org\"/>"
        }

        should("extract value attributes and report healthy system") {
            mockAll("5", "42", "50000")
            val text = resultText(handler.handle("loxone://system/status"))
            text shouldContain "\"stateLabel\": \"running\""
            text shouldContain "\"healthy\": true"
            text shouldContain "\"cpuPercent\": 42"
            text shouldContain "\"cpuHealthy\": true"
            text shouldContain "\"heapBytes\": 50000"
            text shouldContain "\"heapHealthy\": true"
            text shouldContain "\"firmwareVersion\": \"17.1.0\""
            text shouldContain "192.168.1.1"
        }

        should("mark cpuHealthy=false when cpu >= 80") {
            mockAll("5", "80", "50000")
            val text = resultText(handler.handle("loxone://system/status"))
            text shouldContain "\"cpuHealthy\": false"
        }

        should("mark heapHealthy=false when heap <= 10000") {
            mockAll("5", "10", "9999")
            val text = resultText(handler.handle("loxone://system/status"))
            text shouldContain "\"heapHealthy\": false"
        }

        should("report healthy=false when plc state is not 5") {
            mockAll("7", "10", "50000")
            val text = resultText(handler.handle("loxone://system/status"))
            text shouldContain "\"stateLabel\": \"error\""
            text shouldContain "\"healthy\": false"
        }
    }

    context("handleSystemLog truncation") {
        val adapter = mockk<LoxoneAdapter>()
        val handler = DynamicResourceHandler(
            adapter,
            ResourceConfig(
                uri = "loxone://system/log",
                name = "System Log",
                description = "Log",
                mimeType = "text/plain",
                handler = ResourceHandler(type = "system_log")
            )
        )

        should("return only the last N lines") {
            coEvery { adapter.sendRawCommand("dev/fsget/log/def.log") } returns
                "line1\nline2\nline3\nline4\nline5"
            val text = resultText(handler.handle("loxone://system/log?lines=3"))
            text shouldBe "line3\nline4\nline5"
        }

        should("return all lines when lines param exceeds total") {
            coEvery { adapter.sendRawCommand("dev/fsget/log/def.log") } returns "line1\nline2"
            val text = resultText(handler.handle("loxone://system/log?lines=100"))
            text shouldBe "line1\nline2"
        }

        should("default to 200 lines when lines param is absent") {
            val manyLines = (1..250).joinToString("\n") { "line$it" }
            coEvery { adapter.sendRawCommand("dev/fsget/log/def.log") } returns manyLines
            val text = resultText(handler.handle("loxone://system/log"))
            text shouldBe (51..250).joinToString("\n") { "line$it" }
        }
    }
})
