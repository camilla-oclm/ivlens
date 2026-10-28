package com.ivlens.app

import com.ivlens.app.core.GameData
import com.ivlens.app.scan.OcrLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream

object TestData {
    val gd: GameData by lazy { GameData.parse(File("src/main/assets/pokedata.json").readText()) }

    class Image(val px: IntArray, val w: Int, val h: Int)

    /**
     * Top half of a real screenshot, stored as gzipped RGB (header: width, height, rows).
     * The screenshots show game artwork, so they aren't in the repo (`screens/` is git-ignored):
     * a test that needs one is skipped when it's missing.
     */
    fun screenshot(name: String): Image {
        val stream = javaClass.classLoader!!.getResourceAsStream("screens/$name.rgb.gz")
        Assume.assumeTrue("screenshot $name isn't here (not in the repo)", stream != null)
        DataInputStream(GZIPInputStream(stream!!).buffered()).use { inp ->
            val w = inp.readInt()
            val h = inp.readInt()
            val rows = inp.readInt()
            val bytes = ByteArray(w * rows * 3)
            inp.readFully(bytes)
            val px = IntArray(w * h)
            for (i in 0 until w * rows) {
                val r = bytes[i * 3].toInt() and 0xFF
                val g = bytes[i * 3 + 1].toInt() and 0xFF
                val b = bytes[i * 3 + 2].toInt() and 0xFF
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Image(px, w, h)
        }
    }

    @Serializable
    data class FixLine(val text: String, val l: Int, val t: Int, val r: Int, val b: Int)

    @Serializable
    data class Fixture(val w: Int, val h: Int, val lines: List<FixLine>)

    private val json = Json { ignoreUnknownKeys = true }

    /** Lines read by Tesseract from the real screenshots (rougher than ML Kit). */
    fun tesseract(name: String): Fixture =
        json.decodeFromString(Fixture.serializer(), javaClass.classLoader!!.getResource("ocr/$name.tess.json")!!.readText())

    fun Fixture.ocrLines() = lines.map { OcrLine(it.text, it.l, it.t, it.r, it.b) }

    /**
     * Builds lines the way ML Kit tends to return them, from positions measured on the
     * 923x2000 preview of each screenshot, scaled to the real 1080x2340 image.
     */
    fun mlkitLike(vararg items: Pair<String, IntArray>): List<OcrLine> {
        val k = 1080.0 / 923.0
        return items.map { (t, box) ->
            val (cx, cy, w, h) = box.toList()
            OcrLine(t, ((cx - w / 2) * k).toInt(), ((cy - h / 2) * k).toInt(), ((cx + w / 2) * k).toInt(), ((cy + h / 2) * k).toInt())
        }
    }

    fun at(cx: Int, cy: Int, w: Int, h: Int) = intArrayOf(cx, cy, w, h)
}
