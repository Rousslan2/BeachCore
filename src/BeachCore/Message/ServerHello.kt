package BeachCore.Message

import BeachCore.Stream.Writer

class ServerHello() : Writer(null) {
    init { id = 20100 }
    override fun encode() {
        writeInt(24)
        for (i in 0 until 24) {
            writeByte(1)
        }
    }
}

