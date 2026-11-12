package com.sitecouncil

import com.sitecouncil.analysis.RobotsParser
import com.sitecouncil.analysis.TechRules
import com.sitecouncil.domain.CheckStatus
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechRulesTest {

    private fun doc(html: String) = Jsoup.parse(html, "https://www.beispiel.de/")

    @Test
    fun titleLengthIsJudged() {
        assertEquals(CheckStatus.ERROR, TechRules.title(doc("<html><head></head></html>")).status)
        assertEquals(CheckStatus.WARN, TechRules.title(doc("<title>Kurz</title>")).status)
        assertEquals(
            CheckStatus.OK,
            TechRules.title(doc("<title>Physiotherapie in Köln: Termine online buchen</title>")).status,
        )
        assertEquals(CheckStatus.WARN, TechRules.title(doc("<title>${"a".repeat(80)}</title>")).status)
    }

    @Test
    fun descriptionMissingIsWarning() {
        assertEquals(CheckStatus.WARN, TechRules.description(doc("<title>x</title>")).status)
        val ok = doc("<meta name=\"description\" content=\"${"b".repeat(120)}\">")
        assertEquals(CheckStatus.OK, TechRules.description(ok).status)
    }

    @Test
    fun exactlyOneH1WithoutJumpsIsOk() {
        val html = "<h1>A</h1><h2>B</h2><h3>C</h3><h2>D</h2>"
        assertEquals(CheckStatus.OK, TechRules.headings(doc(html)).status)
    }

    @Test
    fun missingOrMultipleH1IsReported() {
        assertEquals(CheckStatus.ERROR, TechRules.headings(doc("<h2>A</h2>")).status)
        assertEquals(CheckStatus.WARN, TechRules.headings(doc("<h1>A</h1><h1>B</h1>")).status)
        assertEquals(CheckStatus.ERROR, TechRules.headings(doc("<p>nichts</p>")).status)
    }

    @Test
    fun headingJumpIsReported() {
        val check = TechRules.headings(doc("<h1>A</h1><h2>B</h2><h4>C</h4>"))
        assertEquals(CheckStatus.WARN, check.status)
        assertTrue(check.finding.contains("h2 auf h4"))
    }

    @Test
    fun jsonLdTypesAreCollected() {
        val html = """
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[{"@type":"Organization","name":"X"},{"@type":["LocalBusiness","MedicalBusiness"]}]}
            </script>
        """.trimIndent()
        val check = TechRules.jsonLd(doc(html))
        assertEquals(CheckStatus.OK, check.status)
        assertTrue(check.finding.contains("Organization"))
        assertTrue(check.finding.contains("MedicalBusiness"))
    }

    @Test
    fun invalidJsonLdIsError() {
        val html = "<script type=\"application/ld+json\">{\"@type\": \"Organization\",}</script>"
        assertEquals(CheckStatus.ERROR, TechRules.jsonLd(doc(html)).status)
        assertEquals(CheckStatus.WARN, TechRules.jsonLd(doc("<p>ohne</p>")).status)
    }

    @Test
    fun imagesWithoutAltAreCounted() {
        val html = "<img src=a.jpg alt=\"Praxis\"><img src=b.jpg alt=\"\"><img src=c.jpg>"
        val check = TechRules.imageAlt(doc(html))
        assertEquals(CheckStatus.WARN, check.status)
        assertTrue(check.finding.startsWith("1 von 3"))
    }

    @Test
    fun genericLinkTextsAreFound() {
        val html = "<a href=\"/a\">Hier klicken</a><a href=\"/b\">Preise ansehen</a><a href=\"/c\">mehr</a>"
        val check = TechRules.linkTexts(doc(html))
        assertEquals(CheckStatus.WARN, check.status)
        assertTrue(check.finding.startsWith("2 nichtssagende"))
        assertEquals(CheckStatus.OK, TechRules.linkTexts(doc("<a href=\"/b\">Preise ansehen</a>")).status)
    }

    @Test
    fun noindexIsErrorButMaxImagePreviewNoneIsNot() {
        val noindex = doc("<meta name=\"robots\" content=\"noindex, follow\">")
        assertEquals(CheckStatus.ERROR, TechRules.robotsMeta(noindex, null).status)
        val preview = doc("<meta name=\"robots\" content=\"index, follow, max-image-preview:none\">")
        assertEquals(CheckStatus.OK, TechRules.robotsMeta(preview, null).status)
        assertEquals(CheckStatus.ERROR, TechRules.robotsMeta(doc("<p></p>"), "googlebot: noindex").status)
        assertEquals(CheckStatus.WARN, TechRules.robotsMeta(doc("<p></p>"), "nofollow").status)
    }

    @Test
    fun canonicalToOtherUrlIsWarning() {
        val self = doc("<link rel=\"canonical\" href=\"https://www.beispiel.de/\">")
        assertEquals(CheckStatus.OK, TechRules.canonical(self, "https://www.beispiel.de/").status)
        val other = doc("<link rel=\"canonical\" href=\"https://www.beispiel.de/andere\">")
        assertEquals(CheckStatus.WARN, TechRules.canonical(other, "https://www.beispiel.de/").status)
    }

    @Test
    fun openGraphMissingPartsAreListed() {
        val html = "<meta property=\"og:title\" content=\"T\">"
        val check = TechRules.openGraph(doc(html))
        assertEquals(CheckStatus.WARN, check.status)
        assertTrue(check.finding.contains("og:image"))
        assertFalse(check.finding.contains("og:title"))
    }

    @Test
    fun renderDiffFlagsJavascriptOnlyContent() {
        assertEquals(CheckStatus.ERROR, TechRules.renderDiff(100, 5000).status)
        assertEquals(CheckStatus.WARN, TechRules.renderDiff(2000, 5000).status)
        assertEquals(CheckStatus.OK, TechRules.renderDiff(4800, 5000).status)
    }

    @Test
    fun statusAndRedirectsAndHttps() {
        assertEquals(CheckStatus.OK, TechRules.status(200).status)
        assertEquals(CheckStatus.ERROR, TechRules.status(404).status)
        assertEquals(CheckStatus.ERROR, TechRules.status(0).status)
        val hops = listOf(
            TechRules.Hop(301, "http://beispiel.de/"),
            TechRules.Hop(301, "https://beispiel.de/"),
            TechRules.Hop(200, "https://www.beispiel.de/"),
        )
        assertEquals(CheckStatus.WARN, TechRules.redirects(hops).status)
        assertEquals(CheckStatus.OK, TechRules.redirects(hops.takeLast(1)).status)
        assertEquals(CheckStatus.ERROR, TechRules.https("http://beispiel.de/").status)
    }

    @Test
    fun robotsSpecificGroupBeatsWildcard() {
        val robots = """
            User-agent: *
            Disallow: /intern/

            User-agent: GPTBot
            Disallow: /

            Sitemap: https://www.beispiel.de/sitemap.xml
        """.trimIndent()
        assertFalse(RobotsParser.check(robots, "GPTBot", "/").allowed)
        assertTrue(RobotsParser.check(robots, "GPTBot", "/").specificGroup)
        assertTrue(RobotsParser.check(robots, "ClaudeBot", "/").allowed)
        assertFalse(RobotsParser.check(robots, "ClaudeBot", "/intern/seite").allowed)
        assertEquals(listOf("https://www.beispiel.de/sitemap.xml"), RobotsParser.sitemaps(robots))
    }

    @Test
    fun robotsLongestRuleWinsAndWildcardsWork() {
        val robots = """
            User-agent: Bingbot
            User-agent: ClaudeBot
            Disallow: /shop/
            Allow: /shop/angebote/
            Disallow: /*.pdf$
        """.trimIndent()
        assertFalse(RobotsParser.check(robots, "Bingbot", "/shop/kasse").allowed)
        assertTrue(RobotsParser.check(robots, "bingbot", "/shop/angebote/heute").allowed)
        assertFalse(RobotsParser.check(robots, "ClaudeBot", "/downloads/flyer.pdf").allowed)
        assertTrue(RobotsParser.check(robots, "ClaudeBot", "/downloads/flyer.pdf?x=1").allowed)
        assertTrue(RobotsParser.check(robots, "PerplexityBot", "/shop/kasse").allowed)
    }

    @Test
    fun robotsCheckSummarisesBlockedBots() {
        val robots = "User-agent: GPTBot\nDisallow: /\n"
        val check = TechRules.robots(robots, "/")
        assertEquals(CheckStatus.WARN, check.status)
        assertTrue(check.recommendation.contains("GPTBot"))
        assertEquals(CheckStatus.WARN, TechRules.robots(null, "/").status)
        assertEquals(CheckStatus.OK, TechRules.robots("User-agent: *\nDisallow:\n", "/").status)
    }
}
