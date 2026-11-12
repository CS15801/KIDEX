package com.sitecouncil.analysis

import com.sitecouncil.domain.CheckStatus
import com.sitecouncil.domain.TechCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.nodes.Document

/**
 * Deterministische Prüfungen ohne Netzwerk. Jede Funktion bekommt fertige
 * Daten und liefert genau einen [TechCheck]. Dadurch sind sie einfach testbar.
 */
object TechRules {

    val BOTS = listOf("GPTBot", "ClaudeBot", "PerplexityBot", "Google-Extended", "Bingbot")

    private val GENERIC_LINK_TEXTS = setOf(
        "hier", "hier klicken", "klicken sie hier", "klick hier", "mehr", "mehr erfahren",
        "mehr lesen", "weiterlesen", "weiter", "link", "click here", "here", "read more", "more",
        "learn more",
    )

    data class Hop(val code: Int, val url: String)

    fun status(code: Int): TechCheck = when {
        code in 200..299 -> TechCheck("status", "Statuscode", CheckStatus.OK, "Die Seite antwortet mit $code.")
        code in 300..399 -> TechCheck(
            "status", "Statuscode", CheckStatus.WARN,
            "Die Weiterleitungskette endet mit $code, ohne eine Seite auszuliefern.",
            "Weiterleitungen prüfen. Am Ende muss eine Seite mit Status 200 stehen.",
        )
        code <= 0 -> TechCheck(
            "status", "Statuscode", CheckStatus.ERROR,
            "Der Server hat nicht geantwortet.",
            "Erreichbarkeit, DNS und Zertifikat prüfen.",
        )
        else -> TechCheck(
            "status", "Statuscode", CheckStatus.ERROR,
            "Die Seite antwortet mit $code.",
            "Suchmaschinen indexieren nur Seiten mit Status 200. Ursache des Fehlers beheben.",
        )
    }

    fun redirects(hops: List<Hop>): TechCheck {
        val count = (hops.size - 1).coerceAtLeast(0)
        val chain = hops.joinToString(" > ") { "${it.code} ${it.url}" }
        return when {
            count == 0 -> TechCheck("redirects", "Redirect-Kette", CheckStatus.OK, "Keine Weiterleitung.")
            count == 1 -> TechCheck("redirects", "Redirect-Kette", CheckStatus.OK, "Eine Weiterleitung: $chain")
            count <= 3 -> TechCheck(
                "redirects", "Redirect-Kette", CheckStatus.WARN,
                "$count Weiterleitungen hintereinander: $chain",
                "Direkt auf die Zieladresse weiterleiten. Jede Zwischenstation kostet Ladezeit.",
            )
            else -> TechCheck(
                "redirects", "Redirect-Kette", CheckStatus.ERROR,
                "$count Weiterleitungen hintereinander: $chain",
                "Kette auf eine einzige Weiterleitung kürzen.",
            )
        }
    }

    fun https(finalUrl: String): TechCheck =
        if (finalUrl.startsWith("https://", ignoreCase = true)) {
            TechCheck("https", "HTTPS", CheckStatus.OK, "Die Seite wird verschlüsselt ausgeliefert.")
        } else {
            TechCheck(
                "https", "HTTPS", CheckStatus.ERROR,
                "Die Seite wird unverschlüsselt über HTTP ausgeliefert.",
                "TLS-Zertifikat einrichten und HTTP dauerhaft (301) auf HTTPS weiterleiten.",
            )
        }

    fun robots(robotsTxt: String?, path: String): TechCheck {
        if (robotsTxt == null) {
            return TechCheck(
                "robots", "robots.txt", CheckStatus.WARN,
                "Keine robots.txt gefunden. Alle Crawler dürfen alles abrufen.",
                "Eine robots.txt anlegen und dort die Sitemap nennen.",
            )
        }
        val verdicts = BOTS.map { it to RobotsParser.check(robotsTxt, it, path) }
        val blocked = verdicts.filter { !it.second.allowed }.map { it.first }
        val summary = verdicts.joinToString(", ") { (bot, v) ->
            val state = if (v.allowed) "erlaubt" else "gesperrt"
            val source = if (v.specificGroup) "eigene Regel" else "allgemeine Regel"
            "$bot: $state ($source)"
        }
        return if (blocked.isEmpty()) {
            TechCheck("robots", "robots.txt", CheckStatus.OK, summary)
        } else {
            TechCheck(
                "robots", "robots.txt", CheckStatus.WARN, summary,
                "Gesperrt: ${blocked.joinToString(", ")}. Prüfen, ob das gewollt ist. " +
                    "Gesperrte KI-Crawler können die Seite nicht zitieren, ein gesperrter Bingbot " +
                    "nimmt sie aus Bing und den darauf aufbauenden Diensten.",
            )
        }
    }

