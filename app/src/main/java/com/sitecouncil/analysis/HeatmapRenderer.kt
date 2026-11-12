package com.sitecouncil.analysis

import android.graphics.Bitmap
import com.sitecouncil.domain.HeatBox
import kotlin.math.max

/**
 * Erzeugt aus den Boxen ein kleines, weichgezeichnetes Bitmap. Die UI legt es
 * gestreckt und gefiltert über den Screenshot, dadurch entstehen weiche Übergänge.
 */
object HeatmapRenderer {
    private const val COLS = 64

    fun render(boxes: List<HeatBox>, pageWidth: Int, pageHeight: Int): Bitmap {
        val rows = (COLS.toLong() * max(1, pageHeight) / max(1, pageWidth)).toInt().coerceIn(32, 1024)
        val grid = HeatGrid.compute(boxes, COLS, rows, blurRadius = 3)
        val pixels = IntArray(COLS * rows) { HeatGrid.colorFor(grid[it]) }
        return Bitmap.createBitmap(pixels, COLS, rows, Bitmap.Config.ARGB_8888)
    }
}
