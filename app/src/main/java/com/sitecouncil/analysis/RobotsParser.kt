package com.sitecouncil.analysis

/** Kleiner robots.txt-Parser nach RFC 9309: längste passende Regel gewinnt, bei Gleichstand Allow. */
object RobotsParser {

    data class Verdict(
        val allowed: Boolean,
        /** true, wenn es eine eigene Gruppe für diesen Bot gibt (nicht nur "*"). */
        val specificGroup: Boolean,
    )

    private data class Rule(val allow: Boolean, val pattern: String)

    private data class Group(val agents: MutableList<String>, val rules: MutableList<Rule>)

    fun check(robotsTxt: String, bot: String, path: String): Verdict {
        val groups = parse(robotsTxt)
        val name = bot.lowercase()
        val specific = groups.filter { g -> g.agents.any { it == name } }
        val chosen = specific.ifEmpty { groups.filter { g -> g.agents.any { it == "*" } } }
        val rules = chosen.flatMap { it.rules }
        val target = path.ifBlank { "/" }

        var best: Rule? = null
        for (rule in rules) {
            if (rule.pattern.isEmpty()) continue
            if (!matches(rule.pattern, target)) continue
            val current = best
            if (current == null ||
                rule.pattern.length > current.pattern.length ||
                (rule.pattern.length == current.pattern.length && rule.allow && !current.allow)
            ) {
                best = rule
            }
        }
        return Verdict(allowed = best?.allow ?: true, specificGroup = specific.isNotEmpty())
    }

    /** Sitemap-Angaben aus der robots.txt. */
    fun sitemaps(robotsTxt: String): List<String> =
        robotsTxt.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.startsWith("sitemap:", ignoreCase = true) }
            .map { it.substringAfter(':').trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private fun parse(text: String): List<Group> {
        val groups = mutableListOf<Group>()
        var current: Group? = null
        var lastWasAgent = false
        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val field = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (field) {
                "user-agent" -> {
                    if (!lastWasAgent || current == null) {
                        current = Group(mutableListOf(), mutableListOf())
                        groups += current
                    }
                    current.agents += value.lowercase()
                    lastWasAgent = true
                }
                "allow", "disallow" -> {
                    current?.rules?.add(Rule(allow = field == "allow", pattern = value))
                    lastWasAgent = false
                }
                else -> lastWasAgent = false
            }
        }
        return groups
    }

    private fun matches(pattern: String, path: String): Boolean {
        val anchored = pattern.endsWith("$")
        val body = if (anchored) pattern.dropLast(1) else pattern
        val regex = buildString {
            append('^')
            for (c in body) {
                if (c == '*') append(".*") else append(Regex.escape(c.toString()))
            }
            if (anchored) append('$')
        }
        return Regex(regex).containsMatchIn(path)
    }
}