    fun sitemap(foundAtDefault: Boolean, declaredInRobots: List<String>): TechCheck = when {
        declaredInRobots.isNotEmpty() -> TechCheck(
            "sitemap", "sitemap.xml", CheckStatus.OK,
            "In der robots.txt genannt: ${declaredInRobots.first()}",
        )
        foundAtDefault -> TechCheck(
            "sitemap", "sitemap.xml", CheckStatus.WARN,
            "Unter /sitemap.xml vorhanden, aber nicht in der robots.txt genannt.",
            "Zeile \"Sitemap: https://domain/sitemap.xml\" in die robots.txt aufnehmen.",
        )
        else -> TechCheck(
            "sitemap", "sitemap.xml", CheckStatus.WARN,
            "Keine Sitemap gefunden, weder unter /sitemap.xml noch in der robots.txt.",
            "XML-Sitemap erzeugen und in der robots.txt sowie in der Search Console hinterlegen.",
        )
    }

    fun llmsTxt(found: Boolean): TechCheck =
        if (found) {
            TechCheck("llms", "llms.txt", CheckStatus.OK, "Unter /llms.txt vorhanden.")
        } else {
            TechCheck(
                "llms", "llms.txt", CheckStatus.WARN,
                "Keine llms.txt gefunden.",
                "Optional. Eine llms.txt fasst die wichtigsten Inhalte für Sprachmodelle zusammen. " +
                    "Der Standard ist noch ein Vorschlag, der Aufwand ist gering.",
            )
        }

    fun title(doc: Document): TechCheck {
        val title = doc.title().trim()
        val len = title.length
        return when {
            len == 0 -> TechCheck(
                "title", "Title", CheckStatus.ERROR, "Kein title-Element vorhanden.",
                "Einen eindeutigen Title mit 30 bis 60 Zeichen setzen, das Hauptthema vorn.",
            )
            len < 30 -> TechCheck(
                "title", "Title", CheckStatus.WARN, "Title mit $len Zeichen ist kurz: \"$title\"",
                "Auf 30 bis 60 Zeichen ausbauen und das Hauptthema nennen.",
            )
            len > 60 -> TechCheck(
                "title", "Title", CheckStatus.WARN, "Title mit $len Zeichen wird im Suchergebnis abgeschnitten: \"$title\"",
                "Auf höchstens 60 Zeichen kürzen, das Wichtigste nach vorn.",
            )
            else -> TechCheck("title", "Title", CheckStatus.OK, "Title mit $len Zeichen: \"$title\"")
        }
    }

    fun description(doc: Document): TechCheck {
        val text = doc.select("meta[name=description]").attr("content").trim()
        val len = text.length
        return when {
            len == 0 -> TechCheck(
                "description", "Meta Description", CheckStatus.WARN, "Keine Meta Description vorhanden.",
                "Eine Description mit 70 bis 160 Zeichen schreiben, die Nutzen und Handlungsaufforderung nennt.",
            )
            len < 70 -> TechCheck(
                "description", "Meta Description", CheckStatus.WARN, "Description mit $len Zeichen ist kurz.",
                "Auf 70 bis 160 Zeichen ausbauen.",
            )
            len > 160 -> TechCheck(
                "description", "Meta Description", CheckStatus.WARN, "Description mit $len Zeichen wird abgeschnitten.",
                "Auf höchstens 160 Zeichen kürzen.",
            )
            else -> TechCheck("description", "Meta Description", CheckStatus.OK, "Description mit $len Zeichen.")
        }
    }

