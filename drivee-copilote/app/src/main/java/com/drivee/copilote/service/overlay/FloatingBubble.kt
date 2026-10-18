package com.drivee.copilote.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.drivee.copilote.R
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Bulle flottante (comme celles d'Uber / Bolt) indiquant que Drivee tourne.
 *  - anneau vert = analyse active, gris = en pause
 *  - un appui = pause / reprise ; glisser = déplacer
 */
class FloatingBubble(private val context: Context, private val onTap: () -> Unit) {

    companion object {
        private const val ACTIVE = 0xFF4ADE80.toInt()
        private const val PAUSED = 0xFF6B7280.toInt()
        private const val BG = 0xF2111513.toInt()
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(active: Boolean) {
        main.post {
            runCatching {
                if (view == null) attach()
                paint(active)
            }
        }
    }

    fun hide() {
        main.post {
            view?.let { v -> runCatching { wm.removeViewImmediate(v) } }
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
        val size = dpi(54f)
        val metrics = context.resources.displayMetrics
        val v = TextView(context).apply {
            text = "D"
            gravity = Gravity.CENTER
            setTextColor(0xFFF2F4F5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium) }.getOrNull()
                ?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.MONOSPACE
            elevation = dp(6f)
        }
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - size - dpi(8f)
            y = (metrics.heightPixels * 0.38f).roundToInt()
        }
        enableDragAndTap(v, p)
        wm.addView(v, p)
        view = v
        params = p
    }

    private fun paint(active: Boolean) {
        view?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BG)
            setStroke(dpi(3f), if (active) ACTIVE else PAUSED)
        }
        view?.alpha = if (active) 1f else 0.7f
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

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).roundToInt()
}
