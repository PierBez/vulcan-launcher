package dev.vulcanlauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Settings: a full-screen M3 page (categories, then one page per category). Picking a value opens a dialog;
// saving a setting recreates the activity, and the page reopens where it was (see MainActivity.onSaveInstanceState).

private val CATEGORY_INFO = mapOf(
    HOME to ("home" to "Grid, pages, icons, labels and search bar"),
    DOCK_GROUP to ("dock" to "Icons, pages and background"),
    DRAWER to ("apps" to "Layout, sorting and search"),
    FOLDERS to ("folder" to "Previews, backgrounds and windows"),
    LOOK to ("palette" to "Theme color, icon pack, shapes and animation"),
    NIGHT to ("moon" to "Dark panels and their schedule"),
    BADGES to ("badge" to "Notification dots and counts"),
    GESTURES to ("gesture" to "Swipes, taps, pinches and the home button"),
    NOW to ("sun" to "Cards on the Now page"),
)

fun MainActivity.showSettings(page: String = "", scroll: Int = 0, restoring: Boolean = false) {
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, insets.bottom + (16 * dp).toInt())
    }
    if (page == "") settingsHome(list) else settingsCategory(page, list)
    val scrollView = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
    val back = Runnable { if (page == "") closeOverlay() else showSettings() }
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(m3.surface)
        isClickable = true
        setPadding(insets.left, insets.top, insets.right, 0)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            addView(iconButton("back", "Back", m3.onSurface) { back.run() })
            addView(text(if (page == "") "Settings" else page, 22f).apply { setPadding((8 * dp).toInt(), 0, 0, 0); maxLines = 1 },
                LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, (64 * dp).toInt()))
        addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        tag = back
    }
    showOverlay(panel)
    settingsPage = page
    settingsScroll = scrollView
    // Reopening after a setting recreated the activity: appear at once, or home shows through the fade-in.
    if (scroll > 0) scrollView.post { scrollView.scrollTo(0, scroll) }
    else if (!restoring) { panel.alpha = 0f; panel.translationX = 24 * dp; panel.animate().alpha(1f).translationX(0f).setDuration(180).start() }
}

private fun MainActivity.settingsHome(list: LinearLayout) {
    list.addView(nameplate())
    fun category(name: String) = CATEGORY_INFO.getValue(name).let { (icon, info) ->
        listItem(symbol(icon, m3.onPrimaryContainer), name, info, tonalIcon = true) { showSettings(name) }
    }
    panel(list, "Layout", listOf(HOME, DOCK_GROUP, DRAWER, FOLDERS).map(::category))
    panel(list, "Appearance", listOf(LOOK, NIGHT, BADGES).map(::category))
    panel(list, "Interaction", listOf(GESTURES, NOW).map(::category))
    panel(list, "Apps", listOf(
        listItem(symbol("hide", m3.onPrimaryContainer), "Hidden apps", "Keep apps out of the drawer", tonalIcon = true) { hideAppsMenu() },
        listItem(symbol("tabs", m3.onPrimaryContainer), "Drawer groups", "Tabs and folders in the drawer", tonalIcon = true) { groupsMenu() },
    ))
    panel(list, "Launcher", listOf(
        listItem(symbol("archive", m3.onPrimaryContainer), "Backup & restore", "Export, import or bring over another launcher's layout", tonalIcon = true) { backupMenu() },
        listItem(symbol("shield", m3.onPrimaryContainer), "Permissions", "What the launcher can access", tonalIcon = true) { permissionsMenu() },
        listItem(symbol("home", m3.onPrimaryContainer), "Default home app", "Make Vulcan your home screen", tonalIcon = true) {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        },
        listItem(symbol("refresh", m3.onPrimaryContainer), "Restart launcher", null, tonalIcon = true) { closeOverlay(); recreate() },
        listItem(symbol("info", m3.onPrimaryContainer), "About", "Version $versionName", tonalIcon = true) {
            dialog().setTitle("Vulcan")
                .setMessage("A home screen with no network access: no ads, analytics or tracking. Everything stays on this device.")
                .setPositiveButton("OK", null).show()
        },
    ))
}

