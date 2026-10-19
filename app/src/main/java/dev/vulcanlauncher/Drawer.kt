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

/** Whether a released drawer pull ends open: flung up faster than [fling] (px/s, upward positive), or past halfway unless flung down. */
fun drawerOpens(progress: Float, vy: Float, fling: Float) = vy > fling || vy > -fling && progress > 0.5f

/** Material "emphasized" easing: quick start, long soft landing. */
val EASE = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)

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
        // Glass: quieter labels (smaller, slightly see-through) so the icons carry the screen.
        val quiet = styleName == "glass"
        IconStyle(it.sizePx, it.labels || styleName == "list", if (quiet) it.labelSize - 1 else it.labelSize,
            if (quiet) a.panelText and 0xFFFFFF or 0xD9000000.toInt() else a.panelText, false, it.iconShadow,
            horizontal = styleName == "list", font = it.font, lines = it.lines)
    }
    private var entries = listOf<Entry>()
    private var tab = ""
    private var showHidden = false
    private val adapter = Adapter()
    private val tabs = LinearLayout(a)
    private val tabScroll = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
    private val grid = GridView(a).apply {
        // Glass: no scrollbar; rows fade out at the edges instead of being cut by the status bar or search bar.
        if (styleName == "glass") { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength((32 * a.dp).toInt()) }
    }
    /** Shown when a search matches no app or contact. */
    private val empty = a.text("", 15f, a.m3.onSurfaceVariant).apply { gravity = Gravity.CENTER; visibility = View.GONE }
    /** Glass style: a tint over the blurred home screen and a frosted search bar with a hairline edge. */
    private val glass = styleName == "glass"
    private val pager = Workspace(a)
    private val search: EditText = EditText(a).apply {
        hint = "Search apps"
        textSize = if (styleName == "glass") 15f else 16f
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

    private lateinit var menuButton: View

    val root = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        // Glass: a tint over the blurred home screen, lighter where the system really blurs what's behind.
        background = if (glass) android.graphics.drawable.ColorDrawable(a.glassTint(a.glassBlur))
            else a.panelBg(a.looks.int("drawerAlpha"), 0f, a.m3.surface)
        visibility = View.GONE
        setOnDragListener(a.passDrag)
        isClickable = true
        val pad = (16 * a.dp).toInt()
        // M3 search bar: a pill with a leading search icon and a trailing menu button. Glass puts it at the bottom,
        // under the thumb; it rides up with the keyboard.
        val bar = LinearLayout(a).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = if (glass) a.rounded(if (a.m3.dark) 0x1FFFFFFF else 0x8CFFFFFF.toInt(), 28 * a.dp, if (a.m3.dark) 0x1AFFFFFF else 0x14000000, a.dp / 2)
                else a.rounded(a.m3.surfaceHighest, 28 * a.dp)
            setPadding(pad, 0, (4 * a.dp).toInt(), 0)
            addView(android.widget.ImageView(a).apply { setImageDrawable(a.symbol("search")) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = pad })
            addView(search, LinearLayout.LayoutParams(0, -2, 1f))
            menuButton = a.iconButton("more") { menu() }
            addView(menuButton)
            setOnClickListener {
                search.requestFocus()
                a.getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
            }
        }
        val barParams = LinearLayout.LayoutParams(-1, ((if (glass) 48 else 56) * a.dp).toInt()).apply { setMargins(pad, pad / 2, pad, pad / 2) }
        if (!glass) addView(bar, barParams)
        tabs.setPadding(pad - (4 * a.dp).toInt(), if (glass) pad / 2 else 0, pad, (8 * a.dp).toInt())
        addView(tabScroll, LinearLayout.LayoutParams(-1, -2))
        val content = FrameLayout(a)
        if (styleName == "pages") content.addView(pager) else content.addView(grid)
        content.addView(empty, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(pad * 2, 0, pad * 2, 0) })
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        if (glass) addView(bar, barParams)
    }

    val visible get() = root.visibility == View.VISIBLE

    init {
        grid.apply {
            numColumns = cols
            verticalSpacing = ((if (glass) 28 else 16) * a.dp).toInt()
            val side = ((if (glass) 12 else 8) * a.dp).toInt()
            setPadding(side, (if (glass) 16 * a.dp else 8 * a.dp).toInt(), side, (8 * a.dp).toInt())
            clipToPadding = false
            adapter = this@Drawer.adapter
            isFastScrollEnabled = a.looks.on("fastScroll")
            setOnItemClickListener { _, v, pos, _ -> entries.getOrNull(pos)?.let { open(it, v) } }
            setOnItemLongClickListener { _, v, pos, _ -> entries.getOrNull(pos)?.let { longPress(it, v) }; true }
            var downY = 0f
            var closing = false
            var velocity: android.view.VelocityTracker? = null
            setOnTouchListener { _, ev ->
                // Pull down from the top of the list: the drawer follows the finger and closes on release (or searches).
                velocity?.addMovement(ev)
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.y; closing = false
                        velocity?.recycle(); velocity = android.view.VelocityTracker.obtain().apply { addMovement(ev) }
                    }
                    MotionEvent.ACTION_MOVE -> if (!closing && ev.y - downY > 24 * a.dp && !canScrollVertically(-1)) {
                        if (a.looks.on("pullToSearch")) {
                            if (ev.y - downY > 96 * a.dp) {
                                downY = Float.MAX_VALUE
                                search.requestFocus()
                                a.getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
                            }
                        } else { closing = true; downY = ev.y; stop() }
                    }
                }
                if (!closing) return@setOnTouchListener false
                if (ev.actionMasked == MotionEvent.ACTION_MOVE) setProgress(1 - (ev.y - downY) / travel())
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    velocity?.computeCurrentVelocity(1000)
                    release(-(velocity?.yVelocity ?: 0f))
                    closing = false
                }
                true
            }
        }
        pager.onGesture = { if (it == "swipeDown") hide() }
    }

    /** How open the drawer is: 0 closed, 1 open. Follows the finger while it's pulled, then animates the rest. */
    private var progress = 0f
    private var anim: android.animation.ValueAnimator? = null
    private var keyboardOnOpen = false

    /** Finger travel that fully opens or closes the drawer. */
    private fun travel() = maxOf(1f, a.root.height * 0.35f)

    /** The drawer at [p]: it fades in while rising [travel]/4, as home blurs and recedes behind it. Linear, so it stays under the finger. */
    private fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        root.visibility = View.VISIBLE
        root.alpha = minOf(1f, progress * 2)
        root.translationY = (1 - progress) * travel() / 4
        if (glass) root.semBlur((40 * a.dp * progress).toInt(), a.glassTint(true), 0f)
        if (a.overlay == null) a.setDepth(progress)
    }

    /** Animates to open or closed from wherever the drawer is; [speed] (px/s, upward positive) shortens a flung finish. */
    private fun settle(open: Boolean, speed: Float = 0f) {
        stop()
        val to = if (open) 1f else 0f
        val left = kotlin.math.abs(to - progress)
        // ponytail: duration from remaining distance and fling speed, not a real spring; good enough at 60–120 Hz.
        val ms = if (kotlin.math.abs(speed) > 0f) (left * travel() / kotlin.math.abs(speed) * 1000 * 2).toLong().coerceIn(140, 320)
            else ((if (open) 340 else 220) * maxOf(0.4f, left)).toLong()
        anim = android.animation.ValueAnimator.ofFloat(progress, to).apply {
            duration = ms
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { setProgress(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(x: android.animation.Animator) { if (anim === x) { anim = null; if (!open) gone() else opened() } }
            })
            start()
        }
    }

    /** Stops a running open/close animation where it is (without its end action). */
    private fun stop() { val old = anim; anim = null; old?.cancel() }

    /** Clears search and resets position, ready to open. */
    private fun prepare() {
        if (visible) return
        search.text.clear()
        if (!a.looks.on("rememberPosition")) { tab = ""; grid.setSelection(0) }
        refresh()
    }

    private fun opened() {
        if (keyboardOnOpen || a.looks.on("showKeyboard")) {
            search.requestFocus()
            a.getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
        }
        keyboardOnOpen = false
    }

    private fun gone() {
        root.alpha = 1f
        root.visibility = View.GONE
        root.semBlur(0, 0, 0f)
        // A drag in progress still routes events to a GONE view by its bounds, so move it off-screen too.
        root.translationY = root.height.toFloat()
        progress = 0f
    }

    fun show(keyboard: Boolean) {
        prepare()
        keyboardOnOpen = keyboard
        settle(open = true)
    }

    /**
     * Home's swipe up, while the finger is still down: [up] px pulled so far. On release ([end]) the drawer opens
     * if flung up ([vy] px/s, upward positive) or pulled past halfway. Returns false for a pull that starts downward.
     */
    fun pull(up: Float, vy: Float, end: Boolean): Boolean {
        if (!end && !visible && up <= 0f) return false
        if (!visible) prepare()
        stop()
        if (end) {
            val open = release(vy)
            if (open) root.tick()
        } else setProgress(up / travel())
        return true
    }

    /** Ends a finger-driven pull: opens if flung up or past halfway, unless flung down. Returns whether it opens. */
    private fun release(vy: Float): Boolean {
        val open = drawerOpens(progress, vy, 600 * a.dp)
        settle(open, vy)
        return open
    }

    /** [animate] false for a drag leaving the drawer: it must be out of the way at once. */
    fun hide(animate: Boolean = true) {
        if (!visible) return
        a.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
        search.text.clear()
        search.clearFocus()
        showHidden = false
        if (animate) settle(open = false)
        else { stop(); if (a.overlay == null) a.setDepth(0f); gone() }
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
        // Glass: search results sit just above the bottom search bar, under the thumb.
        grid.isStackFromBottom = glass && q.isNotEmpty()
        empty.text = "No apps match “$q”"
        empty.visibility = if (q.isNotEmpty() && entries.none { it is Entry.AppE || it is Entry.ContactE }) View.VISIBLE else View.GONE
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
        // Short label so the query survives the one-line ellipsis; the globe says "web".
        is Entry.WebE -> IconView(a, "Search “${e.query}”",GlyphIcon(a.m3.primaryContainer, a.m3.onPrimaryContainer, symbol = "globe"), iconStyle, a.badgeStyle(), "")
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
        a.showMenu(a.screenRect(menuButton)) {
            fun row(sym: String, title: String, act: () -> Unit) = addView(a.listItem(a.symbol(sym), title, dense = true) { a.closeMenu(); act() })
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
        // Stacked from the bottom, GridView right-aligns the short row; padding to whole rows keeps results reading left to right.
        override fun getCount() = if (grid.isStackFromBottom) (entries.size + cols - 1) / cols * cols else entries.size
        override fun getItem(pos: Int) = entries.getOrNull(pos)
        override fun getItemId(pos: Int) = pos.toLong()
        override fun isEnabled(pos: Int) = pos < entries.size
        override fun getView(pos: Int, convert: View?, parent: ViewGroup): View = entries.getOrNull(pos)?.let(::entryView)?.apply {
            if (styleName == "list") setPadding((8 * a.dp).toInt(), 0, 0, 0)
        } ?: entryView(entries[0]).apply { visibility = View.INVISIBLE } // a filler as tall as a real cell, or GridView misplaces the row
        override fun getSections(): Array<String> = sections
        override fun getPositionForSection(s: Int) = entries.indexOfFirst { letter(it) == sections.getOrNull(s) }.coerceAtLeast(0)
        override fun getSectionForPosition(p: Int) = sections.indexOf(entries.getOrNull(p)?.let(::letter)).coerceAtLeast(0)
    }
}

