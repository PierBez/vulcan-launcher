package dev.vulcanlauncher

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** First dock page. Dock page n is stored as page `-1 - n`. */
const val DOCK = -1

fun isDock(page: Int) = page < 0
fun dockIndex(page: Int) = -1 - page

/**
 * One thing placed on a home page, the dock or inside a folder:
 * an app (pkg/cls/user), a widget (widgetId), a pinned shortcut (pkg + shortcutId),
 * a launcher action (action), an activity shortcut (pkg/cls + action "activity") or a folder (children).
 */
data class Item(
    val page: Int,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val pkg: String = "",
    val cls: String = "",
    val widgetId: Int = -1,
    val user: Long = 0,
    val shortcutId: String = "",
    val action: String = "",
    val folder: Boolean = false,
    val children: List<Item> = emptyList(),
    val label: String = "",
    val icon: String = "",
    val swipe: String = "",
) {
    val isWidget get() = widgetId != -1
    val isShortcut get() = shortcutId.isNotEmpty()
    val isAction get() = action.isNotEmpty() && action != "activity"
    /** A plain app icon: can be merged into folders. */
    val isApp get() = pkg.isNotEmpty() && !isShortcut && !isWidget && !folder && action.isEmpty()
    val appKey get() = appKey(pkg, cls, user)
}

fun appKey(pkg: String, cls: String, user: Long) = "$pkg/$cls/$user"

fun Item.toJson(): JSONObject = JSONObject().put("page", page).put("x", x).put("y", y).put("w", w).put("h", h)
    .put("pkg", pkg).put("cls", cls).put("widgetId", widgetId).put("user", user).put("shortcutId", shortcutId)
    .put("action", action).put("folder", folder).put("label", label).put("icon", icon).put("swipe", swipe)
    .put("children", JSONArray().apply { children.forEach { put(it.toJson()) } })

fun itemFromJson(o: JSONObject): Item = Item(
    o.getInt("page"), o.getInt("x"), o.getInt("y"), o.optInt("w", 1), o.optInt("h", 1),
    o.optString("pkg"), o.optString("cls"), o.optInt("widgetId", -1), o.optLong("user"), o.optString("shortcutId"),
    o.optString("action"), o.optBoolean("folder"),
    o.optJSONArray("children")?.let(::itemList) ?: emptyList(),
    o.optString("label"), o.optString("icon"), o.optString("swipe"))

fun itemsToJson(items: List<Item>) = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
/** The items of [s]; an item that can't be read is dropped, not the whole layout. Throws when [s] isn't a JSON array. */
fun itemsFromJson(s: String): MutableList<Item> = itemList(JSONArray(s))

private fun itemList(a: JSONArray) = (0 until a.length()).mapNotNull { runCatching { itemFromJson(a.getJSONObject(it)) }.getOrNull() }.toMutableList()

/** [items] without the apps and shortcuts of an uninstalled package. A folder left with one item turns back into it. */
fun removePackage(items: List<Item>, pkg: String, user: Long) = removeApps(items) { it.pkg == pkg && it.user == user }

/** [items] without the apps, shortcuts and activities [gone] picks (widgets and folders are never passed to it). */
fun removeApps(items: List<Item>, gone: (Item) -> Boolean): List<Item> {
    fun g(i: Item) = i.pkg.isNotEmpty() && !i.isWidget && !i.folder && gone(i)
    return items.filterNot(::g).mapNotNull { f ->
        val rest = f.children.filterNot(::g)
        when {
            rest.size == f.children.size -> f
            rest.isEmpty() -> null
            rest.size == 1 -> rest[0].copy(page = f.page, x = f.x, y = f.y, w = f.w, h = f.h)
            else -> f.copy(children = rest)
        }
    }
}

/**
 * Layout format. 2 stores "sub", the subgrid factor its home positions are in, next to the items; 1 (no "version")
 * left it to the subgrid setting in another file, so a crash between the two writes could halve or double every position.
 */
const val LAYOUT_VERSION = 2

