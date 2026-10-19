package dev.vulcanlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    @Test
    fun shelves() {
        assertEquals("Games", shelf(0, "com.example.x"))
        assertEquals("Entertainment", shelf(2, "com.example.x"))
        assertEquals("Communication", shelf(4, "com.example.x"))
        assertEquals("Productivity", shelf(7, "com.example.x"))
        // Undefined category: guessed from the package name.
        assertEquals("Communication", shelf(-1, "com.whatsapp"))
        assertEquals("Photos", shelf(-1, "com.sec.android.app.camera"))
        assertEquals("Entertainment", shelf(-1, "com.spotify.music"))
        assertEquals("Utilities", shelf(-1, "com.android.settings"))
        assertEquals("Productivity", shelf(4, "com.openai.chatgpt"))
        // A known package beats an odd declared category.
        assertEquals("Productivity", shelf(4, "com.android.chrome"))
        // Every result is a known shelf.
        for (c in -1..8) assertEquals(true, shelf(c, "a.b") in SHELVES)
    }
}
