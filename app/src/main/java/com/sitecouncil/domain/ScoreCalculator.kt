package com.sitecouncil.domain

import kotlin.math.roundToInt

object ScoreCalculator {

    /**
     * Gewichteter Mittelwert der Teilscores. Kategorien ohne Eintrag in
     * [weights] zählen mit Gewicht 1, Gewichte kleiner oder gleich 0 fallen raus.
     */
    fun total(scores: Map<String, Int>, weights: Map<String, Double>): Int {
        var sum = 0.0
        var weightSum = 0.0
        for ((key, score) in scores) {
            val weight = weights[key] ?: 1.0
            if (weight <= 0.0) continue
            sum += score.coerceIn(0, 100) * weight
            weightSum += weight
        }
        if (weightSum == 0.0) return 0
        return (sum / weightSum).roundToInt()
    }

    /** Hohe Wirkung bei geringem Aufwand zuerst. */
    fun prioritize(actions: List<ActionItem>): List<ActionItem> =
        actions.sortedWith(
            compareByDescending<ActionItem> { it.impact - it.effort }
                .thenByDescending { it.impact }
                .thenBy { it.effort }
        )

    /** Ersatz, falls der Moderator ausfällt: Maßnahmen direkt aus den Befunden. */
    fun actionsFromFindings(votes: List<ExpertVote>): List<ActionItem> =
        votes.flatMap { vote ->
            vote.findings.map { f ->
                ActionItem(
                    title = f.problem.ifBlank { f.area },
                    detail = f.fix,
                    category = vote.expert,
                    impact = f.impact.coerceIn(1, 5),
                    effort = f.effort.coerceIn(1, 5),
                    sources = listOf(vote.expert),
                )
            }
        }
}
