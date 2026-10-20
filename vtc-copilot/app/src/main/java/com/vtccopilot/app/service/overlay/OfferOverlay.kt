package com.vtccopilot.app.service.overlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.text.Layout
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
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.vtccopilot.app.R
import com.vtccopilot.app.data.prefs.AppColors
import com.vtccopilot.app.data.prefs.PopupLayout
import com.vtccopilot.app.data.prefs.PopupPrefs
import com.vtccopilot.app.data.prefs.PopupTheme
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

    /** Mise en page + style avec lesquels la vue actuelle a été construite. */
    private var layout = PopupLayout.COMPLET
    private var theme = PopupTheme.ACTUEL

    private lateinit var card: FrameLayout
    private lateinit var accent: View
    private lateinit var platformTv: TextView
    private lateinit var perKmTv: TextView
    private lateinit var priceTv: TextView
    private lateinit var hourlyTv: TextView
    private var tripTv: TextView? = null
    private var verdictTv: TextView? = null
    private var gauge: GaugeView? = null
    private var neonBorder: GradientDrawable? = null
    private var neonAnimator: ValueAnimator? = null
    private var bigSp = 26f
    private var rowAvailablePx = 0

    fun show(d: OfferDisplay) {
        main.post {
            try {
                val l = PopupPrefs.layout(context)
                val t = PopupPrefs.theme(context)
                if (root != null && (l != layout || t != theme)) removeNow() // style changé dans les réglages
                layout = l
                theme = t
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
        neonAnimator?.cancel()
        neonAnimator = null
        val v = root ?: return
        root = null
        runCatching { wm.removeViewImmediate(v) }.onFailure { Log.w(TAG, "removeView a échoué", it) }
    }

    /** Police du style : mono (Actuel / Néon), serif (Classe), machine à écrire grasse (Rétro). */
    private fun popupTypeface(): Typeface = when (theme) {
        PopupTheme.CLASSE -> Typeface.create("serif", Typeface.NORMAL)
        PopupTheme.NEON -> Typeface.create(mono, Typeface.BOLD)
        PopupTheme.RETRO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        PopupTheme.ACTUEL -> mono
    }

    private fun build(windowW: Int): View {
        val compact = layout == PopupLayout.COMPACT
        val outer = FrameLayout(context).apply { setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f)) }

        neonBorder = null
        val radius = dp(if (compact) min(theme.radiusDp, 18f) else theme.radiusDp)
        card = FrameLayout(context).apply {
            background = if (theme == PopupTheme.NEON) neonBackground(radius) else GradientDrawable().apply {
                setColor(theme.bg)
                cornerRadius = radius
                setStroke(dpi(theme.strokeDp), theme.stroke)
            }
            elevation = dp(8f)
        }
        if (theme == PopupTheme.NEON) {
            // Halo rose diffus autour de la carte.
            outer.background = GradientDrawable().apply {
                cornerRadius = radius + dp(6f)
                setColor(Color.TRANSPARENT)
                setStroke(dpi(6f), withAlpha(PopupTheme.NEON_PINK, 0.35f))
            }
        }
        outer.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        // Liseré lumineux en haut de la carte (couleur du verdict).
        accent = View(context)
        card.addView(accent, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpi(2f), Gravity.TOP).apply {
            leftMargin = dpi(70f); rightMargin = dpi(70f)
        })

        val padH = if (compact) 12f else 18f
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (compact) setPadding(dpi(padH), dpi(10f), dpi(padH), dpi(10f))
            else setPadding(dpi(padH), dpi(18f), dpi(padH), dpi(if (layout == PopupLayout.XXL) 14f else 8f))
        }
        card.addView(content)

        // Taille des gros chiffres adaptée à la largeur de l'écran.
        val widthDp = windowW / context.resources.displayMetrics.density
        bigSp = (widthDp / 13f).coerceIn(20f, 32f)
        // Largeur utile = fenêtre − marges extérieures (2×6dp) − marges du contenu.
        rowAvailablePx = windowW - dpi(12f) - dpi(2 * padH)

        platformTv = text(12f, theme.textSoft).apply { letterSpacing = 0.1f; setPadding(0, 0, dpi(8f), 0) }
        perKmTv = text(bigSp, theme.text)
        priceTv = text(bigSp + 2f, theme.text)
        hourlyTv = text(bigSp, theme.text)
        tripTv = null
        verdictTv = null
        gauge = null

        if (layout == PopupLayout.XXL) {
            verdictTv = text(bigSp * 2f, theme.text).apply { gravity = Gravity.CENTER }
            content.addView(verdictTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(10f)
            })
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = true
            gravity = Gravity.CENTER_VERTICAL
        }
        // Logo VTC Copilot (aligné sur la ligne de base des chiffres).
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.app_logo)
            baselineAlignBottom = true
        }
        val logoDp = if (compact) 18f else 22f
        row.addView(logo, LinearLayout.LayoutParams(dpi(logoDp), dpi(logoDp)).apply { rightMargin = dpi(6f) })
        row.addView(platformTv)
        if (compact) {
            // Une seule ligne fine : prix · €/km · €/h · verdict
            row.addView(priceTv)
            row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(perKmTv)
            row.addView(View(context), LinearLayout.LayoutParams(dpi(8f), 1))
            row.addView(hourlyTv)
            row.addView(View(context), LinearLayout.LayoutParams(dpi(10f), 1))
            verdictTv = text(bigSp * 0.75f, theme.text)
            row.addView(verdictTv)
        } else {
            row.addView(perKmTv)
            row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(priceTv)
            row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(hourlyTv)
        }
        content.addView(row)

        if (!compact) {
            tripTv = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(theme.textSoft)
                typeface = if (theme == PopupTheme.ACTUEL) Typeface.create("sans-serif-medium", Typeface.NORMAL) else popupTypeface()
                gravity = Gravity.CENTER
                maxLines = 1
            }
            content.addView(tripTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(6f)
            })
        }

        if (layout == PopupLayout.COMPLET) {
            gauge = GaugeView(context, popupTypeface(), theme).also {
                content.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(66f)).apply {
                    topMargin = dpi(4f)
                })
            }
        }

        return outer
    }

    private fun bind(d: OfferDisplay) {
        val color = theme.verdictColor(AppColors.forScore(context, d.score))
        val label = PopupPrefs.label(context, d.score)

        platformTv.text = d.platform
        perKmTv.text = withUnit(d.perKm?.let { fmt("%.2f€", it) } ?: "—", "/km")
        priceTv.text = formatPrice(d.price)
        priceTv.setTextColor(color)
        glow(priceTv, color, 14f)
        hourlyTv.text = withUnit(d.hourly?.let { fmt("%.0f€", it) } ?: "—", "/h")
        tripTv?.text = listOfNotNull(
            d.minutes?.let { (if (d.timeEstimated) "~" else "") + "${it.roundToInt()} min" },
            d.km?.let { "${formatKm(it)} km" }
        ).joinToString(" · ")
        verdictTv?.let {
            it.text = label
            it.setTextColor(color)
            glow(it, color, 12f)
        }
        if (theme == PopupTheme.NEON) {
            // Chaque chiffre dans sa couleur fluo, avec halo.
            platformTv.setTextColor(PopupTheme.NEON_YELLOW); glow(platformTv, PopupTheme.NEON_YELLOW, 6f)
            perKmTv.setTextColor(PopupTheme.NEON_CYAN); glow(perKmTv, PopupTheme.NEON_CYAN, 8f)
            hourlyTv.setTextColor(PopupTheme.NEON_PINK); glow(hourlyTv, PopupTheme.NEON_PINK, 8f)
            tripTv?.let { it.setTextColor(PopupTheme.NEON_LIME); glow(it, PopupTheme.NEON_LIME, 5f) }
            startNeonAnimation()
        }
        fitTexts()

        if (theme.strokeIsVerdict) (card.background as? GradientDrawable)?.setStroke(dpi(theme.strokeDp), color)
        accent.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT)
        )
        gauge?.update(d.thresholdRatio, color, label)
    }

    /** Fond Néon : bordure dégradée cyan → rose → jaune (animée) autour d'un fond violet nuit. */
    private fun neonBackground(radius: Float): LayerDrawable {
        val border = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(PopupTheme.NEON_CYAN, PopupTheme.NEON_PINK, PopupTheme.NEON_YELLOW, PopupTheme.NEON_CYAN)
        ).apply { cornerRadius = radius }
        val inner = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFA1A0033.toInt(), theme.bg)
        ).apply { cornerRadius = (radius - dp(theme.strokeDp)).coerceAtLeast(0f) }
        neonBorder = border
        val inset = dpi(theme.strokeDp)
        return LayerDrawable(arrayOf(border, inner)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    /** Fait tourner les couleurs de la bordure Néon en continu (effet enseigne lumineuse). */
    private fun startNeonAnimation() {
        if (neonAnimator != null) return
        val border = neonBorder ?: return
        val base = intArrayOf(PopupTheme.NEON_CYAN, PopupTheme.NEON_PINK, PopupTheme.NEON_YELLOW, PopupTheme.NEON_LIME)
        val evaluator = ArgbEvaluator()
        neonAnimator = ValueAnimator.ofFloat(0f, base.size.toFloat()).apply {
            duration = 2_400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val colors = IntArray(4) { i ->
                    val pos = (t + i) % base.size
                    val from = base[pos.toInt() % base.size]
                    val to = base[(pos.toInt() + 1) % base.size]
                    evaluator.evaluate(pos - pos.toInt(), from, to) as Int
                }
                border.colors = colors
            }
            start()
        }
    }

    private fun glow(tv: TextView, color: Int, radiusDp: Float) {
        if (theme.glow > 0f) tv.setShadowLayer(dp(radiusDp * theme.glow), 0f, 0f, withAlpha(color, 0.75f))
        else tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
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

    /**
     * Ajuste les tailles pour que rien ne soit coupé :
     *  - la ligne de chiffres (et le verdict en mode Compact) tient sur la largeur de la carte ;
     *  - le verdict XXL (texte perso, 12 caractères / 2 emojis max) remplit la largeur sans déborder.
     */
    private fun fitTexts() {
        if (rowAvailablePx <= 0) return
        val compact = layout == PopupLayout.COMPACT
        val k = if (compact) 0.72f else 1f
        val rowViews = buildList {
            add(platformTv to 12f * (if (compact) 0.9f else 1f))
            add(perKmTv to bigSp * k)
            add(priceTv to (bigSp + 2f) * k)
            add(hourlyTv to bigSp * k)
            if (compact) verdictTv?.let { add(it to bigSp * 0.75f) }
        }
        val fixedDp = if (compact) 18f + 6f + 8f + 8f + 10f + 8f else 22f + 6f + 8f + 16f
        fun apply(f: Float) = rowViews.forEach { (tv, base) -> tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, base * f) }
        fun needed(): Float = dp(fixedDp) + rowViews.sumOf { (tv, _) -> Layout.getDesiredWidth(tv.text, tv.paint).toDouble() }.toFloat()

        apply(1f)
        val need = needed()
        if (need > rowAvailablePx) apply((rowAvailablePx / need) * 0.97f)

        if (layout == PopupLayout.XXL) verdictTv?.let { tv ->
            val base = bigSp * 2f
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, base)
            val w = Layout.getDesiredWidth(tv.text, tv.paint)
            val avail = rowAvailablePx * 0.96f
            if (w > avail) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, base * avail / w)
        }
    }

    // ------------------------------------------------------------------
    private fun text(sizeSp: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = popupTypeface()
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
