package cz.smarteon.loxmcp

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

internal object LoxCC {

    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_MASK = 0xF
    private const val LOX_CC_MAGIC_WORD = 0xaabbccee
    private const val SHORT_LENGTH = 2
    private const val HEADER_SIZE = 16
    private const val MIN_MATCH_LENGTH = 4 // LZ4: every copy match is at least 4 bytes
    private const val MAX_COMPRESSED_SIZE = 64 * 1024 * 1024 // 64 MB

    /**
     * Decompresses a LoxCC file.
     * @param inputStream the input stream of the LoxCC file
     * @return the decompressed data
     * @throws DecompressException if the file is invalid
     */
    fun decompress(inputStream: InputStream): ByteArray {
        ByteArrayOutputStream().use {
            decompress(inputStream, it)
            return it.toByteArray()
        }
    }

    /**
     * Decompresses a LoxCC file.
     * @param input the input of the LoxCC file as ByteArray
     * @return the decompressed data
     * @throws DecompressException if the file is invalid
     */
    fun decompress(input: ByteArray): ByteArray {
        ByteArrayInputStream(input).use {
            return decompress(it)
        }
    }

    /**
     * Decompresses a LoxCC file.
     * @param inputStream the input stream of the LoxCC file
     * @param outputStream the output stream to write the result to
     * @throws DecompressException if the file is invalid
     */
    fun decompress(inputStream: InputStream, outputStream: OutputStream) {
        val bufferedInputStream = BufferedInputStream(inputStream)
        val (compressedSize, uncompressedSize, checksum) = readHeader(bufferedInputStream)
        val data = bufferedInputStream.readNBytes(compressedSize)
        if (data.size != compressedSize) throw DecompressException("Truncated payload")
        val result = decompressData(data)
        validateResult(result, checksum, uncompressedSize)
        result.writeTo(outputStream)
    }

    @Suppress("MagicNumber")
    private fun readHeader(stream: BufferedInputStream): Triple<Int, Int, UInt> {
        val headerBytes = stream.readNBytes(HEADER_SIZE)
        if (headerBytes.size != HEADER_SIZE) throw DecompressException("Truncated header")
        val headerData = headerBytes.wrap()
        if (headerData.int.toUInt().toLong() != LOX_CC_MAGIC_WORD) throw DecompressException("Not a loxcc file")
        val compressedSize = headerData.int
        val uncompressedSize = headerData.int
        val checksum = headerData.int.toUInt()
        validateSizes(compressedSize, uncompressedSize)
        return Triple(compressedSize, uncompressedSize, checksum)
    }

    private fun validateSizes(compressedSize: Int, uncompressedSize: Int) {
        if (compressedSize < 0 || compressedSize > MAX_COMPRESSED_SIZE)
            throw DecompressException("Invalid compressedSize: $compressedSize")
        if (uncompressedSize < 0 || uncompressedSize > MAX_COMPRESSED_SIZE)
            throw DecompressException("Invalid uncompressedSize: $uncompressedSize")
    }

    private fun validateResult(result: ExtendedByteArrayOutputStream, checksum: UInt, uncompressedSize: Int) {
        val crc32 = CRC32().apply { update(result.toByteArray()) }
        if (checksum.toLong() != crc32.value) throw DecompressException("Checksum mismatch")
        if (result.size() != uncompressedSize) throw DecompressException("Uncompressed size mismatch")
    }

    @Suppress("MagicNumber", "ComplexMethod", "LoopWithTooManyJumpStatements", "NestedBlockDepth")
    private fun decompressData(data: ByteArray): ExtendedByteArrayOutputStream {
        var index = 0
        val result = ExtendedByteArrayOutputStream()

        while (index < data.size) {
            var byte = data[index].toInt() and BYTE_MASK
            index += 1
            var copyBytes = byte shr 4
            byte = byte and NIBBLE_MASK

            if (copyBytes == 15) {
                while (true) {
                    val addByte = data[index].toInt() and BYTE_MASK
                    copyBytes += addByte
                    index += 1
                    if (addByte != BYTE_MASK) break
                }
            }
            if (copyBytes > 0) {
                result.write(data, index, copyBytes)
                index += copyBytes
            }
            if (index >= data.size) break

            val bytesBack =
                ByteBuffer.wrap(data, index, SHORT_LENGTH).order(ByteOrder.LITTLE_ENDIAN).getShort().toUShort()
                    .toInt()
            index += 2
            if (bytesBack == 0) throw DecompressException("Invalid back-reference: offset 0")
            var bytesBackCopied = MIN_MATCH_LENGTH + byte
            if (byte == 15) {
                while (true) {
                    val addByte = data[index].toInt() and BYTE_MASK
                    bytesBackCopied += addByte
                    index += 1
                    if (addByte != BYTE_MASK) break
                }
            }

            while (bytesBackCopied > 0) {
                val start = result.size() - bytesBack
                if (start < 0) {
                    bytesBackCopied -= 1
                    continue
                }
                val copyLen = minOf(bytesBackCopied, bytesBack)
                result.writeFromOwnBuffer(start, copyLen)
                bytesBackCopied -= copyLen
            }
        }

        return result
    }

    @Suppress("ComplexCondition")
    private class ExtendedByteArrayOutputStream : ByteArrayOutputStream() {
        fun writeFromOwnBuffer(start: Int, len: Int) {
            if (start < 0 || start > count || len < 0 || len > count - start) {
                throw IndexOutOfBoundsException("start: $start, len: $len, count: $count")
            }
            write(buf, start, len)
        }
    }

    private fun ByteArray.wrap() =
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    class DecompressException(message: String) : Exception(message)
}
