package dev.vulcanlauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

// Material 3 built from platform views: a colour scheme (the wallpaper's colours on Android 12+, otherwise
// generated from a seed colour) and the few components the launcher uses.

/** Linear mix of two ARGB colours; [f] 0 gives [a], 1 gives [b]. */
fun blend(a: Int, b: Int, f: Float): Int {
    fun ch(s: Int) = ((a shr s and 255) * (1 - f) + (b shr s and 255) * f).roundToInt() shl s
    return ch(24) or ch(16) or ch(8) or ch(0)
}

private fun hsl(h: Float, sat: Float, l: Float): Int {
    val c = (1 - abs(2 * l - 1)) * sat
    val x = c * (1 - abs((h / 60) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when ((h / 60).toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** Relative luminance (sRGB, 0–1). */
fun luminance(c: Int): Double {
    fun ch(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
    return 0.2126 * ch(c shr 16 and 255) + 0.7152 * ch(c shr 8 and 255) + 0.0722 * ch(c and 255)
}

/**
 * [seed]'s hue at M3 tone [tone] (CIE L*: 0 black, 100 white), with its saturation scaled by [satScale] and capped
 * at [satMax]. Builds tonal palettes when the system doesn't provide them.
 */
// ponytail: HSL hue and saturation searched to the right L*, not M3's HCT; chroma differs a little, contrast doesn't.
fun tone(seed: Int, tone: Int, satScale: Float, satMax: Float): Int {
    val r = (seed shr 16 and 255) / 255f
    val g = (seed shr 8 and 255) / 255f
    val b = (seed and 255) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val l0 = (max + min) / 2
    val s = if (d == 0f || l0 == 0f || l0 == 1f) 0f else d / (1 - abs(2 * l0 - 1))
    var h = when {
        d == 0f -> 0f
        max == r -> 60 * (((g - b) / d) % 6)
        max == g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    if (h < 0) h += 360
    val sat = minOf(s * satScale, satMax)
    if (tone <= 0) return 0xFF000000.toInt()
    if (tone >= 100) return 0xFFFFFFFF.toInt()
    val t = tone.toDouble()
    val y = if (t > 8) Math.pow((t + 16) / 116, 3.0) else t / 903.3
    // Luminance rises with HSL lightness for a fixed hue and saturation, so bisect for the target.
    var lo = 0f
    var hi = 1f
    repeat(24) { val mid = (lo + hi) / 2; if (luminance(hsl(h, sat, mid)) < y) lo = mid else hi = mid }
    return hsl(h, sat, (lo + hi) / 2)
}

/** M3 colour roles. Each ramp maps a tone (0–100) to a colour: primary, secondary, neutral, neutral variant. */
class Palette(val dark: Boolean, a1: (Int) -> Int, a2: (Int) -> Int, n1: (Int) -> Int, n2: (Int) -> Int) {
    private fun t(light: Int, dark: Int) = if (this.dark) dark else light
    val primary = a1(t(40, 80))
    val onPrimary = a1(t(100, 20))
    val primaryContainer = a1(t(90, 30))
    val onPrimaryContainer = a1(t(10, 90))
    val secondaryContainer = a2(t(90, 30))
    val onSecondaryContainer = a2(t(10, 90))
    val surface = n1(t(98, 6))
    val surfaceLow = n1(t(96, 10))
    val surfaceContainer = n1(t(94, 12))
    val surfaceHigh = n1(t(92, 17))
    val surfaceHighest = n1(t(90, 22))
    val onSurface = n1(t(10, 90))
    val onSurfaceVariant = n2(t(30, 80))
    val outline = n2(t(50, 60))
    val outlineVariant = n2(t(80, 30))
    val inverseSurface = n1(t(20, 90))
    val inverseOnSurface = n1(t(95, 20))
    val inversePrimary = a1(t(80, 40))
    val errorContainer = t(0xFFF9DEDC.toInt(), 0xFF8C1D18.toInt())
    val onErrorContainer = t(0xFF410E0B.toInt(), 0xFFF9DEDC.toInt())
}

fun seedPalette(seed: Int, dark: Boolean) = Palette(dark,
    { tone(seed, it, 1f, 0.85f) }, { tone(seed, it, 0.4f, 0.3f) }, { tone(seed, it, 0.15f, 0.05f) }, { tone(seed, it, 0.3f, 0.12f) })

/** Android 12+ wallpaper ("Material You") palette. Shade N of a system ramp is tone 100 − N/10; tones in between are mixed. */
@TargetApi(31)
@SuppressLint("DiscouragedApi")
private fun systemPalette(c: Context, dark: Boolean): Palette {
    val shades = intArrayOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
    fun ramp(name: String): (Int) -> Int {
        val colors = shades.map { c.getColor(c.resources.getIdentifier("system_${name}_$it", "color", "android")) }
        return { t ->
            val shade = (100 - t) * 10f
            val i = shades.indexOfLast { it <= shade }.coerceIn(0, shades.size - 2)
            blend(colors[i], colors[i + 1], ((shade - shades[i]) / (shades[i + 1] - shades[i])).coerceIn(0f, 1f))
        }
    }
    return Palette(dark, ramp("accent1"), ramp("accent2"), ramp("neutral1"), ramp("neutral2"))
}

/** "system" means the wallpaper's colours: the system palette on Android 12+, else a seed taken from the wallpaper. */
fun palette(c: Context, dark: Boolean, accent: String): Palette {
    if (accent == "system" && Build.VERSION.SDK_INT >= 31) return systemPalette(c, dark)
    val seed = runCatching { Color.parseColor(accent) }.getOrNull()
        ?: (if (Build.VERSION.SDK_INT >= 27) runCatching {
            WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
        }.getOrNull() else null)
        ?: 0xFF6750A4.toInt()
    return seedPalette(seed, dark)
}

// ---- Components ----

fun MainActivity.rounded(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Float = dp) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke != 0) setStroke(strokeWidth.roundToInt(), stroke)
}

/** M3 state layer: a ripple in the content colour, over [bg] and clipped to [radius]. */
fun MainActivity.pressable(bg: Drawable?, radius: Float = 0f, content: Int = m3.onSurface) =
    RippleDrawable(ColorStateList.valueOf(content and 0xFFFFFF or 0x1F000000), bg, rounded(Color.WHITE, radius))

fun MainActivity.text(s: CharSequence, sp: Float, color: Int = m3.onSurface, medium: Boolean = false) = TextView(this).apply {
    text = s
    textSize = sp
    setTextColor(color)
    if (medium) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
}

fun MainActivity.symbol(name: String, color: Int = m3.onSurfaceVariant, size: Float = 24f) = Symbol(name, color, (size * dp).roundToInt())

/** 48 dp round icon button. */
fun MainActivity.iconButton(name: String, color: Int = m3.onSurfaceVariant, onClick: (View) -> Unit) = ImageView(this).apply {
    setImageDrawable(symbol(name, color))
    scaleType = ImageView.ScaleType.CENTER
    background = pressable(null, 24 * dp)
    layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
    setOnClickListener(onClick)
}

/** Filled tonal button. */
fun MainActivity.tonalButton(label: String, onClick: () -> Unit) = text(label, 14f, m3.onSecondaryContainer, medium = true).apply {
    gravity = Gravity.CENTER
    minHeight = (40 * dp).toInt()
    setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
    background = pressable(rounded(m3.secondaryContainer, 20 * dp), 20 * dp, m3.onSecondaryContainer)
    setOnClickListener { onClick() }
}

/**
 * M3 list item: leading icon, headline, optional supporting text and trailing view. [dense] is the 48 dp menu item.
 * [tonalIcon] puts the icon in a primary-container circle, as on a settings home page.
 */
fun MainActivity.listItem(icon: Drawable?, title: CharSequence, summary: CharSequence? = null, trailing: View? = null,
                          dense: Boolean = false, tonalIcon: Boolean = false, onClick: ((View) -> Unit)?) = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = ((if (dense) 48 else if (summary == null) 56 else 72) * dp).toInt()
    val h = ((if (dense) 12 else 16) * dp).toInt()
    setPadding(h, (8 * dp).toInt(), if (dense) h else (24 * dp).toInt(), (8 * dp).toInt())
    if (onClick != null) { background = pressable(null); setOnClickListener(onClick) }
    if (icon != null) addView(ImageView(context).apply {
        setImageDrawable(icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        if (tonalIcon) {
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(m3.primaryContainer) }
        }
    }, LinearLayout.LayoutParams(((if (tonalIcon) 40 else 24) * dp).toInt(), ((if (tonalIcon) 40 else 24) * dp).toInt()).apply {
        marginEnd = ((if (dense) 12 else 16) * dp).toInt()
    })
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, if (dense) 14f else 16f).apply { maxLines = 2 })
        if (!summary.isNullOrEmpty()) addView(text(summary, 14f, m3.onSurfaceVariant).apply { maxLines = 3 })
    }, LinearLayout.LayoutParams(0, -2, 1f))
    trailing?.let { addView(it, ((it.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(-2, -2)).apply { marginStart = (16 * dp).toInt() }) }
}