private val MainActivity.versionName get() = packageManager.getPackageInfo(packageName, 0).versionName

/** Rows on one raised face, divided by hairlines, under a printed legend: one panel of the appliance. */
private fun MainActivity.panel(list: LinearLayout, title: String?, rows: List<View>) {
    if (title != null) list.addView(legend(title, 12f).apply { setPadding((32 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt()) })
    list.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        housing(this, 24 * dp)
        elevation = 2 * dp
        clipToOutline = true
        setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        rows.forEachIndexed { i, row ->
            // The hairline starts under the text, past a row's icon well.
            if (i > 0) addView(View(context).apply { setBackgroundColor(m3.outlineVariant) }, LinearLayout.LayoutParams(-1, maxOf(1, (dp / 2).roundToInt())).apply {
                marginStart = ((if ((rows[i - 1] as? LinearLayout)?.getChildAt(0) is ImageView) 72 else 16) * dp).toInt()
                marginEnd = (16 * dp).toInt()
            })
            addView(row)
        }
    }, LinearLayout.LayoutParams(-1, -2).apply { setMargins((16 * dp).toInt(), if (title == null) (8 * dp).toInt() else 0, (16 * dp).toInt(), (8 * dp).toInt()) })
}

/** The rating plate at the top of settings: the name, and what the launcher can't do, printed on a raised face. */
private fun MainActivity.nameplate() = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    housing(this, 24 * dp)
    elevation = 2 * dp
    setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(text("Vulcan", 34f).apply {
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            letterSpacing = -0.02f
        })
        addView(legend("No internet permission", 11f).apply { setPadding(0, (6 * dp).toInt(), 0, 0) })
        addView(legend("Version $versionName", 11f, m3.outline).apply { setPadding(0, (2 * dp).toInt(), 0, 0) })
    }, LinearLayout.LayoutParams(0, -2, 1f))
    addView(Grille(this@nameplate))
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt()) }
}

/** A speaker grille punched into the nameplate: 5 dp dots on the page grille's 11 dp pitch. */
private class Grille(private val a: MainActivity) : View(a) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((11 * 6 * a.dp).toInt(), (11 * 5 * a.dp).toInt())
    override fun onDraw(canvas: android.graphics.Canvas) {
        val pitch = 11 * a.dp
        paint.color = a.m3.well
        for (r in 0 until 5) for (c in 0 until 6) canvas.drawCircle((c + 0.5f) * pitch, (r + 0.5f) * pitch, 2.5f * a.dp, paint)
    }
}

private fun MainActivity.settingsCategory(group: String, list: LinearLayout) {
    val choices = (if (group == LOOK) listOf(iconPackChoice()) else emptyList()) + LOOKS.filter { it.group == group }
    fun pick(c: Choice) = dialog().setTitle(c.title).setSingleChoiceItems(c.names.toTypedArray(), c.values.indexOf(looks[c])) { d, j ->
        d.dismiss()
        applyChoice(c, c.values[j])
    }.setNegativeButton("Cancel", null).show()
    panel(list, null, choices.map { c -> when {
        // The housing dock has its own shadow; no background has none to cast.
        c.key == "dockShadow" && looks.str("dockBg") in listOf("housing", "none") ->
            listItem(null, c.title, if (looks.str("dockBg") == "none") "Not used without a dock background" else "The Housing dock has its own shadow") {}.apply { alpha = 0.5f }
        c.values.toSet() == setOf("on", "off") -> {
            val toggle = Toggle(this, looks[c] == "on")
            listItem(null, c.title, trailing = toggle) { v ->
                toggle.checked = !toggle.checked
                // Let the switch finish moving before the activity is recreated.
                v.postDelayed({ applyChoice(c, if (toggle.checked) "on" else "off") }, 200)
            }
        }
        c.action -> listItem(null, c.title, valueName(c)) { pickAction(c.title) { looks[c] = it; showSettings(group, settingsScroll?.scrollY ?: 0) } }
        // Settings the chosen dock background doesn't use: shown dimmed and saying so, still pickable.
        c.key == "dockAlpha" && looks.str("dockBg") in listOf("housing", "glass", "none") || c.key == "dockColor" && looks.str("dockBg") == "none" ->
            listItem(null, c.title, if (looks.str("dockBg") == "none") "Not used without a dock background"
                else "Not used by the ${valueName(LOOKS.first { it.key == "dockBg" })} background") { pick(c) }.apply { alpha = 0.5f }
        else -> listItem(null, c.title, valueName(c), trailing = swatch(c)) { pick(c) }
    } })
}

