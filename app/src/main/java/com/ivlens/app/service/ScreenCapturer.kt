package com.ivlens.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Mirrors the screen into an ImageReader and always keeps the newest frame, so a capture
 * is available even when the screen hasn't changed for a while.
 */
class ScreenCapturer(context: Context, projection: MediaProjection) {
    private val thread = HandlerThread("ivlens-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()
    private var latest: Image? = null
    private val reader: ImageReader
    private val display: VirtualDisplay?
    val width: Int
    val height: Int

    init {
        val wm = context.getSystemService(WindowManager::class.java)
        val dpi: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            width = b.width()
            height = b.height()
            dpi = context.resources.displayMetrics.densityDpi
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            width = m.widthPixels
            height = m.heightPixels
            dpi = m.densityDpi
        }
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r ->
            val img = try {
                r.acquireLatestImage()
            } catch (e: IllegalStateException) {
                null
            } ?: return@setOnImageAvailableListener
            synchronized(lock) {
                latest?.close()
                latest = img
            }
        }, handler)
        display = projection.createVirtualDisplay(
            "ivlens", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler,
        )
    }

    /** Copy of the newest frame, or null if nothing has arrived yet. */
    @SuppressLint("UseKtx")
    fun grab(): Bitmap? = synchronized(lock) {
        val img = latest ?: return null
        val plane = img.planes[0]
        val buf = plane.buffer
        buf.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        val raw = Bitmap.createBitmap(paddedWidth, img.height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        if (paddedWidth == img.width) raw
        else Bitmap.createBitmap(raw, 0, 0, img.width, img.height).also { raw.recycle() }
    }

    /**
     * One part of the newest frame as ARGB pixels, row by row, read straight from the frame
     * buffer without copying the whole screen. Pixels inside [exclude] (our own bubble and chip)
     * are blanked.
     */
    fun region(left: Int, top: Int, right: Int, bottom: Int, exclude: List<Rect>): IntArray? = synchronized(lock) {
        val img = latest ?: return null
        val plane = img.planes[0]
        val buf = plane.buffer
        val ps = plane.pixelStride
        val rs = plane.rowStride
        val pw = minOf(right, img.width) - left
        val b = minOf(bottom, img.height)
        val out = IntArray(pw * (b - top))
        val row = ByteArray(pw * ps)
        for (y in top until b) {
            buf.position(y * rs + left * ps)
            buf.get(row, 0, pw * ps)
            val o = (y - top) * pw
            for (x in 0 until pw) {
                val i = x * ps
                out[o + x] = (0xFF shl 24) or ((row[i].toInt() and 0xFF) shl 16) or
                    ((row[i + 1].toInt() and 0xFF) shl 8) or (row[i + 2].toInt() and 0xFF)
            }
        }
        buf.rewind()
        blankOut(out, pw, left, top, exclude)
        out
    }

    /**
     * Cheap fingerprint of the Pokémon info panel, read straight from the frame buffer:
     * dark-pixel counts per row and per column, plus how white the panel is. Pixels inside
     * [exclude] (our own bubble and chip) are skipped.
     */
    fun sample(exclude: List<Rect>): FrameSample? = synchronized(lock) {
        val img = latest ?: return null
        val plane = img.planes[0]
        val buf = plane.buffer
        val ps = plane.pixelStride
        val rs = plane.rowStride
        val w = img.width
        val h = img.height
        val y0 = (0.395 * h).toInt()
        val y1 = (0.83 * h).toInt()
        val x0 = (0.05 * w).toInt()
        val x1 = (0.95 * w).toInt()
        val rows = IntArray((y1 - y0) / 2 + 1)
        val cols = IntArray((x1 - x0) / 3 + 1)
        var white = 0
        var counted = 0
        var y = y0
        while (y < y1) {
            val ri = (y - y0) / 2
            var x = x0
            while (x < x1) {
                if (exclude.none { it.contains(x, y) }) {
                    val i = y * rs + x * ps
                    val r = buf.get(i).toInt() and 0xFF
                    val g = buf.get(i + 1).toInt() and 0xFF
                    val b = buf.get(i + 2).toInt() and 0xFF
                    if ((r * 3 + g * 6 + b) < 1500) {
                        rows[ri]++
                        cols[(x - x0) / 3]++
                    }
                    if (ri % 4 == 0 && ((x - x0) / 3) % 4 == 0) {
                        counted++
                        if (minOf(r, g, b) >= 235) white++
                    }
                }
                x += 3
            }
            y += 2
        }
        FrameSample(rows, cols, if (counted == 0) 0f else white.toFloat() / counted)
    }

    fun release() {
        display?.release()
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        reader.close()
        thread.quitSafely()
    }
}

/** Blanks [rects] (screen coordinates) out of [px], a [pw]-wide block whose top-left is ([left], [top]). */
fun blankOut(px: IntArray, pw: Int, left: Int, top: Int, rects: List<Rect>) {
    val ph = px.size / pw
    for (r in rects) {
        for (y in maxOf(top, r.top) until minOf(top + ph, r.bottom)) {
            for (x in maxOf(left, r.left) until minOf(left + pw, r.right)) px[(y - top) * pw + (x - left)] = 0
        }
    }
}

/** See [ScreenCapturer.sample]. */
class FrameSample(private val rows: IntArray, private val cols: IntArray, val whiteFraction: Float) {
    /** Total change in dark-pixel counts; 0 for an identical panel. */
    fun diff(o: FrameSample): Int {
        if (rows.size != o.rows.size || cols.size != o.cols.size) return Int.MAX_VALUE
        var d = 0
        for (i in rows.indices) d += kotlin.math.abs(rows[i] - o.rows[i])
        for (i in cols.indices) d += kotlin.math.abs(cols[i] - o.cols[i])
        return d
    }
}
