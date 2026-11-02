package com.phonepad.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Joystick analógico simples. onMove(x,y) com -1..1, Y+ = cima. */
class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var onMove: ((Float, Float) -> Unit)? = null
    /** Chamado ao encostar o dedo (p/ feedback tátil, ex: vibração). */
    var onPress: (() -> Unit)? = null
    /** Quando false (modo edição de layout), ignora toques p/ o bloco poder ser arrastado. */
    var inputEnabled = true
    var baseColor: Int = 0xFFCCCCCC.toInt()
    var knobColor: Int = 0xFF1E88E5.toInt()
    private var knobX = 0f
    private var knobY = 0f
    private val base = Paint().apply { style = Paint.Style.FILL }
    private val knob = Paint().apply { style = Paint.Style.FILL }

    fun setColors(base: Int, knob: Int) {
        baseColor = base
        knobColor = knob
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        base.color = baseColor
        knob.color = knobColor
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.95f
        c.drawCircle(cx, cy, r, base)
        c.drawCircle(cx + knobX * r * 0.6f, cy - knobY * r * 0.6f, r * 0.4f, knob)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (e.action == MotionEvent.ACTION_DOWN) onPress?.invoke()
                val cx = width / 2f
                val cy = height / 2f
                val r = min(cx, cy) * 0.95f
                var dx = (e.x - cx) / (r * 0.6f)
                var dy = (cy - e.y) / (r * 0.6f) // Y+ cima
                val m = hypot(dx, dy)
                if (m > 1f) { dx /= m; dy /= m }
                knobX = dx; knobY = dy
                onMove?.invoke(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = 0f; knobY = 0f
                onMove?.invoke(0f, 0f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
