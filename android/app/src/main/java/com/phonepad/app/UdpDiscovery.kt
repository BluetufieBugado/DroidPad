package com.phonepad.app

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Discovery via broadcast UDP 9998. Request: "PHONEPAD_DISCOVER". Resposta: {"phonepad":1,"name":..,"port":9999,"type":"pc|android"} */
object UdpDiscovery {
    const val PORT = 9998
    const val MAGIC = "PHONEPAD_DISCOVER"

    data class Found(val ip: String, val name: String, val port: Int)

    /** Bloqueante (~timeoutMs). Chame fora da UI thread. Chama onFound p/ cada resposta. */
    fun discover(timeoutMs: Int = 2500, onFound: (Found) -> Unit): List<Found> {
        val out = mutableListOf<Found>()
        val seen = mutableSetOf<String>()
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket().apply {
                broadcast = true
                soTimeout = 600
            }
            val magic = MAGIC.toByteArray()
            val bcast = InetAddress.getByName("255.255.255.255")
            repeat(3) {
                try { sock.send(DatagramPacket(magic, magic.size, bcast, PORT)) } catch (_: Exception) {}
            }
            val end = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(512)
            while (System.currentTimeMillis() < end) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    sock.receive(p)
                    val ip = p.address.hostAddress ?: continue
                    val s = String(p.data, 0, p.length).trim()
                    if (!s.contains("phonepad")) continue
                    val o = JSONObject(s)
                    if (o.optInt("phonepad") != 1) continue
                    val key = "$ip:${o.optInt("port", GamepadPacket.PORT)}"
                    if (seen.add(key)) {
                        val f = Found(ip, o.optString("name", ip), o.optInt("port", GamepadPacket.PORT))
                        out.add(f)
                        onFound(f)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // reenvia 1x p/ alcançar quem entrou depois
                    try { sock.send(DatagramPacket(magic, magic.size, bcast, PORT)) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
        } finally {
            try { sock?.close() } catch (_: Exception) {}
        }
        return out
    }
}

/** Responde discovery quando o celular está em modo Receptor (Android->Android). */
class DiscoveryResponder(private val deviceName: String = android.os.Build.MODEL) {
    @Volatile var running = false
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            try {
                socket = DatagramSocket(UdpDiscovery.PORT).apply { soTimeout = 500 }
                val buf = ByteArray(256)
                while (running) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket?.receive(p)
                        val s = String(p.data, 0, p.length).trim()
                        if (s == UdpDiscovery.MAGIC) {
                            val resp = JSONObject()
                                .put("phonepad", 1)
                                .put("name", deviceName)
                                .put("port", GamepadPacket.PORT)
                                .put("type", "android")
                                .toString().toByteArray()
                            socket?.send(DatagramPacket(resp, resp.size, p.address, p.port))
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }, "phonepad-discovery").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { thread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}
