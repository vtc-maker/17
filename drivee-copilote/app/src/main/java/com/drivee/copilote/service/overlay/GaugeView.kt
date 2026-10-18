package com.drivee.copilote.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * Jauge de rentabilité : barre colorée jusqu'au point, repère "seuil rentable"
 * et verdict (SUPER / MOYEN / MAUVAIS) au-dessus du point.
 *
 * Position du point = ratio × [THRESHOLD_POS] (le seuil est donc toujours au même endroit).
 */
@SuppressLint("ViewConstructor")
class GaugeView(context: Context, mono: Typeface) : View(context) {

    companion object {
        const val THRESHOLD_POS = 0.6f
        private const val TRACK = 0xFF252A28.toInt()
        private const val RING = 0xFF0C0F0E.toInt()
        private const val TICK = 0xFFE6E9EC.toInt()
        private const val SMALL_TEXT = 0xFFD5D9DD.toInt()
        private const val THRESHOLD_LABEL = "seuil rentable"
    }

    private var ratio: Double? = null
    private var color: Int = Color.GRAY
    private var label: String = ""

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RING }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TICK }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mono; textSize = sp(11f); color = SMALL_TEXT; textAlign = Paint.Align.CENTER
    }
    private val verdictText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(mono, Typeface.BOLD); textSize = sp(14f)
        textAlign = Paint.Align.CENTER; letterSpacing = 0.06f
    }

    fun update(ratio: Double?, color: Int, label: String) {
        this.ratio = ratio
        this.color = color
        this.label = label
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val left = dp(10f)
        val right = width - dp(10f)
        val w = right - left
        val y = height * 0.5f
        val th = dp(6f)

        c.drawRoundRect(left, y - th / 2, right, y + th / 2, th / 2, th / 2, trackPaint)

        val pos = ratio?.let { (it.toFloat() * THRESHOLD_POS).coerceIn(0.03f, 0.97f) }
        val dotX = pos?.let { left + w * it }

        if (dotX != null) {
            fillPaint.shader = LinearGradient(left, 0f, dotX, 0f, alpha(color, 0.35f), color, Shader.TileMode.CLAMP)
            c.drawRoundRect(left, y - th / 2, dotX, y + th / 2, th / 2, th / 2, fillPaint)
        }

        // Repère "seuil rentable".
        val tickX = left + w * THRESHOLD_POS
        c.drawRoundRect(tickX - dp(1.5f), y - dp(10f), tickX + dp(1.5f), y + dp(10f), dp(1.5f), dp(1.5f), tickPaint)
        val half = smallText.measureText(THRESHOLD_LABEL) / 2
        c.drawText(THRESHOLD_LABEL, tickX.coerceIn(left + half, right - half), y + dp(25f), smallText)

        if (dotX != null) {
            glowPaint.shader = RadialGradient(dotX, y, dp(18f), alpha(color, 0.55f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(dotX, y, dp(18f), glowPaint)
            c.drawCircle(dotX, y, dp(9f), ringPaint)
            dotPaint.color = color
            c.drawCircle(dotX, y, dp(6.5f), dotPaint)

            verdictText.color = color
            verdictText.setShadowLayer(dp(8f), 0f, 0f, alpha(color, 0.7f))
            val vHalf = verdictText.measureText(label) / 2
            c.drawText(label, dotX.coerceIn(left + vHalf, right - vHalf), y - dp(15f), verdictText)
        }
    }

    private fun alpha(color: Int, a: Float): Int =
        Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
