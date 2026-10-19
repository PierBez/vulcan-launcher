package dev.vulcanlauncher

import android.graphics.Rect
import android.view.View
import kotlin.math.abs

val EFFECTS = listOf("simple", "cube", "flip", "flipVertical", "glass", "accordion", "cardStack", "inOut", "revolvingDoor",
    "tablet", "throw", "wipe", "zoomFade")
val EFFECT_NAMES = listOf("Simple", "Cube", "Flip", "Flip vertical", "Glass", "Accordion", "Card stack", "In and out",
    "Revolving door", "Tablet", "Throw", "Wipe", "Zoom fade")

/**
 * How a page is drawn while scrolling. Translations are in page widths/heights, pivots and clip are fractions.
 * Pages are laid out side by side, so [tx] = -offset keeps a page in place on screen.
 */
data class PageTransform(
    val rotationX: Float = 0f, val rotationY: Float = 0f, val rotation: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f, val alpha: Float = 1f,
    val tx: Float = 0f, val pivotX: Float = 0.5f, val pivotY: Float = 0.5f, val clipLeft: Float = 0f,
)

/** [o] is the page's offset from the screen in page widths: 0 = on screen, -1 = one page left, 1 = one page right. */
fun pageTransform(effect: String, o: Float): PageTransform {
    if (o == 0f || abs(o) >= 1f) return PageTransform()
    val a = abs(o)
    return when (effect) {
        "cube" -> PageTransform(rotationY = 90 * o, pivotX = if (o < 0) 1f else 0f)
        "flip" -> PageTransform(rotationY = -180 * o, tx = -o, alpha = if (a < 0.5f) 1f else 0f)
        "flipVertical" -> PageTransform(rotationX = 180 * o, tx = -o, alpha = if (a < 0.5f) 1f else 0f)
        "glass" -> PageTransform(tx = -o / 2, alpha = 1 - a)
        "accordion" -> PageTransform(scaleX = 1 - a, pivotX = if (o < 0) 0f else 1f, tx = 0f)
        "cardStack" -> if (o > 0) PageTransform(tx = -o, scaleX = 1 - a / 4, scaleY = 1 - a / 4, alpha = 1 - a) else PageTransform()
        "inOut" -> PageTransform(scaleX = 1 - a * 0.3f, scaleY = 1 - a * 0.3f)
        "revolvingDoor" -> PageTransform(rotationY = -90 * o, pivotX = 0f)
        "tablet" -> PageTransform(rotationY = -30 * o)
        "throw" -> PageTransform(rotation = 25 * o, pivotY = 1f, alpha = 1 - a / 2)
        "wipe" -> if (o > 0) PageTransform(tx = -o, clipLeft = o) else PageTransform()
        "zoomFade" -> PageTransform(tx = -o, scaleX = 1 - a / 2, scaleY = 1 - a / 2, alpha = 1 - a)
        else -> PageTransform()
    }
}

fun View.applyTransform(t: PageTransform) {
    rotationX = t.rotationX; rotationY = t.rotationY; rotation = t.rotation
    scaleX = t.scaleX; scaleY = t.scaleY; alpha = t.alpha
    translationX = t.tx * width
    pivotX = t.pivotX * width; pivotY = t.pivotY * height
    clipBounds = if (t.clipLeft > 0f) Rect((t.clipLeft * width).toInt(), 0, width, height) else null
}
