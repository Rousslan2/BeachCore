package BeachCore.Stream

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DataFormatException
import java.util.zip.Inflater

open class Reader(private val input: ByteArrayInputStream) {
    var id: Int = 0
    constructor(initialBytes: ByteArray) : this(ByteArrayInputStream(initialBytes))

    private fun readExact(length: Int): ByteArray {
        if (length < 0) throw IndexOutOfBoundsException("negative length")
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = input.read(buf, read, length - read)
            if (r == -1) throw EOFException("Unexpected EOF while reading $length bytes")
            read += r
        }
        return buf
    }

    fun ReadByte(): Int {
        val v = input.read()
        if (v == -1) throw EOFException("Unexpected EOF while reading 1 byte")
        return v and 0xFF
    }

    fun ReadBool(): Boolean = ReadByte() != 0

    private fun readRrSint32(): Long {
        val n = _readVarint(true)
        return zigzagDecode(n)
    }

    fun ReadSCID(): Long {
        val hi = readRrSint32()
        var lo = 0L
        if (hi != 0L) {
            lo = readRrSint32()
        }
        return hi * 1_000_000L + lo
    }

    fun ReadRRSLONG(): Long {
        val hi = (readRrSint32() and 0xFFFFFFFFL)
        val lo = (readRrSint32() and 0xFFFFFFFFL)
        return (hi shl 32) or lo
    }

    fun ReadUint16(length: Int = 2): Long = readUint(length)

    fun ReadUint32(length: Int = 4): Long = readUint(length)

    private fun readUint(length: Int): Long {
        val b = readExact(length)
        val bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
        return when (length) {
            1 -> (bb.get().toInt() and 0xFF).toLong()
            2 -> (bb.short.toInt() and 0xFFFF).toLong()
            4 -> (bb.int.toLong() and 0xFFFFFFFFL)
            8 -> bb.long
            else -> {
                var res = 0L
                for (x in b) {
                    res = (res shl 8) or (x.toInt() and 0xFF).toLong()
                }
                res
            }
        }
    }

    private fun _sevenBitRotateLeft(byteValue: Int): Int {
        val n0 = byteValue and 0xFF
        val seventh = (n0 and 0x40) shr 6
        val msb = (n0 and 0x80) shr 7
        var n = n0 shl 1
        val mask = 0x181.inv()
        n = n and mask
        n = n or (msb shl 7) or seventh
        return n and 0xFF
    }

    private fun _readVarint(isRr: Boolean): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val raw = ReadByte()
            var i = raw
            if (isRr && shift == 0) {
                i = _sevenBitRotateLeft(i)
            }
            result = result or ((i and 0x7F).toLong() shl shift)
            shift += 7
            if ((i and 0x80) == 0) break
            if (shift > 64) throw IndexOutOfBoundsException("Varint too long")
        }
        return result
    }

    private fun zigzagDecode(n: Long): Long = (n shr 1) xor (-(n and 1))

    fun read_int32(): Long = _readVarint(false)

    fun read_sint32(): Long = zigzagDecode(_readVarint(false))

    fun ReadVint(): Long = zigzagDecode(_readVarint(true))

    fun ReadLong(): Long = ReadUint32(8)

    fun ReadString(): String {
        val length = ReadUint32(4)
        if (length == 0xFFFFFFFFL) return ""
        if (length > Int.MAX_VALUE) throw IndexOutOfBoundsException("String too large: $length")
        val bytes = try {
            readExact(length.toInt())
        } catch (e: OutOfMemoryError) {
            throw IndexOutOfBoundsException("String out of range.")
        }
        return bytes.toString(Charsets.UTF_8)
    }

    fun ReadZString(): ByteArray {
        val length = ReadUint32(4)
        if (length == 0xFFFFFFFFL) return ByteArray(0)
        val zlengthBytes = readExact(4)
        val zlength = ByteBuffer.wrap(zlengthBytes).order(ByteOrder.LITTLE_ENDIAN).int
        val compressedLength = (length - 4).toInt()
        val compressed = try {
            readExact(compressedLength)
        } catch (e: OutOfMemoryError) {
            throw IndexOutOfBoundsException("String out of range.")
        }
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = ByteArray(zlength)
            val written = try {
                inflater.inflate(out)
            } catch (e: DataFormatException) {
                throw IndexOutOfBoundsException("Decompress error: ${e.message}")
            }
            if (written != zlength) {
                out.copyOf(written)
            } else out
        } finally {
            inflater.end()
        }
    }

    fun peek_int(length: Int = 4): Long {
        input.mark(length)
        val buf = ByteArray(length)
        val r = input.read(buf)
        input.reset()
        if (r < length) throw EOFException("Unexpected EOF while peeking")
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    fun ReadHexa(length: Int): String {
        val b = readExact(length)
        return b.joinToString(separator = "") { "%02x".format(it) }
    }
}