/** A colour setting's current colour as a small circle, or null for other settings. */
private fun MainActivity.swatch(c: Choice): View? {
    if (!c.values.any { it.startsWith("#") }) return null
    return View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            // "system" on a housing-coloured part shows the housing it really draws in.
            setColor(when (looks[c]) {
                "system" -> if (m3.housing && c.key in listOf("dockColor", "searchColor", "folderColor")) m3.surface else m3.primary
                "appliance" -> SIGNAL
                else -> looks.color(c.key)
            })
            setStroke(dp.toInt(), m3.outlineVariant)
        }
        layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
    }
}

private fun MainActivity.iconPackChoice(): Choice {
    val packs = iconPacks(this)
    return Choice(LOOK, "iconPack", "Icon pack", "", listOf("") + packs.map { it.first }, listOf("System icons") + packs.map { it.second })
}

private fun MainActivity.valueName(c: Choice) = when {
    c.action -> actionName(looks[c], ::appLabel)
    // Under the Appliance look, "system" on a housing-coloured part draws in the housing colour.
    m3.housing && looks[c] == "system" && c.key in listOf("dockColor", "searchColor", "folderColor") -> "Housing (follows the theme)"
    else -> c.names.getOrElse(c.values.indexOf(looks[c])) { looks[c].ifEmpty { "None" } }
}

/** Saves a setting, asking for a permission it needs first, then recreates the activity. */
private fun MainActivity.applyChoice(c: Choice, value: String) {
    val old = looks[c]
    if (value == old) return
    looks[c] = value
    val perm = when {
        value == "on" && c.key in listOf("contactSearch", "nowContacts") -> android.Manifest.permission.READ_CONTACTS
        value == "on" && c.key == "nowCalendar" -> android.Manifest.permission.READ_CALENDAR
        c.key == "nightMode" && value == "sun" -> android.Manifest.permission.ACCESS_COARSE_LOCATION
        else -> null
    }
    when {
        c.key == "hideClock" && value == "on" && !granted(android.Manifest.permission.WRITE_SECURE_SETTINGS) -> dialog()
            .setTitle("One-time setup needed")
            .setMessage("Android only lets apps hide the status bar clock with a permission you grant from a computer:\n\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\nRun it once, then reopen the launcher.")
            .setPositiveButton("OK") { _, _ -> recreate() }.show()
        // The permission result recreates the activity (onRequestPermissionsResult).
        perm != null && !granted(perm) -> requestPermissions(arrayOf(perm), MainActivity.REQ_PERMISSION)
        else -> recreate()
    }
}

/** Label, icon and (for home items) swipe-up action. Empty label or icon means "use the default". */
fun MainActivity.editDialog(title: String, label: String, defaultLabel: String, icon: String?, swipe: String?, onSave: (String, String, String) -> Unit) {
    var iconV = icon ?: ""
    var swipeV = swipe ?: ""
    val pad = (20 * dp).toInt()
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
    val name = EditText(this).apply { setText(label); hint = defaultLabel.ifEmpty { "Label" }; setSingleLine() }
    box.addView(name)
    if (icon != null) box.addView(Button(this).apply {
        fun caption() { text = if (iconV.isEmpty()) "Icon: default" else "Icon: custom" }
        caption()
        setOnClickListener { pickIcon { iconV = it; caption() } }
    })
    if (swipe != null) box.addView(Button(this).apply {
        fun caption() { text = "Swipe up: ${actionName(swipeV.ifEmpty { "none" }, ::appLabel)}" }
        caption()
        setOnClickListener { pickAction("Swipe up action") { swipeV = if (it == "none") "" else it; caption() } }
    })
    dialog().setTitle(title).setView(box)
        .setPositiveButton("Save") { _, _ -> onSave(name.text.toString().trim(), iconV, swipeV) }
        .setNegativeButton("Cancel", null).show()
}

