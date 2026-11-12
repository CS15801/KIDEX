package com.sitecouncil.analysis

import com.sitecouncil.domain.HeatBox
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Rechnet Bounding Boxes in ein geglättetes Raster um. Reines Kotlin ohne
 * Android-Klassen, damit es in Unit-Tests läuft.
 */
object HeatGrid {

    /**
     * @return Werte im Bereich 0 bis 1, zeilenweise, Größe cols * rows.
     */
    fun compute(boxes: List<HeatBox>, cols: Int, rows: Int, blurRadius: Int = 3): FloatArray {
        val grid = FloatArray(cols * rows)
        if (cols <= 0 || rows <= 0) return grid

        for (b in boxes) {
            val box = b.box().clamped()
            if (!box.usable()) continue
            val intensity = b.intensity.coerceIn(0.0, 1.0).toFloat()
            if (intensity <= 0f) continue
            val x0 = floor(box.x * cols).toInt().coerceIn(0, cols - 1)
            val x1 = (ceil((box.x + box.w) * cols).toInt() - 1).coerceIn(x0, cols - 1)
            val y0 = floor(box.y * rows).toInt().coerceIn(0, rows - 1)
            val y1 = (ceil((box.y + box.h) * rows).toInt() - 1).coerceIn(y0, rows - 1)
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val i = y * cols + x
                    grid[i] = max(grid[i], intensity)
                }
            }
        }
        if (blurRadius <= 0) return grid

        val kernel = gaussianKernel(blurRadius)
        val temp = FloatArray(grid.size)
        // Horizontal
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                var sum = 0f
                for (k in -blurRadius..blurRadius) {
                    val xx = min(cols - 1, max(0, x + k))
                    sum += grid[y * cols + xx] * kernel[k + blurRadius]
                }
                temp[y * cols + x] = sum
            }
        }
        // Vertikal
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                var sum = 0f
                for (k in -blurRadius..blurRadius) {
                    val yy = min(rows - 1, max(0, y + k))
                    sum += temp[yy * cols + x] * kernel[k + blurRadius]
                }
                grid[y * cols + x] = sum.coerceIn(0f, 1f)
            }
        }
        return grid
    }

    private fun gaussianKernel(radius: Int): FloatArray {
        val sigma = radius / 2.0
        val values = FloatArray(radius * 2 + 1) { i ->
            val d = (i - radius).toDouble()
            exp(-(d * d) / (2 * sigma * sigma)).toFloat()
        }
        val sum = values.sum()
        return FloatArray(values.size) { values[it] / sum }
    }

    /**
     * Farbverlauf blau über gelb zu rot als ARGB-Wert (nicht vormultipliziert).
     * Kleine Werte bleiben durchsichtig.
     */
    fun colorFor(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        if (v < 0.03f) return 0
        val alpha = (min(1f, v * 1.3f) * 165f).toInt()
        val r: Int
        val g: Int
        val b: Int
        if (v < 0.5f) {
            val t = v / 0.5f
            r = lerp(0, 255, t)
            g = lerp(90, 220, t)
            b = lerp(255, 0, t)
        } else {
            val t = (v - 0.5f) / 0.5f
            r = 255
            g = lerp(220, 30, t)
            b = 0
        }
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
