package com.sitecouncil.ui.result

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sitecouncil.R
import com.sitecouncil.analysis.HeatmapRenderer
import com.sitecouncil.domain.AnalysisResult
import com.sitecouncil.domain.HeatBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private const val TILE_HEIGHT = 2048

@Composable
fun HeatmapTab(result: AnalysisResult) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val boxes = if (mode == 0) result.heatmap.attention else result.heatmap.change
    var selected by remember(mode) { mutableStateOf<List<HeatBox>?>(null) }

    // null = lädt noch, leere Liste = Datei fehlt
    val tiles by produceState<List<ImageBitmap>?>(initialValue = null, result.screenshotPath) {
        value = withContext(Dispatchers.IO) { loadTiles(result.screenshotPath) }
    }
    val overlay = remember(boxes, result.screenshotWidth, result.screenshotHeight) {
        HeatmapRenderer.render(boxes, result.screenshotWidth, result.screenshotHeight).asImageBitmap()
    }
    val description = stringResource(R.string.heatmap_screenshot_description)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text(stringResource(R.string.heatmap_attention)) },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text(stringResource(R.string.heatmap_change)) },
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.heatmap_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val loaded = tiles
            when {
                loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loaded.isEmpty() -> Text(
                    stringResource(R.string.heatmap_no_screenshot),
                    modifier = Modifier.padding(16.dp),
                )
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(boxes) {
                                detectTapGestures { offset ->
                                    val nx = offset.x / size.width.toDouble()
                                    val ny = offset.y / size.height.toDouble()
                                    selected = boxes
                                        .filter { it.box().contains(nx, ny) }
                                        .sortedByDescending { it.intensity }
                                }
                            }
                    ) {
                        Column {
                            for (tile in loaded) {
                                Image(
                                    bitmap = tile,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Image(
                            bitmap = overlay,
                            contentDescription = description,
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier.matchParentSize(),
                        )
                        val marked = selected?.firstOrNull()
                        if (marked != null) {
                            Canvas(Modifier.matchParentSize()) {
                                val topLeft = Offset(
                                    (marked.x * size.width).toFloat(),
                                    (marked.y * size.height).toFloat(),
                                )
                                val rect = Size(
                                    (marked.w * size.width).toFloat(),
                                    (marked.h * size.height).toFloat(),
                                )
                                drawRect(Color.White, topLeft, rect, style = Stroke(5.dp.toPx()))
                                drawRect(Color.Black, topLeft, rect, style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val hits = selected
                when {
                    hits == null -> Text(stringResource(R.string.heatmap_tap_hint))
                    hits.isEmpty() -> Text(stringResource(R.string.heatmap_no_area))
                    else -> hits.take(3).forEach { hit ->
                        Text(
                            stringResource(R.string.heatmap_intensity, (hit.intensity * 100).roundToInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(hit.reason)
                    }
                }
            }
        }
    }
}

/** Zerlegt den Screenshot in Kacheln, weil sehr hohe Bitmaps die Texturgrenze der GPU sprengen. */
private fun loadTiles(path: String): List<ImageBitmap> {
    if (path.isBlank() || !File(path).exists()) return emptyList()
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
    val full = BitmapFactory.decodeFile(path, options) ?: return emptyList()
    val tiles = mutableListOf<ImageBitmap>()
    var y = 0
    while (y < full.height) {
        val h = min(TILE_HEIGHT, full.height - y)
        tiles += Bitmap.createBitmap(full, 0, y, full.width, h).asImageBitmap()
        y += h
    }
    return tiles
}
