package dev.vulcanlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Page indicator under the home pages: dots or a sliding line. */
class Indicator(context: Context, private val count: Int, private val style: String, private val color: Int) : View(context) {
    var position = 0f
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = context.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (count < 2) return
        val cy = height / 2f
        if (style == "line") {
            val seg = 24 * d
            val left = (width - seg * count) / 2
            paint.color = color and 0x55FFFFFF
            canvas.drawRoundRect(RectF(left, cy - d, left + seg * count, cy + d), d, d, paint)
            paint.color = color
            canvas.drawRoundRect(RectF(left + seg * position, cy - 1.5f * d, left + seg * (position + 1), cy + 1.5f * d), d, d, paint)
            return
        }
        // M3-style dots: the current page stretches into a pill.
        val dot = 6 * d
        val gap = 6 * d
        val widths = List(count) { i -> dot + 12 * d * (1 - kotlin.math.abs(position - i)).coerceIn(0f, 1f) }
        var x = (width - widths.sum() - gap * (count - 1)) / 2
        for (i in 0 until count) {
            val near = (widths[i] - dot) / (12 * d)
            paint.color = color
            paint.alpha = (110 + 145 * near).toInt()
            canvas.drawRoundRect(RectF(x, cy - dot / 2, x + widths[i], cy + dot / 2), dot / 2, dot / 2, paint)
            x += widths[i] + gap
        }
    }
}

