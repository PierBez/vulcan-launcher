package dev.vulcanlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerPullTest {
    @Test
    fun releaseOpensByDistanceOrFling() {
        assertTrue(drawerOpens(0.6f, 0f, 1000f))       // past halfway
        assertFalse(drawerOpens(0.4f, 0f, 1000f))      // short of halfway
        assertTrue(drawerOpens(0.1f, 1500f, 1000f))    // short pull, flung up
        assertFalse(drawerOpens(0.9f, -1500f, 1000f))  // nearly open, flung down
    }
}

class PagingTest {
    @Test
    fun flingSettlesFromWhereTheFingerIs() {
        assertEquals(2, flingPage(1.3f, -2000f, 500f))  // flung on toward the next page
        assertEquals(1, flingPage(1.3f, 2000f, 500f))   // dragged toward 2, flung back: home to 1, not past it to 0
        assertEquals(0, flingPage(1.0f, 2000f, 500f))   // flung from rest goes one page
        assertEquals(1, flingPage(0.6f, 100f, 500f))    // slow release: nearest page
        assertEquals(0, flingPage(-0.1f, 100f, 500f))   // released in edge overscroll
    }

    @Test
    fun overscrollResistsAndStopsAtTheLimit() {
        assertEquals(0f, rubberBand(0f, 100f), 0f)
        assertTrue(rubberBand(50f, 100f) in 1f..50f)
        assertTrue(rubberBand(10_000f, 100f) < 100f)
        assertEquals(-rubberBand(50f, 100f), rubberBand(-50f, 100f), 0f)
    }
}
