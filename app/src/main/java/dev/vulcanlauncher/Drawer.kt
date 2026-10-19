package dev.vulcanlauncher

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SectionIndexer
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** A drawer tab or drawer folder: [type] is "tab" or "folder"; [apps] are app keys. */
data class Group(val name: String, val type: String, val apps: List<String>)

fun groupsFromJson(s: String): List<Group> = JSONArray(s).let { a ->
    List(a.length()) {
        val o = a.getJSONObject(it)
        val apps = o.getJSONArray("apps")
        Group(o.getString("name"), o.getString("type"), List(apps.length()) { i -> apps.getString(i) })
    }
}

fun groupsToJson(groups: List<Group>) = JSONArray().apply {
    groups.forEach { put(JSONObject().put("name", it.name).put("type", it.type).put("apps", JSONArray(it.apps))) }
}.toString()

/**
 * App keys shown in a drawer tab, in [ordered] order. [tab] is "" (all apps), "work", or a tab group's name.
 * The main tab leaves out hidden apps, work apps when they have their own tab, and grouped apps unless [keepInAll].
 */
fun tabApps(ordered: List<String>, groups: List<Group>, tab: String, hidden: Set<String>, showHidden: Boolean,
            keepInAll: Boolean, work: Set<String>, workTab: Boolean): List<String> {
    val shown = ordered.filter { showHidden || it !in hidden }
    return when (tab) {
        "" -> {
            val grouped = if (keepInAll) emptySet() else groups.flatMap { it.apps }.toSet()
            shown.filter { it !in grouped && !(workTab && it in work) }
        }
        "work" -> shown.filter { it in work }
        else -> groups.firstOrNull { it.name == tab && it.type == "tab" }?.apps?.toSet()?.let { g -> shown.filter { it in g } } ?: emptyList()
    }
}

/** The app drawer overlay. Built once per activity; [refresh] rebuilds its contents. */
class Drawer(private val a: MainActivity) {
    private sealed class Entry {
        class AppE(val app: App) : Entry()
        class FolderE(val group: Group) : Entry()
        class WebE(val query: String) : Entry()
        class ContactE(val name: String, val photo: String?, val uri: android.net.Uri) : Entry()
    }

    private val styleName = a.looks.str("drawerStyle")
    private val cols = if (styleName == "list") 1 else a.looks.int("drawerCols")
    private val iconStyle = a.style("drawer").let {
        IconStyle(it.sizePx, it.labels || styleName == "list", it.labelSize, a.panelText, false, it.iconShadow,
            horizontal = styleName == "list", font = it.font, lines = it.lines)
    }
    private var entries = listOf<Entry>()
    private var tab = ""
    private var showHidden = false
    private val adapter = Adapter()
    private val tabs = LinearLayout(a)
    private val tabScroll = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
    private val grid = GridView(a)
    private val pager = Workspace(a)
    private val search: EditText = EditText(a).apply {
        hint = "Search apps"
        textSize = 16f
        setSingleLine()
        setTextColor(a.m3.onSurface)
        setHintTextColor(a.m3.onSurfaceVariant)
        background = null
        setPadding(0, 0, 0, 0)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        setOnEditorActionListener { _, _, _ -> entries.firstOrNull()?.let { open(it, null) }; true }
    }

