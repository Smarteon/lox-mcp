package cz.smarteon.loxmcp.server

import cz.smarteon.loxmcp.LoxCC
import cz.smarteon.loxmcp.LoxoneAdapter
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal valid XML used as test LoxCC payload. */
private const val TEST_XML = "<ControlList/>"

class LoxCCBackrefTest : ShouldSpec({

    context("LoxCC.decompress() - back-reference handling") {

        should("correctly decompress data encoded with a back-reference") {
            val literal = "ABCDEFGH".toByteArray(Charsets.UTF_8) // 8 bytes
            // Packet: litCount=8 in upper nibble (0x80), matchNibble=0 in lower nibble
            // → matchLen = 4 + 0 = 4
            // Back-reference distance = 8 (copy from start), so copies "ABCD"
            val matchNibble = 0
            val litCount = literal.size // 8 < 15, fits in upper nibble
            val payload = ByteArrayOutputStream()
            payload.write(litCount shl 4 or matchNibble)
            payload.write(literal)
            val distance = litCount
            payload.write(distance and 0xFF)
            payload.write(distance shr 8 and 0xFF)

            val expected = "ABCDEFGHABCD".toByteArray(Charsets.UTF_8)

            val payloadBytes = payload.toByteArray()
            val crc = CRC32().apply { update(expected) }.value
            val loxccBytes = ByteBuffer.allocate(16 + payloadBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0xAABBCCEE.toInt())
                .putInt(payloadBytes.size)
                .putInt(expected.size)
                .putInt(crc.toInt())
                .put(payloadBytes)
                .array()

            val result = LoxCC.decompress(loxccBytes)

            result shouldBe expected
        }

        should("correctly decompress overlapping back-reference (repeat pattern)") {
            // Encodes "AB" as literals, then back-reference with distance=2 and matchLen=6
            // Result should be "ABABABAB" (overlapping repeat)
            val literal = "AB".toByteArray(Charsets.UTF_8) // 2 bytes
            val matchNibble = 2 // matchLen = 4 + 2 = 6
            val litCount = literal.size // 2
            val payload = ByteArrayOutputStream()
            payload.write(litCount shl 4 or matchNibble)
            payload.write(literal)
            val distance = litCount // = 2
            payload.write(distance and 0xFF)
            payload.write(distance shr 8 and 0xFF)

            val expected = "ABABABAB".toByteArray(Charsets.UTF_8) // 2 literals + 6 from backref

            val payloadBytes = payload.toByteArray()
            val crc = CRC32().apply { update(expected) }.value
            val loxccBytes = ByteBuffer.allocate(16 + payloadBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0xAABBCCEE.toInt())
                .putInt(payloadBytes.size)
                .putInt(expected.size)
                .putInt(crc.toInt())
                .put(payloadBytes)
                .array()

            val result = LoxCC.decompress(loxccBytes)

            result shouldBe expected
        }
    }
})

