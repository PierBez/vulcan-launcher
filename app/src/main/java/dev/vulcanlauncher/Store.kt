package dev.vulcanlauncher

import android.content.Context
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
    o.optJSONArray("children")?.let { a -> List(a.length()) { itemFromJson(a.getJSONObject(it)) } } ?: emptyList(),
    o.optString("label"), o.optString("icon"), o.optString("swipe"))

fun itemsToJson(items: List<Item>) = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
fun itemsFromJson(s: String): MutableList<Item> = JSONArray(s).let { a -> MutableList(a.length()) { itemFromJson(a.getJSONObject(it)) } }

/**
 * Everything is stored as strings in three SharedPreferences files:
 * `layout` (home items), `looks` (settings) and `data` (hidden apps, drawer groups, launch counts, custom app edits).
 */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)
    val data = context.getSharedPreferences("data", Context.MODE_PRIVATE)!!

    /** Nothing saved yet: first run, or after a reset. */
    val isNew get() = !prefs.contains("items")
    fun load(): MutableList<Item> = itemsFromJson(prefs.getString("items", "[]")!!)
    fun save(items: List<Item>) = prefs.edit().putString("items", itemsToJson(items)).apply()

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
        for (name in FILES) {
            val o = root.optJSONObject(name) ?: JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply {
                o.keys().forEach { putString(it, o.getString(it)) }
            }.commit()
        }
    }

    fun reset() = FILES.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }

    companion object { val FILES = listOf("layout", "looks", "data") }
}
