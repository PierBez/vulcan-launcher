package dev.vulcanlauncher

import android.content.Context

/**
 * One setting: stored value strings in [values], shown to the user as [names].
 * Gesture settings have [action] set and are picked with the action picker instead.
 */
class Choice(
    val group: String, val key: String, val title: String, val default: String,
    val values: List<String>, val names: List<String> = values, val action: Boolean = false,
)

private fun range(from: Int, to: Int, step: Int = 1) = (from..to step step).map { it.toString() }
private fun onOff(group: String, key: String, title: String, default: Boolean) =
    Choice(group, key, title, if (default) "on" else "off", listOf("on", "off"), listOf("On", "Off"))
private fun sizes(group: String, key: String, title: String, default: String = "48") =
    Choice(group, key, title, default, range(32, 72, 4), range(32, 72, 4).map { "$it dp" })
private fun percent(group: String, key: String, title: String, default: String) =
    Choice(group, key, title, default, range(0, 100, 10), range(0, 100, 10).map { "$it%" })

val COLOR_VALUES = listOf("#FFFFFF", "#000000", "#9E9E9E", "#212121", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00897B", "#1E88E5", "#3949AB", "#8E24AA", "#D81B60")
val COLOR_NAMES = listOf("White", "Black", "Grey", "Charcoal", "Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Indigo", "Purple", "Pink")
/** With [themed], "system" (the wallpaper-based Material colour) is offered first. */
private fun color(group: String, key: String, title: String, default: String, themed: Boolean = false) =
    if (themed) Choice(group, key, title, default, listOf("system") + COLOR_VALUES, listOf("Wallpaper colors") + COLOR_NAMES)
    else Choice(group, key, title, default, COLOR_VALUES, COLOR_NAMES)

const val HOME = "Home screen"
const val DOCK_GROUP = "Dock"
const val DRAWER = "App drawer"
const val FOLDERS = "Folders"
const val LOOK = "Look & feel"
const val NIGHT = "Night mode"
const val BADGES = "Notification badges"
const val GESTURES = "Gestures & inputs"
const val NOW = "Now page"

val LOOKS = listOf(
    Choice(HOME, "cols", "Columns", "4", range(3, 12)),
    Choice(HOME, "rows", "Rows", "6", range(3, 12)),
    Choice(HOME, "pages", "Pages", "3", range(1, 9)),
    Choice(HOME, "defaultPage", "Default page", "0", range(0, 8), range(1, 9)),
    onOff(HOME, "subgrid", "Subgrid positioning (half-cell steps)", false),
    sizes(HOME, "iconSize", "Icon size", "56"),
    onOff(HOME, "labels", "Labels", true),
    Choice(HOME, "labelSize", "Label size", "12", range(9, 18)),
    color(HOME, "labelColor", "Label color", "#FFFFFF"),
    Choice(HOME, "labelFont", "Label font", "system", listOf("system", "sans-serif", "sans-serif-medium", "sans-serif-light", "sans-serif-condensed", "serif", "monospace"),
        listOf("System font", "Roboto", "Roboto medium", "Roboto light", "Roboto condensed", "Serif", "Monospace")),
    Choice(HOME, "labelLines", "Label lines", "1", listOf("1", "2"), listOf("Single line", "Two lines")),
    onOff(HOME, "labelShadow", "Label shadow", true),
    Choice(HOME, "scrollEffect", "Transition effect", "simple", EFFECTS, EFFECT_NAMES),
    Choice(HOME, "indicator", "Page indicator", "dots", listOf("none", "dots", "line"), listOf("None", "Dots", "Line")),
    onOff(HOME, "infiniteScroll", "Infinite scroll", false),
    Choice(HOME, "wallpaperScroll", "Wallpaper scrolling", "on", listOf("on", "off", "reverse"), listOf("On", "Off", "Reverse")),
    Choice(HOME, "searchBar", "Search bar", "off", listOf("off", "top", "bottom", "dockAbove", "dockBelow"),
        listOf("Off", "Top of screen", "Bottom of screen", "Dock (above icons)", "Dock (below icons)")),
    Choice(HOME, "searchStyle", "Search bar shape", "pill", listOf("rect", "rounded", "pill"), listOf("Rectangle", "Rounded", "Pill")),
    color(HOME, "searchColor", "Search bar color", "system", themed = true),
    onOff(HOME, "lockLayout", "Lock layout", false),
    onOff(HOME, "widgetOverlap", "Widget overlap when placing", false),
    onOff(HOME, "autoAdd", "Add icons of new apps to home screen", false),
    onOff(HOME, "hideClock", "Hide the status bar clock on the home screen (needs a one-time adb grant)", false),
    Choice(HOME, "statusBar", "Notification bar", "show", listOf("show", "hide"), listOf("Show", "Hide")),
    onOff(HOME, "darkStatusIcons", "Dark notification bar icons", false),
    Choice(HOME, "orientation", "Screen orientation", "auto", listOf("auto", "portrait", "landscape"), listOf("Follow system", "Portrait", "Landscape")),

    onOff(DOCK_GROUP, "dock", "Dock", true),
    Choice(DOCK_GROUP, "dockCols", "Dock icons", "4", range(3, 9)),
    Choice(DOCK_GROUP, "dockPages", "Dock pages", "1", range(1, 3)),
    sizes(DOCK_GROUP, "dockIconSize", "Icon size", "56"),
    onOff(DOCK_GROUP, "dockLabels", "Labels", false),
    Choice(DOCK_GROUP, "dockBg", "Background", "glass", listOf("glass", "none", "rect", "rounded", "card"), listOf("Glass", "None", "Rectangle", "Rounded", "Card")),
    color(DOCK_GROUP, "dockColor", "Background color", "system", themed = true),
    percent(DOCK_GROUP, "dockAlpha", "Background opacity", "50"),
    onOff(DOCK_GROUP, "dockBehindNav", "Draw behind navigation bar", true),
    onOff(DOCK_GROUP, "swipeIndicator", "Swipe indicator arrow above the dock", false),
    onOff(DOCK_GROUP, "dockShadow", "Background shadow", false),
    onOff(DOCK_GROUP, "dockOverlay", "Display as overlay (home grid continues behind the dock)", false),

    Choice(DRAWER, "drawerStyle", "App drawer style", "glass", listOf("glass", "grid", "list", "pages"), listOf("Glass grid", "Vertical grid", "List", "Horizontal pages")),
    Choice(DRAWER, "drawerCols", "Columns", "4", range(3, 8)),
    Choice(DRAWER, "drawerRows", "Rows (horizontal pages)", "6", range(3, 9)),
    sizes(DRAWER, "drawerIconSize", "Icon size", "52"),
    onOff(DRAWER, "drawerLabels", "Labels", true),
    Choice(DRAWER, "drawerTheme", "Panel theme (drawer, menus, settings)", "auto", listOf("auto", "dark", "light"), listOf("Follow night mode", "Dark", "Light")),
    percent(DRAWER, "drawerAlpha", "Background opacity", "90"),
    Choice(DRAWER, "drawerSort", "Sort apps", "az", SORTS, SORT_NAMES),
    onOff(DRAWER, "frequent", "Frequently used apps row", true),
    onOff(DRAWER, "showKeyboard", "Show keyboard when opened", false),
    onOff(DRAWER, "pullToSearch", "Pull down to search (instead of closing)", false),
    onOff(DRAWER, "rememberPosition", "Remember tab and scroll position", false),
    onOff(DRAWER, "closeAfterLaunch", "Close after launching an app", true),
    onOff(DRAWER, "fastScroll", "Fast scrollbar", true),
    onOff(DRAWER, "foldersFirst", "Folders first", true),
    onOff(DRAWER, "keepInAll", "Keep grouped apps in the main tab", true),
    onOff(DRAWER, "workTab", "Work apps in their own tab", true),
    onOff(DRAWER, "webSearch", "Offer a web search in the drawer (opens your browser)", true),
    Choice(DRAWER, "searchEngine", "Web search with", "browser", SEARCH_ENGINES, SEARCH_ENGINE_NAMES),
    onOff(DRAWER, "contactSearch", "Search contacts (asks for contacts permission)", false),

    Choice(FOLDERS, "folderPreview", "Folder preview", "grid", PREVIEWS, PREVIEW_NAMES),
    Choice(FOLDERS, "folderBg", "Folder background", "square", FOLDER_BGS, FOLDER_BG_NAMES),
    color(FOLDERS, "folderColor", "Folder background color", "system", themed = true),
    onOff(FOLDERS, "folderClip", "Clip to background", false),
    onOff(FOLDERS, "folderFirstBig", "Prioritize first icon", false),
    Choice(FOLDERS, "folderWindow", "Window style", "popup", listOf("popup", "fullscreen"), listOf("Pop-up", "Full screen")),
    onOff(FOLDERS, "folderSwipe", "Swipe to open (tap launches the first item)", false),
    onOff(FOLDERS, "folderNames", "Show folder name", true),

    Choice(LOOK, "shape", "Icon shape", "squircle", SHAPES, SHAPE_NAMES),
    Choice(LOOK, "reshape", "Reshape legacy icons", "off", listOf("off", "unthemed", "always"), listOf("Off", "Unthemed only", "Always")),
    onOff(LOOK, "autogen", "Theme icons the icon pack doesn't cover", true),
    onOff(LOOK, "normalize", "Normalize icon size", false),
    onOff(LOOK, "iconShadow", "Icon shadow", false),
    onOff(LOOK, "wiggle", "Wiggle icons during drag and drop", false),
    Choice(LOOK, "launchAnim", "App animation", "system", listOf("system", "zoom", "reveal", "fade", "slideUp", "slideLeft", "none"),
        listOf("System default", "Zoom", "Reveal", "Fade", "Slide up", "Slide left", "None")),
    color(LOOK, "accent", "Theme color", "system", themed = true),
    onOff(LOOK, "blur", "Blur the home screen behind panels (Android 12+)", true),
    onOff(LOOK, "popupShortcuts", "App shortcuts in long-press menu", true),
    onOff(LOOK, "popupNotifications", "Notifications in long-press menu", true),
    onOff(LOOK, "closeApp", "\"Close app\" in long-press menu", true),

    Choice(NIGHT, "nightMode", "Night mode", "system", NIGHT_MODES, NIGHT_MODE_NAMES),
    Choice(NIGHT, "nightStart", "Schedule start", "20", range(0, 23), range(0, 23).map { "$it:00" }),
    Choice(NIGHT, "nightEnd", "Schedule end", "7", range(0, 23), range(0, 23).map { "$it:00" }),

    Choice(BADGES, "badges", "Badge style", "off", listOf("off", "dots", "numeric", "dynamic"),
        listOf("Off", "Dots", "Numeric dots", "Dynamic (notification picture)")),
    Choice(BADGES, "badgePos", "Position", "topRight", listOf("topLeft", "topRight", "bottomLeft", "bottomRight"),
        listOf("Top left", "Top right", "Bottom left", "Bottom right")),
    Choice(BADGES, "badgeSize", "Size", "medium", listOf("small", "medium", "large"), listOf("Small", "Medium", "Large")),
    color(BADGES, "badgeColor", "Color", "#E53935"),

    onOff(NOW, "nowClock", "Date and time", true),
    onOff(NOW, "nowMedia", "Media controls (uses notification access)", true),
    onOff(NOW, "nowCalendar", "Upcoming events (asks for calendar permission)", false),
    onOff(NOW, "nowContacts", "Favourite contacts (asks for contacts permission)", false),
    onOff(NOW, "nowApps", "Frequently used apps", true),
) + GESTURE_KEYS.indices.map { Choice(GESTURES, GESTURE_KEYS[it], GESTURE_NAMES[it], GESTURE_DEFAULTS[it], ACTIONS, ACTION_NAMES, action = true) } + listOf(
    Choice(GESTURES, "homeButton", "Home button", "default", listOf("default", "previews", "none"), listOf("Go to default page", "Desktop previews", "Nothing")),
)

/** Settings in their own SharedPreferences. Changing one recreates the activity. */
class Looks(context: Context) {
    private val prefs = context.getSharedPreferences("looks", Context.MODE_PRIVATE)

    operator fun get(c: Choice): String = prefs.getString(c.key, c.default)!!
    operator fun set(c: Choice, value: String) = prefs.edit().putString(c.key, value).apply()
    operator fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()

    /** A stored value that isn't a [Choice] (cached location), or null. */
    fun raw(key: String): String? = prefs.getString(key, null)

    fun str(key: String) = prefs.getString(key, null) ?: LOOKS.first { it.key == key }.default
    fun int(key: String) = str(key).toIntOrNull() ?: LOOKS.first { it.key == key }.default.toInt()
    fun on(key: String) = str(key) == "on"
    fun color(key: String) = runCatching { android.graphics.Color.parseColor(str(key)) }.getOrDefault(android.graphics.Color.WHITE)

    val cols get() = int("cols")
    val rows get() = int("rows")
    val pages get() = int("pages")
    val dockOn get() = on("dock")
    val dockCols get() = int("dockCols")
    val dockPages get() = int("dockPages")
    val sub get() = if (on("subgrid")) 2 else 1
    val iconPack get() = prefs.getString("iconPack", "")!!
}