    fun canonical(doc: Document, finalUrl: String): TechCheck {
        val links = doc.select("link[rel=canonical]")
        if (links.isEmpty()) {
            return TechCheck(
                "canonical", "Canonical", CheckStatus.WARN, "Kein Canonical-Link vorhanden.",
                "Ein link-Element mit rel=\"canonical\" auf die bevorzugte Adresse setzen.",
            )
        }
        if (links.size > 1) {
            return TechCheck(
                "canonical", "Canonical", CheckStatus.ERROR, "${links.size} Canonical-Links vorhanden.",
                "Genau einen Canonical-Link ausgeben. Mehrere werden ignoriert.",
            )
        }
        val href = links.first()?.attr("abs:href").orEmpty().ifBlank { links.attr("href") }
        return if (normalizeUrl(href) == normalizeUrl(finalUrl)) {
            TechCheck("canonical", "Canonical", CheckStatus.OK, "Canonical zeigt auf die Seite selbst.")
        } else {
            TechCheck(
                "canonical", "Canonical", CheckStatus.WARN,
                "Canonical zeigt auf eine andere Adresse: $href",
                "Prüfen, ob das gewollt ist. Sonst wird diese Seite nicht indexiert, sondern die genannte.",
            )
        }
    }

    fun hreflang(doc: Document): TechCheck {
        val langs = doc.select("link[rel=alternate][hreflang]").map { it.attr("hreflang") }
        return if (langs.isEmpty()) {
            TechCheck(
                "hreflang", "hreflang", CheckStatus.OK,
                "Keine hreflang-Angaben. Das ist in Ordnung, solange es nur eine Sprachfassung gibt.",
            )
        } else {
            TechCheck("hreflang", "hreflang", CheckStatus.OK, "Sprachfassungen: ${langs.distinct().joinToString(", ")}")
        }
    }

    fun openGraph(doc: Document): TechCheck {
        val required = listOf("og:title", "og:description", "og:image")
        val missing = required.filter { doc.select("meta[property=$it]").attr("content").isBlank() }
        return if (missing.isEmpty()) {
            TechCheck("opengraph", "Open Graph", CheckStatus.OK, "og:title, og:description und og:image sind gesetzt.")
        } else {
            TechCheck(
                "opengraph", "Open Graph", CheckStatus.WARN, "Es fehlen: ${missing.joinToString(", ")}",
                "Fehlende Angaben ergänzen, damit geteilte Links mit Titel, Text und Bild erscheinen.",
            )
        }
    }

    fun robotsMeta(doc: Document, xRobotsTag: String?): TechCheck {
        val meta = doc.select("meta[name=robots], meta[name=googlebot]").joinToString(",") { it.attr("content") }
        // Direktiven wie "max-image-preview:none" dürfen nicht als "none" gelesen werden.
        val tokens = (meta + "," + xRobotsTag.orEmpty()).lowercase()
            .split(',')
            .map { it.trim() }
            .filter { !it.startsWith("max-") && !it.startsWith("unavailable_after") }
            .map { it.substringAfter(':').trim() }
        val noindex = "noindex" in tokens || "none" in tokens
        val nofollow = "nofollow" in tokens || "none" in tokens
        return when {
            noindex -> TechCheck(
                "robotsmeta", "noindex / nofollow", CheckStatus.ERROR,
                "Die Seite ist auf noindex gesetzt und erscheint nicht in Suchergebnissen.",
                "noindex entfernen, falls die Seite gefunden werden soll.",
            )
            nofollow -> TechCheck(
                "robotsmeta", "noindex / nofollow", CheckStatus.WARN,
                "Die Seite ist auf nofollow gesetzt. Links auf der Seite werden nicht verfolgt.",
                "nofollow entfernen, falls interne Links gewertet werden sollen.",
            )
            else -> TechCheck("robotsmeta", "noindex / nofollow", CheckStatus.OK, "Weder noindex noch nofollow gesetzt.")
        }
    }

