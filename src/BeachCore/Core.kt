package BeachCore

import java.net.ServerSocket
import java.net.Socket
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import BeachCore.Stream.Factory

class ServerThread(private val ip: String, private val port: Int) {
    fun log(level: String, message: String) {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        println("[$timestamp] [$level] $message")
    }

    fun start() {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(port)
            log("INFO", "Server started successfully")
            log("INFO", "Available packets: ${Factory.availablePackets.keys}")
            while (true) {
                val clientSocket = serverSocket.accept()
                val address = clientSocket.inetAddress.hostAddress
                val port = clientSocket.port
                log("INFO", "New client: $address:$port")
                ClientThread(clientSocket, address, port).start()
            }
        } catch (e: Exception) {
            log("ERROR", "Server error: ${e.message}")
            e.printStackTrace()
        } finally {
            try { serverSocket?.close() } catch (_: Exception) {}
        }
    }
}

class ClientThread(private val client: Socket, private val host: String, private val port: Int) : Thread() {
    init { log("DEBUG", "Client thread started") }

    fun log(level: String, message: String) {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        println("[$timestamp] [$level] [Client:$host] $message")
    }

    private fun recvall(size: Int): ByteArray {
        var remaining = size
        val out = java.io.ByteArrayOutputStream()
        val input = client.getInputStream()
        val buf = ByteArray(4096)
        while (remaining > 0) {
            val toRead = minOf(buf.size, remaining)
            val r = input.read(buf, 0, toRead)
            if (r == -1) throw java.io.EOFException("Connection closed")
            out.write(buf, 0, r)
            remaining -= r
        }
        return out.toByteArray()
    }

    override fun run() {
        try {
            val input = client.getInputStream()
            while (true) {
                val header = ByteArray(7)
                var read = 0
                while (read < 7) {
                    val r = input.read(header, read, 7 - read)
                    if (r == -1) {
                        log("INFO", "Client disconnected")
                        client.close()
                        return
                    }
                    read += r
                }
                val packetId = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
                val length = ((header[2].toInt() and 0xFF) shl 16) or ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
                val version = ((header[5].toInt() and 0xFF) shl 8) or (header[6].toInt() and 0xFF)
                log("INFO", "Packet received - ID: $packetId, Length: $length")
                try {
                    val data = recvall(length)
                    if (data.size != length) {
                        log("WARNING", "Length mismatch: expected $length, got ${data.size}")
                        continue
                    }
                    val packetFactory = Factory.availablePackets[packetId]
                    if (packetFactory != null) {
                        log("DEBUG", "Processing packet $packetId")
                        try {
                            val packetInstance = packetFactory()
                            try {
                                val m = packetInstance::class.java.getMethod("decode", ByteArray::class.java)
                                m.invoke(packetInstance, data)
                            } catch (ns: NoSuchMethodException) {
                                try {
                                    val m2 = packetInstance::class.java.getMethod("decode")
                                    m2.invoke(packetInstance)
                                } catch (_: NoSuchMethodException) {}
                            }
                            try {
                                val mp = packetInstance::class.java.getMethod("process")
                                mp.invoke(packetInstance)
                            } catch (_: NoSuchMethodException) {}
                            log("SUCCESS", "Packet $packetId processed successfully")
                        } catch (e: Exception) {
                            log("ERROR", "Error processing packet $packetId: ${e.message}")
                            e.printStackTrace()
                        }
                    } else {
                        log("DEBUG", "Unknown packet $packetId, skipping")
                    }
                } catch (e: Exception) {
                    log("WARNING", "Error receiving data for packet: ${e.message}")
                    continue
                }
            }
        } catch (e: Exception) {
            log("ERROR", "Client error: ${e.message}")
            e.printStackTrace()
        } finally {
            try { client.close() } catch (_: Exception) {}
            log("INFO", "Connection closed")
        }
    }
}

fun CoreStart() {
    println(
"""   ___                                    _         ___
  F _ ",    ____      ___ _     ____     FJ___    ,"___".    ____     _ ___     ____
 J `-'(|   F __ J    F __` L   F ___J.  J  __ `.  FJ---L]   F __ J   J '__ ",  F __ J
 | ,--.\  | _____J  | |--| |  | |---LJ  | |--| | J |   LJ  | |--| |  | |__|-J | _____J
 F L__J \ F L___--. F L__J J  F L___--. F L  J J | \___--. F L__J J  F L  `-' F L___--.
J_______JJ\______/FJ\____,__LJ\______/FJ__L  J__LJ\_____/FJ\______/FJ__L     J\______/F
|_______F J______F  J____,__F J______F |__L  J__| J_____F  J______F |__L      J______F by FMZNkdv :3
"""
    )
    val server = ServerThread("0.0.0.0", 9339)
    server.start()
}

fun main() = CoreStart()

