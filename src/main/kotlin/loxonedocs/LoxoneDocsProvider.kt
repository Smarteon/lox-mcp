package cz.smarteon.loxmcp.loxonedocs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Provides access to parsed Loxone documentation data.
 * Caches the parsed documentation to avoid repeated downloads.
 */
object LoxoneDocsProvider {

    private val json = Json { prettyPrint = true }

    private var cachedControls: List<LoxoneControl>? = null

    /**
     * Gets all parsed controls, loading from URL if not cached.
     */
    fun getControls(): List<LoxoneControl> {
        return cachedControls ?: loadControls().also { cachedControls = it }
    }

    /**
     * Finds a control by name (case-insensitive).
     */
    fun findByName(name: String): LoxoneControl? {
        return with(LoxoneDocsParser) { getControls().findByName(name) }
    }

    /**
     * Clears the cache, forcing a reload on next access.
     */
    fun clearCache() {
        cachedControls = null
        logger.info { "Loxone documentation cache cleared" }
    }

    private fun loadControls(): List<LoxoneControl> {
        return try {
            logger.info { "Loading Loxone documentation from URL..." }
            LoxoneDocsParser.parseFromUrl()
        } catch (e: Exception) {
            logger.error(e) { "Failed to load Loxone documentation" }
            emptyList()
        }
    }

    /**
     * Handles documentation list resource requests.
     */
    fun handleControlsList(uri: String): ReadResourceResult {
        val controls = getControls()

        if (controls.isEmpty()) {
            return errorResult(uri, "Failed to load Loxone documentation or no controls found")
        }

        val summary: List<LoxoneControlSummary> = controls.map { obj ->
            LoxoneControlSummary(
                name = obj.name,
                statesCount = obj.states.size,
                commandsCount = obj.commands.size,
                detailsCount = obj.details.size
            )
        }

        val content = json.encodeToString(ListSerializer(LoxoneControlSummary.serializer()), summary)
        return successResult(uri, content)
    }

    /**
     * Handles control detail requests.
     */
    fun handleControlDetails(uri: String): ReadResourceResult {
        val controlName = uri.substringAfter("structure-file/object/")
        if (controlName.isBlank()) {
            return errorResult(uri, "Control name not found in URI")
        }

        val control = findByName(controlName)
            ?: return errorResult(uri, "Control not found: $controlName")

        val content = json.encodeToString(LoxoneControl.serializer(), control)
        return successResult(uri, content)
    }

    private fun successResult(uri: String, content: String) = ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                uri = uri,
                mimeType = "application/json",
                text = content
            )
        )
    )

    private fun errorResult(uri: String, message: String) = ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                uri = uri,
                mimeType = "text/plain",
                text = "Error: $message"
            )
        )
    )
}
