package dev.vulcanlauncher

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Do two cell rectangles share at least one cell? Edges touching is not overlap. */
fun overlaps(ax: Int, ay: Int, aw: Int, ah: Int, bx: Int, by: Int, bw: Int, bh: Int) =
    ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah

/** Fixed grid; each child carries its cell rectangle in [CellParams]. */
class CellLayout(context: Context, val cols: Int, val rows: Int, val page: Int) : ViewGroup(context) {

    class CellParams(val x: Int, val y: Int, val w: Int, val h: Int) : LayoutParams(0, 0)

    val cellW get() = width / cols
    val cellH get() = height / rows

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val p = c.layoutParams as CellParams
            c.measure(
                MeasureSpec.makeMeasureSpec(w / cols * p.w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h / rows * p.h, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val p = c.layoutParams as CellParams
            c.layout(p.x * cellW, p.y * cellH, p.x * cellW + c.measuredWidth, p.y * cellH + c.measuredHeight)
        }
    }

    fun cellAt(px: Float, py: Float) =
        (px / cellW).toInt().coerceIn(0, cols - 1) to (py / cellH).toInt().coerceIn(0, rows - 1)

    /** True when the rectangle is inside the grid and overlaps no child except [ignore]. */
    fun isFree(x: Int, y: Int, w: Int, h: Int, ignore: View? = null): Boolean {
        if (x < 0 || y < 0 || x + w > cols || y + h > rows) return false
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c === ignore) continue
            val p = c.layoutParams as CellParams
            if (overlaps(x, y, w, h, p.x, p.y, p.w, p.h)) return false
        }
        return true
    }

    fun firstFree(w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0 until rows) for (x in 0 until cols) if (isFree(x, y, w, h)) return x to y
        return null
    }
}
