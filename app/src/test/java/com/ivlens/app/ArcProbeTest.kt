package com.ivlens.app

import com.ivlens.app.core.Levels
import com.ivlens.app.scan.ArcReader
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Animated backgrounds: sparkles and bright spots on the translucent part of the arc. */
class ArcProbeTest {
    private val gd = TestData.gd

    /** Paints a white four-point sparkle centred on the arc at [deg]. */
    private fun sparkle(img: TestData.Image, deg: Double, size: Int) {
        val r = 0.4096 * img.w
        val cx = img.w / 2.0 + r * cos(Math.toRadians(deg))
        val cy = 0.638 * img.w - r * sin(Math.toRadians(deg))
        for (dy in -size..size) for (dx in -size..size) {
            // Diamond-ish star: thin arms plus a bright core.
            val arm = (abs(dx) <= 3 || abs(dy) <= 3) || abs(dx) + abs(dy) <= size / 2
            if (!arm) continue
            val x = (cx + dx).toInt()
            val y = (cy + dy).toInt()
            if (x in 0 until img.w && y in 0 until img.h) img.px[y * img.w + x] = 0xFFFFFFFF.toInt()
        }
    }

    @Test
    fun sparklesOnTheArcDontMoveTheDot() {
        for ((name, truth) in listOf("metagross2" to 20.0, "espeon" to 17.0, "venusaur" to 24.5, "dialga" to 20.5)) {
            val clean = TestData.screenshot(name)
            val base = ArcReader.read(clean.px, clean.w, clean.h)!!
            for (at in listOf(8.0, 20.0, 33.0)) {
                if (at > base.dotAngle - 6) continue
                val img = TestData.screenshot(name)
                sparkle(img, at, 22)
                val arc = ArcReader.read(img.px, img.w, img.h)!!
                val near = ArcReader.levelsNear(gd, arc, Levels.L50).map { Levels.level(it) }
                println("$name sparkle@$at: dot ${"%.2f".format(arc.dotAngle)} (clean ${"%.2f".format(base.dotAngle)}) -> $near")
                assertEquals("$name with a sparkle at $at°", truth, near.first(), 0.6)
            }
        }
    }
}
