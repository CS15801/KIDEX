package com.sitecouncil.domain

import java.net.URI

object UrlValidator {

    /**
     * Ergänzt fehlendes https:// und prüft, ob die Adresse öffentlich aussieht.
     * Liefert die bereinigte URL oder null.
     */
    fun normalize(input: String): String? {
        var text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) text = "https://$text"
        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        if (!isPublicHost(host)) return null
        return uri.toString()
    }

    fun isPublicHost(host: String): Boolean {
        if (host.isBlank() || !host.contains('.')) return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
        if (host.contains(':')) return false
        val parts = host.split('.')
        val numeric = parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
        if (numeric) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            if (a == 10 || a == 127 || a == 0) return false
            if (a == 192 && b == 168) return false
            if (a == 172 && b in 16..31) return false
            if (a == 169 && b == 254) return false
            return true
        }
        val tld = parts.last()
        return tld.length >= 2 && tld.any { it.isLetter() } && parts.none { it.isEmpty() }
    }
}
