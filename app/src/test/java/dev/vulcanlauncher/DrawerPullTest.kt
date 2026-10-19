package dev.vulcanlauncher

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
