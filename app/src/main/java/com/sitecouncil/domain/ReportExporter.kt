package com.sitecouncil.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Beschriftungen kommen aus strings.xml, damit der Bericht zur UI passt. */
data class ReportLabels(
    val title: String,
    val date: String,
    val audience: String,
    val goal: String,
    val total: String,
    val categories: String,
    val summary: String,
    val conflicts: String,
    val topActions: String,
    val experts: String,
    val personas: String,
    val successRate: String,
    val exitReasons: String,
    val tech: String,
    val heatmapNote: String,
    val statusOk: String,
    val statusWarn: String,
    val statusError: String,
    val categoryNames: Map<String, String>,
)

object ReportExporter {

    fun toMarkdown(result: AnalysisResult, labels: ReportLabels): String = buildString {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(result.createdAt))
        appendLine("# ${labels.title}")
        appendLine()
        appendLine("* URL: ${result.url}")
        appendLine("* ${labels.date}: $date")
        if (result.audience.isNotBlank()) appendLine("* ${labels.audience}: ${result.audience}")
        if (result.goal.isNotBlank()) appendLine("* ${labels.goal}: ${result.goal}")
        appendLine()
        appendLine("## ${labels.total}: ${result.totalScore} / 100")
        appendLine()
        appendLine("### ${labels.categories}")
        for ((key, score) in result.categoryScores) {
            appendLine("* ${labels.categoryNames[key] ?: key}: $score")
        }
        appendLine()

        if (result.moderator.summary.isNotBlank()) {
            appendLine("## ${labels.summary}")
            appendLine(result.moderator.summary)
            appendLine()
        }

        if (result.moderator.conflicts.isNotEmpty()) {
            appendLine("## ${labels.conflicts}")
            for (c in result.moderator.conflicts) {
                appendLine("* **${c.topic}** (${c.experts.joinToString(", ")}): ${c.description}")
                appendLine("  ${c.resolution}")
            }
            appendLine()
        }

        if (result.topActions.isNotEmpty()) {
            appendLine("## ${labels.topActions}")
            result.topActions.forEachIndexed { i, a ->
                appendLine("${i + 1}. **${a.title}** (${a.impact}/5, ${a.effort}/5)")
                if (a.detail.isNotBlank()) appendLine("   ${a.detail}")
            }
            appendLine()
        }

        if (result.votes.isNotEmpty()) {
            appendLine("## ${labels.experts}")
            for (vote in result.votes) {
                val name = labels.categoryNames[vote.key] ?: vote.expert
                appendLine("### $name: ${vote.score} / 100")
                appendLine(vote.verdict)
                for (f in vote.findings) {
                    appendLine("* [${f.severity}] ${f.area}: ${f.problem}")
                    if (f.evidence.isNotBlank()) appendLine("  * ${f.evidence}")
                    if (f.fix.isNotBlank()) appendLine("  * ${f.fix}")
                }
                appendLine()
            }
        }

        if (result.personaResults.isNotEmpty()) {
            val agg = result.personaAggregate
            appendLine("## ${labels.personas}")
            appendLine("* ${labels.successRate}: ${(agg.successRate * 100).toInt()} % (${agg.yes} / ${agg.partial} / ${agg.no})")
            if (agg.exitReasons.isNotEmpty()) {
                appendLine("* ${labels.exitReasons}: " + agg.exitReasons.joinToString(", ") { "${it.label} (${it.count})" })
            }
            appendLine()
            val byId = result.personas.associateBy { it.id }
            for (r in result.personaResults) {
                val p = byId[r.personaId]
                val head = if (p != null) "${p.name}, ${p.age}" else r.personaId
                appendLine("### $head: ${r.taskSuccess}")
                if (r.firstImpression.isNotBlank()) appendLine("* ${r.firstImpression}")
                if (r.nextAction.isNotBlank()) appendLine("* ${r.nextAction}")
                if (r.exitReason.isNotBlank()) appendLine("* ${r.exitPoint} ${r.exitReason}".trim())
                if (r.missing.isNotBlank()) appendLine("* ${r.missing}")
                appendLine()
            }
        }

        if (result.techChecks.isNotEmpty()) {
            appendLine("## ${labels.tech}")
            for (c in result.techChecks) {
                val status = when (c.status) {
                    CheckStatus.OK -> labels.statusOk
                    CheckStatus.WARN -> labels.statusWarn
                    CheckStatus.ERROR -> labels.statusError
                }
                appendLine("* [$status] ${c.title}: ${c.finding}")
                if (c.status != CheckStatus.OK && c.recommendation.isNotBlank()) {
                    appendLine("  * ${c.recommendation}")
                }
            }
            appendLine()
        }

        appendLine("_${labels.heatmapNote}_")
    }
}