    fun headings(doc: Document): TechCheck {
        val levels = doc.select("h1, h2, h3, h4, h5, h6").map { it.tagName().substring(1).toInt() }
        val h1Count = levels.count { it == 1 }
        val jumps = mutableListOf<String>()
        var previous = 0
        for (level in levels) {
            if (previous != 0 && level > previous + 1) jumps += "h$previous auf h$level"
            previous = level
        }
        return when {
            levels.isEmpty() -> TechCheck(
                "headings", "Überschriftenstruktur", CheckStatus.ERROR, "Keine Überschriften vorhanden.",
                "Genau eine h1 für das Hauptthema setzen und den Inhalt mit h2 und h3 gliedern.",
            )
            h1Count == 0 -> TechCheck(
                "headings", "Überschriftenstruktur", CheckStatus.ERROR, "Keine h1 vorhanden.",
                "Genau eine h1 für das Hauptthema der Seite setzen.",
            )
            h1Count > 1 -> TechCheck(
                "headings", "Überschriftenstruktur", CheckStatus.WARN, "$h1Count h1-Überschriften vorhanden.",
                "Auf eine h1 reduzieren, die übrigen zu h2 machen.",
            )
            jumps.isNotEmpty() -> TechCheck(
                "headings", "Überschriftenstruktur", CheckStatus.WARN,
                "Sprünge in der Hierarchie: ${jumps.distinct().joinToString(", ")}",
                "Ebenen nicht überspringen. Auf h2 folgt h3, nicht h4.",
            )
            else -> TechCheck(
                "headings", "Überschriftenstruktur", CheckStatus.OK,
                "Eine h1, ${levels.size} Überschriften, keine Sprünge.",
            )
        }
    }

    fun jsonLd(doc: Document): TechCheck {
        val scripts = doc.select("script[type=application/ld+json]")
        if (scripts.isEmpty()) {
            return TechCheck(
                "jsonld", "schema.org JSON-LD", CheckStatus.WARN, "Keine strukturierten Daten gefunden.",
                "Passende Typen ergänzen, z. B. Organization, LocalBusiness, FAQPage oder Article.",
            )
        }
        val types = linkedSetOf<String>()
        var invalid = 0
        for (script in scripts) {
            try {
                collectTypes(Json.parseToJsonElement(script.data()), types)
            } catch (e: Exception) {
                invalid++
            }
        }
        val typeText = if (types.isEmpty()) "keine Typen erkannt" else types.joinToString(", ")
        return when {
            invalid > 0 -> TechCheck(
                "jsonld", "schema.org JSON-LD", CheckStatus.ERROR,
                "$invalid von ${scripts.size} JSON-LD-Blöcken sind kein gültiges JSON. Erkannte Typen: $typeText",
                "Syntax der Blöcke korrigieren. Ungültige Blöcke werden vollständig ignoriert.",
            )
            types.isEmpty() -> TechCheck(
                "jsonld", "schema.org JSON-LD", CheckStatus.WARN,
                "JSON-LD vorhanden, aber ohne @type.",
                "Jedem Objekt einen schema.org-Typ über @type geben.",
            )
            else -> TechCheck("jsonld", "schema.org JSON-LD", CheckStatus.OK, "Gültig. Typen: $typeText")
        }
    }

    private fun collectTypes(element: JsonElement, into: MutableSet<String>) {
        when (element) {
            is JsonArray -> element.forEach { collectTypes(it, into) }
            is JsonObject -> {
                when (val type = element["@type"]) {
                    is JsonPrimitive -> if (type.isString) into += type.content
                    is JsonArray -> type.forEach { t -> (t as? JsonPrimitive)?.let { into += it.content } }
                    else -> Unit
                }
                element.forEach { (key, value) -> if (key != "@type") collectTypes(value, into) }
            }
            else -> Unit
        }
    }

    fun imageAlt(doc: Document): TechCheck {
        val images = doc.select("img")
        val missing = images.count { !it.hasAttr("alt") }
        return when {
            images.isEmpty() -> TechCheck("alt", "Alt-Texte", CheckStatus.OK, "Keine Bilder im HTML.")
            missing == 0 -> TechCheck("alt", "Alt-Texte", CheckStatus.OK, "Alle ${images.size} Bilder haben ein alt-Attribut.")
            else -> TechCheck(
                "alt", "Alt-Texte", if (missing * 2 > images.size) CheckStatus.ERROR else CheckStatus.WARN,
                "$missing von ${images.size} Bildern haben kein alt-Attribut.",
                "Inhaltliche Bilder beschreiben, rein dekorative mit alt=\"\" kennzeichnen.",
            )
        }
    }

