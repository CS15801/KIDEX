package com.sitecouncil

import com.sitecouncil.analysis.HeatGrid
import com.sitecouncil.analysis.HeatmapFallback
import com.sitecouncil.analysis.PersonaAggregator
import com.sitecouncil.domain.AreaMention
import com.sitecouncil.domain.Box
import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.Finding
import com.sitecouncil.domain.HeatBox
import com.sitecouncil.domain.HeatmapData
import com.sitecouncil.domain.PersonaResult
import com.sitecouncil.domain.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisHelpersTest {

    @Test
    fun urlValidatorAddsSchemeAndRejectsPrivateTargets() {
        assertEquals("https://www.beispiel.de", UrlValidator.normalize(" www.beispiel.de "))
        assertEquals("http://beispiel.de/pfad?a=1", UrlValidator.normalize("http://beispiel.de/pfad?a=1"))
        assertNull(UrlValidator.normalize(""))
        assertNull(UrlValidator.normalize("ftp://beispiel.de"))
        assertNull(UrlValidator.normalize("http://localhost:8080"))
        assertNull(UrlValidator.normalize("http://192.168.1.10/admin"))
        assertNull(UrlValidator.normalize("http://10.0.0.1"))
        assertNull(UrlValidator.normalize("https://nutzer:pass@beispiel.de"))
        assertNull(UrlValidator.normalize("https://intranet"))
        assertNull(UrlValidator.normalize("beispiel .de"))
        assertNotNull(UrlValidator.normalize("https://8.8.8.8"))
    }

    @Test
    fun personaAggregateCountsSuccessAndExitReasons() {
        val results = listOf(
            PersonaResult(personaId = "p1", taskSuccess = "ja", exitCategory = "kein_ausstieg",
                areas = listOf(AreaMention("Hauptüberschrift"), AreaMention("Kontaktformular"))),
            PersonaResult(personaId = "p2", taskSuccess = "Teilweise", exitCategory = "vertrauen_fehlt",
                areas = listOf(AreaMention("hauptüberschrift"), AreaMention("Hauptüberschrift"))),
            PersonaResult(personaId = "p3", taskSuccess = "nein", exitCategory = "vertrauen_fehlt"),
            PersonaResult(personaId = "p4", taskSuccess = "unklar", exitCategory = ""),
        )
        val agg = PersonaAggregator.aggregate(results)
        assertEquals(4, agg.total)
        assertEquals(1, agg.yes)
        assertEquals(1, agg.partial)
        assertEquals(2, agg.no)
        assertEquals(0.375, agg.successRate, 0.0001)
        assertEquals("vertrauen_fehlt", agg.exitReasons.first().label)
        assertEquals(2, agg.exitReasons.first().count)
        assertTrue(agg.exitReasons.none { it.label == "kein_ausstieg" })
        assertEquals("hauptüberschrift", agg.areas.first().label)
        assertEquals(2, agg.areas.first().count)
    }

    @Test
    fun emptyPersonaListGivesEmptyAggregate() {
        val agg = PersonaAggregator.aggregate(emptyList())
        assertEquals(0, agg.total)
        assertEquals(0.0, agg.successRate, 0.0)
    }

    @Test
    fun heatGridIsHottestInsideTheBox() {
        val boxes = listOf(HeatBox(0.25, 0.25, 0.5, 0.5, 1.0, "Mitte"))
        val cols = 40
        val rows = 40
        val grid = HeatGrid.compute(boxes, cols, rows, blurRadius = 3)
        val centre = grid[20 * cols + 20]
        val corner = grid[0]
        assertTrue(centre > 0.9f)
        assertTrue(corner < 0.05f)
        assertTrue(grid.all { it in 0f..1f })
    }

    @Test
    fun heatGridWithoutBoxesStaysEmpty() {
        val grid = HeatGrid.compute(emptyList(), 16, 16)
        assertTrue(grid.all { it == 0f })
        assertEquals(0, HeatGrid.colorFor(0f))
    }

    @Test
    fun heatColorsRunFromBlueOverYellowToRed() {
        val low = HeatGrid.colorFor(0.1f)
        val mid = HeatGrid.colorFor(0.5f)
        val high = HeatGrid.colorFor(1f)
        fun r(c: Int) = (c shr 16) and 0xFF
        fun g(c: Int) = (c shr 8) and 0xFF
        fun b(c: Int) = c and 0xFF
        fun a(c: Int) = (c ushr 24) and 0xFF
        assertTrue(b(low) > r(low))
        assertTrue(r(mid) > 200 && g(mid) > 200 && b(mid) < 30)
        assertTrue(r(high) > 200 && g(high) < 60)
        assertTrue(a(high) > a(low))
    }

    @Test
    fun boxClampKeepsValuesInsideThePage() {
        val box = Box(0.9, -0.2, 0.5, 0.4).clamped()
        assertEquals(0.9, box.x, 0.0001)
        assertEquals(0.0, box.y, 0.0001)
        assertEquals(0.1, box.w, 0.0001)
        assertTrue(box.contains(0.95, 0.2))
    }

    @Test
    fun heatmapFallbackUsesFindingsAndClickTargets() {
        val votes = listOf(
            ExpertVote(
                key = "layout_ux", expert = "Layout", score = 40,
                findings = listOf(
                    Finding(severity = "kritisch", box = Box(0.1, 0.1, 0.8, 0.1), problem = "CTA fehlt"),
                    Finding(severity = "gering", box = null, problem = "ohne Position"),
                ),
            )
        )
        val personas = listOf(PersonaResult(personaId = "p1", clickTarget = "Termin", clickBox = Box(0.2, 0.3, 0.3, 0.05)))
        val data = HeatmapFallback.fromResults(votes, personas, emptyList())
        assertEquals(1, data.change.size)
        assertEquals(1.0, data.change[0].intensity, 0.0001)
        assertEquals(1, data.attention.size)
    }

    @Test
    fun sanitizeDropsEmptyBoxesAndClampsIntensity() {
        val data = HeatmapData(
            attention = listOf(HeatBox(0.1, 0.1, 0.0, 0.2, 0.5, "leer"), HeatBox(0.5, 0.5, 0.9, 0.9, 3.0, "zu groß")),
        )
        val clean = HeatmapFallback.sanitize(data)
        assertEquals(1, clean.attention.size)
        assertEquals(1.0, clean.attention[0].intensity, 0.0001)
        assertEquals(0.5, clean.attention[0].w, 0.0001)
    }
}
