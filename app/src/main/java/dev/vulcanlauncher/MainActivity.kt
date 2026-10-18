package dev.vulcanlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil

class MainActivity : Activity() {
    companion object {
        // ponytail: fixed grid and page count, become settings in the "Looks" milestone
        const val PAGES = 3
        const val COLS = 5
        const val ROWS = 5
        const val HOST_ID = 1024
        const val REQ_BIND = 1
        const val REQ_CONFIG = 2
    }

    private lateinit var store: Store
    private lateinit var items: MutableList<Item>
    private lateinit var host: AppWidgetHost
    private lateinit var awm: AppWidgetManager
    private lateinit var workspace: Workspace
    private lateinit var pages: List<CellLayout>
    private lateinit var dock: CellLayout
    private lateinit var drawer: View
    private lateinit var removeBar: TextView
    private lateinit var search: EditText
    private val views = HashMap<Item, View>()
    private var pendingWidget: Pair<Int, AppWidgetProviderInfo>? = null
    private var lastEdgeFlip = 0L

    private var all = listOf<ResolveInfo>()
    private var shown = listOf<ResolveInfo>()
    private val adapter = AppAdapter()

    private val dp get() = resources.displayMetrics.density

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        items = store.load()
        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, HOST_ID)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        pages = List(PAGES) { i ->
            CellLayout(this, COLS, ROWS, i).apply {
                setOnDragListener(dropListener(this))
                setOnLongClickListener { homeMenu(); true }
            }
        }
        workspace = Workspace(this).apply {
            pages.forEach { addView(it) }
            onSwipeUp = ::showDrawer
            onSwipeDown = ::expandNotifications
        }
        dock = CellLayout(this, COLS, 1, DOCK).apply { setOnDragListener(dropListener(this)) }
        drawer = buildDrawer().apply { visibility = View.GONE }
        removeBar = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCCB00020.toInt())
            visibility = View.GONE
            setOnDragListener { v, e ->
                when (e.action) {
                    DragEvent.ACTION_DROP -> (e.localState as? Item)?.let(::removeItem)
                    DragEvent.ACTION_DRAG_ENDED -> { v.visibility = View.GONE; render() }
                }
                true
            }
        }

        setContentView(FrameLayout(this).apply {
            fitsSystemWindows = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(dock, LinearLayout.LayoutParams(-1, (96 * dp).toInt()))
            })
            addView(drawer)
            addView(removeBar, FrameLayout.LayoutParams(-1, (64 * dp).toInt(), Gravity.TOP))
        })
        render()
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
    }

    // ponytail: full app reload on every resume, cache + package-change receiver if it gets slow
    override fun onResume() {
        super.onResume()
        all = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        filter()
        render() // picks up icon/label changes from app updates
    }

    // Home button: close the drawer, or go back to the first page.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (drawer.visibility == View.VISIBLE) hideDrawer() else workspace.snapTo(0)
    }

    @Deprecated("Launchers swallow back")
    override fun onBackPressed() = hideDrawer()

    // ---- Home screen ----

    /** Rebuilds every page from [items]. Apps that are missing (e.g. mid-update) are skipped, not deleted. */
    private fun render() {
        (pages + dock).forEach { it.removeAllViews() }
        views.clear()
        for (item in items) {
            val cell = if (item.page == DOCK) dock else pages.getOrNull(item.page) ?: continue
            val v = (if (item.isWidget) widgetView(item) else appView(item)) ?: continue
            v.layoutParams = CellLayout.CellParams(item.x, item.y, item.w, item.h)
            v.setOnLongClickListener { startDrag(it, item); true }
            cell.addView(v)
            views[item] = v
        }
    }

    private fun appView(item: Item): View? {
        val cn = ComponentName(item.pkg, item.cls)
        val info = try {
            packageManager.getActivityInfo(cn, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        return iconView(info.loadLabel(packageManager), info.loadIcon(packageManager)).apply {
            setOnClickListener { launch(cn) }
        }
    }

    private fun widgetView(item: Item): View? {
        val info = awm.getAppWidgetInfo(item.widgetId) ?: return null
        return WidgetFrame(this).apply { addView(host.createView(applicationContext, item.widgetId, info)) }
    }

    private fun startDrag(v: View, payload: Any) {
        removeBar.text = if (payload is Item) "✕  Remove" else "✕  Cancel"
        removeBar.visibility = View.VISIBLE
        v.startDragAndDrop(ClipData.newPlainText("", ""), View.DragShadowBuilder(v), payload, 0)
        if (payload is Item) v.alpha = 0.3f
        drawer.visibility = View.GONE
    }

    private fun dropListener(cell: CellLayout) = View.OnDragListener { _, e ->
        when (e.action) {
            DragEvent.ACTION_DRAG_LOCATION -> if (cell.page != DOCK) edgeFlip(e.x, cell.width)
            DragEvent.ACTION_DROP -> return@OnDragListener drop(cell, e.x, e.y, e.localState)
        }
        true
    }

    /** Holding a drag near the left/right edge flips to the neighbouring page. */
    private fun edgeFlip(x: Float, width: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEdgeFlip < 700) return
        val edge = width * 0.06f
        if (x < edge) workspace.snapTo(workspace.current - 1)
        else if (x > width - edge) workspace.snapTo(workspace.current + 1)
        else return
        lastEdgeFlip = now
    }

    private fun drop(cell: CellLayout, px: Float, py: Float, payload: Any?): Boolean {
        val moving = payload as? Item
        val w = moving?.w ?: 1
        val h = moving?.h ?: 1
        val (cx, cy) = cell.cellAt(px, py)
        val x = cx.coerceAtMost(cell.cols - w)
        val y = cy.coerceAtMost(cell.rows - h)
        if (!cell.isFree(x, y, w, h, ignore = moving?.let { views[it] })) return false
        val placed = when (payload) {
            is Item -> { items.remove(payload); payload.copy(page = cell.page, x = x, y = y) }
            is ResolveInfo -> Item(cell.page, x, y, pkg = payload.activityInfo.packageName, cls = payload.activityInfo.name)
            else -> return false
        }
        items += placed
        store.save(items)
        render()
        return true
    }

    private fun removeItem(item: Item) {
        items.remove(item)
        if (item.isWidget) host.deleteAppWidgetId(item.widgetId)
        store.save(items)
    }

    private fun homeMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Add widget", "Wallpaper")) { _, which ->
                if (which == 0) pickWidget()
                else startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
            }.show()
    }

    // ---- Widgets: pick → bind (may ask user) → configure (if the widget wants it) → place ----

    private fun pickWidget() {
        val providers = awm.installedProviders.sortedBy { it.loadLabel(packageManager).lowercase() }
        AlertDialog.Builder(this)
            .setTitle("Widgets")
            .setItems(providers.map { it.loadLabel(packageManager) }.toTypedArray()) { _, i -> addWidget(providers[i]) }
            .show()
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingWidget = id to info
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) configureWidget()
        else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND)
    }

    private fun configureWidget() {
        val (id, info) = pendingWidget ?: return
        if (info.configure != null) host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
        else placeWidget()
    }

    private fun placeWidget() {
        val (id, info) = pendingWidget ?: return
        pendingWidget = null
        val page = pages[workspace.current]
        val w = ceil(info.minWidth / page.cellW.toFloat()).toInt().coerceIn(1, COLS)
        val h = ceil(info.minHeight / page.cellH.toFloat()).toInt().coerceIn(1, ROWS)
        val spot = page.firstFree(w, h)
        if (spot == null) {
            host.deleteAppWidgetId(id)
            Toast.makeText(this, "No room on this page", Toast.LENGTH_SHORT).show()
            return
        }
        items += Item(workspace.current, spot.first, spot.second, w, h, widgetId = id)
        store.save(items)
        render()
    }

    private fun cancelWidget() {
        pendingWidget?.let { host.deleteAppWidgetId(it.first) }
        pendingWidget = null
    }

    @Deprecated("Plain Activity result API is fine without androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val ok = resultCode == RESULT_OK
        when (requestCode) {
            REQ_BIND -> if (ok) configureWidget() else cancelWidget()
            REQ_CONFIG -> if (ok) placeWidget() else cancelWidget()
        }
    }

    // ---- Drawer ----

    private fun buildDrawer(): View {
        search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        val grid = GridView(this).apply {
            numColumns = COLS
            verticalSpacing = (16 * dp).toInt()
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val a = shown[pos].activityInfo
                launch(ComponentName(a.packageName, a.name))
                hideDrawer()
            }
            setOnItemLongClickListener { _, v, pos, _ -> startDrag(v, shown[pos]); true }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6101010.toInt())
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            addView(search)
            addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun showDrawer() {
        search.text.clear()
        drawer.visibility = View.VISIBLE
    }

    private fun hideDrawer() {
        search.text.clear()
        drawer.visibility = View.GONE
    }

    private fun filter() {
        val q = search.text.toString().trim()
        shown = all.filter { it.loadLabel(packageManager).contains(q, ignoreCase = true) }
        adapter.notifyDataSetChanged()
    }

    // ---- Shared ----

    private fun launch(cn: ComponentName) {
        startActivity(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    /** Hidden StatusBarManager API; same approach other launchers use. Needs EXPAND_STATUS_BAR. */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val sbm = getSystemService("statusbar")
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    private fun iconView(label: CharSequence, icon: Drawable) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(ImageView(context).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()))
        addView(TextView(context).apply {
            text = label
            maxLines = 1
            textSize = 12f
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, 0xAA000000.toInt())
            setTextColor(Color.WHITE)
        })
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(pos: Int) = shown[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
            val app = shown[pos]
            return iconView(app.loadLabel(packageManager), app.loadIcon(packageManager))
        }
    }
}
