package dev.vulcanlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StoreTest {
    @Test
    fun badItemIsDroppedNotTheLayout() {
        val json = """[{"page":0,"x":0,"y":0,"pkg":"a","cls":"b"}, {"page":"oops"}, 7,
            {"page":1,"x":0,"y":0,"folder":true,"children":[{"x":1}, {"page":0,"x":0,"y":0,"pkg":"c"}]}]"""
        assertEquals(listOf(Item(0, 0, 0, pkg = "a", cls = "b"), Item(1, 0, 0, folder = true, children = listOf(Item(0, 0, 0, pkg = "c")))),
            itemsFromJson(json))
        assertThrows(Exception::class.java) { itemsFromJson("{not json") }
    }

    @Test
    fun uninstalledPackageLeavesHome() {
        val gone = Item(0, 0, 0, pkg = "x", cls = "X")
        val workTwin = Item(0, 1, 0, pkg = "x", cls = "X", user = 10)
        val shortcut = Item(0, 2, 0, pkg = "x", shortcutId = "s")
        val widget = Item(1, 0, 0, 2, 2, pkg = "x", widgetId = 5)
        val other = Item(0, 0, 0, pkg = "y", cls = "Y")
        val pair = Item(2, 3, 4, 2, 2, folder = true, children = listOf(gone, other))
        val trio = pair.copy(children = listOf(gone, other, workTwin))
        val solo = Item(3, 0, 0, folder = true, children = listOf(gone, shortcut))
        assertEquals(
            listOf(workTwin, widget, other.copy(page = 2, x = 3, y = 4, w = 2, h = 2), trio.copy(children = listOf(other, workTwin))),
            removePackage(listOf(gone, workTwin, shortcut, widget, pair, trio, solo), "x", 0))
    }
}
