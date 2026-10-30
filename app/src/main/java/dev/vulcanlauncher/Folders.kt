package dev.vulcanlauncher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

val PREVIEWS = listOf("grid", "stack", "line", "fan", "radial")
val PREVIEW_NAMES = listOf(
    R.string.preview_grid, R.string.preview_stack, R.string.preview_line, R.string.preview_fan,
    R.string.preview_radial,
)
val FOLDER_BGS = listOf("none", "circle", "disc", "platform", "square")
val FOLDER_BG_NAMES = listOf(
    R.string.folderbg_none, R.string.folderbg_circle, R.string.folderbg_disc, R.string.folderbg_platform,
    R.string.folderbg_square,
)

/** Where one preview icon goes inside a folder icon: centre and size as fractions of the icon, rotation in degrees. */
data class Slot(val cx: Float, val cy: Float, val size: Float, val rotation: Float = 0f)

/** Preview slots for the first [n] folder items, in drawing order of the items (item 0 first). */
fun folderPreview(style: String, n: Int, firstBig: Boolean): List<Slot> {
    if (n == 0) return emptyList()
    if (firstBig) return listOf(Slot(0.5f, 0.42f, 0.56f)) +
        listOf(Slot(0.25f, 0.82f, 0.26f), Slot(0.5f, 0.82f, 0.26f), Slot(0.75f, 0.82f, 0.26f)).take(n - 1)
    return when (style) {
        "stack" -> List(minOf(n, 3)) { Slot(0.42f + 0.08f * it, 0.42f + 0.08f * it, 0.56f) }
        "line" -> List(minOf(n, 3)) { Slot(0.2f + 0.3f * it, 0.5f, 0.28f) }
        "fan" -> listOf(Slot(0.5f, 0.52f, 0.52f), Slot(0.36f, 0.56f, 0.48f, -18f), Slot(0.64f, 0.56f, 0.48f, 18f)).take(n)
        "radial" -> List(minOf(n, 6)) {
            val a = -Math.PI / 2 + 2 * Math.PI * it / minOf(n, 6)
            Slot((0.5 + 0.28 * Math.cos(a)).toFloat(), (0.5 + 0.28 * Math.sin(a)).toFloat(), 0.3f)
        }
        else -> listOf(Slot(0.3f, 0.3f, 0.38f), Slot(0.7f, 0.3f, 0.38f), Slot(0.3f, 0.7f, 0.38f), Slot(0.7f, 0.7f, 0.38f)).take(n)
    }
}

/** A folder's home-screen icon: background plus previews of its first items. */
class FolderIcon(
    private val icons: List<Drawable>, private val style: String, private val bg: String,
    private val color: Int, private val clip: Boolean, private val firstBig: Boolean,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val b = RectF()
    private val oval = RectF()
    private val shape = Path()

    override fun draw(canvas: Canvas) {
        b.set(bounds)
        val w = b.width()
        shape.rewind()
        paint.style = Paint.Style.FILL
        paint.color = color
        when (bg) {
            "circle" -> shape.addCircle(b.centerX(), b.centerY(), w / 2, Path.Direction.CW)
            "disc" -> shape.addCircle(b.centerX(), b.centerY(), w / 2, Path.Direction.CW)
            "square" -> shape.addRoundRect(b, w / 5, w / 5, Path.Direction.CW)
            "platform" -> shape.addOval(oval.apply { set(b.left, b.bottom - w * 0.3f, b.right, b.bottom) }, Path.Direction.CW)
        }
        if (bg != "none") canvas.drawPath(shape, paint)
        if (bg == "disc") {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w / 24
            paint.color = color or 0xFF000000.toInt()
            canvas.drawCircle(b.centerX(), b.centerY(), w / 2 - w / 48, paint)
        }
        canvas.save()
        if (clip && (bg == "circle" || bg == "disc" || bg == "square")) canvas.clipPath(shape)
        val slots = folderPreview(style, icons.size, firstBig)
        for (i in slots.indices.reversed()) { // first item on top
            val s = slots[i]
            val half = s.size * w / 2
            val cx = b.left + s.cx * w
            val cy = b.top + s.cy * b.height()
            canvas.save()
            canvas.rotate(s.rotation, cx, cy)
            icons[i].setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            icons[i].draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}
    @Deprecated("Required override") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Icon for an action shortcut, web search or contact: a tonal disc with a [symbol] or a short [text]. */
class GlyphIcon(private val bg: Int, private val fg: Int, private val text: String? = null, private val symbol: String? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.color = bg
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), b.width() / 2f, paint)
        if (symbol != null) {
            val q = b.width() / 4
            Symbol(symbol, fg, q * 2).apply { setBounds(b.left + q, b.top + q, b.right - q, b.bottom - q) }.draw(canvas)
            return
        }
        paint.color = fg
        paint.textSize = b.height() * 0.42f
        canvas.drawText(text ?: "", b.exactCenterX(), b.exactCenterY() - (paint.ascent() + paint.descent()) / 2, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}
    @Deprecated("Required override") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
