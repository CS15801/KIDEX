package com.sitecouncil.analysis

import com.sitecouncil.domain.AnalysisRequest
import com.sitecouncil.domain.Box
import com.sitecouncil.domain.CheckStatus
import com.sitecouncil.domain.TechCheck
import java.util.Locale

/** Baut den Textteil, den jeder Experte und jede Persona zusammen mit dem Screenshot bekommt. */
object ContextBuilder {

    private const val MAX_TEXT_CHARS = 7000
    private const val MAX_ELEMENTS = 140

    fun build(
        request: AnalysisRequest,
        snapshot: PageSnapshot,
        tech: List<TechCheck>,
        includeTech: Boolean,
    ): String = buildString {
        appendLine("URL: ${snapshot.url}")
        appendLine("Seitentitel: ${snapshot.title.ifBlank { "(leer)" }}")
        appendLine("Zielgruppe: ${request.audience.ifBlank { "(nicht angegeben)" }}")
        appendLine("Seitenziel: ${request.goal.ifBlank { "(nicht angegeben)" }}")
        appendLine("Ansicht: ${if (snapshot.desktop) "Desktop" else "Mobil"}")
        appendLine(
            "Screenshot: ${snapshot.segments.size} Segment(e) von oben nach unten, " +
                "${snapshot.screenshotWidth} x ${snapshot.screenshotHeight} Pixel insgesamt." +
                if (snapshot.truncated) " Die Seite ist länger, erfasst ist nur der obere Teil." else ""
        )
        appendLine()

        appendLine("== ELEMENTE (Typ | Text | x y w h normalisiert | Schriftgröße | Zusatz) ==")
        val elements = snapshot.elements.take(MAX_ELEMENTS)
        for (e in elements) {
            append(if (e.type.isNotBlank() && e.tag == "input") "input:${e.type}" else e.tag)
            append(" | \"").append(e.text).append("\" | ")
            append(formatBox(e.box))
            append(" | ").append(e.fontSizePx.toInt()).append("px")
            val extras = mutableListOf<String>()
            if (e.labeled == false) extras += "ohne Label"
            if (e.color.isNotBlank()) extras += "Farbe ${e.color}"
            if (e.background.isNotBlank() && !e.background.contains("0, 0, 0, 0")) extras += "Hintergrund ${e.background}"
            if (extras.isNotEmpty()) append(" | ").append(extras.joinToString(", "))
            appendLine()
        }
        if (snapshot.elements.size > elements.size) {
            appendLine("(${snapshot.elements.size - elements.size} weitere Elemente ausgelassen)")
        }
        appendLine()

        appendLine("== SICHTBARER TEXT (Auszug) ==")
        val text = snapshot.visibleText.replace(Regex("\\n{3,}"), "\n\n").trim()
        appendLine(text.take(MAX_TEXT_CHARS))
        if (text.length > MAX_TEXT_CHARS) appendLine("(gekürzt, insgesamt ${text.length} Zeichen)")
        appendLine()

        if (includeTech) {
            appendLine("== TECHNIKDATEN (gemessen) ==")
            for (c in tech) {
                val status = when (c.status) {
                    CheckStatus.OK -> "ok"
                    CheckStatus.WARN -> "Warnung"
                    CheckStatus.ERROR -> "Fehler"
                }
                appendLine("[$status] ${c.title}: ${c.finding}")
            }
        }
    }

    fun formatBox(box: Box): String =
        String.format(Locale.US, "%.3f %.3f %.3f %.3f", box.x, box.y, box.w, box.h)
}
