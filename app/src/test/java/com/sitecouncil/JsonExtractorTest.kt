package com.sitecouncil

import com.sitecouncil.domain.AnalysisError
import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.JsonExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonExtractorTest {

    private val valid = """
        {"expert":"SEO-Experte","score":72,"verdict":"Solide Basis.","findings":[
          {"severity":"wichtig","area":"Title","box":null,"problem":"Zu lang","evidence":"74 Zeichen",
           "fix":"Kürzen auf: Physiotherapie in Köln","impact":4,"effort":1}
        ]}
    """.trimIndent()

    @Test
    fun parsesPlainJson() {
        val vote = JsonExtractor.parse(valid, ExpertVote.serializer())
        assertEquals("SEO-Experte", vote.expert)
        assertEquals(72, vote.score)
        assertEquals(1, vote.findings.size)
        assertNull(vote.findings[0].box)
        assertEquals(4, vote.findings[0].impact)
    }

    @Test
    fun parsesJsonInsideCodeFenceWithProse() {
        val raw = "Hier ist mein Votum:\n```json\n$valid\n```\nIch hoffe, das hilft."
        val vote = JsonExtractor.parse(raw, ExpertVote.serializer())
        assertEquals(72, vote.score)
    }

    @Test
    fun bracesInsideStringsDoNotBreakExtraction() {
        val raw = """{"expert":"X","score":10,"verdict":"Klammer } im Text und \" Anführungszeichen","findings":[]} Rest"""
        val extracted = JsonExtractor.extractObject(raw)
        assertNotNull(extracted)
        assertTrue(extracted!!.endsWith("\"findings\":[]}"))
        assertEquals(10, JsonExtractor.parse(raw, ExpertVote.serializer()).score)
    }

    @Test
    fun acceptsScoreAsDecimalOrString() {
        val decimal = """{"expert":"X","score":85.0,"verdict":"","findings":[{"impact":"4","effort":2.4}]}"""
        val vote = JsonExtractor.parse(decimal, ExpertVote.serializer())
        assertEquals(85, vote.score)
        assertEquals(4, vote.findings[0].impact)
        assertEquals(2, vote.findings[0].effort)
    }

    @Test
    fun ignoresUnknownKeysAndFillsDefaults() {
        val raw = """{"score":50,"unbekannt":true}"""
        val vote = JsonExtractor.parse(raw, ExpertVote.serializer())
        assertEquals(50, vote.score)
        assertEquals("", vote.verdict)
        assertTrue(vote.findings.isEmpty())
    }

    @Test
    fun skipsBrokenObjectAndUsesNextOne() {
        val raw = """Beispiel {kein json} und dann {"score":33}"""
        assertEquals(33, JsonExtractor.parse(raw, ExpertVote.serializer()).score)
    }

    @Test
    fun truncatedJsonThrowsInvalidJson() {
        val raw = """{"expert":"X","score":10,"findings":[{"severity":"wichtig","""
        try {
            JsonExtractor.parse(raw, ExpertVote.serializer())
            fail("InvalidJson erwartet")
        } catch (e: AnalysisError.InvalidJson) {
            assertTrue(e.detail.isNotBlank())
        }
    }

    @Test
    fun textWithoutJsonThrowsInvalidJson() {
        try {
            JsonExtractor.parse("Leider kann ich das nicht beurteilen.", ExpertVote.serializer())
            fail("InvalidJson erwartet")
        } catch (e: AnalysisError.InvalidJson) {
            // erwartet
        }
    }
}