    fun linkTexts(doc: Document): TechCheck {
        val links = doc.select("a[href]")
        val generic = links.map { it.text().trim().lowercase().trimEnd('.', '!', '»', '>', ' ') }
            .filter { it in GENERIC_LINK_TEXTS }
        val empty = links.count {
            it.text().isBlank() && it.select("img[alt]").none { img -> img.attr("alt").isNotBlank() } &&
                it.attr("aria-label").isBlank() && it.attr("title").isBlank()
        }
        return when {
            generic.isEmpty() && empty == 0 -> TechCheck(
                "linktext", "Linktexte", CheckStatus.OK, "Keine nichtssagenden oder leeren Linktexte unter ${links.size} Links.",
            )
            else -> TechCheck(
                "linktext", "Linktexte", CheckStatus.WARN,
                buildString {
                    if (generic.isNotEmpty()) {
                        append("${generic.size} nichtssagende Linktexte (${generic.distinct().take(5).joinToString(", ") { "\"$it\"" }}). ")
                    }
                    if (empty > 0) append("$empty Links ohne Text oder Beschriftung.")
                }.trim(),
                "Linktexte so formulieren, dass das Ziel ohne Kontext klar ist, z. B. \"Preise ansehen\" statt \"mehr\".",
            )
        }
    }

    fun renderDiff(rawTextLength: Int, renderedTextLength: Int): TechCheck {
        if (renderedTextLength <= 0) {
            return TechCheck("renderdiff", "Rohes und gerendertes HTML", CheckStatus.OK, "Kein gerenderter Text zum Vergleich vorhanden.")
        }
        val ratio = rawTextLength.toDouble() / renderedTextLength
        val percent = (ratio * 100).toInt().coerceAtMost(100)
        return when {
            ratio < 0.2 -> TechCheck(
                "renderdiff", "Rohes und gerendertes HTML", CheckStatus.ERROR,
                "Nur etwa $percent % des sichtbaren Textes stehen im rohen HTML. Der Rest entsteht per JavaScript.",
                "Inhalte serverseitig ausliefern (SSR oder Prerendering). Viele KI-Crawler führen kein JavaScript aus.",
            )
            ratio < 0.6 -> TechCheck(
                "renderdiff", "Rohes und gerendertes HTML", CheckStatus.WARN,
                "Etwa $percent % des sichtbaren Textes stehen im rohen HTML. Ein Teil erscheint nur per JavaScript.",
                "Prüfen, ob wichtige Inhalte betroffen sind, und diese serverseitig ausliefern.",
            )
            else -> TechCheck(
                "renderdiff", "Rohes und gerendertes HTML", CheckStatus.OK,
                "Der sichtbare Text steht weitgehend im rohen HTML.",
            )
        }
    }

    fun loadTime(serverMs: Long, renderMs: Long): TechCheck {
        val text = "Antwort des Servers nach $serverMs ms, Seite in der WebView geladen nach $renderMs ms."
        return when {
            serverMs > 1500 || renderMs > 6000 -> TechCheck(
                "loadtime", "Ladezeit (grob)", CheckStatus.ERROR, text,
                "Serverantwort, Bildgrößen und blockierende Skripte prüfen. Messung mit PageSpeed Insights bestätigen.",
            )
            serverMs > 600 || renderMs > 3000 -> TechCheck(
                "loadtime", "Ladezeit (grob)", CheckStatus.WARN, text,
                "Caching, Bildkompression und Skripte prüfen. Messung mit PageSpeed Insights bestätigen.",
            )
            else -> TechCheck("loadtime", "Ladezeit (grob)", CheckStatus.OK, text)
        }
    }

    fun pageSize(htmlBytes: Int): TechCheck {
        val kb = htmlBytes / 1024
        return when {
            htmlBytes > 1_000_000 -> TechCheck(
                "size", "Seitengröße", CheckStatus.ERROR, "Das HTML-Dokument ist $kb KB groß.",
                "Inline-Skripte, Inline-Styles und eingebettete Daten auslagern.",
            )
            htmlBytes > 300_000 -> TechCheck(
                "size", "Seitengröße", CheckStatus.WARN, "Das HTML-Dokument ist $kb KB groß.",
                "Prüfen, ob Inline-Skripte oder eingebettete Daten ausgelagert werden können.",
            )
            else -> TechCheck("size", "Seitengröße", CheckStatus.OK, "Das HTML-Dokument ist $kb KB groß (ohne Bilder, Skripte und Styles).")
        }
    }

    private fun normalizeUrl(url: String): String =
        url.trim().substringBefore('#').trimEnd('/').lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
}