fun MainActivity.divider() = View(this).apply {
    setBackgroundColor(m3.outlineVariant)
    layoutParams = LinearLayout.LayoutParams(-1, maxOf(1, dp.toInt())).apply { setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) }
}

/** M3 section header in a list. */
fun MainActivity.sectionHeader(title: String) = text(title, 14f, m3.primary, medium = true).apply {
    setPadding((16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
}

/** Blurs the home screen behind an open panel (Android 12+, when the Blur setting is on). */
fun MainActivity.blurHome(on: Boolean) {
    if (Build.VERSION.SDK_INT < 31) return
    val r = 24 * dp
    content.setRenderEffect(if (on && looks.on("blur")) android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP) else null)
}

/** M3 modal bottom sheet over a scrim, with a drag handle and an optional title. Tap the scrim to close. */
fun MainActivity.showSheet(title: String?, build: LinearLayout.() -> Unit) {
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(m3.surfaceLow)
            val r = 28 * dp
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        isClickable = true
        setPadding(0, 0, 0, insets.bottom + (12 * dp).toInt())
        addView(View(context).apply { background = rounded(m3.onSurfaceVariant and 0x66FFFFFF, 2 * dp) },
            LinearLayout.LayoutParams((32 * dp).toInt(), (4 * dp).toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, (16 * dp).toInt(), 0, (12 * dp).toInt()) })
        if (title != null) addView(text(title, 22f).apply { setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), (12 * dp).toInt()) })
        build()
    }
    val scrim = FrameLayout(this).apply {
        setBackgroundColor(0x52000000)
        setOnClickListener { closeOverlay() }
        addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(body) },
            FrameLayout.LayoutParams(minOf(root.width, (640 * dp).toInt()).takeIf { it > 0 } ?: -1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = insets.top + (48 * dp).toInt()
            })
    }
    showOverlay(scrim)
    body.translationY = root.height.toFloat()
    body.animate().translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator(2f)).start()
}

