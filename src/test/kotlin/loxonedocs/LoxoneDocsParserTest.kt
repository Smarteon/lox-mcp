package cz.smarteon.loxmcp.loxonedocs

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.net.HttpURLConnection
import java.net.URI
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for LoxoneDocs parser.
 *
 * Note: These tests require internet connectivity and will be skipped
 * if the Loxone documentation URL is not reachable.
 */
class LoxoneDocsParserTest : ShouldSpec({

    should("parse controls from URL").config(
        timeout = 60.seconds,
        enabled = isDocumentationAvailable()
    ) {
        val controls = LoxoneDocsParser.parseFromUrl()

        controls.shouldNotBeEmpty()
        controls.size shouldBeGreaterThan 90

        // Verify multi-word controls are present
        val intelligentRoomController = with(LoxoneDocsParser) {
            controls.findByName("Intelligent Room Controller v2")
        }
        intelligentRoomController.shouldNotBeNull()
        intelligentRoomController.states.shouldNotBeEmpty()
        intelligentRoomController.commands.shouldNotBeEmpty()
    }

    should("provider caches controls").config(
        enabled = isDocumentationAvailable()
    ) {
        LoxoneDocsProvider.clearCache()

        val controls1 = LoxoneDocsProvider.getControls()
        val controls2 = LoxoneDocsProvider.getControls()

        controls1.size shouldBe controls2.size
    }
})

/**
 * Checks if the Loxone documentation URL is reachable.
 * Used to conditionally enable tests that require internet connectivity.
 */
private fun isDocumentationAvailable(): Boolean {
    return try {
        val connection = URI.create(LoxoneDocsParser.LOXONE_DOCS_URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "HEAD"
        connection.connect()
        val available = connection.responseCode == 200
        connection.disconnect()
        available
    } catch (e: Exception) {
        println("Skipping Loxone documentation tests - URL not reachable: ${e.message}")
        false
    }
}
