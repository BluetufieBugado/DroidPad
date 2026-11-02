package com.phonepad.app

/** Sessão compartilhada entre a tela de conexão e a tela do controle. */
object Session {
    val packet = GamepadPacket()
    val sender = UdpSender("192.168.0.100")

    fun start(host: String) {
        sender.setHost(host)
        sender.packet = packet
        sender.start()
    }

    fun stop() = sender.stop()
}
