package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxkt.app.LoxoneApp
import cz.smarteon.loxkt.app.StatisticEntry
import cz.smarteon.loxkt.app.StatisticUnit
import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.config.ResourceConfig
import cz.smarteon.loxmcp.config.ResourceHandler
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant

class DynamicResourceHandlerStatisticsTest : ShouldSpec({

    val adapter = mockk<LoxoneAdapter>()
    val resourceConfig = ResourceConfig(
        uri = "loxone://statistics",
        name = "Statistics",
        description = "Statistics",
        mimeType = "application/json",
        handler = ResourceHandler(type = "statistics")
    )
    val handler = DynamicResourceHandler(adapter, resourceConfig)

    fun responseText(uri: String): String {
        val result = handler.parseQueryParamsList(uri.substringAfter("?", ""))
        return result.toString() // just for parseQueryParamsList unit tests
    }

    context("parseQueryParamsList") {
        should("return empty map for blank string") {
            handler.parseQueryParamsList("") shouldBe emptyMap()
        }

        should("parse a single key-value pair") {
            handler.parseQueryParamsList("foo=bar") shouldBe mapOf("foo" to listOf("bar"))
        }

        should("collect repeated keys into a list") {
            handler.parseQueryParamsList("uuid=aaa&uuid=bbb") shouldBe
                mapOf("uuid" to listOf("aaa", "bbb"))
        }

        should("parse multiple distinct keys") {
            handler.parseQueryParamsList("uuid=aaa&from=100&to=200") shouldBe
                mapOf("uuid" to listOf("aaa"), "from" to listOf("100"), "to" to listOf("200"))
        }

        should("URL-decode keys and values") {
            handler.parseQueryParamsList("my%20key=hello%20world") shouldBe
                mapOf("my key" to listOf("hello world"))
        }

        should("skip entries without an equals sign") {
            handler.parseQueryParamsList("noequalssign&uuid=aaa") shouldBe
                mapOf("uuid" to listOf("aaa"))
        }
    }

    context("handleStatistics - error cases") {
        suspend fun handle(uri: String): String {
            val result = handler.handle(uri)
            return (result.contents.first() as TextResourceContents).text
        }

        should("return error when no query parameters provided") {
            handle("loxone://statistics") shouldContain "No query parameters provided"
        }

        should("return error when uuid is missing") {
            handle("loxone://statistics?from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z") shouldContain "Missing required parameter: uuid"
        }

        should("return error when from is missing") {
            handle("loxone://statistics?uuid=abc&to=2024-11-22T00:00:00Z") shouldContain "Missing required parameter: from"
        }

        should("return error when to is missing") {
            handle("loxone://statistics?uuid=abc&from=2024-11-15T00:00:00Z") shouldContain "Missing required parameter: to"
        }

        should("return error on mismatched parameter counts") {
            val uri = "loxone://statistics?uuid=aaa&uuid=bbb&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z"
            handle(uri) shouldContain "Mismatched parameter counts"
        }

        should("return error for unknown unit value") {
            val uri = "loxone://statistics?uuid=abc&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z&unit=INVALID"
            handle(uri) shouldContain "Unknown unit"
        }

        should("return error for invalid from timestamp") {
            val app = mockk<LoxoneApp> { every { controls } returns emptyMap() }
            coEvery { adapter.getApp() } returns app

            handle("loxone://statistics?uuid=abc&from=notadate&to=2024-11-22T00:00:00Z") shouldContain "Invalid 'from' value"
        }

        should("return error for invalid to timestamp") {
            val app = mockk<LoxoneApp> { every { controls } returns emptyMap() }
            coEvery { adapter.getApp() } returns app

            handle("loxone://statistics?uuid=abc&from=2024-11-15T00:00:00Z&to=notadate") shouldContain "Invalid 'to' value"
        }
    }

    context("handleStatistics - successful cases") {
        suspend fun handle(uri: String): String {
            val result = handler.handle(uri)
            return (result.contents.first() as TextResourceContents).text
        }

        should("return control-not-found error in result when uuid does not match any control") {
            val app = mockk<LoxoneApp> { every { controls } returns emptyMap() }
            coEvery { adapter.getApp() } returns app

            val text = handle("loxone://statistics?uuid=missing-uuid&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z")

            text shouldContain "Control not found"
            text shouldContain "missing-uuid"
        }

        should("return statistics result for a known control") {
            val control = mockk<Control> {
                every { uuidAction } returns "ctrl-uuid"
                every { name } returns "Test Control"
                every { type } returns "Switch"
                every { statistic } returns null
                every { statisticV2 } returns null
            }
            val app = mockk<LoxoneApp> { every { controls } returns mapOf("ctrl-uuid" to control) }
            coEvery { adapter.getApp() } returns app
            coEvery {
                adapter.fetchControlStatistics(
                    "ctrl-uuid",
                    Instant.parse("2024-11-15T00:00:00Z"),
                    Instant.parse("2024-11-22T00:00:00Z"),
                    StatisticUnit.DAY
                )
            } returns emptyList()

            val text = handle("loxone://statistics?uuid=ctrl-uuid&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z")

            text shouldContain "ctrl-uuid"
            text shouldContain "Test Control"
            text shouldContain "entryCount"
        }

        should("respect the unit parameter") {
            val control = mockk<Control> {
                every { uuidAction } returns "ctrl-uuid"
                every { name } returns "Test Control"
                every { type } returns "Switch"
                every { statistic } returns null
                every { statisticV2 } returns null
            }
            val app = mockk<LoxoneApp> { every { controls } returns mapOf("ctrl-uuid" to control) }
            coEvery { adapter.getApp() } returns app
            coEvery {
                adapter.fetchControlStatistics(
                    "ctrl-uuid",
                    Instant.parse("2024-11-15T00:00:00Z"),
                    Instant.parse("2024-11-22T00:00:00Z"),
                    StatisticUnit.MONTH
                )
            } returns emptyList()

            val text = handle("loxone://statistics?uuid=ctrl-uuid&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z&unit=MONTH")

            text shouldContain "\"unit\": \"MONTH\""
        }

        should("handle multiple uuids in a single request") {
            val control1 = mockk<Control> {
                every { uuidAction } returns "uuid-1"
                every { name } returns "Control 1"
                every { type } returns "Switch"
                every { statistic } returns null
                every { statisticV2 } returns null
            }
            val control2 = mockk<Control> {
                every { uuidAction } returns "uuid-2"
                every { name } returns "Control 2"
                every { type } returns "Dimmer"
                every { statistic } returns null
                every { statisticV2 } returns null
            }
            val app = mockk<LoxoneApp> {
                every { controls } returns mapOf("uuid-1" to control1, "uuid-2" to control2)
            }
            coEvery { adapter.getApp() } returns app
            coEvery { adapter.fetchControlStatistics("uuid-1", any(), any(), any()) } returns emptyList()
            coEvery { adapter.fetchControlStatistics("uuid-2", any(), any(), any()) } returns emptyList()

            val text = handle(
                "loxone://statistics?uuid=uuid-1&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z" +
                    "&uuid=uuid-2&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z"
            )

            text shouldContain "uuid-1"
            text shouldContain "uuid-2"
        }

        should("unit matching is case-insensitive") {
            val app = mockk<LoxoneApp> { every { controls } returns emptyMap() }
            coEvery { adapter.getApp() } returns app

            // lowercase "day" should not produce an unknown-unit error
            val text = handle("loxone://statistics?uuid=x&from=2024-11-15T00:00:00Z&to=2024-11-22T00:00:00Z&unit=day")
            text shouldContain "Control not found"
        }
    }
})