/** Home positions saved in subgrid factor [saved] (null: a version 1 layout, already in [current]), converted to [current]. */
fun layoutIn(items: List<Item>, saved: Int?, current: Int): List<Item> = when {
    saved == null || saved == current -> items
    current > saved -> scaleItems(items, current / saved)
    else -> scaleItems(items, -(saved / current))
}

/**
 * Everything is stored as strings in three SharedPreferences files:
 * `layout` (home items), `looks` (settings) and `data` (hidden apps, drawer groups, launch counts, custom app edits).
 */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
    val data = context.getSharedPreferences("data", Context.MODE_PRIVATE)!!

    /** Nothing saved yet: first run, or after a reset. */
    val isNew get() = !prefs.contains("items")
    /** Daily automatic backups, newest first. Three generations, so a bad day can't overwrite the last good copy. */
    val autoBackups get() = listOf("auto-backup.json", "auto-backup-2.json", "auto-backup-3.json").map { File(context.filesDir, it) }

    /**
     * The saved layout, in subgrid factor [sub]. If it can't be read at all (a bad restore or a hand edit), the raw text
     * is kept in `files/layout-unreadable.json` and the newest readable automatic backup is used, so the launcher never
     * crash-loops.
     */
    fun load(sub: Int): MutableList<Item> {
        val raw = prefs.getString("items", "[]")!!
        runCatching { return layoutIn(itemsFromJson(raw), prefs.getString("sub", null)?.toIntOrNull(), sub).toMutableList() }
        runCatching { File(context.filesDir, "layout-unreadable.json").writeText(raw) }
        for (f in autoBackups) runCatching {
            val b = JSONObject(f.readText())
            val layout = b.getJSONObject("layout")
            // A backup from before version 2 is in the subgrid factor of the settings saved with it.
            val saved = layout.optString("sub").toIntOrNull() ?: if (b.optJSONObject("looks")?.optString("subgrid") == "on") 2 else 1
            return layoutIn(itemsFromJson(layout.getString("items")), saved, sub).toMutableList()
        }
        return mutableListOf()
    }

    /** Saves [items], whose home positions are in subgrid factor [sub]. Strings only, so backups round-trip them. */
    fun save(items: List<Item>, sub: Int) = prefs.edit().putString("items", itemsToJson(items))
        .putString("version", LAYOUT_VERSION.toString()).putString("sub", sub.toString()).apply()

    /** Writes today's automatic backup, keeping the two before it. */
    fun rotateAutoBackup() {
        val (a, b, c) = autoBackups
        if (b.exists()) b.renameTo(c)
        if (a.exists()) a.renameTo(b)
        a.writeText(export())
    }

    fun strings(key: String): MutableSet<String> = JSONArray(data.getString(key, "[]")).let { a -> MutableList(a.length()) { a.getString(it) }.toMutableSet() }
    fun putStrings(key: String, set: Collection<String>) = data.edit().putString(key, JSONArray(set.toList()).toString()).apply()
    fun obj(key: String) = JSONObject(data.getString(key, "{}")!!)
    fun putObj(key: String, o: JSONObject) = data.edit().putString(key, o.toString()).apply()

    /** Whole configuration as one JSON document, for backup files. */
    fun export(): String = JSONObject().apply {
        for (name in FILES) put(name, JSONObject(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { it.value.toString() }))
    }.put("format", "vulcan-backup-1").toString(2)

    /** Replaces the configuration with a backup made by [export]. Throws on a file that isn't one. */
    fun import(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "vulcan-backup-1") { "Not a Vulcan backup" }
        // Read the whole file before writing anything, so a bad one leaves the current setup untouched.
        val files = FILES.associateWith { name ->
            val o = root.optJSONObject(name) ?: JSONObject()
            o.keys().asSequence().associateWith { o.getString(it) }
        }
        files["layout"]?.get("items")?.let { JSONArray(it) }
        // ponytail: three commits, not one transaction; an I/O failure between them can mix old and new files. The layout
        // carries its own subgrid factor, so its positions still read right; only grid sizes could be the old ones (fitItems copes)
        for ((name, values) in files) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply {
            values.forEach { (k, v) -> putString(k, v) }
        }.commit()
    }

    fun reset() = FILES.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }

    companion object { val FILES = listOf("layout", "looks", "data") }
}
