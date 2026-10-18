package dev.vulcanlauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapTest {
    @Test
    fun overlaps() {
        assertTrue(overlaps(0, 0, 2, 2, 1, 1, 1, 1))   // icon inside a 2x2 widget
        assertTrue(overlaps(1, 1, 1, 1, 0, 0, 2, 2))   // symmetric
        assertFalse(overlaps(0, 0, 2, 2, 2, 0, 1, 1))  // touching right edge
        assertFalse(overlaps(0, 0, 2, 2, 0, 2, 1, 1))  // touching bottom edge
        assertFalse(overlaps(0, 0, 1, 1, 3, 3, 1, 1))  // far apart
    }
}
