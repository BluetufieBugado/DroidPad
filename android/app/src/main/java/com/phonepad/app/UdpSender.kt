package com.phonepad.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Envia GamepadPacket a 60Hz via UDP. Thread própria, não bloqueia UI. */
class UdpSender(private var host: String, private val port: Int = GamepadPacket.PORT) {
    @Volatile var running = false
    @Volatile var packet = GamepadPacket()
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    fun setHost(h: String) { host = h }

    fun start() {
        // Reinicia sempre: o thread antigo resolveu o IP uma vez e ignorava
        // trocas (ex: Buscar achou IP novo mas Controlar não reconectava).
        stop()
        try { thread?.join(300) } catch (_: Exception) {}
        running = true
        thread = Thread({
            try {
                socket = DatagramSocket()
                val addr = InetAddress.getByName(host)
                while (running) {
                    val bytes = packet.toJson().toByteArray()
                    socket?.send(DatagramPacket(bytes, bytes.size, addr, port))
                    Thread.sleep(16) // ~60Hz
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }, "phonepad-sender").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { thread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}
