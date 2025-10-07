package BeachCore.Stream

import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

open class Writer(private val device: Any?) {
    val buffer = ByteArrayOutputStream()
    var id: Int? = null
    var version: Int? = null

    fun writeByte(data: Int) {
        writeInt(data.toLong(), 1)
    }

    fun writeInt(data: Long, length: Int = 4) {
        val b = ByteArray(length)
        for (i in 0 until length) {
            val shift = (length - 1 - i) * 8
            b[i] = ((data shr shift) and 0xFF).toByte()
        }
        buffer.write(b)
    }

    fun writeVint(inputData: Long) {
        var data = inputData
        var rotate = true
        val final = ByteArrayOutputStream()
        if (data == 0L) {
            writeByte(0)
        } else {
            data = (data shl 1) xor (data shr 31)
            while (data != 0L) {
                var b = (data and 0x7FL).toInt()
                if (data >= 0x80L) b = b or 0x80
                if (rotate) {
                    rotate = false
                    val lsb = b and 0x1
                    val msb = (b and 0x80) shr 7
                    var nb = b shr 1
                    nb = nb and (0xC0.inv())
                    nb = nb or (msb shl 7) or (lsb shl 6)
                    b = nb
                }
                final.write(byteArrayOf(b.toByte()))
                data = data shr 7
            }
        }
        buffer.write(final.toByteArray())
    }

    fun writeString(data: String?) {
        if (data != null) {
            writeInt(data.length.toLong())
            buffer.write(data.toByteArray(Charsets.UTF_8))
        } else {
            writeInt((1L shl 32) - 1, 4)
        }
    }

    fun writeHexa(data: String?) {
        if (data != null && data.isNotEmpty()) {
            var s = data
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
            s = s.replace("-", "")
            s = s.replace(Regex("\\s+"), "")
            val bytes = hexStringToByteArray(s)
            buffer.write(bytes)
        }
    }

    open fun encode() {}

    fun Send() {
        encode()
        val idVal = id ?: throw IllegalStateException("id is not set")
        try {
            if (version != null) {
                val m: Method = device!!.javaClass.getMethod("SendData", Integer::class.javaPrimitiveType, ByteArray::class.java, Integer::class.javaPrimitiveType)
                m.invoke(device, idVal, buffer.toByteArray(), version)
            } else {
                val m: Method = device!!.javaClass.getMethod("SendData", Integer::class.javaPrimitiveType, ByteArray::class.java)
                m.invoke(device, idVal, buffer.toByteArray())
            }
        } catch (e: NoSuchMethodException) {
            try {
                val m2: Method = device!!.javaClass.getMethod("SendData", Integer::class.java, ByteArray::class.java)
                m2.invoke(device, idVal, buffer.toByteArray())
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        if (len % 2 != 0) return byteArrayOf()
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val hi = Character.digit(s[i], 16)
            val lo = Character.digit(s[i + 1], 16)
            if (hi == -1 || lo == -1) return byteArrayOf()
            data[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return data
    }
}

