package dev.vulcanlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class FitIconTest {
    @Test
    fun fitIcon() {
        assertEquals(192 to true, fitIcon(192, 270, 330, 60))      // 4x6 grid: room to spare
        assertEquals(158 to true, fitIcon(192, 180, 330, 60))      // narrow: icon shrinks, keeps air
        assertEquals(130 to true, fitIcon(192, 270, 190, 60))      // short: icon shrinks above the label
        assertEquals(79 to false, fitIcon(192, 90, 330, 60))       // 12 columns: label goes, icon fits
        assertEquals(150 to true, fitIcon(192, 1 shl 24, 210, 60)) // unspecified width
        assertEquals(100 to true, fitIcon(192, 270, 100, 0))       // no label: capped by height
    }
}
