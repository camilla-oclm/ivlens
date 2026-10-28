package com.ivlens.app.scan

import com.ivlens.app.core.Gender

/**
 * The ♂ / ♀ icon right of the name, by its look: pale blue-grey on the white (or tinted) page.
 * ♀ (a circle over a cross) is as heavy on the left as on the right; ♂ (a circle with an arrow up
 * and to the right) is about three times heavier at the top right. Genderless Pokémon have no icon.
 */
object GenderReader {
    /**
     * Where to look, as left, top, right, bottom: a band at the right of the page, from the
     * name's top to a little below it. Without a name line, where it sits on an unscrolled page.
     */
    fun region(w: Int, h: Int, name: OcrLine?): IntArray {
        val top = name?.top ?: (0.43 * h).toInt()
        val bottom = name?.let { it.bottom + 2 * it.height } ?: (0.48 * h).toInt()
        return intArrayOf((0.80 * w).toInt(), top.coerceIn(0, h), (0.96 * w).toInt(), bottom.coerceIn(0, h))
    }

    /** [px]: the region's pixels (ARGB, [pw] wide); [w]: the screen width, for sizes. */
    fun findIn(px: IntArray, pw: Int, w: Int): Gender? {
        val ph = px.size / pw
        var n = 0
        var x0 = pw; var x1 = -1; var y0 = ph; var y1 = -1
        val icon = BooleanArray(px.size)
        for (y in 0 until ph) for (x in 0 until pw) {
            val c = px[y * pw + x]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // Blue-grey: bluer than red, but only a little; the page (white or cream) and the
            // date badge (orange) are never bluer than red.
            if (b >= r + 12 && g >= r + 4 && r in 120..225 && b - r < 60) {
                icon[y * pw + x] = true
                n++
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        // About 37–59 px across on a 1080 px screen; a tinted page or a Pokémon would be bigger.
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        if (n < w / 5 || bw !in (w / 50)..(w / 13) || bh !in (w / 50)..(w / 13)) return null
        val mx = (x0 + x1) / 2.0
        val my = (y0 + y1) / 2.0
        var left = 0
        var right = 0
        for (y in y0..y1) for (x in x0..x1) {
            if (!icon[y * pw + x] || y >= my) continue
            if (x < mx) left++ else right++
        }
        return when {
            right > 1.8 * left -> Gender.MALE
            right < 1.25 * left && left < 1.25 * right -> Gender.FEMALE
            else -> null
        }
    }
}
