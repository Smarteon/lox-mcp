package cz.smarteon.loxmcp.loxonedocs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Parser for Loxone Structure File documentation PDF.
 *
 * This parser downloads and parses the official Loxone Structure File documentation
 * from https://www.loxone.com/wp-content/uploads/datasheets/StructureFile.pdf
 */
object LoxoneDocsParser {

    const val LOXONE_DOCS_URL = "https://www.loxone.com/wp-content/uploads/datasheets/StructureFile.pdf"
    private const val DOWNLOAD_TIMEOUT_SECONDS = 30L

    private enum class Section {
        NONE,
        COVERED_CONFIG,
        STATES,
        COMMANDS,
        DETAILS
    }

    /**
     * Downloads and parses the Loxone documentation from the official URL.
     * @param url The URL to download from (defaults to official Loxone docs)
     * @return List of parsed Loxone control definitions
     * @throws java.net.http.HttpTimeoutException if download times out
     * @throws IllegalStateException if HTTP response is not successful
     */
    fun parseFromUrl(url: String = LOXONE_DOCS_URL): List<LoxoneControl> {
        logger.info { "Downloading Loxone documentation from $url" }

        try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DOWNLOAD_TIMEOUT_SECONDS))
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DOWNLOAD_TIMEOUT_SECONDS))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            check(response.statusCode() == 200) {
                "Failed to download documentation: HTTP ${response.statusCode()}"
            }

            return response.body().use { inputStream ->
                parseFromStream(inputStream)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to download Loxone documentation from $url" }
            throw e
        }
    }

    /**
     * Parses Loxone documentation from an input stream.
     * The input stream is consumed but not closed by this method.
     */
    fun parseFromStream(inputStream: InputStream): List<LoxoneControl> {
        val text = extractText(inputStream)
        val cleaned = cleanText(text)
        return parseText(cleaned)
    }

    /**
     * Parses already extracted and cleaned text.
     */
    fun parseText(text: String): List<LoxoneControl> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val state = ParserState()

        // Use index-based loop for look-ahead capability
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val nextLine = lines.getOrNull(i + 1) ?: ""

            parseLine(line, nextLine, state)
            i++
        }

        state.finalizeCurrent()

        logger.info { "Parsed ${state.objects.size} Loxone controls" }
        return state.objects.toList()
    }

    private fun parseLine(line: String, nextLine: String, state: ParserState) {
        when {
            isControlHeader(line, nextLine) -> state.startNewControl(extractControlName(line))
            line == "Covered Config Items" -> state.switchSection(Section.COVERED_CONFIG)
            line == "States" || line == "@States" -> state.switchSection(Section.STATES)
            line == "Commands" -> state.switchSection(Section.COMMANDS)
            line == "Details" -> state.switchSection(Section.DETAILS)
            state.currentSection == Section.COVERED_CONFIG -> state.currentControl?.addCoveredConfigItem(line)
            state.currentSection in listOf(Section.STATES, Section.DETAILS) -> parseFieldLine(line, state)
            state.currentSection == Section.COMMANDS -> parseCommandLine(line, state)
        }
    }

    private fun parseFieldLine(line: String, state: ParserState) {
        when {
            isFieldName(line) -> {
                state.currentField?.let { state.currentControl?.addField(state.currentSection, it) }
                state.currentField = ControlFieldBuilder(line)
            }
            isEnumLine(line) -> {
                val (value, label) = parseEnumLine(line)
                state.currentField?.addEnumValue(value, label)
            }
            else -> state.currentField?.appendDescription(line)
        }
    }

    private fun parseCommandLine(line: String, state: ParserState) {
        when {
            isFieldName(line) -> {
                state.currentCommand?.let { state.currentControl?.addCommand(it) }
                state.currentCommand = ControlCommandBuilder(line)
            }
            isEffectLine(line) -> state.currentCommand?.addEffect(line.removePrefix("-").trim())
            else -> state.currentCommand?.appendDescription(line)
        }
    }

    private class ParserState {
        val objects = mutableListOf<LoxoneControl>()
        var currentControl: ControlBuilder? = null
        var currentSection = Section.NONE
        var currentField: ControlFieldBuilder? = null
        var currentCommand: ControlCommandBuilder? = null

        fun startNewControl(name: String) {
            finalizeCurrent()
            currentControl = ControlBuilder(name)
            currentSection = Section.NONE
            currentField = null
            currentCommand = null
            logger.debug { "Found control: $name" }
        }

        fun switchSection(section: Section) {
            currentField?.let { currentControl?.addField(currentSection, it) }
            currentCommand?.let { currentControl?.addCommand(it) }
            currentSection = section
            currentField = null
            currentCommand = null
        }

        fun finalizeCurrent() {
            currentField?.let { currentControl?.addField(currentSection, it) }
            currentCommand?.let { currentControl?.addCommand(it) }
            currentControl?.build()?.let { objects.add(it) }
        }
    }

    private fun extractText(inputStream: InputStream): String {
        return try {
            Loader.loadPDF(inputStream.readAllBytes()).use { doc ->
                PDFTextStripper().getText(doc)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to extract text from PDF" }
            throw IllegalStateException("Failed to parse Loxone documentation PDF", e)
        }
    }

    private fun cleanText(text: String): String {
        return text
            .lines()
            .filterNot { it.contains("Structure File Page") }
            .filterNot { it.contains("Structure File") && it.contains("Page") }
            .filterNot { it.matches(Regex("^\\d+\\.\\d+\\s*$")) } // Remove standalone section numbers
            .joinToString("\n")
            // Replace unicode bullets with simple markers
            .replace("—‹", "○")
            .replace("—", "●")
            .replace("–", "■")
            .replace("ƒ", "●")
            // Normalize bullets to dashes
            .replace(Regex("[●○■]"), "-")
            // Normalize whitespace but keep newlines
            .replace(Regex(" +"), " ")
    }

    /**
     * Determines if a line is a control type header.
     * Uses context (next line) and pattern matching to identify headers.
     */
    private fun isControlHeader(line: String, nextLine: String): Boolean {
        // Direct match with known multi-word headers
        if (isKnownMultiWordHeader(line)) return true

        // Filter out reserved keywords and invalid patterns
        if (isReservedKeyword(line)) return false
        if (isInvalidHeaderFormat(line)) return false

        // Single word PascalCase names are control headers
        if (isSingleWordHeader(line)) return true

        // Multi-word headers require additional validation
        return isValidMultiWordHeader(line, nextLine)
    }

    private fun isKnownMultiWordHeader(line: String): Boolean {
        val knownMultiWordHeaders = setOf(
            "Intelligent Room Controller v2",
            "Intelligent Room Controller",
            "NFC Code Touch",
            "UpDownLeftRight digital",
            "UpDownLeftRight analog",
            "Intelligent Room Controller Daytimer v2",
            "Intelligent Room Controller Daytimer",
            "Pool Daytimer"
        )
        return knownMultiWordHeaders.contains(line)
    }

    private fun isReservedKeyword(line: String): Boolean {
        val reservedKeywords = setOf(
            // Section markers
            "States", "Commands", "Details",
            "Covered Config Items", "Sub-Controls", "Subcontrols",
            "Binary Result", "Secured Details", "Control History",
            "Data Structure", "Trigger Types", "Control Types",
            "Mandatory fields", "Optional fields", "Info",
            "BMW Wallbox specific", "Manual Temperature Range in Schedule",
            "Locking and Unlocking Controls",
            // Document structure (not control types)
            "STRUCTURE FILE", "General Info", "Revision History",
            "Table of contents", "Central Objects",
            "Loxone Audioserver", "Miniserver Compact", "Loxone Music Server",
            "Status Monitor"
        )
        return reservedKeywords.contains(line)
    }

    private fun isInvalidHeaderFormat(line: String): Boolean {
        // Should not be empty or overly long
        if (line.isBlank() || line.length > 50) return true
        // Must start with uppercase letter
        if (!line.first().isUpperCase()) return true
        // Must not start with bullets or dashes
        if (startsWithBulletOrDash(line)) return true

        return false
    }

    private fun startsWithBulletOrDash(line: String): Boolean {
        return line.startsWith("-") ||
               line.startsWith("—") ||
               line.startsWith("ƒ") ||
               line.startsWith("–")
    }

    private fun isSingleWordHeader(line: String): Boolean {
        return line.matches(Regex("^[A-Z][a-zA-Z0-9]+$"))
    }

    private fun isValidMultiWordHeader(line: String, nextLine: String): Boolean {
        if (!line.contains(" ")) return false

        // Must match pattern: starts with capital, contains only letters/numbers/spaces/v
        if (!line.matches(Regex("^[A-Z][a-zA-Z0-9 v]+$"))) return false

        // Exclude description text patterns
        if (looksLikeDescriptionText(line)) return false

        // If next line indicates this is a header, accept it
        if (nextLineIndicatesHeader(nextLine)) return true

        // Accept if it has multiple capital letters (typical of control names)
        val capitalCount = line.count { it.isUpperCase() }
        return capitalCount >= 2 && line.length < 40
    }

    private fun looksLikeDescriptionText(line: String): Boolean {
        val descriptionPatterns = listOf(
            "available since", "since Miniserver", "since Config",
            "The daytimer", "This control", "Please note",
            "Possible values", "Example", "Available",
            "Depending on", "Structure description",
            "If a mood", "When wanting", "Only when",
            "Activates", "Deactivates", "Changes to",
            "which", "that", "are", "can be", "is a", "will"
        )
        return descriptionPatterns.any { line.contains(it, ignoreCase = true) }
    }

    private fun nextLineIndicatesHeader(nextLine: String): Boolean {
        val headerFollowedByPatterns = listOf(
            "available since",
            "Covered Config Items",
            "States",
            "Commands",
            "Details",
            "Info"
        )
        return headerFollowedByPatterns.any { nextLine.startsWith(it, ignoreCase = true) }
    }

    private fun extractControlName(line: String): String = line.trim()

    private fun isFieldName(line: String): Boolean {
        if (!line.startsWith("- ") || isEnumLine(line)) return false
        val name = line.removePrefix("- ").trim()
        return name.isNotBlank() &&
            !name.contains(" ") &&
            name.length < 50 &&
            name.first().isLowerCase()
    }

    private fun isEnumLine(line: String): Boolean {
        return line.matches(Regex("^-?\\s*\\d+\\s*=.*"))
    }

    private fun parseEnumLine(line: String): Pair<Int, String> {
        val cleaned = line.removePrefix("-").trim()
        val parts = cleaned.split("=", limit = 2)
        return Pair(parts[0].trim().toInt(), parts[1].trim())
    }

    private fun isEffectLine(line: String): Boolean {
        return line.startsWith("- ") && line.contains(" to ")
    }

    /**
     * Finds a control by name (case-insensitive).
     */
    fun List<LoxoneControl>.findByName(name: String): LoxoneControl? {
        return find { it.name.equals(name, ignoreCase = true) }
    }

    private class ControlBuilder(val name: String) {
        private val coveredConfigItems = mutableListOf<String>()
        private val states = mutableListOf<ControlField>()
        private val commands = mutableListOf<ControlCommand>()
        private val details = mutableListOf<ControlField>()

        fun addCoveredConfigItem(item: String) {
            coveredConfigItems.add(item.removePrefix("-").trim())
        }

        fun addField(section: Section, field: ControlFieldBuilder) {
            val builtField = field.build()
            when (section) {
                Section.STATES -> states.add(builtField)
                Section.DETAILS -> details.add(builtField)
                else -> {}
            }
        }

        fun addCommand(command: ControlCommandBuilder) {
            commands.add(command.build())
        }

        fun build(): LoxoneControl = LoxoneControl(
            name = name,
            coveredConfigItems = coveredConfigItems.toList(),
            states = states.toList(),
            commands = commands.toList(),
            details = details.toList()
        )
    }

    private class ControlFieldBuilder(name: String) {
        val name: String = name.removePrefix("- ").trim()
        private val descriptionParts = mutableListOf<String>()
        private val enumValues = mutableMapOf<Int, String>()

        fun appendDescription(text: String) {
            val cleaned = text.removePrefix("-").trim()
            if (cleaned.isNotBlank()) {
                descriptionParts.add(cleaned)
            }
        }

        fun addEnumValue(value: Int, label: String) {
            enumValues[value] = label
        }

        fun build(): ControlField = ControlField(
            name = name,
            description = descriptionParts.joinToString(" "),
            enumValues = enumValues.takeIf { it.isNotEmpty() }
        )
    }

    private class ControlCommandBuilder(name: String) {
        val name: String = name.removePrefix("- ").trim()
        private val descriptionParts = mutableListOf<String>()
        private val effects = mutableListOf<String>()

        fun appendDescription(text: String) {
            val cleaned = text.removePrefix("-").trim()
            if (cleaned.isNotBlank() && !cleaned.contains(" to ")) {
                descriptionParts.add(cleaned)
            }
        }

        fun addEffect(effect: String) {
            effects.add(effect)
        }

        fun build(): ControlCommand = ControlCommand(
            name = name,
            description = descriptionParts.joinToString(" "),
            effects = effects.toList()
        )
    }
}