fun MainActivity.editItem(item: Item) {
    val default = when {
        item.folder -> "Folder"
        item.isApp -> appLabel(item.appKey) ?: ""
        else -> resolve(item.copy(label = ""))?.first?.toString() ?: ""
    }
    editDialog(if (item.folder) "Edit folder" else "Edit", item.label, default, if (item.folder) null else item.icon, item.swipe) { l, ic, sw ->
        val i = items.indexOf(item)
        if (i < 0) return@editDialog
        items[i] = item.copy(label = if (item.folder) l.ifEmpty { "Folder" } else l, icon = ic, swipe = sw)
        save()
        render()
    }
}

/** Drawer edits apply to the app everywhere it has no home-screen override. */
fun MainActivity.editApp(app: App) {
    val c = customs.optJSONObject(app.key)
    editDialog("Edit app", c?.optString("label") ?: "", app.info.label.toString(), c?.optString("icon") ?: "", null) { l, ic, _ ->
        customs.put(app.key, JSONObject().put("label", l).put("icon", ic))
        store.putObj("custom", customs)
        loadApps()
        drawer.refresh()
        render()
    }
}

fun MainActivity.pickIcon(onPicked: (String) -> Unit) {
    val packs = iconPacks(this)
    val options = listOf("Default icon") + packs.map { "From ${it.second}" } + "Image from gallery"
    dialog().setTitle("Icon").setItems(options.toTypedArray()) { _, i ->
        when {
            i == 0 -> onPicked("")
            i <= packs.size -> pickPackIcon(packs[i - 1].first, onPicked)
            else -> {
                onPickedImage = { onPicked("uri:$it") }
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), MainActivity.REQ_ICON)
            }
        }
    }.show()
}

private fun MainActivity.pickPackIcon(pkg: String, onPicked: (String) -> Unit) {
    val pack = loadIconPack(this, pkg) ?: run { toast("Can't read this icon pack"); return }
    val all = pack.names
    var shown = all
    val size = (56 * dp).toInt()
    val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup) = ((convert as? ImageView) ?: ImageView(this@pickPackIcon).apply {
            layoutParams = android.widget.AbsListView.LayoutParams(size, size)
        }).apply { setImageDrawable(pack.drawable(shown[p])) }
    }
    val grid = GridView(this).apply { numColumns = GridView.AUTO_FIT; columnWidth = size; this.adapter = adapter; verticalSpacing = size / 4 }
    val filter = EditText(this).apply {
        hint = "Filter ${all.size} icons"
        setSingleLine()
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { shown = all.filter { it.contains(s.toString(), true) }; adapter.notifyDataSetChanged() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(filter)
        addView(grid, LinearLayout.LayoutParams(-1, (400 * dp).toInt()))
    }
    val d = dialog().setTitle("Choose icon").setView(box).setNegativeButton("Cancel", null).show()
    grid.setOnItemClickListener { _, _, p, _ -> d.dismiss(); onPicked("pack:$pkg:${shown[p]}") }
}

