package com.drivee.copilote.service.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import com.drivee.copilote.R
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Bulle flottante « D » (comme celles d'Uber / Bolt) indiquant que Drivee tourne.
 *  - analyse active : anneau coloré qui « respire » (halo lumineux pulsé, effet Bolt)
 *  - en pause : anneau gris, immobile
 *  - un appui = ouvrir l'application ; glisser = déplacer
 */
class FloatingBubble(private val context: Context, private val onTap: () -> Unit) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null

    /** [ringColor] = couleur de l'anneau (réglages), utilisée si [active]. */
    fun show(active: Boolean, ringColor: Int) {
        main.post {
            runCatching {
                if (view == null) attach()
                view?.setState(active, ringColor)
            }
        }
    }

    fun hide() {
        main.post {
            view?.let { v ->
                v.stop()
                runCatching { wm.removeViewImmediate(v) }
            }
            view = null
        }
    }

    /** Zone occupée à l'écran (exclue de l'OCR). */
    fun visibleRect(): Rect? {
        val p = params ?: return null
        if (view == null) return null
        return Rect(p.x, p.y, p.x + p.width, p.y + p.height)
    }

    private fun attach() {
        val size = dpi(76f)
        val metrics = context.resources.displayMetrics
        val v = BubbleView(context)
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - size
            y = (metrics.heightPixels * 0.38f).roundToInt()
        }
        enableDragAndTap(v, p)
        wm.addView(v, p)
        view = v
        params = p
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDragAndTap(v: View, p: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        p.x = (startX + dx.toInt()).coerceAtLeast(0)
                        p.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragging) onTap(); true }
                else -> false
            }
        }
    }

    private fun dpi(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).roundToInt()

    /** Vue dessinée : halo pulsé + anneau + disque sombre + « D ». */
    private class BubbleView(context: Context) : View(context) {

        companion object {
            private const val BG = 0xF2111513.toInt()
            private const val PAUSED = 0xFF6B7280.toInt()
            private const val BREATH_MS = 1_400L
        }

        private var active = true
        private var ringColor = 0xFF4ADE80.toInt()
        private var phase = 0f // 0 → 1 → 0 (respiration)

        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F4F5.toInt()
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium) }.getOrNull()
                ?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.MONOSPACE
        }

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATH_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }

        fun setState(active: Boolean, ringColor: Int) {
            this.active = active
            this.ringColor = ringColor
            if (active) {
                if (!animator.isStarted) animator.start()
            } else {
                animator.cancel()
                phase = 0f
            }
            alpha = if (active) 1f else 0.75f
            invalidate()
        }

        fun stop() = animator.cancel()

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val coreR = dp(25f)
            val color = if (active) ringColor else PAUSED

            if (active) {
                // Halo qui respire : rayon et intensité pulsés.
                val haloR = coreR + dp(5f) + dp(8f) * phase
                haloPaint.shader = RadialGradient(
                    cx, cy, haloR,
                    intArrayOf(withAlpha(color, 0.55f + 0.25f * phase), withAlpha(color, 0.25f * (1f - phase)), Color.TRANSPARENT),
                    floatArrayOf(coreR / haloR, 0.8f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, haloR, haloPaint)
            }

            canvas.drawCircle(cx, cy, coreR, corePaint)
            ringPaint.color = if (active) withAlpha(color, 0.75f + 0.25f * phase) else color
            ringPaint.strokeWidth = dp(3f) + dp(1f) * phase
            canvas.drawCircle(cx, cy, coreR - ringPaint.strokeWidth / 2f, ringPaint)

            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("D", cx, baseline, textPaint)
        }

        private fun withAlpha(c: Int, a: Float) =
            Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

        private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    }
}