    val root = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = a.panelBg(a.looks.int("drawerAlpha"), 0f, a.m3.surface)
        visibility = View.GONE
        setOnDragListener(a.passDrag)
        isClickable = true
        val pad = (16 * a.dp).toInt()
        // M3 search bar: a 56 dp pill with a leading search icon and a trailing menu button.
        addView(LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = a.rounded(a.m3.surfaceHighest, 28 * a.dp)
            setPadding(pad, 0, (4 * a.dp).toInt(), 0)
            addView(android.widget.ImageView(a).apply { setImageDrawable(a.symbol("search")) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = pad })
            addView(search, LinearLayout.LayoutParams(0, -2, 1f))
            addView(a.iconButton("more") { menu() })
            setOnClickListener {
                search.requestFocus()
                a.getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
            }
        }, LinearLayout.LayoutParams(-1, (56 * a.dp).toInt()).apply { setMargins(pad, pad / 2, pad, pad / 2) })
        tabs.setPadding(pad - (4 * a.dp).toInt(), 0, pad, (8 * a.dp).toInt())
        addView(tabScroll, LinearLayout.LayoutParams(-1, -2))
        val content = FrameLayout(a)
        if (styleName == "pages") content.addView(pager) else content.addView(grid)
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    val visible get() = root.visibility == View.VISIBLE

    init {
        grid.apply {
            numColumns = cols
            verticalSpacing = (16 * a.dp).toInt()
            setPadding((8 * a.dp).toInt(), (8 * a.dp).toInt(), (8 * a.dp).toInt(), (8 * a.dp).toInt())
            clipToPadding = false
            adapter = this@Drawer.adapter
            isFastScrollEnabled = a.looks.on("fastScroll")
            setOnItemClickListener { _, v, pos, _ -> open(entries[pos], v) }
            setOnItemLongClickListener { _, v, pos, _ -> longPress(entries[pos], v); true }
            var downY = 0f
            setOnTouchListener { _, ev ->
                // Pull down from the top of the list closes the drawer.
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) downY = ev.y
                if (ev.actionMasked == MotionEvent.ACTION_MOVE && ev.y - downY > 96 * a.dp && !canScrollVertically(-1)) {
                    if (a.looks.on("pullToSearch")) {
                        downY = Float.MAX_VALUE
                        search.requestFocus()
                        a.getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
                    } else hide()
                    true
                } else false
            }
        }
        pager.onGesture = { if (it == "swipeDown") hide() }
    }

    fun show(keyboard: Boolean) {
        search.text.clear()
        if (!a.looks.on("rememberPosition")) { tab = ""; grid.setSelection(0) }
        refresh()
        root.translationY = 0f
        root.visibility = View.VISIBLE
        a.blurHome(true)
        root.alpha = 0f
        root.translationY = 48 * a.dp
        root.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        if (keyboard || a.looks.on("showKeyboard")) {
            search.requestFocus()
            a.getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
        }
    }

    fun hide() {
        if (!visible) return
        a.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
        search.text.clear()
        search.clearFocus()
        showHidden = false
        root.animate().cancel()
        root.alpha = 1f
        root.visibility = View.GONE
        if (a.overlay == null) a.blurHome(false)
        // A drag in progress still routes events to a GONE view by its bounds, so move it off-screen too.
        root.translationY = root.height.toFloat()
    }

    fun groups() = groupsFromJson(a.store.data.getString("groups", "[]")!!)

    fun refresh() {
        val q = search.text.toString().trim()
        val hidden = a.store.strings("hidden")
        val launches = a.launches()
        val ordered = a.apps.values.sortedWith(compareBy(drawerComparator(a.looks.str("drawerSort"))) {
            SortKey(a.label(it), it.info.firstInstallTime, launches.optInt(it.key))
        })
        val groups = groups()
        val work = a.apps.values.filter { a.isWork(it) }.map { it.key }.toSet()
        val workTab = a.looks.on("workTab") && work.isNotEmpty()
        if (tab == "work" && !workTab || tab.isNotEmpty() && tab != "work" && groups.none { it.name == tab && it.type == "tab" }) tab = ""
        entries = if (q.isNotEmpty()) {
            ordered.filter { (showHidden || it.key !in hidden) && a.label(it).contains(q, ignoreCase = true) }.map { Entry.AppE(it) } +
                contacts(q) + if (a.looks.on("webSearch")) listOf(Entry.WebE(q)) else emptyList()
        } else {
            val keys = tabApps(ordered.map { it.key }, groups, tab, hidden, showHidden, a.looks.on("keepInAll"), work, workTab)
            val list = keys.mapNotNull { a.apps[it] }.map { Entry.AppE(it) }
            val folders = if (tab == "") groups.filter { it.type == "folder" }.map { Entry.FolderE(it) } else emptyList()
            val frequent = if (tab == "" && a.looks.on("frequent") && styleName == "grid")
                a.apps.values.filter { launches.optInt(it.key) > 0 && it.key !in hidden }.sortedByDescending { launches.optInt(it.key) }
                    .take(cols).map { Entry.AppE(it) } else emptyList()
            frequent + if (a.looks.on("foldersFirst")) folders + list else list + folders
        }
        buildTabs(groups, workTab, q.isEmpty())
        if (styleName == "pages") buildPages() else adapter.notifyDataSetChanged()
    }

    /** Up to four matching contacts, when contact search is on and the user granted access. */
    private fun contacts(q: String): List<Entry> {
        if (!a.looks.on("contactSearch") || !a.granted(android.Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Entry>()
        runCatching {
            a.contentResolver.query(android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_FILTER_URI, android.net.Uri.encode(q)),
                arrayOf("_id", "lookup", "display_name", "photo_thumb_uri"), null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < 4) out += Entry.ContactE(c.getString(2) ?: "", c.getString(3),
                    android.provider.ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)))
            }
        }
        return out
    }

    fun refreshBadges() { if (visible) grid.invalidateViews() }

    private fun buildTabs(groups: List<Group>, workTab: Boolean, show: Boolean) {
        tabs.removeAllViews()
        val names = listOf("" to "All apps") + (if (workTab) listOf("work" to "Work") else emptyList()) +
            groups.filter { it.type == "tab" }.map { it.name to it.name }
        tabScroll.visibility = if (show && names.size > 1) View.VISIBLE else View.GONE
        // M3 filter chips: the selected one filled with the secondary container and a check mark.
        for ((key, title) in names) tabs.addView(a.text(title, 14f, if (key == tab) a.m3.onSecondaryContainer else a.m3.onSurfaceVariant, medium = true).apply {
            val on = key == tab
            gravity = Gravity.CENTER_VERTICAL
            val p = (16 * a.dp).toInt()
            setPadding(if (on) p / 2 else p, 0, p, 0)
            compoundDrawablePadding = (8 * a.dp).toInt()
            if (on) setCompoundDrawablesRelative(a.symbol("check", a.m3.onSecondaryContainer, 18f).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }, null, null, null)
            background = a.pressable(if (on) a.rounded(a.m3.secondaryContainer, 8 * a.dp) else a.rounded(0, 8 * a.dp, a.m3.outlineVariant), 8 * a.dp)
            layoutParams = LinearLayout.LayoutParams(-2, (32 * a.dp).toInt()).apply { marginStart = (8 * a.dp).toInt() }
            setOnClickListener { tab = key; refresh() }
            if (key != "" && key != "work") setOnLongClickListener { a.editGroup(groups.first { it.name == key }); true }
        })
    }

    private fun buildPages() {
        pager.removeAllViews()
        val rows = a.looks.int("drawerRows")
        entries.chunked(cols * rows).ifEmpty { listOf(emptyList()) }.forEach { chunk ->
            pager.addView(CellLayout(a, cols, rows, 0).apply {
                chunk.forEachIndexed { i, e ->
                    addView(entryView(e).apply {
                        layoutParams = CellLayout.CellParams(i % cols, i / cols, 1, 1)
                        setOnClickListener { v -> open(e, v) }
                        setOnLongClickListener { v -> longPress(e, v); true }
                    })
                }
            })
        }
        pager.snapTo(0, animate = false)
    }

    private fun entryView(e: Entry): IconView = when (e) {
        is Entry.AppE -> IconView(a, a.label(e.app), a.icon(e.app), iconStyle, a.badgeStyle(), e.app.pkg).also {
            if (a.looks.str("badges") != "off") a.badge(it.image, e.app.pkg)
        }
        is Entry.FolderE -> IconView(a, e.group.name, a.folderIcon(folderItems(e.group)), iconStyle, a.badgeStyle(), "")
        is Entry.WebE -> IconView(a, "Search the web for “${e.query}”", GlyphIcon(a.m3.primaryContainer, a.m3.onPrimaryContainer, symbol = "globe"), iconStyle, a.badgeStyle(), "")
        is Entry.ContactE -> IconView(a, e.name, e.photo?.let { p ->
            runCatching { a.contentResolver.openInputStream(android.net.Uri.parse(p))!!.use { android.graphics.drawable.BitmapDrawable(a.resources, android.graphics.BitmapFactory.decodeStream(it)) } }.getOrNull()
        } ?: GlyphIcon(a.m3.primaryContainer, a.m3.onPrimaryContainer, text = e.name.take(1).uppercase()), iconStyle, a.badgeStyle(), "")
    }

    private fun folderItems(g: Group) = g.apps.mapNotNull { a.apps[it] }.map { Item(0, 0, 0, pkg = it.pkg, cls = it.cls, user = it.serial) }

    private fun open(e: Entry, v: View?) {
        when (e) {
            is Entry.AppE -> a.launch(e.app, v)
            is Entry.FolderE -> {
                val apps = e.group.apps.mapNotNull { a.apps[it] }
                a.openFolderWindow(e.group.name, folderItems(e.group), null) { i -> apps[i] }
            }
            is Entry.WebE -> a.webSearch(e.query)
            is Entry.ContactE -> runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, e.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun longPress(e: Entry, v: View) {
        when (e) {
            is Entry.AppE -> a.startDrag(v, e.app)
            is Entry.FolderE -> a.editGroup(e.group)
            is Entry.WebE, is Entry.ContactE -> {}
        }
    }

    private fun menu() {
        a.showSheet(null) {
            fun row(sym: String, title: String, act: () -> Unit) = addView(a.listItem(a.symbol(sym), title) { a.closeOverlay(); act() })
            row(if (showHidden) "hide" else "show", if (showHidden) "Don't show hidden apps" else "Show hidden apps") { showHidden = !showHidden; refresh() }
            row("sort", "Sort apps") {
                a.dialog().setTitle("Sort apps").setSingleChoiceItems(SORT_NAMES.toTypedArray(), SORTS.indexOf(a.looks.str("drawerSort"))) { d, i ->
                    a.looks["drawerSort"] = SORTS[i]; d.dismiss(); refresh()
                }.show()
            }
            row("tabs", "Drawer groups") { a.groupsMenu() }
            row("hide", "Hidden apps") { a.hideAppsMenu() }
            row("settings", "Launcher settings") { hide(); a.showSettings() }
        }
    }

    private inner class Adapter : BaseAdapter(), SectionIndexer {
        private fun letter(e: Entry) = when (e) {
            is Entry.AppE -> a.label(e.app).firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"
            is Entry.FolderE -> "▤"
            is Entry.WebE -> "⌕"
            is Entry.ContactE -> "☺"
        }
        private var sections = arrayOf<String>()

        override fun notifyDataSetChanged() {
            sections = entries.map(::letter).distinct().toTypedArray()
            super.notifyDataSetChanged()
        }
        override fun getCount() = entries.size
        override fun getItem(pos: Int) = entries[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View = entryView(entries[pos]).apply {
            if (styleName == "list") setPadding((8 * a.dp).toInt(), 0, 0, 0)
        }
        override fun getSections(): Array<String> = sections
        override fun getPositionForSection(s: Int) = entries.indexOfFirst { letter(it) == sections.getOrNull(s) }.coerceAtLeast(0)
        override fun getSectionForPosition(p: Int) = sections.indexOf(entries.getOrNull(p)?.let(::letter)).coerceAtLeast(0)
    }
}

