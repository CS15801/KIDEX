package com.sitecouncil.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sitecouncil.R
import com.sitecouncil.analysis.PersonaAggregator
import com.sitecouncil.domain.AnalysisResult
import com.sitecouncil.domain.CheckStatus
import com.sitecouncil.domain.CountItem
import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.Finding
import com.sitecouncil.domain.Persona
import com.sitecouncil.domain.PersonaResult
import com.sitecouncil.domain.TechCheck
import com.sitecouncil.experts.ExpertDef
import com.sitecouncil.ui.theme.ScoreBad
import com.sitecouncil.ui.theme.ScoreGood
import com.sitecouncil.ui.theme.ScoreMedium
import com.sitecouncil.ui.theme.scoreColor
import kotlin.math.roundToInt

private val ListPadding = PaddingValues(16.dp)

// ---------- Experten ----------

@Composable
fun ExpertsTab(result: AnalysisResult) {
    if (result.votes.isEmpty()) {
        Text(stringResource(R.string.experts_empty), modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(result.votes, key = { it.key }) { vote -> ExpertCard(vote) }
    }
}

@Composable
private fun ExpertCard(vote: ExpertVote) {
    val def = ExpertDef.byKey(vote.key)
    val name = if (def != null) stringResource(def.labelRes) else vote.expert
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    vote.score.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(vote.score),
                )
            }
            Text(vote.verdict)
            for (finding in vote.findings) {
                HorizontalDivider()
                FindingBlock(finding)
            }
        }
    }
}

@Composable
private fun FindingBlock(finding: Finding) {
    val color = when (finding.severity.trim().lowercase()) {
        "kritisch" -> ScoreBad
        "wichtig" -> ScoreMedium
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${finding.severity.uppercase()} · ${finding.area}",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(finding.problem, fontWeight = FontWeight.Medium)
        if (finding.evidence.isNotBlank()) {
            Text(
                stringResource(R.string.finding_evidence, finding.evidence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (finding.fix.isNotBlank()) {
            Text(stringResource(R.string.finding_fix, finding.fix))
        }
        Text(
            stringResource(R.string.overview_impact_effort, finding.impact, finding.effort),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Testnutzer ----------

@Composable
fun PersonasTab(result: AnalysisResult) {
    if (result.personaResults.isEmpty()) {
        Text(stringResource(R.string.personas_empty), modifier = Modifier.padding(16.dp))
        return
    }
    val personas = result.personas.associateBy { it.id }
    val aggregate = result.personaAggregate
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val percent = (aggregate.successRate * 100).roundToInt()
                    Text(
                        stringResource(R.string.personas_success_rate, percent),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(percent),
                    )
                    Text(stringResource(R.string.personas_counts, aggregate.yes, aggregate.partial, aggregate.no))
                    CountList(stringResource(R.string.personas_exit_reasons), aggregate.exitReasons)
                    CountList(stringResource(R.string.personas_areas), aggregate.areas)
                }
            }
        }
        items(result.personaResults, key = { it.personaId }) { r ->
            PersonaCard(personas[r.personaId], r)
        }
        item {
            Text(
                stringResource(R.string.heatmap_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountList(title: String, items: List<CountItem>) {
    if (items.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    for (item in items) {
        Text(stringResource(R.string.personas_count_item, item.label.replace('_', ' '), item.count))
    }
}

@Composable
private fun PersonaCard(persona: Persona?, r: PersonaResult) {
    val success = PersonaAggregator.normalizeSuccess(r.taskSuccess)
    val color = when (success) {
        "ja" -> ScoreGood
        "teilweise" -> ScoreMedium
        else -> ScoreBad
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(persona?.name ?: r.personaId, style = MaterialTheme.typography.titleMedium)
            if (persona != null) {
                val traits = listOf(persona.device, persona.vision, persona.attitude)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                Text(
                    stringResource(R.string.persona_meta, persona.age, traits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.persona_success, success), color = color, fontWeight = FontWeight.Bold)
            Answer(stringResource(R.string.persona_q_first), r.firstImpression)
            Answer(stringResource(R.string.persona_q_understanding), r.understanding)
            Answer(stringResource(R.string.persona_q_next), r.nextAction)
            Answer(stringResource(R.string.persona_q_exit), listOf(r.exitPoint, r.exitReason).filter { it.isNotBlank() }.joinToString(": "))
            Answer(stringResource(R.string.persona_q_missing), r.missing)
            Answer(stringResource(R.string.persona_q_confusing), r.confusing)
        }
    }
}

@Composable
private fun Answer(question: String, answer: String) {
    if (answer.isBlank()) return
    Column {
        Text(
            question,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(answer)
    }
}

// ---------- Technik ----------

@Composable
fun TechTab(result: AnalysisResult) {
    if (result.techChecks.isEmpty()) {
        Text(stringResource(R.string.tech_empty), modifier = Modifier.padding(16.dp))
        return
    }
    // Fehler zuerst, dann Warnungen, dann alles, was in Ordnung ist.
    val sorted = result.techChecks.sortedBy {
        when (it.status) {
            CheckStatus.ERROR -> 0
            CheckStatus.WARN -> 1
            CheckStatus.OK -> 2
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sorted, key = { it.id }) { check -> TechRow(check) }
    }
}

@Composable
private fun TechRow(check: TechCheck) {
    val color: Color
    val statusText: String
    when (check.status) {
        CheckStatus.OK -> {
            color = ScoreGood
            statusText = stringResource(R.string.tech_status_ok)
        }
        CheckStatus.WARN -> {
            color = ScoreMedium
            statusText = stringResource(R.string.tech_status_warn)
        }
        CheckStatus.ERROR -> {
            color = ScoreBad
            statusText = stringResource(R.string.tech_status_error)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Icon(
            imageVector = when (check.status) {
                CheckStatus.OK -> Icons.Default.CheckCircle
                CheckStatus.WARN -> Icons.Default.Info
                CheckStatus.ERROR -> Icons.Default.Warning
            },
            contentDescription = statusText,
            tint = color,
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${check.title} · $statusText", fontWeight = FontWeight.Bold)
            Text(check.finding)
            if (check.status != CheckStatus.OK && check.recommendation.isNotBlank()) {
                Text(
                    stringResource(R.string.tech_recommendation, check.recommendation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
