package com.phonepad.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Tela do controle: horizontal, cheia, 2 analógicos + botões, com modo edição e perfis por jogo. */
class ControllerActivity : AppCompatActivity() {

    private val blockIds = listOf(
        R.id.blockLB, R.id.blockStickL, R.id.blockDpad, R.id.blockSys,
        R.id.blockRB, R.id.blockFace, R.id.blockStickR, R.id.blockMacro
    )
    private val blockKeys = listOf(
        "blockLB", "blockStickL", "blockDpad", "blockSys",
        "blockRB", "blockFace", "blockStickR", "blockMacro"
    )
    private val gameplayBtnIds = listOf(
        R.id.btnA, R.id.btnB, R.id.btnX, R.id.btnY,
        R.id.btnLB, R.id.btnRB, R.id.btnBack, R.id.btnStart,
        R.id.btnL3, R.id.btnR3,
        R.id.btnUp, R.id.btnDown, R.id.btnLeft, R.id.btnRight,
        R.id.btnLT, R.id.btnRT, R.id.btnDisconnect,
        R.id.btnM1, R.id.btnM2, R.id.btnM3, R.id.btnM4
    )

    private var editMode = false
    private var selected: View? = null
    private lateinit var root: FrameLayout
    private lateinit var profileAdapter: ArrayAdapter<String>
    private val defaults = mutableMapOf<String, ControllerProfileStore.Block>()
    private var currentSkin: Skin = Skins.XBOX
    /** Modo do analógico: false = joystick fixo, true = área flutuante (dedo onde tocar vira origem). */
    private var floatL = false
    private var floatR = false

    // ---------- motor de saídas (botões físicos + macros dividem as mesmas saídas) ----------
    // Contadores p/ o mesmo botão poder estar preso pelo dedo E por um macro ao mesmo tempo:
    // a saída só desliga quando todo mundo soltar.
    private val bitRefs = IntArray(14)
    private var ltRefs = 0
    private var rtRefs = 0
    /** Direções de analógico impostas por macros segurados (id macro -> direção). Último vence. */
    private val stickLDrivers = LinkedHashMap<String, Pair<Float, Float>>()
    private val stickRDrivers = LinkedHashMap<String, Pair<Float, Float>>()
    /** Posição dos dedos nos analógicos (quando o macro solta, o dedo — se ainda lá — reassume). */
    private var fingerL = 0f to 0f
    private var fingerR = 0f to 0f

    private fun outPacket(): GamepadPacket = Session.packet

    private fun pressBits(bits: Int) {
        for (b in 0..13) if (bits and (1 shl b) != 0) bitRefs[b]++
        syncBtn()
    }

    private fun releaseBits(bits: Int) {
        for (b in 0..13) if (bits and (1 shl b) != 0) bitRefs[b] = (bitRefs[b] - 1).coerceAtLeast(0)
        syncBtn()
    }

    private fun syncBtn() {
        var v = 0
        for (b in 0..13) if (bitRefs[b] > 0) v = v or (1 shl b)
        outPacket().btn = v
    }

    private fun pressLt() { ltRefs++; syncTriggers() }
    private fun releaseLt() { ltRefs = (ltRefs - 1).coerceAtLeast(0); syncTriggers() }
    private fun pressRt() { rtRefs++; syncTriggers() }
    private fun releaseRt() { rtRefs = (rtRefs - 1).coerceAtLeast(0); syncTriggers() }

    private fun syncTriggers() {
        val p = outPacket()
        p.lt = if (ltRefs > 0) 1f else 0f
        p.rt = if (rtRefs > 0) 1f else 0f
    }

    private fun syncStickL() {
        val p = outPacket()
        val d = stickLDrivers.values.lastOrNull()
        if (d != null) { p.lx = d.first; p.ly = d.second }
        else { p.lx = fingerL.first; p.ly = fingerL.second }
    }

    private fun syncStickR() {
        val p = outPacket()
        val d = stickRDrivers.values.lastOrNull()
        if (d != null) { p.rx = d.first; p.ry = d.second }
        else { p.rx = fingerR.first; p.ry = fingerR.second }
    }

    // ---------- macros ----------
    private val macroKeys = listOf("m1", "m2", "m3", "m4")
    private val macroBtnIds = listOf(R.id.btnM1, R.id.btnM2, R.id.btnM3, R.id.btnM4)
    private var macros: Map<String, MacroDef> = emptyMap()