/** M3 search bar on the home screen: tap opens drawer search, the mic starts voice search. */
fun MainActivity.searchBar(): View {
    val color = themed("searchColor", m3.surfaceHigh)
    val light = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 > 150
    val fg = if (looks.str("searchColor") == "system") m3.onSurfaceVariant else if (light) 0xFF444746.toInt() else Color.WHITE
    val radius = when (looks.str("searchStyle")) { "rect" -> 0f; "rounded" -> 12 * dp; else -> 28 * dp }
    val pill = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = pressable(rounded(color and 0xFFFFFF or 0xF2000000.toInt(), radius), radius, fg)
        elevation = 2 * dp
        setPadding((16 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        addView(ImageView(context).apply { setImageDrawable(symbol("search", fg)) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (16 * dp).toInt() })
        addView(text("Search", 16f, fg), LinearLayout.LayoutParams(0, -2, 1f))
        addView(iconButton("mic", fg) { perform("voice") })
        setOnClickListener { perform("search") }
        setOnLongClickListener { homeMenu(); true }
    }
    return FrameLayout(this).apply {
        val p = (16 * dp).toInt()
        setPadding(p, p / 2, p, p / 2)
        addView(pill, FrameLayout.LayoutParams(-1, (56 * dp).toInt()))
    }
}

fun MainActivity.showOverlay(v: View, onClose: () -> Unit = {}) {
    closeOverlay()
    blurHome(true)
    root.addView(v)
    overlay = v
    overlayClosed = onClose
}

fun MainActivity.closeOverlay() {
    val v = overlay ?: return
    overlay = null
    settingsPage = null
    settingsScroll = null
    root.removeView(v)
    if (!drawer.visible) blurHome(false)
    overlayClosed().also { overlayClosed = {} }
}

/** Opens a home-screen folder; its name is editable in the window. */
fun MainActivity.openFolder(item: Item) {
    openFolderWindow(item.label, item.children, { name ->
        val i = items.indexOf(item)
        if (i >= 0 && name != item.label) { items[i] = item.copy(label = name.ifEmpty { "Folder" }); save(); render() }
    }) { FolderChild(item, it) }
}

/** Shows [children] in a pop-up or full-screen folder window. [payload] makes the drag payload for child [i]. */
fun MainActivity.openFolderWindow(title: String, children: List<Item>, onRename: ((String) -> Unit)?, payload: (Int) -> Any) {
    drawer.hide()
    val full = looks.str("folderWindow") == "fullscreen"
    val base = style("")
    val style = IconStyle(base.sizePx, true, base.labelSize, panelText, false, base.iconShadow, font = base.font, lines = base.lines)
    val cols = minOf(4, maxOf(3, ceil(sqrt(children.size.toDouble())).toInt()))
    val rows = maxOf(1, ceil(children.size / cols.toDouble()).toInt())
    val cellH = base.sizePx + (44 * dp).toInt()
    val cellW = base.sizePx + (36 * dp).toInt()
    val cell = CellLayout(this, cols, rows, 0)
    children.forEachIndexed { i, c ->
        val (label, icon) = resolve(c) ?: return@forEachIndexed
        cell.addView(IconView(this, label, icon, style, badgeStyle(), c.pkg).apply {
            layoutParams = CellLayout.CellParams(i % cols, i / cols, 1, 1)
            if (looks.str("badges") != "off") badge(image, c.pkg)
            setOnClickListener { v -> closeOverlay(); open(c, v) }
            setOnLongClickListener { v -> startDrag(v, payload(i)); true }
        })
    }
    val name: TextView = (if (onRename != null) EditText(this) else TextView(this)).apply {
        text = title
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(m3.onSurface)
        background = null
        setSingleLine()
        setPadding(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBg(if (full) 100 else 97, if (full) 0f else 28 * dp, m3.surfaceHigh)
        isClickable = true
        val p = (16 * dp).toInt()
        setPadding(p, p + if (full) insets.top else 0, p, p)
        elevation = 6 * dp
        addView(name)
        addView(ScrollView(context).apply {
            addView(cell, FrameLayout.LayoutParams(cols * cellW, rows * cellH))
        }, LinearLayout.LayoutParams(-2, if (full) 0 else minOf(rows, 4) * cellH, if (full) 1f else 0f).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }
    val dim = FrameLayout(this).apply {
        setBackgroundColor(0x52000000)
        setOnClickListener { closeOverlay() }
        setOnDragListener(passDrag)
        addView(card, if (full) FrameLayout.LayoutParams(-1, -1)
            else FrameLayout.LayoutParams(cols * cellW + (32 * dp).toInt(), -2, Gravity.CENTER))
    }
    showOverlay(dim) { onRename?.invoke(name.text.toString().trim()) }
    if (!full) {
        card.scaleX = 0.9f; card.scaleY = 0.9f; card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }
}

/** Desktop previews: every page as a thumbnail. Tap to go there; long-press to set default, move or remove; "+" adds a page. */
@SuppressLint("SetTextI18n")
fun MainActivity.showOverview() {
    drawer.hide()
    // Three tiles per row, each with 6 dp margins on both sides.
    val w = maxOf(1, ((workspace.width - 48 * dp) / 3).toInt())
    val h = maxOf(1, w * workspace.height / maxOf(1, workspace.width))
    val default = looks.int("defaultPage")
    val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    var row = LinearLayout(this)
    fun add(v: View) {
        if (row.childCount == 0) grid.addView(row)
        row.addView(v, LinearLayout.LayoutParams(w, h + (24 * dp).toInt()).apply { setMargins((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt()) })
        if (row.childCount == 3) row = LinearLayout(this)
    }
    pages.forEachIndexed { i, page ->
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Some widgets hold hardware bitmaps, which a software canvas can't draw; the thumbnail is then left blank.
        runCatching { Canvas(bmp).apply { scale(w / maxOf(1f, page.width.toFloat()), h / maxOf(1f, page.height.toFloat())); page.draw(this) } }
        add(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ImageView(context).apply {
                setImageBitmap(bmp)
                val current = i == workspace.current
                background = rounded(m3.onSurface and 0xFFFFFF or 0x14000000, 16 * dp, if (current) m3.primary else m3.outlineVariant, (if (current) 3 else 1) * dp)
                clipToOutline = true
                val p = (if (current) 3 * dp else dp).toInt()
                setPadding(p, p, p, p)
                foreground = pressable(null, 16 * dp)
                isDuplicateParentStateEnabled = true
            }, LinearLayout.LayoutParams(w, h))
            addView(text("Page ${i + 1}", 12f, m3.onSurface, medium = i == default).apply {
                gravity = Gravity.CENTER
                setPadding(0, (6 * dp).toInt(), 0, 0)
                if (i == default) {
                    compoundDrawablePadding = (4 * dp).toInt()
                    setCompoundDrawablesRelative(symbol("home", m3.primary, 14f).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }, null, null, null)
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
            setOnClickListener { workspace.snapTo(i, animate = false); closeOverlay() }
            setOnLongClickListener { pageMenu(i); true }
        })
    }
    if (pages.size < 9) add(LinearLayout(this).apply {
        addView(ImageView(context).apply {
            setImageDrawable(symbol("add", m3.onSecondaryContainer, 32f))
            scaleType = ImageView.ScaleType.CENTER
            background = pressable(rounded(m3.secondaryContainer, 16 * dp), 16 * dp, m3.onSecondaryContainer)
            isDuplicateParentStateEnabled = true
        }, LinearLayout.LayoutParams(w, h))
        setOnClickListener { looks["pages"] = (pages.size + 1).toString(); recreate() }
    })
    showOverlay(FrameLayout(this).apply {
        setBackgroundColor(m3.surface and 0xFFFFFF or 0xD9000000.toInt())
        setOnClickListener { closeOverlay() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, insets.top + (24 * dp).toInt(), 0, insets.bottom)
            addView(text("Pages", 28f).apply { gravity = Gravity.CENTER })
            addView(text("Tap to open · long-press for options", 14f, m3.onSurfaceVariant).apply { gravity = Gravity.CENTER; setPadding(0, (4 * dp).toInt(), 0, (16 * dp).toInt()) })
            addView(ScrollView(context).apply { addView(grid) }, LinearLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    })
}

private fun MainActivity.pageMenu(i: Int) {
    val n = pages.size
    val options = listOf("Set as default page", "Move left", "Move right", "Remove page")
    dialog().setTitle("Page ${i + 1}").setItems(options.toTypedArray()) { _, which ->
        val default = looks.int("defaultPage")
        when (which) {
            0 -> { looks["defaultPage"] = i.toString(); showOverview() }
            1, 2 -> {
                val to = if (which == 1) i - 1 else i + 1
                if (to !in 0 until n) return@setItems
                items = movePage(items, i, to).toMutableList()
                if (default == i) looks["defaultPage"] = to.toString() else if (default == to) looks["defaultPage"] = i.toString()
                save(); render(); showOverview()
            }
            3 -> {
                if (n == 1) { toast("The home screen needs at least one page"); return@setItems }
                val doRemove = {
                    val (kept, gone) = removePage(items, i)
                    gone.filter { it.isWidget }.forEach { host.deleteAppWidgetId(it.widgetId) }
                    items = kept.toMutableList()
                    gone.filter { it.isShortcut }.forEach { syncPins(it.pkg, it.user) }
                    save()
                    looks["pages"] = (n - 1).toString()
                    looks["defaultPage"] = (if (default > i) default - 1 else default).coerceAtMost(n - 2).toString()
                    recreate()
                }
                if (items.none { it.page == i }) doRemove()
                else dialog().setMessage("Remove page ${i + 1} and everything on it?")
                    .setPositiveButton("Remove") { _, _ -> doRemove() }.setNegativeButton("Cancel", null).show()
            }
        }
    }.show()
}

/** Drag handles on a widget's edges; tapping outside applies the new size. */
fun MainActivity.showResize(cell: CellLayout, item: Item, v: View, minW: Int, minH: Int, canW: Boolean, canH: Boolean) {
    val frame = ResizeFrame(this, cell, item, v, minW, minH, canW, canH)
    showOverlay(frame) {
        val n = frame.result()
        val i = items.indexOf(item)
        if (n != item && i >= 0) { items[i] = n; save(); render() }
    }
}

@SuppressLint("ViewConstructor")
class ResizeFrame(
    private val a: MainActivity, private val cell: CellLayout, private val item: Item, private val ignore: View,
    private val minW: Int, private val minH: Int, private val canW: Boolean, private val canH: Boolean,
) : View(a) {
    private var l = item.x
    private var t = item.y
    private var r = item.x + item.w
    private var b = item.y + item.h
    private var edge = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3 * a.dp }
    private val origin = IntArray(2)

    fun result() = item.copy(x = l, y = t, w = r - l, h = b - t)

    private fun rect(): RectF {
        val c = IntArray(2).also { cell.getLocationInWindow(it) }
        getLocationInWindow(origin)
        val ox = (c[0] - origin[0]).toFloat()
        val oy = (c[1] - origin[1]).toFloat()
        return RectF(ox + l * cell.cellW, oy + t * cell.cellH, ox + r * cell.cellW, oy + b * cell.cellH)
    }

    private fun handles(rc: RectF) = listOf(rc.left to rc.centerY(), rc.centerX() to rc.top, rc.right to rc.centerY(), rc.centerX() to rc.bottom)

    override fun onDraw(canvas: Canvas) {
        val rc = rect()
        paint.style = Paint.Style.STROKE
        paint.color = a.accent
        canvas.drawRoundRect(rc, 8 * a.dp, 8 * a.dp, paint)
        paint.style = Paint.Style.FILL
        handles(rc).forEachIndexed { i, (x, y) ->
            if (if (i % 2 == 0) canW else canH) canvas.drawCircle(x, y, 9 * a.dp, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val rc = rect()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edge = handles(rc).indexOfFirst { (x, y) -> hypot(ev.x - x, ev.y - y) < 32 * a.dp }
                if (edge >= 0 && !(if (edge % 2 == 0) canW else canH)) edge = -1
                if (edge < 0 && !rc.contains(ev.x, ev.y)) a.closeOverlay()
            }
            MotionEvent.ACTION_MOVE -> if (edge >= 0) {
                val ox = rc.left - l * cell.cellW
                val oy = rc.top - t * cell.cellH
                val cx = ((ev.x - ox) / cell.cellW).roundToInt()
                val cy = ((ev.y - oy) / cell.cellH).roundToInt()
                var nl = l; var nt = t; var nr = r; var nb = b
                when (edge) {
                    0 -> nl = cx.coerceIn(0, r - minW)
                    1 -> nt = cy.coerceIn(0, b - minH)
                    2 -> nr = cx.coerceIn(l + minW, cell.cols)
                    3 -> nb = cy.coerceIn(t + minH, cell.rows)
                }
                if (cell.isFree(nl, nt, nr - nl, nb - nt, ignore) || a.looks.on("widgetOverlap")) { l = nl; t = nt; r = nr; b = nb; invalidate() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> edge = -1
        }
        return true
    }
}

/** "Item removed · Undo" for four seconds; [finalize] runs if it wasn't undone. */
// ponytail: finalize is skipped if the activity is destroyed within the 4 s, leaking one widget ID at worst
@SuppressLint("SetTextI18n")
fun MainActivity.showUndo(item: Item, finalize: () -> Unit) {
    // M3 snackbar: inverse surface with an inverse-primary action.
    val bar = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (48 * dp).toInt()
        val p = (16 * dp).toInt()
        setPadding(p, 0, p / 2, 0)
        background = rounded(m3.inverseSurface, 8 * dp)
        elevation = 6 * dp
        addView(text("Item removed", 14f, m3.inverseOnSurface), LinearLayout.LayoutParams(0, -2, 1f))
        addView(text("Undo", 14f, m3.inversePrimary, medium = true).apply {
            gravity = Gravity.CENTER
            setPadding(p * 3 / 4, 0, p * 3 / 4, 0)
            background = pressable(null, 20 * dp, m3.inversePrimary)
        }, LinearLayout.LayoutParams(-2, (40 * dp).toInt()))
    }
    bar.getChildAt(1).setOnClickListener {
        root.removeView(bar)
        items += item
        save()
        render()
    }
    root.addView(bar, FrameLayout.LayoutParams(minOf(root.width - (32 * dp).toInt(), (560 * dp).toInt()), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
        bottomMargin = insets.bottom + (120 * dp).toInt()
    })
    bar.translationY = 24 * dp; bar.alpha = 0f
    bar.animate().translationY(0f).alpha(1f).setDuration(200).start()
    bar.postDelayed({ if (bar.parent != null) { root.removeView(bar); finalize() } }, 4000)
}
