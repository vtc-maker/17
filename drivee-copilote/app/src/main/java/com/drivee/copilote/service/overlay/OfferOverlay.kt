package com.drivee.copilote.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.drivee.copilote.R
import com.drivee.copilote.data.prefs.AppColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ⭐ POP-UP D'OFFRE — affichée par le SERVICE D'ACCESSIBILITÉ lui-même
 * (fenêtre TYPE_ACCESSIBILITY_OVERLAY) :
 *  - aucune permission "superposition" ni foreground service nécessaire ;
 *  - vit aussi longtemps que le service d'accessibilité (donc en arrière-plan).
 *
 *   ┌──────────────────────────────────────────────┐
 *   │ UBER 1.78€/km      16€       74€/h            │
 *   │               13 min · 9 km                   │
 *   │ ━━━━━━━━━━━━━━━━━━━━━━|━━━━━━━━━● SUPER       │
 *   │                  seuil rentable               │
 *   └──────────────────────────────────────────────┘
 *
 * En haut de l'écran, déplaçable (glisser), un appui la ferme.
 * Toutes les opérations de vue sont faites sur le thread principal.
 */
class OfferOverlay(private val context: Context) {

    companion object {
        private const val TAG = "OfferOverlay"
        private const val AUTO_DISMISS_MS = 30_000L

        private const val BG = 0xF2111513.toInt()
        private const val STROKE = 0x26FFFFFF
        private const val TEXT = 0xFFF2F4F5.toInt()
        private const val TEXT_SOFT = 0xFFE3E6E8.toInt()
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val dismissRunnable: Runnable = Runnable { removeNow() }
    private val mono: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium) }.getOrNull()
            ?: Typeface.MONOSPACE
    }

    private var root: View? = null
    private var savedX = 0
    private var savedY: Int? = null

    private lateinit var accent: View
    private lateinit var platformTv: TextView
    private lateinit var perKmTv: TextView
    private lateinit var priceTv: TextView
    private lateinit var hourlyTv: TextView
    private lateinit var tripTv: TextView
    private lateinit var gauge: GaugeView

    fun show(d: OfferDisplay) {
        main.post {
            try {
                if (root == null) attach()
                bind(d)
                main.removeCallbacks(dismissRunnable)
                main.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
            } catch (t: Throwable) {
                Log.w(TAG, "Affichage de la pop-up impossible", t)
            }
        }
    }

    fun hide() {
        main.post { removeNow() }
    }

    /** Zone occupée par la pop-up à l'écran (exclue de l'OCR pour ne pas relire nos propres chiffres). */
    fun visibleRect(): Rect? {
        val v = root ?: return null
        if (v.width == 0) return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    // ------------------------------------------------------------------
    private fun attach() {
        val screenW = context.resources.displayMetrics.widthPixels
        val windowW = min(screenW - dpi(8f), dpi(520f))
        val view = build(windowW)

        val params = WindowManager.LayoutParams(
            windowW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = savedX
            y = savedY ?: (statusBarHeight() + dpi(4f))
        }

        enableDragAndTap(view, params)
        wm.addView(view, params)
        view.alpha = 0f
        view.translationY = -dp(16f)
        view.animate().alpha(1f).translationY(0f).setDuration(180).start()
        root = view
    }

    private fun removeNow() {
        main.removeCallbacks(dismissRunnable)
        val v = root ?: return
        root = null
        runCatching { wm.removeViewImmediate(v) }.onFailure { Log.w(TAG, "removeView a échoué", it) }
    }

    private fun build(windowW: Int): View {
        val outer = FrameLayout(context).apply { setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f)) }

        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(BG)
                cornerRadius = dp(26f)
                setStroke(dpi(1f), STROKE)
            }
            elevation = dp(8f)
        }
        outer.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        // Liseré lumineux en haut de la carte (couleur du verdict).
        accent = View(context)
        card.addView(accent, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpi(2f), Gravity.TOP).apply {
            leftMargin = dpi(70f); rightMargin = dpi(70f)
        })

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(18f), dpi(18f), dpi(18f), dpi(8f))
        }
        card.addView(content)

        // Taille des gros chiffres adaptée à la largeur de l'écran.
        val widthDp = windowW / context.resources.displayMetrics.density
        val bigSp = (widthDp / 13f).coerceIn(20f, 32f)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = true
        }
        platformTv = text(12f, TEXT).apply { letterSpacing = 0.1f; setPadding(0, 0, dpi(8f), 0) }
        perKmTv = text(bigSp, TEXT)
        priceTv = text(bigSp + 2f, TEXT)
        hourlyTv = text(bigSp, TEXT)
        // Logo VTC Copilot (aligné sur la ligne de base des chiffres).
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.app_logo)
            baselineAlignBottom = true
        }
        row.addView(logo, LinearLayout.LayoutParams(dpi(22f), dpi(22f)).apply { rightMargin = dpi(6f) })
        row.addView(platformTv)
        row.addView(perKmTv)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(priceTv)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(hourlyTv)
        content.addView(row)

        tripTv = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(TEXT_SOFT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        content.addView(tripTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpi(6f)
        })

        gauge = GaugeView(context, mono)
        content.addView(gauge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(66f)).apply {
            topMargin = dpi(4f)
        })

        return outer
    }

    private fun bind(d: OfferDisplay) {
        val color = AppColors.forScore(context, d.score)

        platformTv.text = d.platform
        perKmTv.text = withUnit(d.perKm?.let { fmt("%.2f€", it) } ?: "—", "/km")
        priceTv.text = formatPrice(d.price)
        priceTv.setTextColor(color)
        priceTv.setShadowLayer(dp(14f), 0f, 0f, withAlpha(color, 0.75f))
        hourlyTv.text = withUnit(d.hourly?.let { fmt("%.0f€", it) } ?: "—", "/h")
        tripTv.text = listOfNotNull(
            d.minutes?.let { (if (d.timeEstimated) "~" else "") + "${it.roundToInt()} min" },
            d.km?.let { "${formatKm(it)} km" }
        ).joinToString(" · ")

        accent.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT)
        )
        gauge.update(d.thresholdRatio, color, d.score.label)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDragAndTap(view: View, p: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        p.x = startX + dx.toInt()
                        p.y = (startY + dy.toInt()).coerceAtLeast(0)
                        savedX = p.x; savedY = p.y
                        runCatching { wm.updateViewLayout(view, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) removeNow() // simple appui = fermer
                    true
                }
                else -> false
            }
        }
    }

    // ------------------------------------------------------------------
    private fun text(sizeSp: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = mono
        maxLines = 1
        includeFontPadding = false
    }

    private fun withUnit(value: String, unit: String): CharSequence =
        SpannableStringBuilder(value).apply {
            val start = length
            append(unit)
            setSpan(RelativeSizeSpan(0.55f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun formatPrice(p: Double): String =
        if (abs(p - p.roundToInt()) < 0.05) fmt("%.0f€", p) else fmt("%.1f€", p)

    private fun formatKm(km: Double): String =
        if (km >= 10 || abs(km - km.roundToInt()) < 0.05) fmt("%.0f", km) else fmt("%.1f", km)

    private fun fmt(pattern: String, v: Double) = String.format(Locale.US, pattern, v)

    private fun withAlpha(color: Int, a: Float): Int =
        Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dpi(24f)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).roundToInt()
}
