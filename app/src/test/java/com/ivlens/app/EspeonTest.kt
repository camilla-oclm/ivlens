package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.ArcReader
import org.junit.Assert.assertEquals
import org.junit.Test

class EspeonTest {
    private val gd = TestData.gd
    @Test
    fun espeonArc() {
        val img = TestData.screenshot("espeon")
        val arc = ArcReader.read(img.px, img.w, img.h)
        println("arc: geom=${arc?.geometry} cov=${arc?.coverage} angle=${arc?.dotAngle} found=${arc?.dotFound}")
        val near = arc?.let { ArcReader.levelsNear(gd, it, Levels.L50) } ?: emptyList()
        println("levels near: ${near.map { Levels.label(it) }}")
        for (lv in listOf(16.5, 17.0, 17.5, 18.0, 18.5)) println("L$lv angle ${ArcReader.angleFor(gd, Levels.indexOf(lv), Levels.L50)}")
        val calc = IvCalculator(gd)
        val plain = calc.calculate(CalcInput("espeon", 1495, 95, 2200))!!
        println("no arc: ${plain.combos.size} -> ${plain.combos.groupBy { Levels.label(it.level) }.mapValues { e -> e.value.map { it.ivText + " " + it.percent + "%" } }}")
        val withArc = calc.calculate(CalcInput("espeon", 1495, 95, 2200, levelHint = near))!!
        println("with arc: ${withArc.combos.size} -> ${withArc.combos.map { it.ivText + " " + it.percent + "% L" + Levels.label(it.level) }} usedArc=${withArc.usedArc}")
        // Special purple background; our card masked out. Dot at level 17 leaves 13/12/10 or 13/11/11.
        assertEquals(listOf(Levels.indexOf(17.0)), near)
        assertEquals(19, plain.combos.size)
        assertEquals(setOf("13/12/10", "13/11/11"), withArc.combos.map { it.ivText }.toSet())
    }
}