class GetLoxoneXmlHandlerTest : ShouldSpec({

    val adapter = mockk<LoxoneAdapter>()
    val handler = GetLoxoneXmlHandler(adapter)

    context("handle() - single LoxCC file") {

        should("return slimmed XML wrapped with the sps0 file header") {
            coEvery { adapter.fetchRawBytes(any()) } returns buildTestLoxCC(TEST_XML)

            val result = handler.handle()

            result.isError shouldBe false
            val text = (result.content.first() as TextContent).text
            text shouldContain "=== $SINGLE_FILE_NAME ==="
            text shouldContain "ControlList"
        }

        should("call fetchRawBytes with the correct project path") {
            coEvery { adapter.fetchRawBytes(any()) } returns buildTestLoxCC(TEST_XML)

            handler.handle()

            coVerify { adapter.fetchRawBytes("dev/fsget/prog/sps.LoxCC") }
        }
    }

    context("handle() - ZIP file (multi-Miniserver chain)") {

        should("return XML for each Miniserver with .LoxCC suffix replaced by .Loxone") {
            val zipBytes = buildTestZip(
                "sps0.LoxCC" to buildTestLoxCC(TEST_XML),
                "sps1.LoxCC" to buildTestLoxCC(TEST_XML)
            )
            coEvery { adapter.fetchRawBytes(any()) } returns zipBytes

            val result = handler.handle()

            result.isError shouldBe false
            val text = (result.content.first() as TextContent).text
            text shouldContain "=== sps0.Loxone ==="
            text shouldContain "=== sps1.Loxone ==="
        }

        should("skip non-LoxCC entries inside the ZIP") {
            val zipBytes = buildTestZip(
                "sps0.LoxCC" to buildTestLoxCC(TEST_XML),
                "README.txt" to "ignore me".toByteArray()
            )
            coEvery { adapter.fetchRawBytes(any()) } returns zipBytes

            val result = handler.handle()

            result.isError shouldBe false
            val text = (result.content.first() as TextContent).text
            text shouldContain "=== sps0.Loxone ==="
            text shouldNotContain "README"
        }
    }

    context("handle() - error cases") {

        should("return isError=true when bytes are neither LoxCC nor ZIP") {
            coEvery { adapter.fetchRawBytes(any()) } returns byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)

            val result = handler.handle()

            result.isError shouldBe true
            (result.content.first() as TextContent).text shouldContain "Decompression error"
        }

        should("return isError=true when fetchRawBytes throws") {
            coEvery { adapter.fetchRawBytes(any()) } throws RuntimeException("Connection refused")

            val result = handler.handle()

            result.isError shouldBe true
            (result.content.first() as TextContent).text shouldContain "Error"
        }

        should("return isError=true when ZIP contains no LoxCC files") {
            val zipBytes = buildTestZip("README.txt" to "ignore me".toByteArray())
            coEvery { adapter.fetchRawBytes(any()) } returns zipBytes

            val result = handler.handle()

            result.isError shouldBe true
            (result.content.first() as TextContent).text shouldContain "Decompression error"
        }
    }
})

// ── Test helpers ─────────────────────────────────────────────────────────────

/**
 * Builds a valid LoxCC byte array from [content] using a literals-only encoding
 * (no back-references). This mirrors the LoxCC decompression format exactly
 * without requiring the compression code.
 */
private fun buildTestLoxCC(content: String): ByteArray {
    val data = content.toByteArray(Charsets.UTF_8)
    val payload = encodeLiterals(data)
    val crc = CRC32().apply { update(data) }.value

    return ByteBuffer.allocate(16 + payload.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(0xAABBCCEE.toInt())   // magic
        .putInt(payload.size)          // compressed size
        .putInt(data.size)             // uncompressed size
        .putInt(crc.toInt())           // CRC32 checksum
        .put(payload)
        .array()
}

/**
 * Encodes [data] as LoxCC literal packets with no back-references.
 *
 * Packet layout: `hdr | [ext…] | literals`
 *  - `hdr` upper nibble = min(literalCount, 15); lower nibble = 0 (no match)
 *  - If upper nibble is 15: read additional bytes until < 255 (extension scheme)
 *  - Then copy `literalCount` bytes verbatim.
 */
private fun encodeLiterals(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    val n = data.size
    if (n < 15) {
        out.write(n shl 4)          // e.g. 14 bytes → 0xE0
    } else {
        out.write(0xF0)             // upper nibble = 15 (extension follows)
        var ext = n - 15
        while (ext >= 255) { out.write(255); ext -= 255 }
        out.write(ext)              // final extension byte (< 255)
    }
    out.write(data)
    // No back-reference: loop ends because index >= data.size
    return out.toByteArray()
}

/** Builds a standard ZIP archive with the given named entries. */
private fun buildTestZip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zos ->
        entries.forEach { (name, data) ->
            zos.putNextEntry(ZipEntry(name))
            zos.write(data)
            zos.closeEntry()
        }
    }
    return out.toByteArray()
}