fun MainActivity.hideAppsMenu() {
    val list = apps.values.sortedBy { label(it).lowercase() }
    val hidden = store.strings("hidden")
    val checked = BooleanArray(list.size) { list[it].key in hidden }
    dialog().setTitle("Hide apps")
        .setMultiChoiceItems(list.map { label(it) }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
        .setPositiveButton("Save") { _, _ ->
            store.putStrings("hidden", list.filterIndexed { i, _ -> checked[i] }.map { it.key })
            drawer.refresh()
        }.setNegativeButton("Cancel", null).show()
}

// ---- Drawer groups (tabs and folders) ----

private fun MainActivity.saveGroups(groups: List<Group>) {
    store.data.edit().putString("groups", groupsToJson(groups)).apply()
    drawer.refresh()
}

fun MainActivity.groupsMenu() {
    val groups = drawer.groups()
    val names = groups.map { (if (it.type == "tab") "Tab: " else "Folder: ") + it.name } + listOf("＋ New tab", "＋ New folder", "✦ Create smart folders")
    dialog().setTitle("Drawer groups").setItems(names.toTypedArray()) { _, i ->
        when (i - groups.size) {
            0 -> newGroup("tab")
            1 -> newGroup("folder")
            2 -> smartFolders()
            else -> editGroup(groups[i])
        }
    }.show()
}

private fun MainActivity.askName(title: String, current: String, onName: (String) -> Unit) {
    val input = EditText(this).apply { setText(current); setSingleLine() }
    dialog().setTitle(title).setView(input)
        .setPositiveButton("OK") { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onName) }
        .setNegativeButton("Cancel", null).show()
}

private fun MainActivity.newGroup(type: String) = askName(if (type == "tab") "New tab" else "New folder", "") { name ->
    val groups = drawer.groups()
    if (groups.any { it.name == name }) { toast("A group with that name exists"); return@askName }
    chooseApps(name, emptyList()) { saveGroups(groups + Group(name, type, it)) }
}

private fun MainActivity.chooseApps(title: String, current: List<String>, onDone: (List<String>) -> Unit) {
    val list = apps.values.sortedBy { label(it).lowercase() }
    val checked = BooleanArray(list.size) { list[it].key in current }
    dialog().setTitle(title)
        .setMultiChoiceItems(list.map { label(it) }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
        .setPositiveButton("Save") { _, _ -> onDone(list.filterIndexed { i, _ -> checked[i] }.map { it.key }) }
        .setNegativeButton("Cancel", null).show()
}

fun MainActivity.editGroup(g: Group) {
    val groups = drawer.groups()
    fun replace(n: Group?) = saveGroups(groups.mapNotNull { if (it.name == g.name) n else it })
    val other = if (g.type == "tab") "folder" else "tab"
    val options = arrayOf("Rename", "Choose apps", "Convert to $other", "Delete")
    dialog().setTitle(g.name).setItems(options) { _, i ->
        when (i) {
            0 -> askName("Rename", g.name) { replace(g.copy(name = it)) }
            1 -> chooseApps(g.name, g.apps) { replace(g.copy(apps = it)) }
            2 -> replace(g.copy(type = other))
            else -> replace(null)
        }
    }.show()
}

fun MainActivity.addToGroup(key: String) {
    val groups = drawer.groups()
    val names = groups.map { it.name } + "＋ New folder"
    dialog().setTitle("Add to group").setItems(names.toTypedArray()) { _, i ->
        if (i == groups.size) askName("New folder", "") { saveGroups(groups + Group(it, "folder", listOf(key))) }
        else saveGroups(groups.map { if (it.name == groups[i].name && key !in it.apps) it.copy(apps = it.apps + key) else it })
    }.show()
}

/** Groups apps into drawer folders by the category each app declares (games, social, productivity…). */
private fun MainActivity.smartFolders() {
    val groups = drawer.groups()
    val byCat = apps.values.groupBy { it.info.applicationInfo.category }.filterKeys { it != ApplicationInfo.CATEGORY_UNDEFINED }
    val made = byCat.filterValues { it.size >= 2 }.mapNotNull { (cat, list) ->
        val name = ApplicationInfo.getCategoryTitle(this, cat)?.toString() ?: return@mapNotNull null
        if (groups.any { it.name == name }) null else Group(name, "folder", list.map { it.key })
    }
    if (made.isEmpty()) toast("No new categories to group") else { saveGroups(groups + made); toast("Created ${made.size} folders") }
}

// ---- Backup, permissions ----

@Suppress("DEPRECATION")
private fun MainActivity.backupMenu() {
    val autos = store.autoBackups.filter { it.exists() }
    val autoNames = autos.map { "Restore automatic backup (${SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it.lastModified()))})" }
        .ifEmpty { listOf("Restore automatic backup (none yet)") }
    dialog().setTitle("Backup & restore").setItems((listOf("Back up to a file…", "Restore from a file…", "Reset to defaults",
        "Import a launcher backup (.novabackup)…") + autoNames).toTypedArray()) { _, i ->
        when (i) {
            0 -> startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "vulcan-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json"), MainActivity.REQ_BACKUP)
            1 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), MainActivity.REQ_RESTORE)
            3 -> {
                onDocument = { uri -> importBackup(uri) }
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), MainActivity.REQ_DOC)
            }
            in 4 until 4 + autos.size -> runCatching { store.import(autos[i - 4].readText()) }
                .onSuccess {
                    dropForeignWidgets()
                    recreate()
                }.onFailure { toast("Restore failed: ${it.message}") }
            2 -> dialog().setMessage("Remove every setting and the whole home screen layout?")
                .setPositiveButton("Reset") { _, _ ->
                    items.filter { it.isWidget }.forEach { host.deleteAppWidgetId(it.widgetId) }
                    store.reset()
                    recreate()
                }.setNegativeButton("Cancel", null).show()
            else -> {} // "none yet"
        }
    }.show()
}

