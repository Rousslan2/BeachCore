package BeachCore.Stream

import BeachCore.Message.ClientHello
import BeachCore.Message.ServerHello

object Factory {
    val availablePackets: Map<Int, () -> Any> = mapOf(
        10100 to { ClientHello() },
        20100 to { ServerHello() }
    )
}

