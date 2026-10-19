package dev.vulcanlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    private fun lum(c: Int) = luminance(c)
    /** WCAG contrast ratio; M3 pairs text roles with their containers at 4.5:1 or more. */
    private fun contrast(a: Int, b: Int) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)

    @Test
    fun tones() {
        val blue = 0xFF1E88E5.toInt()
        assertEquals(0xFF000000.toInt(), tone(blue, 0, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), tone(blue, 100, 1f, 1f))
        assertEquals(119.0, (tone(0xFFFF0000.toInt(), 50, 0f, 1f) and 255).toDouble(), 1.0) // no saturation: grey at L* 50 (#777777)
        val red = tone(0xFFFF0000.toInt(), 50, 1f, 1f) // hue kept: red dominates
        assertTrue((red shr 16 and 255) > 3 * (red shr 8 and 255))
        assertEquals(0xFF804020.toInt(), blend(0xFF000000.toInt(), 0xFFFF8040.toInt(), 0.5f) or 0xFF000000.toInt())
    }

    @Test
    fun schemeContrast() {
        for (seed in listOf(0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFF6750A4.toInt(), 0xFF9E9E9E.toInt(), 0xFF43A047.toInt(), 0xFFE53935.toInt(), 0xFF000000.toInt())) for (dark in listOf(false, true)) {
            check(seedPalette(seed, dark), "seed ${Integer.toHexString(seed)} dark=$dark", dark)
        }
        for (dark in listOf(false, true)) {
            val p = appliancePalette(dark)
            check(p, "appliance dark=$dark", dark)
            // The signal as a glyph or dot on the housing and in a well: 3:1, the WCAG minimum for graphics.
            for (bg in listOf(p.surface, p.well)) assertTrue("appliance signalInk dark=$dark", contrast(p.signalInk, bg) >= 3.0)
        }
    }

    private fun check(p: Palette, name: String, dark: Boolean) {
        for ((role, pair) in mapOf("surface" to (p.onSurface to p.surfaceHigh), "variant" to (p.onSurfaceVariant to p.surfaceHigh),
            "primary" to (p.onPrimary to p.primary), "primaryContainer" to (p.onPrimaryContainer to p.primaryContainer),
            "secondaryContainer" to (p.onSecondaryContainer to p.secondaryContainer), "inverse" to (p.inverseOnSurface to p.inverseSurface))) {
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$name $role $ratio", ratio >= 4.5)
        }
        // Surface containers step away from the surface in order.
        val steps = listOf(p.surface, p.surfaceLow, p.surfaceContainer, p.surfaceHigh, p.surfaceHighest).map(::lum)
        assertEquals(steps, if (dark) steps.sorted() else steps.sortedDescending())
    }
}