    /** Elementos que podem ser ocultados (tudo menos Desconectar). */
    private fun toggleableViews(): List<View> {
        val ids = gameplayBtnIds.filter { it != R.id.btnDisconnect } +
                listOf(R.id.joyLeft, R.id.joyRight)
        return ids.map { findViewById<View>(it) }
    }

    private fun keyOf(v: View): String? = try {
        if (v.id == View.NO_ID) null else resources.getResourceEntryName(v.id)
    } catch (_: Exception) { null }

    private fun hiddenKeys(): Set<String> = toggleableViews()
        .filter { it.visibility != View.VISIBLE }
        // Joystick fixo escondido por causa do modo área não é "oculto pelo usuário".
        .filterNot { (it.id == R.id.joyLeft && floatL) || (it.id == R.id.joyRight && floatR) }
        .mapNotNull { keyOf(it) }.toSet()

    private fun showAll() {
        toggleableViews().forEach { it.visibility = View.VISIBLE }
        // O fixo de um lado em modo área não volta: ele não existe nesse modo.
        if (floatL) findViewById<View>(R.id.joyLeft).visibility = View.GONE
        if (floatR) findViewById<View>(R.id.joyRight).visibility = View.GONE
        applyMacrosToUi()
    }

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_controller)

        root = findViewById(R.id.rootController)
        applySkin(Skins.byId(
            getSharedPreferences("phonepad_ui", MODE_PRIVATE).getString("skin", "xbox") ?: "xbox"
        ))

        val joyL = findViewById<JoystickView>(R.id.joyLeft)
        val joyR = findViewById<JoystickView>(R.id.joyRight)
        joyL.onMove = { x, y -> fingerL = x to y; syncStickL() }
        joyR.onMove = { x, y -> fingerR = x to y; syncStickR() }
        joyL.onPress = { buzz() }
        joyR.onPress = { buzz() }

        val areaL = findViewById<FloatingStickArea>(R.id.floatLeft)
        val areaR = findViewById<FloatingStickArea>(R.id.floatRight)
        areaL.side = FloatingStickArea.LEFT
        areaR.side = FloatingStickArea.RIGHT
        areaL.onMove = { x, y -> fingerL = x to y; syncStickL() }
        areaR.onMove = { x, y -> fingerR = x to y; syncStickR() }
        areaL.onPress = { buzz() }
        areaR.onPress = { buzz() }
        // O topo (gatilhos LB/LT/RB/RT + barra de sistema) não vira joystick.
        root.post { updateTopExclude() }
        val uiPrefs = getSharedPreferences("phonepad_ui", MODE_PRIVATE)
        applyStickMode(
            uiPrefs.getBoolean("floatL", false),
            uiPrefs.getBoolean("floatR", false)
        )

        fun holdBtn(id: Int, bit: Int) {
            val b = findViewById<Button>(id)
            b.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { pressBits(1 shl bit); b.isPressed = true; buzz(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { releaseBits(1 shl bit); b.isPressed = false; true }
                    else -> false
                }
            }
        }
        holdBtn(R.id.btnA, 0); holdBtn(R.id.btnB, 1); holdBtn(R.id.btnX, 2); holdBtn(R.id.btnY, 3)
        holdBtn(R.id.btnLB, 4); holdBtn(R.id.btnRB, 5)
        holdBtn(R.id.btnBack, 6); holdBtn(R.id.btnStart, 7)
        holdBtn(R.id.btnL3, 8); holdBtn(R.id.btnR3, 9)
        holdBtn(R.id.btnUp, 10); holdBtn(R.id.btnDown, 11)
        holdBtn(R.id.btnLeft, 12); holdBtn(R.id.btnRight, 13)

        fun trigger(id: Int, press: () -> Unit, release: () -> Unit) {
            val b = findViewById<Button>(id)
            b.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { press(); b.isPressed = true; buzz(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { release(); b.isPressed = false; true }
                    else -> false
                }
            }
        }
        trigger(R.id.btnLT, ::pressLt, ::releaseLt)
        trigger(R.id.btnRT, ::pressRt, ::releaseRt)

        for (i in macroBtnIds.indices) wireMacroButton(macroBtnIds[i], macroKeys[i])

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            Session.stop()
            finish()
        }

        // ---- modo edição ----
        for (id in blockIds) attachDrag(findViewById(id))
        findViewById<Button>(R.id.btnEdit).setOnClickListener { setEditMode(!editMode) }
        findViewById<Button>(R.id.btnDone).setOnClickListener { setEditMode(false) }
        findViewById<Button>(R.id.btnBigger).setOnClickListener { resizeSelected(0.15f) }
        findViewById<Button>(R.id.btnSmaller).setOnClickListener { resizeSelected(-0.15f) }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener { askSaveProfile() }
        findViewById<Button>(R.id.btnDelProfile).setOnClickListener { deleteSelectedProfile() }
        findViewById<Button>(R.id.btnResetLayout).setOnClickListener {
            if (defaults.isNotEmpty()) {
                applyProfile(ControllerProfileStore.Profile(defaults, emptySet(), currentSkin.id))
                select(null)
                Toast.makeText(this, R.string.layout_reset, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnShowAll).setOnClickListener {
            showVisibilityMenu()
        }
        findViewById<Button>(R.id.btnStickMode).setOnClickListener {
            showStickModeDialog()
        }
        findViewById<Button>(R.id.btnMacros).setOnClickListener {
            showMacroList()
        }
        findViewById<Button>(R.id.btnSkin).setOnClickListener {
            showSkinPicker()
        }
        findViewById<Button>(R.id.btnVib).setOnClickListener {
            cycleVib()
        }
        updateVibLabel()
        attachPanelDrag(findViewById(R.id.editHandle), findViewById(R.id.editBar))

        profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf(getString(R.string.profiles_hint)))
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val sp = findViewById<Spinner>(R.id.spProfiles)
        sp.adapter = profileAdapter
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos <= 0) return
                val name = profileAdapter.getItem(pos) ?: return
                ControllerProfileStore.load(this@ControllerActivity, name)?.let {
                    applyProfile(it)
                    ControllerProfileStore.setLast(this@ControllerActivity, name)
                    Toast.makeText(this@ControllerActivity, getString(R.string.profile_loaded, name), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refreshProfiles()

        // Guarda posição padrão (pós-layout) p/ o botão ⟲ restaurar
        root.post {
            if (defaults.isEmpty()) {
                for (i in blockIds.indices) {
                    val v = findViewById<View>(blockIds[i])
                    defaults[blockKeys[i]] = ControllerProfileStore.Block(
                        v.x / root.width, v.y / root.height, 1f)
                }
            }
        }

        // Aplica último perfil usado (se houver)
        ControllerProfileStore.last(this)?.let { name ->
            ControllerProfileStore.load(this, name)?.let { profile ->
                root.post { applyProfile(profile) }
            }
        }
    }

    // ---------- edição ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun setEditMode(on: Boolean) {
        if (on == editMode) return
        editMode = on
        val bar = findViewById<View>(R.id.editBar)
        if (on) {
            bar.visibility = View.VISIBLE
            findViewById<JoystickView>(R.id.joyLeft).inputEnabled = false
            findViewById<JoystickView>(R.id.joyRight).inputEnabled = false
            findViewById<FloatingStickArea>(R.id.floatLeft).apply { inputEnabled = false; invalidate() }
            findViewById<FloatingStickArea>(R.id.floatRight).apply { inputEnabled = false; invalidate() }
            // isClickable=false é essencial: botão desabilitado MAS clicável ainda
            // engole o toque (retorna true) e o arrasto nunca chega no bloco.
            for (id in gameplayBtnIds) {
                findViewById<Button>(id).apply { isEnabled = false; isClickable = false }
            }
            // Tela cheia de verdade: nada é deslocado. O painel flutuante volta p/
            // a última posição (ou o centro, na 1ª vez) sem cobrir os blocos à toa.
            bar.post { placePanel(false) }
            // Bloco de macros aparece p/ poder posicionar, mesmo vazio.
            applyMacrosToUi()
            Toast.makeText(this, R.string.edit_hint, Toast.LENGTH_LONG).show()
        } else {
            bar.visibility = View.GONE
            findViewById<JoystickView>(R.id.joyLeft).inputEnabled = true
            findViewById<JoystickView>(R.id.joyRight).inputEnabled = true
            findViewById<FloatingStickArea>(R.id.floatLeft).apply { inputEnabled = true; invalidate() }
            findViewById<FloatingStickArea>(R.id.floatRight).apply { inputEnabled = true; invalidate() }
            updateTopExclude()
            for (id in gameplayBtnIds) {
                findViewById<Button>(id).apply { isEnabled = true; isClickable = true }
            }
            // Restaura o modo fixo/área: o fixo de um lado flutuante continua escondido no jogo.
            applyStickMode(floatL, floatR)
            // Bloco de macros some do jogo se todos estiverem vazios.
            applyMacrosToUi()
            select(null)
        }
    }

    private fun select(v: View?) {
        selected?.setBackgroundColor(Color.TRANSPARENT)
        selected = v
        v?.setBackgroundColor(0x6633B5E5)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(v: View) {
        var offX = 0f
        var offY = 0f
        v.setOnTouchListener { _, e ->
            if (!editMode) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    select(v)
                    offX = v.x - e.rawX
                    offY = v.y - e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (root.width - v.width).coerceAtLeast(0).toFloat()
                    val maxY = (root.height - v.height).coerceAtLeast(0).toFloat()
                    v.x = (e.rawX + offX).coerceIn(0f, maxX)
                    v.y = (e.rawY + offY).coerceIn(0f, maxY)
                    true
                }
                else -> true
            }
        }
    }

    /** Arrasto do painel flutuante pela alça ✥. Posição salva em fração da tela. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachPanelDrag(handle: View, panel: View) {
        var offX = 0f
        var offY = 0f
        handle.setOnTouchListener { _, e ->
            if (!editMode) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    select(null)
                    offX = panel.x - e.rawX
                    offY = panel.y - e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (root.width - panel.width).coerceAtLeast(0).toFloat()
                    val maxY = (root.height - panel.height).coerceAtLeast(0).toFloat()
                    panel.x = (e.rawX + offX).coerceIn(0f, maxX)
                    panel.y = (e.rawY + offY).coerceIn(0f, maxY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val w = root.width.takeIf { it > 0 } ?: return@setOnTouchListener true
                    val h = root.height.takeIf { it > 0 } ?: return@setOnTouchListener true
                    getSharedPreferences("phonepad_ui", MODE_PRIVATE).edit()
                        .putFloat("panelX", panel.x / w)
                        .putFloat("panelY", panel.y / h)
                        .apply()
                    true
                }
                else -> true
            }
        }
    }

    /**
     * Posiciona o painel: última posição salva, ou centro na 1ª vez.
     * [clampOnly] = true só traz p/ dentro da tela (ex: após girar o celular).
     */
    private fun placePanel(clampOnly: Boolean) {
        val panel = findViewById<View>(R.id.editBar)
        val w = root.width.takeIf { it > 0 } ?: return
        val h = root.height.takeIf { it > 0 } ?: return
        val prefs = getSharedPreferences("phonepad_ui", MODE_PRIVATE)
        if (!clampOnly && prefs.contains("panelX")) {
            panel.x = (prefs.getFloat("panelX", 0.5f) * w)
                .coerceIn(0f, (w - panel.width).coerceAtLeast(0).toFloat())
            panel.y = (prefs.getFloat("panelY", 0.5f) * h)
                .coerceIn(0f, (h - panel.height).coerceAtLeast(0).toFloat())
        } else if (!clampOnly) {
            panel.x = ((w - panel.width) / 2f).coerceAtLeast(0f)
            panel.y = ((h - panel.height) / 2f).coerceAtLeast(0f)
        } else {
            panel.x = panel.x.coerceIn(0f, (w - panel.width).coerceAtLeast(0).toFloat())
            panel.y = panel.y.coerceIn(0f, (h - panel.height).coerceAtLeast(0).toFloat())
        }
    }

    // ---------- vibração ----------

    /** Nível 0=desligada, 1=fraca, 2=média, 3=forte. Global do aparelho. */
    private fun vibLevel(): Int =
        getSharedPreferences("phonepad_ui", MODE_PRIVATE).getInt("vib", 2)

    /** Pulso curto ao pressionar botão/stick. minSdk 26: VibrationEffect direto. */
    private fun buzz() {
        val level = vibLevel()
        if (level <= 0) return
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = longArrayOf(0L, 15L, 30L, 60L)[level.coerceIn(0, 3)]
        val amp = intArrayOf(0, 90, 170, 255)[level.coerceIn(0, 3)]
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    private fun vibName(level: Int): String = when (level) {
        1 -> getString(R.string.vib_weak)
        2 -> getString(R.string.vib_medium)
        3 -> getString(R.string.vib_strong)
        else -> getString(R.string.vib_off)
    }

    /** Botão 📳: alterna desligada → fraca → média → forte, com preview na hora. */
    private fun cycleVib() {
        val next = (vibLevel() + 1) % 4
        getSharedPreferences("phonepad_ui", MODE_PRIVATE).edit().putInt("vib", next).apply()
        updateVibLabel()
        buzz()
        Toast.makeText(this, getString(R.string.vib_set, vibName(next)), Toast.LENGTH_SHORT).show()
    }

    private fun updateVibLabel() {
        // Símbolo + nível atual, ex: "📳✓" desligada vs com intensidade.
        val label = when (vibLevel()) {
            0 -> "📳✕"
            1 -> "📳·"
            2 -> "📳:"
            else -> "📳!"
        }
        findViewById<Button>(R.id.btnVib).text = label
    }

    /** Nome amigável p/ o menu de visibilidade. */
    private fun macroPretty(i: Int): String {
        val m = macros[macroKeys[i]] ?: MacroDef()
        val t = if (m.name.isBlank()) "M${i + 1}" else m.name
        return getString(R.string.macro_item, t)
    }

    private fun prettyName(v: View): String = when (keyOf(v)) {
        "btnA" -> "A"
        "btnB" -> "B"
        "btnX" -> "X"
        "btnY" -> "Y"
        "btnLB" -> "LB"
        "btnRB" -> "RB"
        "btnLT" -> "LT"
        "btnRT" -> "RT"
        "btnBack" -> "Back"
        "btnStart" -> "Start"
        "btnL3" -> "L3"
        "btnR3" -> "R3"
        "btnUp" -> "D-pad ↑"
        "btnDown" -> "D-pad ↓"
        "btnLeft" -> "D-pad ←"
        "btnRight" -> "D-pad →"
        "joyLeft" -> getString(R.string.el_left_stick)
        "joyRight" -> getString(R.string.el_right_stick)
        "btnM1" -> macroPretty(0)
        "btnM2" -> macroPretty(1)
        "btnM3" -> macroPretty(2)
        "btnM4" -> macroPretty(3)
        else -> keyOf(v) ?: "?"
    }

    /** Menu flutuante: marca/desmarca o que aparece. Preview ao vivo. */
    private fun showVisibilityMenu() {
        // O joystick fixo de um lado em modo área não existe na tela: some da lista.
        val views = toggleableViews().filterNot {
            (it.id == R.id.joyLeft && floatL) || (it.id == R.id.joyRight && floatR)
        }
        val labels = views.map { prettyName(it) }.toTypedArray()
        val checked = views.map { it.visibility == View.VISIBLE }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.visibility_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                views[which].visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.show_all) { _, _ -> showAll() }
            .show()
    }

    /** Aplica skin: rótulos + cores dos botões, analógicos e fundo. */
    private fun applySkin(skin: Skin) {
        currentSkin = skin
        for (id in gameplayBtnIds) {
            val b = findViewById<Button>(id)
            val sb = keyOf(b)?.let { skin.buttons[it] }
            if (sb != null) {
                b.text = sb.label
                b.setTextColor(sb.fg)
                b.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f
                    setColor(sb.bg)
                }
            } else {
                b.setTextColor(0xFFFFFFFF.toInt())
                b.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f
                    setColor(0xFF616161.toInt())
                }
            }
        }
        findViewById<JoystickView>(R.id.joyLeft).setColors(skin.joyBase, skin.joyKnob)
        findViewById<JoystickView>(R.id.joyRight).setColors(skin.joyBase, skin.joyKnob)
        findViewById<FloatingStickArea>(R.id.floatLeft).setColors(skin.joyBase, skin.joyKnob)
        findViewById<FloatingStickArea>(R.id.floatRight).setColors(skin.joyBase, skin.joyKnob)
        // Macros têm cor própria (roxo) em qualquer skin, p/ achar fácil na tela.
        for (id in macroBtnIds) {
            findViewById<Button>(id).apply {
                setTextColor(0xFFFFFFFF.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f
                    setColor(0xFF6A1B9A.toInt())
                }
            }
        }
        root.setBackgroundColor(skin.background)
        getSharedPreferences("phonepad_ui", MODE_PRIVATE).edit()
            .putString("skin", skin.id).apply()
    }

    private fun showSkinPicker() {
        val titles = Skins.ALL.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.skin_title)
            .setItems(titles) { _, which ->
                applySkin(Skins.ALL[which])
                Toast.makeText(this, getString(R.string.skin_applied, Skins.ALL[which].title), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun resizeSelected(delta: Float) {
        val v = selected ?: return
        val s = (v.scaleX + delta).coerceIn(0.5f, 2.0f)
        v.scaleX = s
        v.scaleY = s
    }

    private fun collectProfile(): ControllerProfileStore.Profile {
        val w = root.width.takeIf { it > 0 }
            ?: return ControllerProfileStore.Profile(emptyMap(), hiddenKeys(), currentSkin.id, floatL, floatR)
        val h = root.height.takeIf { it > 0 }
            ?: return ControllerProfileStore.Profile(emptyMap(), hiddenKeys(), currentSkin.id, floatL, floatR)
        val blocks = buildMap {
            for (i in blockIds.indices) {
                val v = findViewById<View>(blockIds[i])
                put(blockKeys[i], ControllerProfileStore.Block(v.x / w, v.y / h, v.scaleX))
            }
        }
        return ControllerProfileStore.Profile(blocks, hiddenKeys(), currentSkin.id, floatL, floatR, macros)
    }

    private fun applyProfile(p: ControllerProfileStore.Profile) {
        applySkin(Skins.byId(p.skin))
        applyStickMode(p.floatL, p.floatR)
        macros = p.macros
        val w = root.width.takeIf { it > 0 } ?: return
        val h = root.height.takeIf { it > 0 } ?: return
        for (i in blockIds.indices) {
            val b = p.blocks[blockKeys[i]] ?: continue
            val v = findViewById<View>(blockIds[i])
            v.x = (b.fx * w).coerceIn(0f, (w - v.width).coerceAtLeast(0).toFloat())
            v.y = (b.fy * h).coerceIn(0f, (h - v.height).coerceAtLeast(0).toFloat())
            v.scaleX = b.s.coerceIn(0.5f, 2.0f)
            v.scaleY = v.scaleX
        }
        val byKey = toggleableViews().mapNotNull { v -> keyOf(v)?.let { it to v } }.toMap()
        for ((key, v) in byKey) {
            // O fixo de um lado em modo área fica sempre escondido (não é "oculto pelo usuário").
            if ((key == "joyLeft" && floatL) || (key == "joyRight" && floatR)) {
                v.visibility = View.GONE
            } else {
                v.visibility = if (key in p.hidden) View.GONE else View.VISIBLE
            }
        }
        applyMacrosToUi()
    }

    // ---------- modo fixo vs área flutuante ----------

    /** Recalcula a faixa do topo que não vira joystick (gatilhos + botões de sistema). */
    private fun updateTopExclude() {
        val tops = listOf(R.id.blockLB, R.id.blockRB, R.id.blockSys).mapNotNull { id ->
            findViewById<View>(id)?.let { if (it.visibility == View.VISIBLE) it.bottom.toFloat() else null }
        }
        val exclude = (tops.maxOrNull() ?: 0f) + 8f * resources.displayMetrics.density
        findViewById<FloatingStickArea>(R.id.floatLeft).topExcludePx = exclude
        findViewById<FloatingStickArea>(R.id.floatRight).topExcludePx = exclude
    }

    /** Troca fixo <-> área flutuante por lado. O fixo some, a área assume; botões continuam por cima. */
    private fun applyStickMode(leftFloat: Boolean, rightFloat: Boolean) {
        floatL = leftFloat
        floatR = rightFloat
        findViewById<FloatingStickArea>(R.id.floatLeft).visibility = if (leftFloat) View.VISIBLE else View.GONE
        findViewById<FloatingStickArea>(R.id.floatRight).visibility = if (rightFloat) View.VISIBLE else View.GONE
        // O fixo some SEMPRE que o lado está em modo área (jogo e edição) —
        // só respeita o 👁 do usuário quando o lado está em modo fixo.
        val hidden = hiddenKeys()
        findViewById<View>(R.id.joyLeft).visibility =
            if (leftFloat || "joyLeft" in hidden) View.GONE else View.VISIBLE
        findViewById<View>(R.id.joyRight).visibility =
            if (rightFloat || "joyRight" in hidden) View.GONE else View.VISIBLE
        findViewById<FloatingStickArea>(R.id.floatLeft).invalidate()
        findViewById<FloatingStickArea>(R.id.floatRight).invalidate()
        getSharedPreferences("phonepad_ui", MODE_PRIVATE).edit()
            .putBoolean("floatL", leftFloat)
            .putBoolean("floatR", rightFloat)
            .apply()
    }

    /** Diálogo do botão 🕹: cada analógico escolhe entre fixo e área flutuante. Salvo no perfil. */
    private fun showStickModeDialog() {
        val leftBox = CheckBox(this).apply {
            text = getString(R.string.stick_left_float)
            isChecked = floatL
        }
        val rightBox = CheckBox(this).apply {
            text = getString(R.string.stick_right_float)
            isChecked = floatR
        }
        val hint = TextView(this).apply {
            text = getString(R.string.stick_mode_hint)
            textSize = 12f
            setPadding(0, 8, 0, 0)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(leftBox)
            addView(rightBox)
            addView(hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.stick_mode_title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                applyStickMode(leftBox.isChecked, rightBox.isChecked)
                Toast.makeText(this, R.string.stick_mode_saved_hint, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- macros (combos segurados) ----------

    /** Rótulo do botão na skin atual (A/B/X/Y, ✕/○/□/△, L1/R1/L2/R2…). */
    private fun skinLabel(key: String, fallback: String): String =
        currentSkin.buttons[key]?.label ?: fallback

    @SuppressLint("ClickableViewAccessibility")
    private fun wireMacroButton(id: Int, key: String) {
        val b = findViewById<Button>(id)
        b.setOnTouchListener { _, e ->
            val m = macros[key] ?: MacroDef()
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (m.isEmpty()) {
                        Toast.makeText(this, R.string.macro_empty_hint, Toast.LENGTH_SHORT).show()
                        true
                    } else {
                        macroDown(key, m); b.isPressed = true; buzz(); true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!m.isEmpty()) macroUp(key, m)
                    b.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun macroDown(key: String, m: MacroDef) {
        pressBits(m.bits)
        if (m.lt) pressLt()
        if (m.rt) pressRt()
        if (m.hasStickL()) { stickLDrivers[key] = m.lx to m.ly; syncStickL() }
        if (m.hasStickR()) { stickRDrivers[key] = m.rx to m.ry; syncStickR() }
    }

    private fun macroUp(key: String, m: MacroDef) {
        releaseBits(m.bits)
        if (m.lt) releaseLt()
        if (m.rt) releaseRt()
        if (stickLDrivers.remove(key) != null) syncStickL()
        if (stickRDrivers.remove(key) != null) syncStickR()
    }

    /** Atualiza textos dos botões e mostra/esconde o bloco (vazio some do jogo). */
    private fun applyMacrosToUi() {
        for (i in macroKeys.indices) {
            val m = macros[macroKeys[i]] ?: MacroDef()
            findViewById<Button>(macroBtnIds[i]).text =
                if (m.name.isBlank()) "M${i + 1}" else m.name
        }
        val any = macroKeys.any { !(macros[it] ?: MacroDef()).isEmpty() }
        val anyVisible = macroBtnIds.any { findViewById<View>(it).visibility == View.VISIBLE }
        findViewById<View>(R.id.blockMacro).visibility =
            if ((editMode || any) && anyVisible) View.VISIBLE else View.GONE
    }

    /** Lista M1..M4 com o resumo do combo (nomes da skin atual). Toque edita. */
    private fun showMacroList() {
        val labels = macroKeys.mapIndexed { i, k ->
            val m = macros[k] ?: MacroDef()
            val t = if (m.name.isBlank()) "M${i + 1}" else m.name
            val s = MacroDef.summary(m, ::skinLabel)
            if (s.isEmpty()) "$t — ${getString(R.string.macro_empty)}" else "$t: $s"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.macros_title)
            .setItems(labels) { _, which -> showMacroEditor(macroKeys[which]) }
            .show()
    }

    /** Editor do combo: nome + botões segurados juntos + direção de cada analógico. */
    private fun showMacroEditor(key: String) {
        val idx = macroKeys.indexOf(key)
        val cur = macros[key] ?: MacroDef()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.macro_name_hint)
            setText(cur.name)
        }
        col.addView(nameInput)

        fun sectionTitle(res: Int) {
            col.addView(TextView(this).apply {
                text = getString(res)
                textSize = 13f
                setPadding(0, 20, 0, 4)
            })
        }
        sectionTitle(R.string.macro_buttons_section)
        // (chave skin, rótulo padrão, bit do protocolo)
        val rows = listOf(
            Triple("btnA", "A", 0), Triple("btnB", "B", 1),
            Triple("btnX", "X", 2), Triple("btnY", "Y", 3),
            Triple("btnLB", "LB", 4), Triple("btnRB", "RB", 5),
            Triple("btnL3", "L3", 8), Triple("btnR3", "R3", 9),
            Triple("btnBack", "Back", 6), Triple("btnStart", "Start", 7),
            Triple("btnUp", "▲", 10), Triple("btnDown", "▼", 11),
            Triple("btnLeft", "◀", 12), Triple("btnRight", "▶", 13)
        )
        val grid = GridLayout(this).apply { columnCount = 2 }
        val boxes = rows.map { (k, fb, bit) ->
            bit to CheckBox(this).apply {
                text = skinLabel(k, fb)
                textSize = 14f
                isChecked = cur.bits and (1 shl bit) != 0
            }.also { grid.addView(it) }
        }
        col.addView(grid)
        val ltBox = CheckBox(this).apply {
            text = skinLabel("btnLT", "LT"); textSize = 14f; isChecked = cur.lt
        }
        val rtBox = CheckBox(this).apply {
            text = skinLabel("btnRT", "RT"); textSize = 14f; isChecked = cur.rt
        }
        col.addView(ltBox)
        col.addView(rtBox)

        fun stickSpinner(labelRes: Int, x: Float, y: Float): Spinner {
            col.addView(TextView(this).apply {
                text = getString(labelRes); textSize = 13f; setPadding(0, 20, 0, 4)
            })
            return Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@ControllerActivity,
                    android.R.layout.simple_spinner_item,
                    MacroDef.DIRS.map { it.first }
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(MacroDef.dirIndexOf(x, y))
            }.also { col.addView(it) }
        }
        val spL = stickSpinner(R.string.el_left_stick, cur.lx, cur.ly)
        val spR = stickSpinner(R.string.el_right_stick, cur.rx, cur.ry)

        val scroll = ScrollView(this).apply { addView(col) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.macro_edit_title, if (cur.name.isBlank()) "M${idx + 1}" else cur.name))
            .setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var bits = 0
                for ((bit, box) in boxes) if (box.isChecked) bits = bits or (1 shl bit)
                val (lx, ly) = MacroDef.DIRS[spL.selectedItemPosition].second
                val (rx, ry) = MacroDef.DIRS[spR.selectedItemPosition].second
                macros = macros + (key to MacroDef(
                    name = nameInput.text.toString().trim(),
                    bits = bits, lt = ltBox.isChecked, rt = rtBox.isChecked,
                    lx = lx, ly = ly, rx = rx, ry = ry
                ))
                applyMacrosToUi()
                Toast.makeText(this, R.string.macro_saved_hint, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.macro_clear) { _, _ ->
                macros = macros + (key to MacroDef())
                applyMacrosToUi()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- perfis ----------

    private fun refreshProfiles() {
        val names = ControllerProfileStore.names(this)
        profileAdapter.clear()
        profileAdapter.add(getString(R.string.profiles_hint))
        profileAdapter.addAll(names)
        profileAdapter.notifyDataSetChanged()
        findViewById<Spinner>(R.id.spProfiles).setSelection(0)
    }

    private fun askSaveProfile() {
        val profile = collectProfile()
        if (profile.blocks.isEmpty()) return
        val input = EditText(this).apply {
            hint = getString(R.string.profile_name_hint)
            setText(ControllerProfileStore.last(this@ControllerActivity) ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_profile_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Perfil ${System.currentTimeMillis()}" }
                ControllerProfileStore.save(this, name, profile)
                refreshProfiles()
                Toast.makeText(this, getString(R.string.profile_saved, name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedProfile() {
        val sp = findViewById<Spinner>(R.id.spProfiles)
        if (sp.selectedItemPosition <= 0) return
        val name = profileAdapter.getItem(sp.selectedItemPosition) ?: return
        ControllerProfileStore.delete(this, name)
        refreshProfiles()
        Toast.makeText(this, getString(R.string.profile_deleted, name), Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    /** Esconde botões de sistema (voltar/home/recentes). Respeita o switch da conexão. */
    private fun applyImmersive() {
        val immersive = getSharedPreferences("phonepad_ui", MODE_PRIVATE)
            .getBoolean("immersive", true)
        if (!immersive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    override fun onDestroy() {
        if (isFinishing) Session.stop()
        super.onDestroy()
    }
}
