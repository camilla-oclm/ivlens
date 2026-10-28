package com.ivlens.app.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ivlens.app.AppGraph
import com.ivlens.app.scan.AppraisalReader
import com.ivlens.app.scan.ArcGeometry
import com.ivlens.app.scan.ArcReading
import com.ivlens.app.scan.ArcReader
import com.ivlens.app.scan.GenderReader
import com.ivlens.app.scan.OcrLine
import com.ivlens.app.scan.ScanOutcome
import com.ivlens.app.scan.ScreenParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition plus the level-arc reader, turned into a [ScanOutcome]. */
class ScreenReader(private val graph: AppGraph) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** [exclude]: our own overlays on screen, ignored for text and the level arc. */
    suspend fun read(bitmap: Bitmap, known: ArcGeometry? = null, exclude: List<Rect> = emptyList()): ScanOutcome {
        val text = recognize(bitmap)
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val b = line.boundingBox ?: return@mapNotNull null
                // Our chip's own text sits wholly inside it; game text just grazing its edge stays.
                if (exclude.any { it.contains(b.centerX(), b.centerY()) }) return@mapNotNull null
                OcrLine(line.text, b.left, b.top, b.right, b.bottom)
            }
        }
        return withContext(Dispatchers.Default) {
            val w = bitmap.width
            val h = bitmap.height
            val parsed = ScreenParser.parse(lines, w, h)
            // The ♂ / ♀ icon beside the name: some evolutions need one gender (Gallade).
            val (gl, gt, gr, gb) = GenderReader.region(w, h, parsed.nameLine)
            val reading = if (gb > gt) {
                val icon = IntArray((gr - gl) * (gb - gt))
                bitmap.getPixels(icon, 0, gr - gl, gl, gt, gr - gl, gb - gt)
                blankOut(icon, gr - gl, gl, gt, exclude)
                parsed.copy(gender = GenderReader.findIn(icon, gr - gl, w))
            } else parsed
            val settings = graph.settings.state.value
            val arc = if (settings.useArc) {
                // The arc sits in the top half; the rest of the array stays empty.
                val px = IntArray(w * h)
                bitmap.getPixels(px, 0, w, 0, 0, w, minOf(h, h / 2 + 2))
                for (r in exclude) {
                    for (yy in maxOf(0, r.top) until minOf(h / 2 + 2, r.bottom)) {
                        for (xx in maxOf(0, r.left) until minOf(w, r.right)) px[yy * w + xx] = 0xFF000000.toInt()
                    }
                }
                runCatching { ArcReader.read(px, w, h, known) }.getOrNull()
            } else null
            val outcome = graph.resolver.resolve(reading, arc, settings.trainerLevel, settings.useArc)
            // The Appraise screen: its stat bars give the exact IVs, found by their look alone.
            val (l, t, r, b) = AppraisalReader.region(w, h)
            val part = IntArray((r - l) * (b - t))
            bitmap.getPixels(part, 0, r - l, l, t, r - l, b - t)
            blankOut(part, r - l, l, t, exclude)
            val bars = AppraisalReader.findIn(part, r - l, b - t, w)
            if (bars == null) outcome else outcome.copy(appraisal = bars.appraisal, appraising = true)
        }
    }

    /** The level arc alone, from a fresh frame (no text recognition). */
    suspend fun readArc(bitmap: Bitmap, known: ArcGeometry?, exclude: List<Rect>): ArcReading? = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, minOf(h, h / 2 + 2))
        for (r in exclude) {
            for (yy in maxOf(0, r.top) until minOf(h / 2 + 2, r.bottom)) {
                for (xx in maxOf(0, r.left) until minOf(w, r.right)) px[yy * w + xx] = 0xFF000000.toInt()
            }
        }
        runCatching { ArcReader.read(px, w, h, known) }.getOrNull()
    }

    private suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun close() = recognizer.close()
}
