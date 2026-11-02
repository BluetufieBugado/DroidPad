package com.phonepad.app

/** Skin = rótulos + cores dos botões, cores dos analógicos e fundo. Tudo nativo, sem assets. */
data class SkinButton(val label: String, val bg: Int, val fg: Int)

data class Skin(
    val id: String,
    val title: String,
    val buttons: Map<String, SkinButton>,
    val joyBase: Int,
    val joyKnob: Int,
    val background: Int
)

object Skins {
    private const val BTN = 0xFF616161.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    private fun greyKeys(vararg keys: String, bg: Int = BTN, fg: Int = WHITE) =
        keys.associateWith { SkinButton(it.uppercase().removePrefix("BTN"), bg, fg) }

    /** D-pad mantém as setas, só troca as cores. */
    private fun dpad(bg: Int = BTN, fg: Int = WHITE) = mapOf(
        "btnUp" to SkinButton("▲", bg, fg),
        "btnDown" to SkinButton("▼", bg, fg),
        "btnLeft" to SkinButton("◀", bg, fg),
        "btnRight" to SkinButton("▶", bg, fg)
    )

    val XBOX = Skin(
        id = "xbox", title = "Xbox",
        buttons = mapOf(
            "btnA" to SkinButton("A", 0xFF107C10.toInt(), WHITE),
            "btnB" to SkinButton("B", 0xFFC81E1E.toInt(), WHITE),
            "btnX" to SkinButton("X", 0xFF1565C0.toInt(), WHITE),
            "btnY" to SkinButton("Y", 0xFFF9A825.toInt(), 0xFF212121.toInt())
        ) + greyKeys("btnLB", "btnRB", "btnLT", "btnRT", "btnL3", "btnR3",
            "btnBack", "btnStart", "btnDisconnect") + dpad(),
        joyBase = 0xFFBDBDBD.toInt(), joyKnob = 0xFF212121.toInt(),
        background = 0xFF141414.toInt()
    )

    val PLAYSTATION = Skin(
        id = "ps", title = "PlayStation",
        buttons = mapOf(
            // Posições físicas iguais às do Xbox: cima=Y, direita=B, baixo=A, esq.=X
            "btnY" to SkinButton("△", 0xFF212121.toInt(), 0xFF00E676.toInt()),
            "btnB" to SkinButton("○", 0xFF212121.toInt(), 0xFFFF5252.toInt()),
            "btnA" to SkinButton("✕", 0xFF212121.toInt(), 0xFF448AFF.toInt()),
            "btnX" to SkinButton("□", 0xFF212121.toInt(), 0xFFE040FB.toInt()),
            "btnLB" to SkinButton("L1", 0xFF37474F.toInt(), WHITE),
            "btnRB" to SkinButton("R1", 0xFF37474F.toInt(), WHITE),
            "btnLT" to SkinButton("L2", 0xFF37474F.toInt(), WHITE),
            "btnRT" to SkinButton("R2", 0xFF37474F.toInt(), WHITE),
            "btnBack" to SkinButton("Select", 0xFF37474F.toInt(), WHITE),
            "btnStart" to SkinButton("Start", 0xFF37474F.toInt(), WHITE)
        ) + greyKeys("btnL3", "btnR3", "btnDisconnect", bg = 0xFF37474F.toInt()) +
                dpad(bg = 0xFF37474F.toInt()),
        joyBase = 0xFFBDBDBD.toInt(), joyKnob = 0xFF212121.toInt(),
        background = 0xFF0D1B3E.toInt()
    )

    val SWITCH = Skin(
        id = "switch", title = "Switch",
        buttons = mapOf(
            "btnA" to SkinButton("A", 0xFF455A64.toInt(), WHITE),
            "btnB" to SkinButton("B", 0xFF455A64.toInt(), WHITE),
            "btnX" to SkinButton("X", 0xFF455A64.toInt(), WHITE),
            "btnY" to SkinButton("Y", 0xFF455A64.toInt(), WHITE),
            "btnLB" to SkinButton("L", 0xFF546E7A.toInt(), WHITE),
            "btnRB" to SkinButton("R", 0xFF546E7A.toInt(), WHITE),
            "btnLT" to SkinButton("ZL", 0xFF546E7A.toInt(), WHITE),
            "btnRT" to SkinButton("ZR", 0xFF546E7A.toInt(), WHITE),
            "btnBack" to SkinButton("−", 0xFF546E7A.toInt(), WHITE),
            "btnStart" to SkinButton("+", 0xFF546E7A.toInt(), WHITE)
        ) + greyKeys("btnL3", "btnR3", "btnDisconnect", bg = 0xFF546E7A.toInt()) +
                dpad(bg = 0xFF546E7A.toInt()),
        joyBase = 0xFFB0BEC5.toInt(), joyKnob = 0xFF37474F.toInt(),
        background = 0xFF263238.toInt()
    )

    val ALL = listOf(XBOX, PLAYSTATION, SWITCH)
    fun byId(id: String): Skin = ALL.find { it.id == id } ?: XBOX
}
