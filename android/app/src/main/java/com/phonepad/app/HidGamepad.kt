package com.phonepad.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import java.util.concurrent.Executors

/**
 * Celular como controle Bluetooth HID (gamepad padrão).
 * O outro aparelho (emulador etc.) pareia normal, sem app. Requer API 28+.
 * Report de 7 bytes: [btn0, btn1, LX, LY, RX, RY, hat].
 * @param legacy receptores antigos que numeram botões em sequência (inclui slots
 *  C/Z/L2/R2/Mode): reposiciona os usages p/ cair no label certo. Trocar exige
 *  desparear/parear de novo no receptor (ele cacheia o descritor).
 */
@SuppressLint("MissingPermission")
class HidGamepad(private val appCtx: Context, private val legacy: Boolean, private val listener: Listener) {

    interface Listener {
        fun onRegistered(ok: Boolean)
        fun onDevice(connected: Boolean, name: String?)
    }

    companion object {
        const val DEVICE_NAME = "DroidPad"
        const val PREF_BT = "phonepad_bt"
        const val KEY_LEGACY = "legacy_mapping"
        fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** Monta descritor com N botões (14 padrão / 16 legacy). Eixos + hat iguais. */
        fun buildDescriptor(nButtons: Int): ByteArray {
            val pad = 16 - nButtons // 14->2, 16->0 (report continua 7 bytes)
            val out = ArrayList<Byte>()
            fun b(vararg vs: Int) { vs.forEach { out.add(it.toByte()) } }
            b(0x05, 0x01, 0x09, 0x05, 0xA1, 0x01)                    // GamePad
            b(0x05, 0x09, 0x19, 0x01, 0x29, nButtons,
              0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, nButtons, 0x81, 0x02)
            if (pad > 0) b(0x75, 0x01, 0x95, pad, 0x81, 0x01)        // padding
            b(0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
              0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x04, 0x81, 0x02) // X Y Z Rz
            b(0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3B, 0x01,
              0x65, 0x14, 0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
              0x65, 0x00, 0x75, 0x04, 0x95, 0x01, 0x81, 0x01)        // hat + pad
            b(0xC0)
            return out.toByteArray()
        }
    }

    @Volatile var running = false
    @Volatile private var proxy: BluetoothHidDevice? = null
    @Volatile private var device: BluetoothDevice? = null
    private var sendThread: Thread? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            listener.onRegistered(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                this@HidGamepad.device = device
                listener.onDevice(true, try { device.name } catch (_: Exception) { "?" })
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (this@HidGamepad.device?.address == device.address) {
                    this@HidGamepad.device = null
                    listener.onDevice(false, null)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            proxy?.replyReport(device, type, id, buildReport(Session.packet))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_NOT_READY)
        }
    }

    fun start() {
        if (running || !supported()) return
        running = true
        val manager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: run { running = false; return }
        adapter.getProfileProxy(appCtx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (!running) return
                this@HidGamepad.proxy = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    DEVICE_NAME, "DroidPad gamepad", "DroidPad",
                    BluetoothHidDevice.SUBCLASS1_NONE,
                    buildDescriptor(if (legacy) 16 else 14)
                )
                val qos = BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, 800, 9, 0, 0, 0)
                this@HidGamepad.proxy?.registerApp(sdp, null, qos, Executors.newSingleThreadExecutor(), callback)
                startSender()
            }

            override fun onServiceDisconnected(profile: Int) {
                this@HidGamepad.proxy = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        running = false
        try { sendThread?.interrupt() } catch (_: Exception) {}
        sendThread = null
        val p = proxy
        try { p?.unregisterApp() } catch (_: Exception) {}
        proxy = null
        device = null
        try {
            val manager = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (p != null) manager.adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, p)
        } catch (_: Exception) {}
    }

    private fun startSender() {
        sendThread = Thread({
            while (running) {
                try {
                    val dev = device
                    val p = proxy
                    if (dev != null && p != null) {
                        p.sendReport(dev, 0, buildReport(Session.packet))
                    }
                    Thread.sleep(16) // ~60Hz
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                }
            }
        }, "phonepad-hid").apply { isDaemon = true; start() }
    }

    /** GamepadPacket -> report HID de 7 bytes.
     *  Padrão: identidade (receptores modernos/PC). Legacy: usages reposicionados
     *  p/ receptores que numeram em sequência (A=1,B=2,X=4,Y=5,LB=7,RB=8,LT=9,
     *  RT=10,Back=11,Start=12,L3=14,R3=15). */
    private val BTN_MAP = if (legacy) intArrayOf(
        1, 2, 4, 5, 7, 8, 11, 12,  // A B X Y LB RB Back Start
        14, 15,                     // L3 R3
        -1, -1, -1, -1              // D-pad vai no hat, não em botão
    ) else intArrayOf(
        1, 2, 3, 4, 5, 6, 7, 8,  // A B X Y LB RB Back Start
        9, 10,                    // L3 R3
        -1, -1, -1, -1            // D-pad vai no hat, não em botão
    )
    private val LT_HID = if (legacy) 9 else 11
    private val RT_HID = if (legacy) 10 else 12

    private fun buildReport(pkt: GamepadPacket): ByteArray {
        val btn = pkt.btn
        var hid = 0 // bitmask de 14 bits: bit N = botão HID N+1
        for (bit in 0..9) {
            val h = BTN_MAP[bit]
            if (h in 1..16 && btn and (1 shl bit) != 0) hid = hid or (1 shl (h - 1))
        }
        if (pkt.lt > 0.5f) hid = hid or (1 shl (LT_HID - 1)) // LT digital
        if (pkt.rt > 0.5f) hid = hid or (1 shl (RT_HID - 1)) // RT digital
        val b0 = hid and 0xFF
        val b1 = (hid shr 8) and 0xFF

        fun axis(v: Float, invert: Boolean): Byte {
            val c = v.coerceIn(-1f, 1f)
            val n = if (invert) 128 - c * 127 else 128 + c * 127
            return n.toInt().coerceIn(0, 255).toByte()
        }

        val up = btn and (1 shl 10) != 0
        val down = btn and (1 shl 11) != 0
        val left = btn and (1 shl 12) != 0
        val right = btn and (1 shl 13) != 0
        val hat = when {
            up && right -> 1
            right && down -> 3
            down && left -> 5
            left && up -> 7
            up -> 0
            right -> 2
            down -> 4
            left -> 6
            else -> 15 // neutro (null)
        }

        return byteArrayOf(
            b0.toByte(), b1.toByte(),
            axis(pkt.lx, false), axis(pkt.ly, true),
            axis(pkt.rx, false), axis(pkt.ry, true),
            hat.toByte()
        )
    }
}
