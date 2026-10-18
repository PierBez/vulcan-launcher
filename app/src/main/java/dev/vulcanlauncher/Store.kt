package dev.vulcanlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val DOCK = -1

/** One thing placed on a home page or the dock: an app (pkg/cls) or a widget (widgetId). */
data class Item(
    val page: Int,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val pkg: String = "",
    val cls: String = "",
    val widgetId: Int = -1,
) {
    val isWidget get() = widgetId != -1
}

/** Home layout persisted as one JSON array in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)

    fun load(): MutableList<Item> {
        val arr = JSONArray(prefs.getString("items", "[]"))
        return MutableList(arr.length()) {
            val o = arr.getJSONObject(it)
            Item(o.getInt("page"), o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"),
                o.getString("pkg"), o.getString("cls"), o.getInt("widgetId"))
        }
    }

    fun save(items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("page", it.page).put("x", it.x).put("y", it.y).put("w", it.w)
                .put("h", it.h).put("pkg", it.pkg).put("cls", it.cls).put("widgetId", it.widgetId))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
