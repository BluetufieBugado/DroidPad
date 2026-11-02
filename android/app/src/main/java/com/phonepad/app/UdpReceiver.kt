package com.phonepad.app

import java.net.DatagramPacket
import java.net.DatagramSocket

/** Escuta UDP 9999 e entrega pacotes via callback. Usado no modo Receptor (Android receptor visualiza). */
class UdpReceiver(private val port: Int = GamepadPacket.PORT, private val onPacket: (GamepadPacket, String) -> Unit) {
    @Volatile var running = false
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            try {
                socket = DatagramSocket(port).apply { soTimeout = 500 }
                val buf = ByteArray(2048)
                while (running) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket?.receive(p)
                        val s = String(p.data, 0, p.length).trim()
                        GamepadPacket.fromJson(s)?.let { onPacket(it, p.address.hostAddress ?: "?") }
                    } catch (_: java.net.SocketTimeoutException) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }, "phonepad-receiver").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { thread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}
