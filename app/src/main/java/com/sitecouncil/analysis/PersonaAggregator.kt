package com.sitecouncil.analysis

import com.sitecouncil.domain.CountItem
import com.sitecouncil.domain.PersonaAggregate
import com.sitecouncil.domain.PersonaResult

object PersonaAggregator {

    private const val NO_EXIT = "kein_ausstieg"

    fun aggregate(results: List<PersonaResult>): PersonaAggregate {
        if (results.isEmpty()) return PersonaAggregate()

        var yes = 0
        var partial = 0
        var no = 0
        for (r in results) {
            when (normalizeSuccess(r.taskSuccess)) {
                "ja" -> yes++
                "teilweise" -> partial++
                else -> no++
            }
        }

        val exitReasons = results
            .map { it.exitCategory.trim().lowercase().ifBlank { "sonstiges" } }
            .filter { it != NO_EXIT }
            .groupingBy { it }
            .eachCount()
            .map { CountItem(it.key, it.value) }
            .sortedWith(compareByDescending<CountItem> { it.count }.thenBy { it.label })

        // Jede Persona zählt pro Bereich nur einmal.
        val areas = results
            .flatMap { r -> r.areas.map { it.area.trim().lowercase() }.filter { it.isNotBlank() }.distinct() }
            .groupingBy { it }
            .eachCount()
            .map { CountItem(it.key, it.value) }
            .sortedWith(compareByDescending<CountItem> { it.count }.thenBy { it.label })
            .take(8)

        return PersonaAggregate(
            total = results.size,
            yes = yes,
            partial = partial,
            no = no,
            successRate = (yes + 0.5 * partial) / results.size,
            exitReasons = exitReasons,
            areas = areas,
        )
    }

    fun normalizeSuccess(value: String): String {
        val v = value.trim().lowercase()
        return when {
            v.startsWith("ja") || v == "yes" -> "ja"
            v.startsWith("teil") || v == "partly" || v == "partially" -> "teilweise"
            else -> "nein"
        }
    }
}
