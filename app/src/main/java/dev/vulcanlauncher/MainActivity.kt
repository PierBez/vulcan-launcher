package dev.vulcanlauncher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.hypot

/** A launchable app from any profile (personal or work). [serial] is the profile's UserManager serial number. */
class App(val info: LauncherActivityInfo, val serial: Long) {
    val pkg: String = info.componentName.packageName
    val cls: String = info.componentName.className
    val key = appKey(pkg, cls, serial)
}

/** Drag payload for an item dragged out of a home-screen folder. */
class FolderChild(val folder: Item, val index: Int)

class MainActivity : Activity() {
    companion object {
        const val HOST_ID = 1024
        const val REQ_BIND = 1
        const val REQ_CONFIG = 2
        const val REQ_SHORTCUT = 3
        const val REQ_BACKUP = 4
        const val REQ_RESTORE = 5
        const val REQ_ICON = 6
        const val REQ_RECONFIG = 7
        const val REQ_DOC = 8
        const val REQ_PERMISSION = 9
        /** The live launcher activity, for the on-device check (androidTest/DeviceCheck). */
        var running: java.lang.ref.WeakReference<MainActivity>? = null
    }

    lateinit var store: Store
    lateinit var looks: Looks
    lateinit var icons: Icons
    lateinit var items: MutableList<Item>
    lateinit var host: AppWidgetHost
    lateinit var awm: AppWidgetManager
    lateinit var launcherApps: LauncherApps
    lateinit var um: UserManager
    lateinit var root: FrameLayout
    /** Home pages, indicator and dock: blurred while a panel is open. */
    lateinit var content: LinearLayout
    var settingsScroll: android.widget.ScrollView? = null
    lateinit var workspace: Workspace
    lateinit var pages: List<CellLayout>
    lateinit var dock: Workspace
    lateinit var dockPages: List<CellLayout>
    lateinit var dockBox: LinearLayout
    lateinit var indicator: Indicator
    lateinit var drawer: Drawer
    /** The date and status strip above the first page, when on (see Now.kt). */
    var glance: LinearLayout? = null
    private lateinit var dropBar: LinearLayout
    /** The open folder window, desktop previews or resize frame, and what to do when it closes. */
    var overlay: View? = null
    var overlayClosed: () -> Unit = {}
    val views = HashMap<Item, View>()
    val viewItems = HashMap<View, Item>()
    private val iconViews = ArrayList<IconView>()
    var apps = mapOf<String, App>()
    var customs = JSONObject()
    var night = false
    var pendingWidget: Pair<Int, AppWidgetProviderInfo>? = null
    var onPickedImage: ((Uri) -> Unit)? = null
    var onDocument: ((Uri) -> Unit)? = null
    private var lastEdgeFlip = 0L
    private var dragSource: View? = null
    private var dragPayload: Any? = null
    private var dragStart: PointF? = null
    private var dragMoved = false
    private var barHidden = false
    private val iconCache = HashMap<String, Drawable>()

    val dp get() = resources.displayMetrics.density
    val sub get() = looks.sub
    /** Material 3 colours for this activity, from the theme colour setting and the panel theme. */
    lateinit var m3: Palette
    val accent get() = m3.primary
    val locked get() = looks.on("lockLayout")
    val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    /** Housing, card and glass docks float above the bottom edge instead of running under the navigation bar. */
    val floatingDock get() = looks.str("dockBg") in listOf("housing", "card", "glass")
    /** Text and panel colours for drawer, folders and menus. */
    val darkPanels get() = when (looks.str("drawerTheme")) { "dark" -> true; "light" -> false; else -> night }
    val panelText get() = m3.onSurface
    fun panelBg(alphaPercent: Int = 100, radius: Float = 16 * dp, color: Int = m3.surfaceContainer) =
        rounded(color and 0xFFFFFF or ((alphaPercent * 255 / 100) shl 24), radius)
    /** A colour setting; "system" gives [fallback], a Material colour role. */
    fun themed(key: String, fallback: Int) = if (looks.str(key) == "system") fallback else looks.color(key)
    /** How far home sits behind a panel (0–1), and its running animation (see setDepth, blurHome). */
    var depth = 0f
    var depthAnim: android.animation.ValueAnimator? = null
    /** Where the last touch went down, in window coordinates; menus opened by a long-press appear there. */
    val lastDown = PointF()
    /** System window insets, kept for overlays that draw edge to edge. */
    val insets = Rect()
    /** The settings page showing ("" for the top level), kept across recreate(). */
    var settingsPage: String? = null

