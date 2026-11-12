package com.sitecouncil.ui.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sitecouncil.R
import com.sitecouncil.domain.ActionItem
import com.sitecouncil.domain.AnalysisResult
import com.sitecouncil.experts.ExpertDef
import com.sitecouncil.ui.formatTokens
import com.sitecouncil.ui.formatUsd
import com.sitecouncil.ui.theme.scoreColor

@Composable
fun OverviewTab(result: AnalysisResult) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(result.totalScore)
            Text(
                stringResource(R.string.overview_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (result.truncated) {
            Text(
                stringResource(R.string.overview_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle(stringResource(R.string.overview_categories))
        for ((key, score) in result.categoryScores) {
            val def = ExpertDef.byKey(key)
            CategoryRow(if (def != null) stringResource(def.labelRes) else key, score)
        }
        if (result.failedExperts.isNotEmpty()) {
            val names = result.failedExperts.map { key ->
                val def = ExpertDef.byKey(key)
                if (def != null) stringResource(def.labelRes) else key
            }
            Text(
                stringResource(R.string.overview_failed_experts, names.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionTitle(stringResource(R.string.overview_top_actions))
        if (result.topActions.isEmpty()) {
            Text(stringResource(R.string.overview_no_actions))
        }
        result.topActions.forEachIndexed { index, action -> ActionCard(index + 1, action) }

        if (result.moderator.summary.isNotBlank()) {
            SectionTitle(stringResource(R.string.overview_summary))
            Text(result.moderator.summary)
        }

        if (result.moderator.conflicts.isNotEmpty()) {
            SectionTitle(stringResource(R.string.overview_conflicts))
            for (conflict in result.moderator.conflicts) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(conflict.topic, fontWeight = FontWeight.Bold)
                        if (conflict.experts.isNotEmpty()) {
                            Text(
                                conflict.experts.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(conflict.description)
                        Text(stringResource(R.string.overview_resolution, conflict.resolution))
                    }
                }
            }
        }

        Text(
            stringResource(
                R.string.overview_cost,
                formatTokens(result.usage.inputTokens),
                formatTokens(result.usage.outputTokens),
                formatUsd(result.usage.costUsd),
                result.usage.calls,
                result.model,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val color = scoreColor(score)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.overview_total) + ": $score"
    Box(
        modifier = modifier
            .size(160.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * score.coerceIn(0, 100) / 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(score.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryRow(label: String, score: Int) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(score.toString(), fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { score.coerceIn(0, 100) / 100f },
            color = scoreColor(score),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun ActionCard(position: Int, action: ActionItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$position. ${action.title}", fontWeight = FontWeight.Bold)
            if (action.detail.isNotBlank()) Text(action.detail)
            Text(
                stringResource(R.string.overview_impact_effort, action.impact, action.effort) +
                    if (action.category.isNotBlank()) " · ${action.category}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
