package cz.smarteon.loxmcp

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import no.synth.kmpzip.crypto.Crypto

internal object LoxCC {

    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_MASK = 0xF
    private val LOX_CC_MAGIC_WORD = 0xAABBCCEE.toInt()
    private const val HEADER_SIZE = 16
    private const val MIN_MATCH_LENGTH = 4
    private const val MAX_COMPRESSED_SIZE = 64 * 1024 * 1024

    fun decompress(input: ByteArray): ByteArray {
        if (input.size < HEADER_SIZE) throw DecompressException("Truncated header")
        var pos = 0

        val magic = readIntLe(input, pos); pos += 4
        if (magic != LOX_CC_MAGIC_WORD) throw DecompressException("Not a loxcc file")
        val compressedSize = readIntLe(input, pos); pos += 4
        val uncompressedSize = readIntLe(input, pos); pos += 4
        val checksum = readIntLe(input, pos).toUInt(); pos += 4

        validateSizes(compressedSize, uncompressedSize)
        if (input.size - pos < compressedSize) throw DecompressException("Truncated payload")

        val data = try {
            decompressData(input, pos, compressedSize).readByteArray()
        } catch (e: IndexOutOfBoundsException) {
            throw DecompressException("Truncated payload")
        }
        validateResult(data, checksum, uncompressedSize)
        return data
    }

    private fun validateSizes(compressedSize: Int, uncompressedSize: Int) {
        if (compressedSize < 0 || compressedSize > MAX_COMPRESSED_SIZE)
            throw DecompressException("Invalid compressedSize: $compressedSize")
        if (uncompressedSize < 0 || uncompressedSize > MAX_COMPRESSED_SIZE)
            throw DecompressException("Invalid uncompressedSize: $uncompressedSize")
    }

    private fun validateResult(data: ByteArray, checksum: UInt, uncompressedSize: Int) {
        val crc = Crypto.crc32(data).toUInt()
        if (checksum != crc) throw DecompressException("Checksum mismatch")
        if (data.size != uncompressedSize) throw DecompressException("Uncompressed size mismatch")
    }

    @Suppress("MagicNumber", "ComplexMethod", "LoopWithTooManyJumpStatements", "NestedBlockDepth")
    private fun decompressData(data: ByteArray, offset: Int, length: Int): Buffer {
        var index = offset
        val end = offset + length
        var out = ByteArray(maxOf(length * 4, 4096))
        var outPos = 0

        fun ensureCapacity(required: Int) {
            if (required > out.size) {
                var newSize = out.size
                while (newSize < required) newSize = newSize shl 1
                out = out.copyOf(newSize)
            }
        }

        while (index < end) {
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
                ensureCapacity(outPos + copyBytes)
                data.copyInto(out, outPos, index, index + copyBytes)
                outPos += copyBytes
                index += copyBytes
            }
            if (index >= end) break

            val bytesBack = readShortLe(data, index) and 0xFFFF
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
                val start = outPos - bytesBack
                if (start < 0) {
                    bytesBackCopied -= 1
                    continue
                }
                val copyLen = minOf(bytesBackCopied, bytesBack)
                ensureCapacity(outPos + copyLen)
                out.copyInto(out, outPos, start, start + copyLen)
                outPos += copyLen
                bytesBackCopied -= copyLen
            }
        }

        return Buffer().also { it.write(out, 0, outPos) }
    }

    private fun readShortLe(data: ByteArray, pos: Int): Int {
        val lo = data[pos].toInt() and BYTE_MASK
        val hi = data[pos + 1].toInt() and BYTE_MASK
        return (hi shl 8) or lo
    }

    private fun readIntLe(data: ByteArray, pos: Int): Int {
        val b0 = data[pos].toInt() and BYTE_MASK
        val b1 = data[pos + 1].toInt() and BYTE_MASK
        val b2 = data[pos + 2].toInt() and BYTE_MASK
        val b3 = data[pos + 3].toInt() and BYTE_MASK
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    class DecompressException(message: String) : Exception(message)
}
