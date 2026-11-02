package com.phonepad.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Área flutuante de joystick: onde o dedo tocar vira a origem do analógico.
 *
 * Cada instância cobre a tela toda (match_parent) mas só consome toques da sua
 * [side] (metade esquerda/direita) e abaixo de [topExcludePx] (faixa dos
 * gatilhos / botões de sistema no topo). Como fica ATRÁS dos botões no
 * FrameLayout, toques em cima de botões vão para os botões primeiro — só o
 * "vazio" vira joystick. É assim que D-pad, L3 etc. continuam funcionando.
 *
 * onMove(x,y) com -1..1, Y+ = cima — igual ao [JoystickView].
 */
class FloatingStickArea @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val LEFT = 0
        const val RIGHT = 1
    }

    var onMove: ((Float, Float) -> Unit)? = null
    /** Chamado ao encostar o dedo (p/ feedback tátil, ex: vibração). */
    var onPress: (() -> Unit)? = null
    /** Lado da tela que esta área atende. */
    var side: Int = LEFT
    /** Faixa do topo (px) que NÃO vira joystick — gatilhos ficam ali. */
    var topExcludePx: Float = 0f
    /** Quando false (modo edição), ignora toques e desenha a zona p/ referência. */
    var inputEnabled = true
    var baseColor: Int = 0xFFCCCCCC.toInt()
    var knobColor: Int = 0xFF1E88E5.toInt()

    private var activePointer = MotionEvent.INVALID_POINTER_ID
    private var originX = 0f
    private var originY = 0f
    private var knobDx = 0f
    private var knobDy = 0f
    private var touching = false

    private val base = Paint().apply { style = Paint.Style.FILL; alpha = 110 }
    private val knob = Paint().apply { style = Paint.Style.FILL; alpha = 160 }
    private val zonePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        alpha = 120
    }

    fun setColors(base: Int, knob: Int) {
        baseColor = base
        knobColor = knob
        invalidate()
    }

    private fun radiusPx(): Float {
        val dp = 56f // curso do polegar até saturar em 1.0
        return dp * resources.displayMetrics.density
    }

    private fun inZone(x: Float, y: Float): Boolean {
        if (y < topExcludePx) return false
        val half = width / 2f
        return if (side == LEFT) x < half else x >= half
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (touching) {
            val r = radiusPx()
            base.color = baseColor
            knob.color = knobColor
            c.drawCircle(originX, originY, r, base)
            c.drawCircle(
                originX + knobDx * r * 0.6f,
                originY - knobDy * r * 0.6f,
                r * 0.4f, knob
            )
        } else if (!inputEnabled) {
            // Modo edição: mostra onde a zona atua (metade da tela, abaixo do topo).
            zonePaint.color = knobColor
            val l = if (side == LEFT) 0f else width / 2f
            c.drawRect(
                l + 4f, topExcludePx + 4f,
                l + width / 2f - 4f, height.toFloat() - 4f, zonePaint
            )
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (touching) return false // um dedo por zona
                val i = e.actionIndex
                if (!inZone(e.getX(i), e.getY(i))) return false
                activePointer = e.getPointerId(i)
                originX = e.getX(i)
                originY = e.getY(i)
                knobDx = 0f; knobDy = 0f
                touching = true
                onPress?.invoke()
                onMove?.invoke(0f, 0f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return false
                val r = radiusPx().coerceAtLeast(1f)
                var dx = (e.getX(i) - originX) / r
                var dy = (originY - e.getY(i)) / r // Y+ cima
                val m = hypot(dx, dy)
                if (m > 1f) { dx /= m; dy /= m }
                knobDx = dx; knobDy = dy
                onMove?.invoke(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val i = e.actionIndex
                val isMine = e.actionMasked == MotionEvent.ACTION_CANCEL ||
                        e.getPointerId(i) == activePointer
                if (!isMine) return false
                touching = false
                activePointer = MotionEvent.INVALID_POINTER_ID
                knobDx = 0f; knobDy = 0f
                onMove?.invoke(0f, 0f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /** Tamanho visual do joystick (p/ previews). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        min(measuredWidth, measuredHeight)
    }
}
