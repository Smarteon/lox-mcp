package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxCC
import cz.smarteon.loxmcp.LoxoneAdapter
import cz.smarteon.loxmcp.LoxoneXmlProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import no.synth.kmpzip.zip.ZipInputStream

private val logger = KotlinLogging.logger {}

private const val PROJECT_PATH = "dev/fsget/prog/sps.LoxCC"
private const val LOXCC_EXTENSION = ".LoxCC"
private const val LOXONE_EXTENSION = ".Loxone"
internal const val SINGLE_FILE_NAME = "sps0$LOXONE_EXTENSION"
private val LOXCC_SUFFIX_REGEX = Regex("\\$LOXCC_EXTENSION\$", RegexOption.IGNORE_CASE)

/**
 * Handles the `get_loxone_xml` MCP tool.
 *
 * Fetches the Loxone project file from the configured Miniserver, decompresses
 * it from the proprietary LoxCC binary format (or extracts a ZIP for chained
 * Miniservers), sanitises known XML quirks, and strips UI-only elements and
 * attributes to keep the result within LLM context limits.
 */
internal class GetLoxoneXmlHandler(private val adapter: LoxoneAdapter) {

    suspend fun handle(): CallToolResult {
        return try {
            logger.info { "Fetching Loxone project XML" }
            val rawBytes = adapter.fetchRawBytes(PROJECT_PATH)

            val xmlFiles: Map<String, String> = if (isZip(rawBytes)) {
                logger.debug { "Detected ZIP — multi-Miniserver chain" }
                extractLoxCCsFromZip(rawBytes, LOXCC_EXTENSION)
                    .mapKeys { (name, _) -> name.replace(LOXCC_SUFFIX_REGEX, LOXONE_EXTENSION) }
                    .mapValues { (_, data) -> processLoxCC(data) }
            } else {
                logger.debug { "Detected single LoxCC file" }
                mapOf(SINGLE_FILE_NAME to processLoxCC(rawBytes))
            }

            val text = xmlFiles.entries.joinToString("\n\n") { (name, xml) ->
                "=== $name ===\n$xml"
            }

            CallToolResult(content = listOf(TextContent(text)), isError = false)

        } catch (e: LoxCC.DecompressException) {
            logger.error(e) { "LoxCC decompression failed" }
            CallToolResult(
                content = listOf(TextContent("Decompression error: ${e.message}")),
                isError = true
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch Loxone project XML" }
            CallToolResult(
                content = listOf(TextContent("Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun processLoxCC(data: ByteArray): String {
        var xml = LoxCC.decompress(data).decodeToString()
        xml = LoxoneXmlProcessor.sanitize(xml)
        return LoxoneXmlProcessor.slim(xml)
    }

    /** ZIP magic: PK\x03\x04 */
    private fun isZip(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == 0x50.toByte() &&
            data[1] == 0x4B.toByte() &&
            data[2] == 0x03.toByte() &&
            data[3] == 0x04.toByte()

    private fun extractLoxCCsFromZip(data: ByteArray, extension: String): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(data).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name.endsWith(extension, ignoreCase = true)) {
                    result[entry.name] = zis.readBytes()
                }
            }
        }
        if (result.isEmpty()) throw LoxCC.DecompressException("No $extension files found in ZIP")
        return result
    }
}