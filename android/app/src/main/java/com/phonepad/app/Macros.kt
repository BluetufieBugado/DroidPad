package com.phonepad.app

import org.json.JSONObject

/**
 * Macro = combo simultâneo segurado: enquanto o botão macro está pressionado,
 * um conjunto de botões + gatilhos + direções dos analógicos fica ativo.
 * Ex: analógico p/ frente + □, ou L1 + △. Sem temporização (isso seria v2).
 *
 * [bits]: bitmask dos botões (mesmos bits do protocolo: 0=A..13=RIGHT).
 * [lt]/[rt]: gatilhos digitais. [lx]/[ly]/[rx]/[ry]: direção (-1..1, Y+ cima);
 * direção (0,0) = analógico fora do combo.
 */
data class MacroDef(
    val name: String = "",
    val bits: Int = 0,
    val lt: Boolean = false,
    val rt: Boolean = false,
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f
) {
    fun isEmpty(): Boolean =
        bits == 0 && !lt && !rt && lx == 0f && ly == 0f && rx == 0f && ry == 0f

    fun hasStickL(): Boolean = lx != 0f || ly != 0f
    fun hasStickR(): Boolean = rx != 0f || ry != 0f

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("bits", bits)
        .put("lt", lt).put("rt", rt)
        .put("lx", lx.toDouble()).put("ly", ly.toDouble())
        .put("rx", rx.toDouble()).put("ry", ry.toDouble())

    companion object {
        fun fromJson(o: JSONObject): MacroDef = MacroDef(
            name = o.optString("name", ""),
            bits = o.optInt("bits", 0),
            lt = o.optBoolean("lt", false),
            rt = o.optBoolean("rt", false),
            lx = o.optDouble("lx").toFloat(),
            ly = o.optDouble("ly").toFloat(),
            rx = o.optDouble("rx").toFloat(),
            ry = o.optDouble("ry").toFloat()
        )

        /** Direções p/ os spinners do editor: rótulo + vetor (Y+ cima). */
        val DIRS: List<Pair<String, Pair<Float, Float>>> = listOf(
            "—" to (0f to 0f),
            "↑" to (0f to 1f),
            "↗" to (0.7f to 0.7f),
            "→" to (1f to 0f),
            "↘" to (0.7f to -0.7f),
            "↓" to (0f to -1f),
            "↙" to (-0.7f to -0.7f),
            "←" to (-1f to 0f),
            "↖" to (-0.7f to 0.7f)
        )

        fun dirIndexOf(x: Float, y: Float): Int {
            if (x == 0f && y == 0f) return 0
            var best = 1
            var bestD = Float.MAX_VALUE
            for (i in 1 until DIRS.size) {
                val (dx, dy) = DIRS[i].second
                val d = (dx - x) * (dx - x) + (dy - y) * (dy - y)
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }

        /**
         * Resumo legível do combo, ex: "→ + □ + L1". Nomes dos botões vêm da
         * [labelOf] (que usa a skin atual: A/B/X/Y, ✕/○/□/△, L1/R1/L2/R2…).
         */
        fun summary(m: MacroDef, labelOf: (btnKey: String, fallback: String) -> String): String {
            val parts = mutableListOf<String>()
            if (m.hasStickL()) parts += "L" + DIRS[dirIndexOf(m.lx, m.ly)].first
            if (m.hasStickR()) parts += "R" + DIRS[dirIndexOf(m.rx, m.ry)].first
            if (m.lt) parts += labelOf("btnLT", "LT")
            if (m.rt) parts += labelOf("btnRT", "RT")
            val keys = mapOf(
                0 to ("btnA" to "A"), 1 to ("btnB" to "B"),
                2 to ("btnX" to "X"), 3 to ("btnY" to "Y"),
                4 to ("btnLB" to "LB"), 5 to ("btnRB" to "RB"),
                6 to ("btnBack" to "Back"), 7 to ("btnStart" to "Start"),
                8 to ("btnL3" to "L3"), 9 to ("btnR3" to "R3"),
                10 to ("btnUp" to "▲"), 11 to ("btnDown" to "▼"),
                12 to ("btnLeft" to "◀"), 13 to ("btnRight" to "▶")
            )
            for (bit in 0..13) {
                if (m.bits and (1 shl bit) != 0) {
                    val (key, fb) = keys[bit]!!
                    parts += labelOf(key, fb)
                }
            }
            return parts.joinToString(" + ")
        }
    }
}