/** Dialogs: M3 surface, 28 dp corners, primary-coloured buttons and a lighter scrim. */
fun MainActivity.styleDialog(d: AlertDialog) {
    d.window?.apply {
        setBackgroundDrawable(InsetDrawable(rounded(m3.surfaceHigh, 28 * dp), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt()))
        setDimAmount(0.32f)
    }
    for (b in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) d.getButton(b)?.apply {
        setTextColor(m3.primary)
        isAllCaps = false
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
}

/** M3 switch: 52×32 dp track, thumb that grows when on. The row around it handles taps. */
@SuppressLint("ViewConstructor")
class Toggle(private val a: MainActivity, on: Boolean) : View(a) {
    private var pos = if (on) 1f else 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var checked = on
        set(v) {
            field = v
            ValueAnimator.ofFloat(pos, if (v) 1f else 0f).apply {
                duration = 180
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            }.start()
        }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((52 * a.dp).toInt(), (32 * a.dp).toInt())

    override fun onDraw(canvas: Canvas) {
        val d = a.dp
        val p = a.m3
        val track = RectF(d, d, width - d, height - d)
        paint.style = Paint.Style.FILL
        paint.color = blend(p.surfaceHighest, p.primary, pos)
        canvas.drawRoundRect(track, track.height() / 2, track.height() / 2, paint)
        if (pos < 1f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * d
            paint.color = p.outline
            paint.alpha = ((1 - pos) * 255).toInt()
            canvas.drawRoundRect(track, track.height() / 2, track.height() / 2, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = blend(p.outline, p.onPrimary, pos)
        canvas.drawCircle(16 * d + 20 * d * pos, height / 2f, (8 + 4 * pos) * d, paint)
    }
}
