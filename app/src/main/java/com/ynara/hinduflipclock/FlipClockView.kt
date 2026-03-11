package com.ynara.hinduflipclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.roundToInt

class FlipClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Card(
        var cur: Int = 0,
        var nxt: Int = 0,
        var prog: Float = 0f,  // 0..1 animation progress
        var animating: Boolean = false
    )

    private val cards = Array(6) { Card() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1C")
    }
    private val splitPaint = Paint().apply {
        color = Color.BLACK
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAA00")
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAA00")
        textAlign = Paint.Align.CENTER
    }

    private val animators = arrayOfNulls<ValueAnimator>(6)

    private var cardW = 0f
    private var cardH = 0f
    private var gap = 0f

    fun setDigitColor(color: Int) {
        textPaint.color = color
        sepPaint.color = color
        invalidate()
    }

    fun setCardBackgroundColor(color: Int) {
        bgPaint.color = color
        invalidate()
    }

    fun setTime(h: Int, m: Int, s: Int) {
        val values = intArrayOf(h / 10, h % 10, m / 10, m % 10, s / 10, s % 10)
        for (i in values.indices) {
            if (values[i] != cards[i].cur && !cards[i].animating) {
                cards[i].nxt = values[i]
                cards[i].animating = true
                val idx = i
                animators[i]?.cancel()
                animators[i] = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 350
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        cards[idx].prog = anim.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            cards[idx].cur = cards[idx].nxt
                            cards[idx].animating = false
                            cards[idx].prog = 0f
                            invalidate()
                        }
                    })
                    start()
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        gap = w * 0.012f
        cardW = (w - 8 * gap) / (6f + 2f * 0.45f)
        cardH = h * 0.88f
        textPaint.textSize = cardH * 0.72f
        sepPaint.textSize  = cardH * 0.55f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * 0.22f).roundToInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val topY = (height - cardH) / 2f
        var x = gap

        val sepW = cardW * 0.45f
        val slots = listOf(0, 1, -1, 2, 3, -1, 4, 5)

        for (slot in slots) {
            if (slot == -1) {
                drawSeparator(canvas, x, topY, sepW, cardH)
                x += sepW + gap
            } else {
                drawCard(canvas, cards[slot], x, topY, cardW, cardH)
                x += cardW + gap
            }
        }
    }

    private fun drawSeparator(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val cx = x + w / 2f
        val third = h / 3f
        val r = w * 0.3f
        canvas.drawCircle(cx, y + third, r, sepPaint)
        canvas.drawCircle(cx, y + third * 2f, r, sepPaint)
    }

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float, w: Float, h: Float) {
        val cx = x + w / 2f
        val cy = y + h / 2f
        val r = h * 0.08f

        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, bgPaint)
        canvas.drawRect(x, cy - 1.5f, x + w, cy + 1.5f, splitPaint)

        val prog = card.prog

        if (!card.animating) {
            drawDigitFull(canvas, card.cur.toString(), cx, y, w, h)
        } else {
            canvas.save()
            canvas.clipRect(x, cy, x + w, y + h)
            drawDigitFull(canvas, if (prog < 0.5f) card.cur.toString() else card.nxt.toString(),
                cx, y, w, h)
            canvas.restore()

            val scaleY = if (prog < 0.5f) 1f - prog * 2f else (prog - 0.5f) * 2f
            val digitStr = if (prog < 0.5f) card.cur.toString() else card.nxt.toString()
            canvas.save()
            canvas.clipRect(x, y, x + w, cy)
            canvas.scale(1f, scaleY, cx, cy)
            drawDigitFull(canvas, digitStr, cx, y, w, h)
            canvas.restore()
        }
    }

    private fun drawDigitFull(canvas: Canvas, text: String, cx: Float, top: Float, w: Float, h: Float) {
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val baseline = top + h / 2f - textHeight / 2f - fm.ascent
        canvas.drawText(text, cx, baseline, textPaint)
    }
}