/** Replaces the home layout (and grid settings) with an imported launcher backup's, after confirming. */
private fun MainActivity.importBackup(uri: android.net.Uri) {
    val (layout, settings) = runCatching { readLauncherBackup(this, uri) }.getOrElse { toast("Import failed: ${it.message}"); return }
    val note = if (layout.skipped > 0) "\n\n${layout.skipped} items can't come over (widgets must be added again, and some old-style shortcuts aren't supported)." else ""
    dialog().setTitle("Import this layout?")
        .setMessage("${layout.items.size} home screen items and ${settings.size} grid settings will replace your current layout.$note")
        .setPositiveButton("Import") { _, _ ->
            items.filter { it.isWidget }.forEach { host.deleteAppWidgetId(it.widgetId) }
            settings.forEach { (k, v) -> looks[k] = v }
            looks["subgrid"] = if (layout.subgrid) "on" else "off"
            val pages = (layout.items.filter { !isDock(it.page) }.maxOfOrNull { it.page } ?: 0) + 1
            if (pages > looks.int("pages")) looks["pages"] = pages.coerceAtMost(9).toString()
            store.save(layout.items, if (layout.subgrid) 2 else 1)
            layout.items.flatMap { listOf(it) + it.children }.filter { it.isShortcut }.map { it.pkg to it.user }.distinct()
                .forEach { (p, u) -> runCatching { launcherApps.pinShortcuts(p, layout.items.flatMap { listOf(it) + it.children }
                    .filter { it.isShortcut && it.pkg == p && it.user == u }.map { it.shortcutId }, userOf(u)) } }
            recreate()
        }.setNegativeButton("Cancel", null).show()
}

private fun MainActivity.permissionsMenu() {
    val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
    val badges = ComponentName(this, BadgeService::class.java).flattenToString() in listeners
    val actions = ActionService.instance != null
    val home = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
    fun state(on: Boolean) = if (on) "on" else "off"
    val rows = arrayOf(
        "Notification access: ${state(badges)}\n   Badges and notifications in the long-press menu",
        "Accessibility service: ${state(actions)}\n   Screen lock, recent apps, power menu",
        "Default launcher: ${if (home) "yes" else "no"}\n   Needed for app shortcuts",
    )
    dialog().setTitle("Permissions").setItems(rows) { _, i ->
        startActivity(Intent(when (i) {
            0 -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            1 -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_HOME_SETTINGS
        }))
    }.show()
}