    fun dialog(): AlertDialog.Builder = object : AlertDialog.Builder(this, when {
        m3.housing -> if (m3.dark) R.style.VulcanDialogApplianceDark else R.style.VulcanDialogAppliance
        else -> if (m3.dark) R.style.VulcanDialogDark else R.style.VulcanDialog
    }) {
        override fun create(): AlertDialog = super.create().also { d -> d.setOnShowListener { styleDialog(d) } }
    }
    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        running = java.lang.ref.WeakReference(this)
        store = Store(this)
        looks = Looks(this)
        icons = Icons(this, looks)
        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, HOST_ID)
        launcherApps = getSystemService(LauncherApps::class.java)
        um = getSystemService(UserManager::class.java)
        night = computeNight()
        m3 = palette(this, darkPanels, looks.str("accent"))
        requestedOrientation = when (looks.str("orientation")) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        loadApps()
        items = fit(if (store.isNew) defaultDock() else store.load())
        store.save(items)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            (if (looks.on("darkStatusIcons")) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0)
        barHidden = looks.str("statusBar") == "hide"
        applyBar()

        val gesture = { g: String -> perform(looks.str("g_$g")) }
        pages = List(looks.pages) { i ->
            CellLayout(this, looks.cols * sub, looks.rows * sub, i).apply {
                setOnDragListener(dropListener(this))
                setOnLongClickListener { homeMenu(); true }
            }
        }
        indicator = Indicator(this, looks.pages, looks.str("indicator"), looks.color("labelColor"), m3.signal)
        workspace = Workspace(this).apply {
            pages.forEach { addView(it) }
            onGesture = gesture
            effect = looks.str("scrollEffect")
            infinite = looks.on("infiniteScroll")
            // The glance strip belongs to the first page: it scrolls away with it.
            onScroll = { indicator.position = it; wallpaperScroll(it); glance?.translationX = -it * width }
            snapTo(looks.int("defaultPage").coerceAtMost(looks.pages - 1), animate = false)
        }
        dockPages = List(looks.dockPages) { CellLayout(this, looks.dockCols, 1, -1 - it, landscape).apply { setOnDragListener(dropListener(this)) } }
        dock = Workspace(this).apply { dockPages.forEach { addView(it) }; onGesture = gesture }
        val search = looks.str("searchBar")
        dockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (looks.dockOn) View.VISIBLE else View.GONE
            // Landscape: the dock is a column down the right edge; its search bar moves under the pages.
            if (search == "dockAbove" && !landscape) addView(searchBar())
            val labels = if (looks.on("dockLabels")) 22 else 0
            addView(dock, if (landscape) LinearLayout.LayoutParams(((looks.int("dockIconSize") + 28) * dp).toInt(), -1)
                else LinearLayout.LayoutParams(-1, ((looks.int("dockIconSize") + 28 + labels) * dp).toInt()))
            if (search == "dockBelow" && !landscape) addView(searchBar())
            val bg = looks.str("dockBg")
            // Glass: a light frost that's a little brighter at the top, with a hairline edge; on Samsung it really
            // blurs the wallpaper behind it.
            // Housing: an Appliance body standing off the wallpaper, a pill in the housing colour (or the chosen one)
            // with a recessed channel that the icons sit in.
            if (bg == "housing") {
                val c = themed("dockColor", m3.surface)
                housing(this, 1000 * dp, c)
                background = android.graphics.drawable.LayerDrawable(arrayOf(housingFace(c, 1000 * dp), well(1000 * dp, blend(c, Color.BLACK, if (m3.dark) 0.3f else 0.06f))))
                    .apply { val i = (5 * dp).toInt(); setLayerInset(1, i, i, i, i) }
            }
            else if (bg == "glass") {
                val c = if (looks.str("dockColor") == "system") 0xFFFFFF else looks.color("dockColor") and 0xFFFFFF
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c or 0x3D000000, c or 0x24000000)).apply {
                    cornerRadius = 32 * dp
                    setStroke(maxOf(1, (dp / 2).toInt()), 0x2EFFFFFF)
                }
                if (viewBlur) semBlur((24 * dp).toInt(), c or 0x33000000, 32 * dp)
            } else if (bg != "none") background = GradientDrawable().apply {
                setColor(themed("dockColor", m3.surfaceContainer) and 0xFFFFFF or ((looks.int("dockAlpha") * 255 / 100) shl 24))
                cornerRadius = if (bg == "rect") 0f else 28 * dp
            }
            if (bg != "none" && bg != "housing" && looks.on("dockShadow")) {
                elevation = 8 * dp
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
        }
        val dockHeight = ((looks.int("dockIconSize") + 28 + (if (looks.on("dockLabels")) 22 else 0)) * dp).toInt()
        val pagesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (search == "top") addView(searchBar())
            // Not in landscape: the screen is short, and every row it takes shrinks the home icons (the status bar keeps the time).
            if (looks.on("glance") && !landscape) glance = glanceView().also { addView(it) }
            // As an overlay, later children (indicator, dock) are pulled up over the bottom of the pages.
            val overlay = looks.on("dockOverlay") && looks.dockOn && !landscape
            addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { if (overlay) bottomMargin = -dockHeight })
            if (looks.str("indicator") != "none" && looks.pages > 1) addView(indicator, LinearLayout.LayoutParams(-1, (14 * dp).toInt()))
            if (looks.on("swipeIndicator")) addView(TextView(context).apply {
                text = "︿"
                gravity = Gravity.CENTER
                setTextColor(looks.color("labelColor"))
                setOnClickListener { perform("drawer") }
            }, LinearLayout.LayoutParams(-1, (20 * dp).toInt()))
            if (landscape && search in listOf("dockAbove", "dockBelow")) addView(searchBar())
            if (!landscape) addView(dockBox, LinearLayout.LayoutParams(-1, -2).apply {
                if (floatingDock) setMargins((14 * dp).toInt(), (4 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            })
            if (search == "bottom") addView(searchBar())
        }
        content = if (!landscape) pagesBox else LinearLayout(this).apply {
            addView(pagesBox, LinearLayout.LayoutParams(0, -1, 1f))
            addView(dockBox, LinearLayout.LayoutParams(-2, -1).apply {
                if (floatingDock) setMargins((4 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt())
            })
        }
        drawer = Drawer(this)
        // Swipe up opens the drawer under the finger instead of after the gesture ends.
        if (looks.str("g_swipeUp") == "drawer") { workspace.onPull = drawer::pull; dock.onPull = drawer::pull }
        dropBar = LinearLayout(this).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            val p = (12 * dp).toInt()
            setPadding(p, p, p, p)
            listOf("remove", "info", "uninstall").forEach {
                addView(dropTarget(it), LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f).apply { setMargins(p / 3, 0, p / 3, 0) })
            }
        }
        root = FrameLayout(this).apply {
            addView(content)
            addView(drawer.root)
            addView(dropBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            setOnApplyWindowInsetsListener { _, ins ->
                val behind = looks.on("dockBehindNav") && looks.dockOn && !floatingDock && !landscape
                content.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight,
                    if (behind) 0 else ins.systemWindowInsetBottom)
                dockBox.setPadding(0, 0, 0, if (behind) ins.systemWindowInsetBottom else 0)
                drawer.applyInsets(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
                dropBar.setPadding(dropBar.paddingLeft, ins.systemWindowInsetTop + dropBar.paddingBottom, dropBar.paddingRight, dropBar.paddingBottom)
                insets.set(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
                ins
            }
        }
        setContentView(root)
        render()
        launcherApps.registerCallback(appsCallback)
        BadgeService.onChange = { runOnUiThread { updateBadges(); drawer.refreshBadges(); updateGlance() } }
        // Rotation recreates the activity: stay on the page the user was on.
        savedInstanceState?.getInt("page", -1)?.takeIf { it in 0 until looks.pages }?.let { workspace.snapTo(it, animate = false) }
        savedInstanceState?.getString("settings")?.let { page ->
            val scroll = savedInstanceState.getInt("settingsScroll")
            root.post { showSettings(page, scroll) }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) lastDown.set(ev.x, ev.y)
        return super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", workspace.current)
        settingsPage?.let {
            outState.putString("settings", it)
            outState.putInt("settingsScroll", settingsScroll?.scrollY ?: 0)
        }
    }

    /** Back inside an overlay: its own back handler (a view tag) if it has one, else close it. */
    private fun backInOverlay() = (overlay?.tag as? Runnable)?.run() ?: closeOverlay()

    override fun onDestroy() {
        super.onDestroy()
        launcherApps.unregisterCallback(appsCallback)
        BadgeService.onChange = {}
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
        // Daily automatic backup, kept privately on the device and offered in Backup & restore.
        // Once a day (not on every stop) so a reset or bad change can't overwrite the last good copy straight away.
        val auto = java.io.File(filesDir, "auto-backup.json")
        if (!auto.exists() || System.currentTimeMillis() - auto.lastModified() > 24 * 3600_000L) runCatching { auto.writeText(store.export()) }
    }

    override fun onResume() {
        super.onResume()
        updateLocation()
        if (computeNight() != night) { recreate(); return }
        setClockHidden(true)
        updateGlance()
        // PinActivity may have added an item while we were in the background.
        val stored = fit(store.load())
        if (stored != items) { items = stored; render() }
    }

    override fun onPause() {
        super.onPause()
        setClockHidden(false)
    }

    /** Persisted, so a launcher killed while showing (no onPause) still puts the clock back on its next start. */
    private var addedClock: Boolean
        get() = looks.raw("clockHiddenByUs") == "1"
        set(v) { looks["clockHiddenByUs"] = if (v) "1" else "0" }

    /** "Hide clock": hides the status-bar clock while the home screen is showing. Needs WRITE_SECURE_SETTINGS (granted over adb). */
    private fun setClockHidden(hide: Boolean) {
        if (!looks.on("hideClock") && !addedClock) return
        if (!granted(android.Manifest.permission.WRITE_SECURE_SETTINGS)) return
        // Only our own "clock" token is added or removed; the rest of the system's list is left exactly as it was.
        val list = Settings.Secure.getString(contentResolver, "icon_blacklist").orEmpty().split(",").filter { it.isNotBlank() }.toMutableList()
        val want = hide && looks.on("hideClock")
        if (want && "clock" !in list) { list += "clock"; addedClock = true }
        else if (!want && addedClock) { list.remove("clock"); addedClock = false }
        else return
        runCatching { Settings.Secure.putString(contentResolver, "icon_blacklist", list.joinToString(",")) }
            .onFailure { android.util.Log.w("Vulcan", "hide clock: can't write icon_blacklist", it) }
    }

    /** For sunset-to-sunrise night mode: the last location Android already knows. No new fix is requested. */
    @SuppressLint("MissingPermission")
    private fun updateLocation() {
        if (looks.str("nightMode") != "sun" || !granted(android.Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val lm = getSystemService(android.location.LocationManager::class.java)
        val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return
        looks["lat"] = loc.latitude.toString()
        looks["lon"] = loc.longitude.toString()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION) recreate()
    }

    /** Sends a query to the chosen search engine (in the browser) or to the default web search app. */
    fun webSearch(q: String) {
        val url = searchUrl(looks.str("searchEngine"), q)
        val intent = if (url != null) Intent(Intent.ACTION_VIEW, Uri.parse(url))
            else Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast("No app handles web search") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when {
            menu != null -> closeMenu()
            overlay != null -> closeOverlay()
            drawer.visible -> drawer.hide()
            else -> when (looks.str("homeButton")) {
                "default" -> workspace.snapTo(looks.int("defaultPage"))
                "previews" -> showOverview()
            }
        }
    }

    @Deprecated("Launchers swallow back")
    override fun onBackPressed() {
        when {
            menu != null -> closeMenu()
            overlay != null -> backInOverlay()
            else -> drawer.hide()
        }
    }

    private fun computeNight(): Boolean {
        val sys = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val cal = Calendar.getInstance()
        if (looks.str("nightMode") == "sun") {
            val lat = looks.raw("lat")?.toDoubleOrNull()
            val lon = looks.raw("lon")?.toDoubleOrNull()
            val offset = cal.timeZone.getOffset(cal.timeInMillis) / 3_600_000.0
            val sun = if (lat != null && lon != null) sunTimes(lat, lon, cal.get(Calendar.DAY_OF_YEAR), offset) else null
            // Without a location yet, fall back to the manual schedule.
            if (sun != null) return isNightSun(cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0, sun)
            return isNight("schedule", cal.get(Calendar.HOUR_OF_DAY), looks.int("nightStart"), looks.int("nightEnd"), sys)
        }
        return isNight(looks.str("nightMode"), cal.get(Calendar.HOUR_OF_DAY), looks.int("nightStart"), looks.int("nightEnd"), sys)
    }

    fun fit(list: List<Item>) = fitItems(list, looks.pages, looks.cols * sub, looks.rows * sub, looks.dockCols, looks.dockPages).toMutableList()

    fun save() = store.save(items)

    // ---- Apps ----

    fun serial(user: UserHandle) = um.getSerialNumberForUser(user)
    fun userOf(serial: Long): UserHandle = um.getUserForSerialNumber(serial) ?: Process.myUserHandle()

    fun loadApps() {
        apps = launcherApps.profiles.flatMap { user -> launcherApps.getActivityList(null, user).map { App(it, serial(user)) } }
            .filter { it.pkg != packageName }.associateBy { it.key }
        customs = store.obj("custom")
        iconCache.clear()
    }

    /** First run: the default phone, messages, browser, camera and gallery apps in the dock (as many as fit). */
    fun defaultDock(): MutableList<Item> {
        val me = serial(Process.myUserHandle())
        return listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
        ).mapNotNull { intent ->
            // The user's default first; with no default set, resolveActivity returns the chooser, so fall back to any handler.
            val handlers = listOfNotNull(packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)) + packageManager.queryIntentActivities(intent, 0)
            handlers.firstNotNullOfOrNull { r -> apps.values.firstOrNull { it.pkg == r.activityInfo.packageName && it.serial == me } }
        }.distinctBy { it.key }.take(looks.dockCols)
            .mapIndexed { i, a -> Item(DOCK, i, 0, pkg = a.pkg, cls = a.cls, user = a.serial) }.toMutableList()
    }

    private val appsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(pkg: String, user: UserHandle) {
            reload()
            if (looks.on("autoAdd")) launcherApps.getActivityList(pkg, user).forEach { addToHome(App(it, serial(user)), quiet = true) }
        }
        override fun onPackageRemoved(pkg: String, user: UserHandle) = reload()
        override fun onPackageChanged(pkg: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onShortcutsChanged(pkg: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = render()
        private fun reload() { loadApps(); drawer.refresh(); render() }
    }

    fun label(a: App): String = customs.optJSONObject(a.key)?.optString("label")?.takeIf { it.isNotEmpty() } ?: a.info.label.toString()
    fun icon(a: App): Drawable = iconCache.getOrPut(a.key) {
        icons.get(a.pkg, a.cls, customs.optJSONObject(a.key)?.optString("icon") ?: "") { a.info.getBadgedIcon(0) }
    }
    fun appLabel(key: String) = apps[key]?.let(::label)
    fun isWork(a: App) = a.info.user != Process.myUserHandle()

    fun launch(a: App, v: View?) {
        runCatching { launcherApps.startMainActivity(a.info.componentName, a.info.user, bounds(v), launchOptions(v)) }
            .onFailure { toast("Can't open ${label(a)}") }
        val counts = store.obj("launches")
        store.putObj("launches", counts.put(a.key, counts.optInt(a.key) + 1))
        if (drawer.visible && looks.on("closeAfterLaunch")) drawer.hide()
    }

    fun launches() = store.obj("launches")

    private fun bounds(v: View?): Rect? = v?.let {
        val loc = IntArray(2).also { l -> it.getLocationOnScreen(l) }
        Rect(loc[0], loc[1], loc[0] + it.width, loc[1] + it.height)
    }

    fun launchOptions(v: View?): Bundle? = when (looks.str("launchAnim")) {
        "zoom" -> v?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        "reveal" -> v?.let { ActivityOptions.makeClipRevealAnimation(it, 0, 0, it.width, it.height).toBundle() }
        "fade" -> ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        "slideUp" -> ActivityOptions.makeCustomAnimation(this, R.anim.slide_up_in, R.anim.stay).toBundle()
        "slideLeft" -> ActivityOptions.makeCustomAnimation(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right).toBundle()
        "none" -> ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        else -> null
    }

    fun shortcutsFor(a: App): List<ShortcutInfo> = runCatching {
        launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setPackage(a.pkg).setActivity(a.info.componentName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST), a.info.user)
            .orEmpty().sortedBy { it.rank }.take(5)
    }.getOrDefault(emptyList())

    private fun pinnedShortcut(item: Item): ShortcutInfo? = runCatching {
        launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setPackage(item.pkg).setShortcutIds(listOf(item.shortcutId))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), userOf(item.user))?.firstOrNull()
    }.getOrNull()

    fun shortcutIcon(s: ShortcutInfo): Drawable =
        runCatching { launcherApps.getShortcutIconDrawable(s, resources.displayMetrics.densityDpi) }.getOrNull() ?: packageManager.defaultActivityIcon

    fun startShortcut(pkg: String, id: String, user: UserHandle, v: View?) {
        runCatching { launcherApps.startShortcut(pkg, id, bounds(v), launchOptions(v), user) }
            .onFailure { toast("Shortcut isn't available (set Vulcan as the default launcher)") }
    }

    /** Pins exactly the shortcuts of [pkg] that are on the home screen, so removed ones are released. */
    fun syncPins(pkg: String, user: Long) {
        val ids = (items + items.flatMap { it.children }).filter { it.isShortcut && it.pkg == pkg && it.user == user }.map { it.shortcutId }.distinct()
        runCatching { launcherApps.pinShortcuts(pkg, ids, userOf(user)) }
    }

    // ---- Home screen ----

    fun style(surface: String): IconStyle {
        val size = looks.int(if (surface == "") "iconSize" else "${surface}IconSize")
        val labels = looks.on(if (surface == "") "labels" else "${surface}Labels")
        return IconStyle((size * dp).toInt(), labels, looks.int("labelSize").toFloat(), looks.color("labelColor"),
            looks.on("labelShadow"), looks.on("iconShadow"), font = looks.str("labelFont"), lines = looks.int("labelLines"))
    }

    fun badgeStyle() = BadgeStyle(looks.str("badges"), looks.str("badgePos"),
        when (looks.str("badgeSize")) { "small" -> 0.8f; "large" -> 1.3f; else -> 1f }, looks.color("badgeColor"))

    /** Rebuilds every page from [items]. Apps that are missing (e.g. mid-update) are skipped, not deleted. */
    fun render() {
        (pages + dockPages).forEach { it.removeAllViews() }
        views.clear()
        viewItems.clear()
        iconViews.clear()
        val home = style("")
        val dockStyle = style("dock")
        for (item in items) {
            val cell = (if (isDock(item.page)) dockPages.getOrNull(dockIndex(item.page)) else pages.getOrNull(item.page)) ?: continue
            if (item.x + item.w > cell.cols || item.y + item.h > cell.rows) continue // no room after a grid change
            val v = itemView(item, if (isDock(item.page)) dockStyle else home) ?: continue
            v.layoutParams = CellLayout.CellParams(item.x, item.y, item.w, item.h)
            v.setOnLongClickListener { startDrag(it, item); true }
            cell.addView(v)
            views[item] = v
            viewItems[v] = item
        }
        updateBadges()
    }

    /** Label and icon for any non-widget item, or null when its app is gone. */
    fun resolve(item: Item): Pair<CharSequence, Drawable>? = when {
        item.folder -> item.label to folderIcon(item.children)
        item.isAction -> item.label.ifEmpty { actionName(item.action, ::appLabel) } to
            (icons.custom(item.icon) ?: GlyphIcon(m3.primaryContainer, m3.onPrimaryContainer, symbol = when {
                item.action.startsWith("page:") -> "carousel"
                item.action.startsWith("app:") -> "apps"
                else -> ACTION_SYMBOLS[item.action] ?: "bolt"
            }))
        item.isShortcut -> pinnedShortcut(item).let { s ->
            item.label.ifEmpty { s?.shortLabel?.toString() ?: "" } to (icons.custom(item.icon) ?: s?.let(::shortcutIcon) ?: packageManager.defaultActivityIcon)
        }
        item.action == "activity" -> runCatching { packageManager.getActivityInfo(ComponentName(item.pkg, item.cls), 0) }.getOrNull()?.let {
            item.label.ifEmpty { it.loadLabel(packageManager).toString() } to (icons.custom(item.icon) ?: it.loadIcon(packageManager))
        }
        else -> apps[item.appKey]?.let { a -> item.label.ifEmpty { label(a) } to (icons.custom(item.icon) ?: icon(a)) }
    }

    fun folderIcon(children: List<Item>) = FolderIcon(children.take(6).mapNotNull { resolve(it)?.second },
        looks.str("folderPreview"), looks.str("folderBg"),
        if (looks.str("folderColor") == "system") m3.surfaceHigh and 0xFFFFFF or 0xE6000000.toInt() else looks.color("folderColor") and 0xFFFFFF or 0x99000000.toInt(), looks.on("folderClip"), looks.on("folderFirstBig"))

    fun itemView(item: Item, style: IconStyle): View? {
        if (item.isWidget) return widgetView(item)
        val (label, icon) = resolve(item) ?: return null
        val shown = if (item.folder && !looks.on("folderNames")) "" else label
        val key = if (item.folder) item.children.joinToString("|") { it.pkg } else item.pkg
        return IconView(this, shown, icon, style, badgeStyle(), key).apply {
            setOnClickListener { open(item, it) }
            onSwipe = when {
                item.folder && looks.on("folderSwipe") -> { { openFolder(item) } }
                item.swipe.isNotEmpty() -> { { perform(item.swipe) } }
                else -> null
            }
            iconViews += this
        }
    }

    fun open(item: Item, v: View?) {
        when {
            item.folder -> if (looks.on("folderSwipe")) item.children.firstOrNull()?.let { open(it, v) } else openFolder(item)
            item.isAction -> perform(item.action)
            item.isShortcut -> startShortcut(item.pkg, item.shortcutId, userOf(item.user), v)
            item.action == "activity" -> try {
                startActivity(Intent().setComponent(ComponentName(item.pkg, item.cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), launchOptions(v))
            } catch (e: Exception) { toast("Can't open this activity") }
            else -> apps[item.appKey]?.let { launch(it, v) } ?: toast("App isn't installed")
        }
    }

    @Suppress("DEPRECATION")
    private fun widgetView(item: Item): View? {
        val info = awm.getAppWidgetInfo(item.widgetId) ?: return null
        val hv: AppWidgetHostView = host.createView(applicationContext, item.widgetId, info)
        hv.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) {
                val w = ((r - l) / dp).toInt()
                val h = ((b - t) / dp).toInt()
                (v as AppWidgetHostView).updateAppWidgetSize(null, w, h, w, h)
            }
        }
        return WidgetFrame(this).apply { addView(hv) }
    }

    fun updateBadges() {
        val on = looks.str("badges") != "off"
        for (v in iconViews) badge(v.image, if (on) v.badgeKey else "")
    }

    fun badge(image: IconImage, keys: String) {
        val list = if (keys.isEmpty()) emptyList() else keys.split("|").flatMap { BadgeService.byPackage[it].orEmpty() }
        image.badgeCount = list.size
        image.badgeIcon = if (looks.str("badges") == "dynamic") list.firstOrNull()?.notification?.getLargeIcon()?.loadDrawable(this) else null
    }

    // ---- Drag and drop ----

    fun startDrag(v: View, payload: Any) {
        if (locked) { showPopup(v, payload); return }
        dragSource = v
        dragPayload = payload
        dragStart = null
        dragMoved = false
        (dropBar.getChildAt(0) as TextView).text = if (payload is Item || payload is FolderChild) "Remove" else "Cancel"
        dropBar.getChildAt(1).visibility = if (appOf(payload) != null) View.VISIBLE else View.GONE
        dropBar.getChildAt(2).visibility = if (appOf(payload) != null) View.VISIBLE else View.GONE
        v.startDragAndDrop(ClipData.newPlainText("", ""), View.DragShadowBuilder(v), payload, 0)
        if (payload is Item) v.alpha = 0.3f
        if (looks.on("wiggle")) wiggles = iconViews.map { iv ->
            android.animation.ObjectAnimator.ofFloat(iv.image, "rotation", -4f, 4f).apply {
                duration = 140
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }
        }
    }

    private var wiggles = listOf<android.animation.Animator>()

    /** A drag that never moves is a long-press: it shows the item's menu instead. */
    fun trackDrag(v: View, e: DragEvent) {
        if (e.action != DragEvent.ACTION_DRAG_LOCATION || dragMoved) return
        val loc = IntArray(2).also { v.getLocationOnScreen(it) }
        val x = loc[0] + e.x
        val y = loc[1] + e.y
        val s = dragStart ?: PointF(x, y).also { dragStart = it }
        if (hypot(x - s.x, y - s.y) > 16 * dp) {
            dragMoved = true
            // A drag that never moves stays a long-press: no drop targets until it does.
            dropBar.visibility = View.VISIBLE
            root.tick()
            closeMenu()
            drawer.hide(animate = false)
            closeOverlay()
        }
    }

    /** For views that cover home (drawer, folder window): follow the drag but never accept the drop. */
    val passDrag = View.OnDragListener { v, e -> trackDrag(v, e); e.action != DragEvent.ACTION_DROP }

    private fun dropListener(cell: CellLayout) = View.OnDragListener { v, e ->
        trackDrag(v, e)
        when (e.action) {
            DragEvent.ACTION_DRAG_LOCATION -> edgeFlip(if (isDock(cell.page)) dock else workspace, e.x, cell.width)
            DragEvent.ACTION_DROP -> return@OnDragListener drop(cell, e.x, e.y, e.localState)
            // An accepted drop's ENDED goes to the accepting view only, so every cell must clean up too.
            DragEvent.ACTION_DRAG_ENDED -> dragEnded()
        }
        true
    }

    /** Drop target pill shown at the top while dragging; it fills with a container colour while hovered. */
    private fun dropTarget(kind: String) = text(when (kind) { "info" -> "App info"; "uninstall" -> "Uninstall"; else -> "Remove" }, 14f, medium = true).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        val p = (8 * dp).toInt()
        setPadding(p, 0, p, 0)
        compoundDrawablePadding = p * 3 / 4
        fun look(hover: Boolean) {
            val (bg, fg) = when {
                !hover -> (m3.surfaceHigh and 0xFFFFFF or 0xF2000000.toInt()) to m3.onSurface
                kind == "remove" -> m3.errorContainer to m3.onErrorContainer
                else -> m3.primaryContainer to m3.onPrimaryContainer
            }
            background = rounded(bg, 24 * dp)
            setTextColor(fg)
            setCompoundDrawablesRelative(symbol(when (kind) { "info" -> "info"; "uninstall" -> "delete"; else -> "close" }, fg, 20f).apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }, null, null, null)
        }
        look(false)
        elevation = 3 * dp
        setOnDragListener { _, e ->
            val p = e.localState
            when (e.action) {
                DragEvent.ACTION_DRAG_ENTERED -> { look(true); performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
                DragEvent.ACTION_DRAG_EXITED -> look(false)
                DragEvent.ACTION_DROP -> when (kind) {
                    "remove" -> when (p) {
                        is Item -> removeItem(p)
                        is FolderChild -> { removeFromFolder(p); save() }
                    }
                    "info" -> appOf(p)?.let { launcherApps.startAppDetailsActivity(it.info.componentName, it.info.user, null, null) }
                    "uninstall" -> appOf(p)?.let { uninstall(it) }
                }
                DragEvent.ACTION_DRAG_ENDED -> { look(false); dragEnded() }
            }
            true
        }
    }

    private fun dragEnded() {
        if (dragSource == null) return // ENDED can fan out to several listeners; first one wins
        wiggles.forEach { it.cancel() }
        iconViews.forEach { it.image.rotation = 0f }
        wiggles = emptyList()
        dropBar.visibility = View.GONE
        dragSource?.alpha = 1f
        val src = dragSource
        if (!dragMoved && src != null && src.isAttachedToWindow) showPopup(src, dragPayload) else render()
        dragSource = null
    }

    /** Holding a drag near the left/right edge flips to the neighbouring page. */
    private fun edgeFlip(ws: Workspace, x: Float, width: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEdgeFlip < 700 || ws.childCount < 2) return
        val edge = width * 0.06f
        if (x < edge) ws.snapTo(ws.current - 1)
        else if (x > width - edge) ws.snapTo(ws.current + 1)
        else return
        lastEdgeFlip = now
    }

    fun appOf(p: Any?): App? = when (p) {
        is App -> p
        is Item -> if (p.isApp) apps[p.appKey] else null
        is FolderChild -> p.folder.children.getOrNull(p.index)?.let { if (it.isApp) apps[it.appKey] else null }
        else -> null
    }

    /** The payload as a 1×1 item that can live in a folder, or null (widgets, folders). */
    private fun asChild(p: Any?): Item? = when (p) {
        is Item -> if (p.isWidget || p.folder) null else p.copy(page = 0, x = 0, y = 0, w = 1, h = 1)
        is App -> Item(0, 0, 0, pkg = p.pkg, cls = p.cls, user = p.serial)
        is FolderChild -> p.folder.children.getOrNull(p.index)
        is ShortcutInfo -> Item(0, 0, 0, pkg = p.`package`, shortcutId = p.id, user = serial(p.userHandle), label = p.shortLabel?.toString() ?: "")
        else -> null
    }

    private fun drop(cell: CellLayout, px: Float, py: Float, payload: Any?): Boolean {
        if (!dragMoved) return false
        val moving = payload as? Item
        if (isDock(cell.page) && moving?.isWidget == true) return false
        val unit = if (isDock(cell.page)) 1 else sub
        val w = if (moving?.isWidget == true) moving.w else unit
        val h = if (moving?.isWidget == true) moving.h else unit
        val (gx, gy) = cell.toGrid(px, py)
        val x = (gx - w / 2f + 0.5f).toInt().coerceIn(0, maxOf(0, cell.cols - w))
        val y = (gy - h / 2f + 0.5f).toInt().coerceIn(0, maxOf(0, cell.rows - h))
        val child = asChild(payload)
        val (fx, fy) = cell.cellAt(px, py)
        val target = cell.viewAt(fx, fy)?.let { viewItems[it] }
        if (child != null && target != null && target != moving && !(payload is FolderChild && payload.folder == target) &&
            (target.isApp || target.folder || target.isShortcut || target.isAction)) {
            val merged = if (target.folder) target.copy(children = target.children + child)
                else Item(target.page, target.x, target.y, target.w, target.h, folder = true, label = "Folder", children = listOf(asChild(target)!!, child))
            detach(payload)
            items[items.indexOf(target)] = merged
            root.confirm()
            save()
            render()
            return true
        }
        val overlap = looks.on("widgetOverlap") && moving?.isWidget == true
        if (!overlap && !cell.isFree(x, y, w, h, ignore = moving?.let { views[it] })) return false
        val placed = (if (moving != null) moving else child ?: return false).copy(page = cell.page, x = x, y = y, w = w, h = h)
        detach(payload)
        items += placed
        root.tick()
        if (placed.isShortcut) syncPins(placed.pkg, placed.user)
        save()
        render()
        return true
    }

    private fun detach(p: Any?) {
        when (p) {
            is Item -> items.remove(p)
            is FolderChild -> removeFromFolder(p)
        }
    }

    /** Takes one item out of a folder; a folder left with one item turns back into that item. */
    fun removeFromFolder(fc: FolderChild) {
        val f = fc.folder
        val i = items.indexOf(f)
        if (i < 0) return
        val rest = f.children.filterIndexed { j, _ -> j != fc.index }
        when (rest.size) {
            0 -> items.removeAt(i)
            1 -> items[i] = rest[0].copy(page = f.page, x = f.x, y = f.y, w = f.w, h = f.h)
            else -> items[i] = f.copy(children = rest)
        }
    }

    fun removeItem(item: Item) {
        items.remove(item)
        save()
        showUndo(item) {
            if (item.isWidget) host.deleteAppWidgetId(item.widgetId)
            if (item.isShortcut) syncPins(item.pkg, item.user)
        }
    }

    fun uninstall(a: App) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")).putExtra(Intent.EXTRA_USER, a.info.user))
    }

    /** Puts an item on the first free spot, starting at the current page. */
    fun addItem(item: Item, quiet: Boolean = false): Boolean {
        val home = items.filter { !isDock(it.page) }
        val spot = firstFreeSpot(home, looks.pages, looks.cols * sub, looks.rows * sub, item.w, item.h, workspace.current)
        if (spot == null) { if (!quiet) toast("No room on the home screen"); return false }
        items += item.copy(page = spot.page, x = spot.x, y = spot.y)
        save()
        render()
        if (!quiet) toast("Added to home screen")
        return true
    }

    fun addToHome(a: App, quiet: Boolean = false) = addItem(Item(0, 0, 0, sub, sub, pkg = a.pkg, cls = a.cls, user = a.serial), quiet)

    // ---- Long-press menu ----

    fun showPopup(anchor: View, payload: Any?) {
        val rows = ArrayList<View>()
        val tiles = ArrayList<Triple<String, String, () -> Unit>>()
        fun row(icon: Drawable, text: String, summary: String? = null, long: ((View) -> Unit)? = null, click: () -> Unit) {
            rows += listItem(icon, text, summary, dense = true) { closeMenu(); click() }.apply {
                // A drag that starts here closes the menu once it moves (see trackDrag).
                if (long != null) setOnLongClickListener { v -> long(v); if (dragSource !== v) closeMenu(); true }
            }
        }
        fun tile(sym: String, title: String, click: () -> Unit) { tiles += Triple(sym, title, click) }
        val item = payload as? Item
        val app = appOf(payload)
        if (app != null && looks.on("popupShortcuts")) shortcutsFor(app).forEach { s ->
            row(shortcutIcon(s), s.shortLabel?.toString() ?: "", long = { v -> if (!locked) startDrag(v, s) }) {
                startShortcut(s.`package`, s.id, s.userHandle, anchor)
            }
        }
        if (app != null && looks.on("popupNotifications")) BadgeService.byPackage[app.pkg].orEmpty().take(3).forEach { n ->
            val title = n.notification.extras.getCharSequence("android.title") ?: ""
            val body = n.notification.extras.getCharSequence("android.text") ?: ""
            row(symbol("notifications"), title.toString(), body.toString(), long = { BadgeService.instance?.cancelNotification(n.key) }) {
                runCatching { n.notification.contentIntent?.send() }
            }
        }
        when {
            payload is App -> {
                if (!locked) tile("add", "Add") { addToHome(payload) }
                tile("edit", "Edit") { editApp(payload) }
                val hidden = payload.key in store.strings("hidden")
                tile(if (hidden) "show" else "hide", if (hidden) "Unhide" else "Hide") { toggleHidden(payload.key) }
                tile("tabs", "Group") { addToGroup(payload.key) }
            }
            item != null && !locked -> {
                if (!item.isWidget) tile("edit", "Edit") { editItem(item) }
                if (item.isWidget) tile("resize", "Resize") { resizeWidget(item) }
                if (item.isWidget && awm.getAppWidgetInfo(item.widgetId)?.configure != null) tile("settings", "Settings") {
                    runCatching { host.startAppWidgetConfigureActivityForResult(this, item.widgetId, 0, REQ_RECONFIG, null) }
                }
                tile("close", "Remove") { removeItem(item); render() }
            }
            payload is FolderChild && !locked -> tile("close", "Remove") { removeFromFolder(payload); save(); render() }
        }
        if (app != null) {
            tile("info", "App info") { launcherApps.startAppDetailsActivity(app.info.componentName, app.info.user, null, null) }
            if (!isSystemApp(app)) tile("delete", "Uninstall") { uninstall(app) }
            if (awm.installedProviders.any { it.provider.packageName == app.pkg }) tile("widgets", "Widgets") { pickWidget(app.pkg) }
            tile("download", "Save APK") { saveApk(app) }
            if (looks.on("closeApp")) tile("stop", "Close") {
                getSystemService(android.app.ActivityManager::class.java).killBackgroundProcesses(app.pkg)
                toast("Closed background tasks for ${label(app)}")
            }
            tile("image", "Icon PNG") { exportIcon(app) }
        }
        if (rows.isEmpty() && tiles.isEmpty()) return
        showMenu(screenRect(anchor)) {
            rows.forEach { addView(it) }
            if (rows.isNotEmpty() && tiles.isNotEmpty()) addView(hairline())
            addTiles(tiles, 4)
        }
    }

    /** The open long-press or home menu, an overlay inside this window. */
    var menu: View? = null
        private set

    fun closeMenu() {
        val m = menu ?: return
        menu = null
        // Let touches through at once; it only fades out.
        m.setOnClickListener(null); m.isClickable = false
        m.animate().alpha(0f).setDuration(120).withEndAction { root.removeView(m) }.start()
    }

    /** [v]'s bounds in this window. */
    fun screenRect(v: View): Rect {
        val a = IntArray(2).also { v.getLocationInWindow(it) }
        val r = IntArray(2).also { root.getLocationInWindow(it) }
        return Rect(a[0] - r[0], a[1] - r[1], a[0] - r[0] + v.width, a[1] - r[1] + v.height)
    }

    /**
     * A glass popover menu next to [anchor] (window coordinates): below it if there's room, else above. It grows out
     * of the anchor. Drawn inside this window rather than as a PopupWindow: a drag started from another window never
     * delivers its end event here.
     */
    fun showMenu(anchor: Rect, build: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            frost(this, 24 * dp)
            clipToOutline = true
            val p = (6 * dp).toInt()
            setPadding(0, p, 0, p)
            build()
        }
        closeMenu()
        // Wider with a larger system font size, so the tracked tile legends don't clip.
        val margin = (8 * dp).toInt()
        val w = minOf((264 * dp * maxOf(1f, resources.configuration.fontScale)).toInt(), root.width - 2 * margin)
        box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val x = (anchor.centerX() - w / 2).coerceIn(margin, maxOf(margin, root.width - w - margin))
        val below = anchor.bottom + margin
        val y = if (below + box.measuredHeight < root.height - insets.bottom) below
            else maxOf(insets.top, anchor.top - box.measuredHeight - margin)
        val shade = FrameLayout(this).apply {
            setOnClickListener { closeMenu() }
            setOnDragListener(passDrag)
            addView(box, FrameLayout.LayoutParams(w, -2).apply { leftMargin = x; topMargin = y })
        }
        root.addView(shade)
        menu = shade
        box.pivotX = (anchor.centerX() - x).toFloat()
        box.pivotY = if (y >= below) 0f else box.measuredHeight.toFloat()
        box.scaleX = 0.8f; box.scaleY = 0.8f; box.alpha = 0f
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start()
    }

    /** Icon-over-label action tiles, [cols] to a row, for a menu. */
    fun LinearLayout.addTiles(tiles: List<Triple<String, String, () -> Unit>>, cols: Int) = tiles.chunked(cols).forEach { chunk ->
        addView(LinearLayout(context).apply {
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            chunk.forEach { (sym, title, click) ->
                addView((if (m3.housing) legend(title, 10f) else text(title, 11f, m3.onSurfaceVariant)).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    compoundDrawablePadding = (6 * dp).toInt()
                    setCompoundDrawablesWithIntrinsicBounds(null, symbol(sym, m3.onSurface, 22f), null, null)
                    setPadding(0, (12 * dp).toInt(), 0, (10 * dp).toInt())
                    background = pressable(null, 16 * dp)
                    setOnClickListener { closeMenu(); click() }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            // Keep tiles the same width in a short last row.
            repeat(cols - chunk.size) { addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
        })
    }

    /** A hairline between menu sections. */
    private fun hairline() = View(this).apply {
        setBackgroundColor(m3.onSurface and 0xFFFFFF or 0x1F000000)
        layoutParams = LinearLayout.LayoutParams(-1, maxOf(1, (dp / 2).toInt())).apply { setMargins(0, (6 * dp).toInt(), 0, (6 * dp).toInt()) }
    }

    // ponytail: copies the base APK only; split APKs (app bundles) need all of applicationInfo.splitSourceDirs to install
    @Suppress("DEPRECATION")
    private fun saveApk(a: App) {
        onDocument = { uri ->
            runCatching {
                java.io.File(a.info.applicationInfo.sourceDir).inputStream().use { src -> contentResolver.openOutputStream(uri, "wt")!!.use { src.copyTo(it) } }
            }.onSuccess { toast("APK saved") }.onFailure { toast("Couldn't save: ${it.message}") }
        }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "${a.pkg}.apk"), REQ_DOC)
    }

    @Suppress("DEPRECATION")
    private fun exportIcon(a: App) {
        onDocument = { uri ->
            val size = (192 * dp).toInt()
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            icon(a).apply { setBounds(0, 0, size, size); draw(android.graphics.Canvas(bmp)) }
            runCatching { contentResolver.openOutputStream(uri, "wt")!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                .onSuccess { toast("Icon saved") }.onFailure { toast("Couldn't save: ${it.message}") }
        }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/png").putExtra(Intent.EXTRA_TITLE, "${label(a)}.png"), REQ_DOC)
    }

    private fun isSystemApp(a: App) = a.info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

    fun toggleHidden(key: String) {
        val hidden = store.strings("hidden")
        if (!hidden.remove(key)) hidden += key
        store.putStrings("hidden", hidden)
        drawer.refresh()
    }

    // ---- Home menu, widgets and shortcuts ----

    fun homeMenu() {
        val entries = listOf(
            Triple("widgets", "Widgets") { pickWidget() },
            Triple("bolt", "Shortcuts") { addShortcutMenu() },
            Triple("image", "Wallpaper") { perform("wallpaper") },
            Triple("carousel", "Pages") { showOverview() },
            Triple(if (locked) "unlock" else "lock", if (locked) "Unlock" else "Lock") {
                looks["lockLayout"] = if (locked) "off" else "on"; toast(if (locked) "Layout locked" else "Layout unlocked")
            },
            Triple("settings", "Settings") { showSettings() },
        ).filter { !locked || it.first !in listOf("widgets", "bolt") }
        val at = Rect(lastDown.x.toInt(), lastDown.y.toInt(), lastDown.x.toInt(), lastDown.y.toInt())
        showMenu(at) { addTiles(entries, 3) }
    }

    fun pickWidget(pkg: String? = null) {
        val providers = awm.installedProviders.filter { pkg == null || it.provider.packageName == pkg }
            .sortedWith(compareBy({ it.loadLabel(packageManager).lowercase() }))
        val page = pages[workspace.current]
        val names = providers.map {
            val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it.provider.packageName, 0)) }.getOrDefault("")
            val w = ceil(it.minWidth / page.cellW.toFloat() / sub).toInt().coerceAtLeast(1)
            val h = ceil(it.minHeight / page.cellH.toFloat() / sub).toInt().coerceAtLeast(1)
            "${it.loadLabel(packageManager)}  ($w×$h)\n$app"
        }
        dialog().setTitle("Widgets").setItems(names.toTypedArray()) { _, i -> addWidget(providers[i]) }.show()
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
        val w = ceil(info.minWidth / page.cellW.toFloat()).toInt().coerceIn(1, page.cols)
        val h = ceil(info.minHeight / page.cellH.toFloat()).toInt().coerceIn(1, page.rows)
        if (!addItem(Item(workspace.current, 0, 0, w, h, widgetId = id), quiet = true)) {
            host.deleteAppWidgetId(id)
            toast("No room on the home screen")
        }
    }

    private fun cancelWidget() {
        pendingWidget?.let { host.deleteAppWidgetId(it.first) }
        pendingWidget = null
    }

    fun resizeWidget(item: Item) {
        val v = views[item] ?: return
        val cell = v.parent as? CellLayout ?: return
        val info = awm.getAppWidgetInfo(item.widgetId) ?: return
        val minW = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL == 0) item.w
            else ceil(info.minResizeWidth.coerceAtLeast(1) / cell.cellW.toFloat()).toInt().coerceIn(1, item.w)
        val minH = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0) item.h
            else ceil(info.minResizeHeight.coerceAtLeast(1) / cell.cellH.toFloat()).toInt().coerceIn(1, item.h)
        showResize(cell, item, v, minW, minH,
            info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0, info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
    }

    private fun addShortcutMenu() {
        val options = arrayOf("Launcher action", "App shortcut", "Activity")
        dialog().setTitle("Add to home screen").setItems(options) { _, which ->
            when (which) {
                0 -> pickAction("Launcher action") { addItem(Item(0, 0, 0, sub, sub, action = it)) }
                1 -> {
                    val list = packageManager.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
                        .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                    dialog().setTitle("App shortcut").setItems(list.map { it.loadLabel(packageManager) }.toTypedArray()) { _, i ->
                        val a = list[i].activityInfo
                        runCatching { startActivityForResult(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(ComponentName(a.packageName, a.name)), REQ_SHORTCUT) }
                    }.show()
                }
                else -> pickApp("Activity") { app ->
                    val acts = runCatching { packageManager.getPackageInfo(app.pkg, PackageManager.GET_ACTIVITIES).activities }
                        .getOrNull().orEmpty().filter { it.exported }
                    dialog().setTitle(label(app)).setItems(acts.map { "${it.loadLabel(packageManager)}\n${it.name.removePrefix(app.pkg)}" }.toTypedArray()) { _, i ->
                        addItem(Item(0, 0, 0, sub, sub, pkg = app.pkg, cls = acts[i].name, action = "activity"))
                    }.show()
                }
            }
        }.show()
    }

    fun pickApp(title: String, onPicked: (App) -> Unit) {
        val list = apps.values.sortedBy { label(it).lowercase() }
        dialog().setTitle(title).setItems(list.map { label(it) + if (isWork(it)) " (work)" else "" }.toTypedArray()) { _, i -> onPicked(list[i]) }.show()
    }

    fun pickAction(title: String, onPicked: (String) -> Unit) {
        val names = ACTION_NAMES + listOf("Jump to page…", "Open app…")
        dialog().setTitle(title).setItems(names.toTypedArray()) { _, i ->
            when (i) {
                ACTIONS.size -> dialog().setTitle("Page").setItems(Array(looks.pages) { "Page ${it + 1}" }) { _, p -> onPicked("page:$p") }.show()
                ACTIONS.size + 1 -> pickApp("Open app") { onPicked("app:${it.key}") }
                else -> onPicked(ACTIONS[i])
            }
        }.show()
    }

    // ---- Actions ----

    fun perform(action: String) {
        when {
            action.startsWith("page:") -> workspace.snapTo(action.removePrefix("page:").toIntOrNull() ?: 0)
            action.startsWith("app:") -> apps[action.removePrefix("app:")]?.let { launch(it, null) } ?: toast("App isn't installed")
            else -> when (action) {
                "drawer" -> drawer.show(false)
                "now" -> showNow()
                "search" -> drawer.show(true)
                "notifications" -> statusBar("expandNotificationsPanel", AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "quicksettings" -> statusBar("expandSettingsPanel", AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "togglebar" -> { barHidden = !barHidden; applyBar() }
                "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "lock" -> if (android.os.Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else toast("Screen lock needs Android 9")
                "power" -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                "defaultpage" -> workspace.snapTo(looks.int("defaultPage"))
                "previews" -> showOverview()
                "toggledock" -> dockBox.visibility = if (dockBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                "voice" -> startAny(Intent(RecognizerIntent.ACTION_WEB_SEARCH), Intent(Intent.ACTION_VOICE_COMMAND))
                "assistant" -> startAny(Intent(Intent.ACTION_ASSIST), Intent(Intent.ACTION_VOICE_COMMAND))
                "settings" -> showSettings()
                "widgets" -> pickWidget()
                "wallpaper" -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
            }
        }
    }

    private fun startAny(vararg intents: Intent) {
        for (i in intents) try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {}
        toast("No app handles this")
    }

    /** Hidden StatusBarManager API (needs EXPAND_STATUS_BAR); falls back to the accessibility service. */
    @SuppressLint("WrongConstant")
    private fun statusBar(method: String, fallback: Int) {
        val ok = runCatching {
            val sbm = getSystemService("statusbar")
            sbm.javaClass.getMethod(method).invoke(sbm)
        }.isSuccess
        if (!ok) global(fallback)
    }

    private fun global(action: Int) {
        val svc = ActionService.instance
        if (svc != null) { svc.performGlobalAction(action); return }
        dialog().setTitle("Turn on the accessibility service")
            .setMessage("Screen lock, recent apps and the power menu need Vulcan's accessibility service. It reads no screen content; it only performs these actions.")
            .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Cancel", null).show()
    }

    @Suppress("DEPRECATION")
    private fun applyBar() {
        if (barHidden) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun wallpaperScroll(pos: Float) {
        val n = looks.pages
        val token = window.decorView.windowToken ?: return
        val f = if (n > 1) (pos / (n - 1)).coerceIn(0f, 1f) else 0.5f
        val x = when (looks.str("wallpaperScroll")) { "off" -> 0.5f; "reverse" -> 1 - f; else -> f }
        val wm = WallpaperManager.getInstance(this)
        wm.setWallpaperOffsetSteps(if (n > 1) 1f / (n - 1) else 1f, 1f)
        wm.setWallpaperOffsets(token, x, 0.5f)
    }

    // ---- Activity results ----

    @Deprecated("Plain Activity result API is fine without androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val ok = resultCode == RESULT_OK
        when (requestCode) {
            REQ_BIND -> if (ok) configureWidget() else cancelWidget()
            REQ_CONFIG -> if (ok) placeWidget() else cancelWidget()
            REQ_SHORTCUT -> if (ok && data != null) {
                val req = launcherApps.getPinItemRequest(data)
                // ponytail: pre-Android 8 style EXTRA_SHORTCUT_INTENT results aren't stored; every current app uses pin requests
                if (req != null && addPinned(this, req)) { items = fit(store.load()); render() }
            }
            REQ_BACKUP -> data?.data?.takeIf { ok }?.let { uri ->
                runCatching { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.export().toByteArray()) } }
                    .onSuccess { toast("Backup saved") }.onFailure { toast("Backup failed: ${it.message}") }
            }
            REQ_RESTORE -> data?.data?.takeIf { ok }?.let { uri ->
                runCatching { store.import(contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }) }
                    .onSuccess {
                        // Widget IDs belong to the install that made them; drop the ones this install doesn't own.
                        store.save(store.load().filter { !it.isWidget || awm.getAppWidgetInfo(it.widgetId) != null })
                        recreate()
                    }.onFailure { toast("Restore failed: ${it.message}") }
            }
            REQ_DOC -> data?.data?.takeIf { ok }?.let { onDocument?.invoke(it) }.also { onDocument = null }
            REQ_ICON -> data?.data?.takeIf { ok }?.let { uri ->
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                onPickedImage?.invoke(uri)
                onPickedImage = null
            }
        }
    }
}
