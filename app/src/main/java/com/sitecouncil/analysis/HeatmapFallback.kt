package com.sitecouncil.analysis

import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.HeatBox
import com.sitecouncil.domain.HeatmapData
import com.sitecouncil.domain.PersonaResult

/** Bereinigt die Heatmap des Modells und liefert Ersatz, wenn der Aufruf scheitert. */
object HeatmapFallback {

    private const val MAX_BOXES = 40

    fun sanitize(data: HeatmapData): HeatmapData =
        HeatmapData(attention = clean(data.attention), change = clean(data.change))

    private fun clean(boxes: List<HeatBox>): List<HeatBox> =
        boxes.mapNotNull { b ->
            val box = b.box().clamped()
            if (!box.usable()) return@mapNotNull null
            HeatBox(box.x, box.y, box.w, box.h, b.intensity.coerceIn(0.0, 1.0), b.reason)
        }.take(MAX_BOXES)

    fun fromResults(
        votes: List<ExpertVote>,
        personas: List<PersonaResult>,
        elements: List<PageElement>,
    ): HeatmapData {
        val change = votes.flatMap { vote ->
            vote.findings.mapNotNull { f ->
                val box = f.box?.clamped() ?: return@mapNotNull null
                if (!box.usable()) return@mapNotNull null
                val intensity = when (f.severity.trim().lowercase()) {
                    "kritisch" -> 1.0
                    "wichtig" -> 0.7
                    else -> 0.4
                }
                HeatBox(box.x, box.y, box.w, box.h, intensity, "${vote.expert}: ${f.problem} ${f.fix}".trim())
            }
        }

        val attention = mutableListOf<HeatBox>()
        for (r in personas) {
            val click = r.clickBox?.clamped()
            if (click != null && click.usable()) {
                attention += HeatBox(click.x, click.y, click.w, click.h, 0.8, "Klickziel einer Persona: ${r.clickTarget}")
            }
            for (a in r.areas) {
                val box = a.box?.clamped() ?: continue
                if (box.usable()) attention += HeatBox(box.x, box.y, box.w, box.h, 0.5, "Von einer Persona erwähnt: ${a.area}")
            }
        }
        if (attention.isEmpty()) {
            for (e in elements) {
                val intensity = when {
                    e.tag == "h1" -> 0.9
                    e.tag == "button" || e.type == "submit" -> 0.7
                    e.tag == "h2" -> 0.5
                    else -> continue
                }
                // Weiter unten sinkt die erwartete Aufmerksamkeit.
                val falloff = (1.0 - e.box.y * 0.6).coerceIn(0.3, 1.0)
                attention += HeatBox(e.box.x, e.box.y, e.box.w, e.box.h, intensity * falloff, "${e.tag}: ${e.text}")
            }
        }
        return sanitize(HeatmapData(attention = attention, change = change))
    }
}
