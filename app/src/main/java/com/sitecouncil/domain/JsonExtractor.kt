package com.sitecouncil.domain

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Holt ein JSON-Objekt aus einer Modellantwort, auch wenn davor oder danach
 * Text steht oder die Antwort in Code-Zäune gepackt ist.
 */
object JsonExtractor {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Liefert das erste vollständige Objekt ab [fromIndex] oder null.
     * Klammern innerhalb von Zeichenketten werden nicht mitgezählt.
     */
    fun extractObject(raw: String, fromIndex: Int = 0): String? {
        val start = raw.indexOf('{', fromIndex)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** Parst die Antwort oder wirft [AnalysisError.InvalidJson]. */
    fun <T> parse(raw: String, deserializer: DeserializationStrategy<T>): T {
        var from = 0
        var lastError = "kein JSON-Objekt gefunden"
        repeat(5) {
            val start = raw.indexOf('{', from)
            if (start < 0) throw AnalysisError.InvalidJson(lastError)
            val candidate = extractObject(raw, start)
            if (candidate != null) {
                try {
                    return json.decodeFromString(deserializer, candidate)
                } catch (e: Exception) {
                    lastError = e.message?.take(300) ?: "Parserfehler"
                }
            } else {
                lastError = "JSON-Objekt ist nicht abgeschlossen"
            }
            from = start + 1
        }
        throw AnalysisError.InvalidJson(lastError)
    }
}
