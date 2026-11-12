package com.sitecouncil.analysis

import com.sitecouncil.domain.Box

/** Ein sichtbares Element der Seite mit normalisierter Position. */
data class PageElement(
    val tag: String,
    val type: String,
    val text: String,
    val box: Box,
    val fontSizePx: Double,
    val color: String,
    val background: String,
    /** Nur bei Formularfeldern gesetzt: hat das Feld eine Beschriftung? */
    val labeled: Boolean?,
)

/** Alles, was nach dem Laden der Seite für die Analyse gebraucht wird. */
data class PageSnapshot(
    val url: String,
    val title: String,
    val lang: String,
    val renderedHtml: String,
    val visibleText: String,
    val elements: List<PageElement>,
    /** JPEG-Segmente als Base64, von oben nach unten. */
    val segments: List<String>,
    val screenshotPath: String,
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    /** true, wenn die Seite länger ist als der erfasste Bereich. */
    val truncated: Boolean,
    val desktop: Boolean,
    val loadMs: Long,
)
