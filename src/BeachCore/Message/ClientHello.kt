package BeachCore.Message

import BeachCore.Stream.Reader
import BeachCore.Message.ServerHello

class ClientHello() : Reader(ByteArray(0)) {
    init { id = 10100 }
    fun decode() {}
    fun process() {
        ServerHello().Send()
    }
}

