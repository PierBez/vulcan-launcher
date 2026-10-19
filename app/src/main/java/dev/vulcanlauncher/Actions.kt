package dev.vulcanlauncher

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Launcher actions, used by gestures, the home button, icon swipe actions and action shortcuts.
 * Parameterised forms: "page:N" (0-based page) and "app:pkg/cls/user".
 */
val ACTIONS = listOf("none", "drawer", "now", "notifications", "quicksettings", "togglebar", "recents", "defaultpage", "previews",
    "toggledock", "search", "voice", "assistant", "lock", "power", "settings", "widgets", "wallpaper")
val ACTION_NAMES = listOf("Nothing", "App drawer", "Now page", "Expand notifications", "Expand quick settings", "Toggle notification bar",
    "Recent apps", "Default page", "Desktop previews", "Toggle dock", "Search", "Voice search", "Assistant", "Screen lock",
    "Power menu", "Launcher settings", "Add widget", "Wallpaper")

/** Display name for an action code; [appLabel] resolves "app:" codes. */
fun actionName(code: String, appLabel: (String) -> String? = { null }): String = when {
    code.startsWith("page:") -> "Jump to page ${(code.removePrefix("page:").toIntOrNull() ?: 0) + 1}"
    code.startsWith("app:") -> "Open " + (appLabel(code.removePrefix("app:")) ?: "app")
    else -> ACTION_NAMES.getOrElse(ACTIONS.indexOf(code)) { "Nothing" }
}

val GESTURE_KEYS = listOf("g_swipeUp", "g_swipeDown", "g_swipeUp2", "g_swipeDown2", "g_swipeLeft", "g_swipeRight", "g_doubleTap",
    "g_doubleTapUp", "g_doubleTapDown", "g_pinchIn", "g_pinchOut", "g_rotateCw", "g_rotateCcw")
val GESTURE_NAMES = listOf("Swipe up", "Swipe down", "Two finger swipe up", "Two finger swipe down", "Swipe left (last page)",
    "Swipe right (first page)", "Double tap", "Double tap + swipe up", "Double tap + swipe down", "Pinch in", "Pinch out",
    "Two finger rotation (clockwise)", "Two finger rotation (counterclockwise)")
val GESTURE_DEFAULTS = listOf("drawer", "notifications", "none", "quicksettings", "none", "now", "none", "none", "none",
    "previews", "none", "none", "none")

/**
 * Classifies a finished two-finger gesture from the start and end positions of both fingers,
 * returning a gesture name without the "g_" prefix, or null when nothing clear happened.
 * Screen coordinates (y grows downward), so a positive angle change is clockwise.
 */
fun twoFingerGesture(
    ax0: Float, ay0: Float, bx0: Float, by0: Float,
    ax1: Float, ay1: Float, bx1: Float, by1: Float, minMove: Float,
): String? {
    val d0 = hypot(bx0 - ax0, by0 - ay0)
    val d1 = hypot(bx1 - ax1, by1 - ay1)
    var turn = (atan2(by1 - ay1, bx1 - ax1) - atan2(by0 - ay0, bx0 - ax0)) * 180 / PI.toFloat()
    if (turn > 180) turn -= 360
    if (turn < -180) turn += 360
    val dy = (ay1 + by1 - ay0 - by0) / 2
    return when {
        abs(turn) > 35 -> if (turn > 0) "rotateCw" else "rotateCcw"
        d1 < d0 * 0.7f -> "pinchIn"
        d1 > d0 * 1.4f -> "pinchOut"
        dy < -minMove -> "swipeUp2"
        dy > minMove -> "swipeDown2"
        else -> null
    }
}

val NIGHT_MODES = listOf("off", "on", "system", "schedule", "sun")
val NIGHT_MODE_NAMES = listOf("Off", "Always on", "Follow system", "Schedule", "Sunset to sunrise (location)")

/**
 * Local sunrise and sunset in hours (e.g. 6.5 = 06:30) for a day of the year, computed offline with the NOAA
 * approximation. Null during polar day or night.
 */
fun sunTimes(lat: Double, lon: Double, dayOfYear: Int, utcOffsetHours: Double): Pair<Double, Double>? {
    val g = 2 * Math.PI / 365 * (dayOfYear - 1 + 0.5)
    val eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g))
    val decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) +
        0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g)
    val la = Math.toRadians(lat)
    val cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl)
    if (cosHa < -1 || cosHa > 1) return null
    val ha = Math.toDegrees(Math.acos(cosHa))
    fun local(min: Double) = ((min / 60 + utcOffsetHours) % 24 + 24) % 24
    return local(720 - 4 * (lon + ha) - eq) to local(720 - 4 * (lon - ha) - eq)
}

/** Night between sunset and sunrise; [hour] is fractional local time. */
fun isNightSun(hour: Double, sun: Pair<Double, Double>) = hour < sun.first || hour >= sun.second

val SEARCH_ENGINES = listOf("browser", "google", "duckduckgo", "bing", "startpage", "ecosia", "brave")
val SEARCH_ENGINE_NAMES = listOf("Default web search app", "Google", "DuckDuckGo", "Bing", "Startpage", "Ecosia", "Brave Search")

/** URL for a web search, or null for "hand the query to the default web search app". */
fun searchUrl(engine: String, q: String): String? {
    val e = java.net.URLEncoder.encode(q, "UTF-8")
    return when (engine) {
        "google" -> "https://www.google.com/search?q=$e"
        "duckduckgo" -> "https://duckduckgo.com/?q=$e"
        "bing" -> "https://www.bing.com/search?q=$e"
        "startpage" -> "https://www.startpage.com/do/search?q=$e"
        "ecosia" -> "https://www.ecosia.org/search?q=$e"
        "brave" -> "https://search.brave.com/search?q=$e"
        else -> null
    }
}

/** Is it night? A schedule from [start] to [end] (hours) may wrap past midnight. */
fun isNight(mode: String, hour: Int, start: Int, end: Int, systemNight: Boolean) = when (mode) {
    "on" -> true
    "system" -> systemNight
    "schedule" -> if (start <= end) hour in start until end else hour >= start || hour < end
    else -> false
}

val SORTS = listOf("az", "za", "installed", "used")
val SORT_NAMES = listOf("Title (A–Z)", "Title (Z–A)", "Newest first", "Most used")

/** One drawer entry's sort facts. */
class SortKey(val label: String, val installed: Long, val launches: Int)

fun drawerComparator(sort: String): Comparator<SortKey> = when (sort) {
    "za" -> compareByDescending { it.label.lowercase() }
    "installed" -> compareByDescending<SortKey> { it.installed }.thenBy { it.label.lowercase() }
    "used" -> compareByDescending<SortKey> { it.launches }.thenBy { it.label.lowercase() }
    else -> compareBy { it.label.lowercase() }
}
