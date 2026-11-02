package com.phonepad.app

import org.json.JSONObject

/** Pacote do protocolo v1. btn bits: 0=A,1=B,2=X,3=Y,4=LB,5=RB,6=BACK,7=START,8=L3,9=R3,10=UP,11=DOWN,12=LEFT,13=RIGHT */
data class GamepadPacket(
    var lx: Float = 0f,
    var ly: Float = 0f,
    var rx: Float = 0f,
    var ry: Float = 0f,
    var lt: Float = 0f,
    var rt: Float = 0f,
    var btn: Int = 0
) {
    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("t", System.currentTimeMillis())
        .put("lx", lx.toDouble()).put("ly", ly.toDouble())
        .put("rx", rx.toDouble()).put("ry", ry.toDouble())
        .put("lt", lt.toDouble()).put("rt", rt.toDouble())
        .put("btn", btn)
        .toString()

    fun setButton(bit: Int, pressed: Boolean) {
        btn = if (pressed) btn or (1 shl bit) else btn and (1 shl bit).inv()
    }

    companion object {
        const val PORT = 9999
        fun fromJson(s: String): GamepadPacket? = try {
            val o = JSONObject(s)
            GamepadPacket(
                lx = o.optDouble("lx").toFloat(),
                ly = o.optDouble("ly").toFloat(),
                rx = o.optDouble("rx").toFloat(),
                ry = o.optDouble("ry").toFloat(),
                lt = o.optDouble("lt").toFloat(),
                rt = o.optDouble("rt").toFloat(),
                btn = o.optInt("btn")
            )
        } catch (_: Exception) { null }
    }
}
