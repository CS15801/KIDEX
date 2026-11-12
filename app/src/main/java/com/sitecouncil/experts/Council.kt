package com.sitecouncil.experts

import com.sitecouncil.analysis.ContextBuilder
import com.sitecouncil.analysis.HeatmapFallback
import com.sitecouncil.data.AssetStore
import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.HeatmapData
import com.sitecouncil.domain.JsonExtractor
import com.sitecouncil.domain.ModeratorResult
import com.sitecouncil.domain.Persona
import com.sitecouncil.domain.PersonaAggregate
import com.sitecouncil.domain.PersonaList
import com.sitecouncil.domain.PersonaResult
import kotlinx.serialization.builtins.ListSerializer

/**
 * Alle Modellaufrufe des Rats: Experten, Moderator, Personas, Heatmap.
 * Die Klasse kennt nur Prompts und Datenformate, die Ablaufsteuerung liegt im AnalysisController.
 */
class Council(
    private val llm: LlmRunner,
    private val assets: AssetStore,
    private val language: String,
) {
    suspend fun expert(def: ExpertDef, context: String, images: List<String>): ExpertVote {
        val vars = mapOf("language" to language, "expert_name" to def.promptName)
        val system = assets.load(def.asset).trim() + "\n\n" + assets.render("prompts/common.txt", vars)
        val vote = llm.askJson(system, context, images, 3500, ExpertVote.serializer())
        return vote.copy(
            key = def.key,
            expert = vote.expert.ifBlank { def.promptName },
            score = vote.score.coerceIn(0, 100),
            findings = vote.findings.take(8).map {
                it.copy(
                    box = it.box?.clamped()?.takeIf { b -> b.usable() },
                    impact = it.impact.coerceIn(1, 5),
                    effort = it.effort.coerceIn(1, 5),
                )
            },
        )
    }

    suspend fun moderator(
        audience: String,
        goal: String,
        votes: List<ExpertVote>,
        aggregate: PersonaAggregate,
        personaResults: List<PersonaResult>,
    ): ModeratorResult {
        val system = assets.render("prompts/moderator.txt", mapOf("language" to language))
        val user = buildString {
            appendLine("Zielgruppe: ${audience.ifBlank { "(nicht angegeben)" }}")
            appendLine("Seitenziel: ${goal.ifBlank { "(nicht angegeben)" }}")
            appendLine()
            appendLine("== EXPERTENVOTEN ==")
            appendLine(JsonExtractor.json.encodeToString(ListSerializer(ExpertVote.serializer()), votes))
            appendLine()
            appendLine("== PERSONA-TESTLÄUFE ==")
            if (personaResults.isEmpty()) {
                appendLine("(keine Ergebnisse)")
            } else {
                appendLine("Erfolg: ${aggregate.yes} ja, ${aggregate.partial} teilweise, ${aggregate.no} nein")
                appendLine("Ausstiegsgründe: " + aggregate.exitReasons.joinToString(", ") { "${it.label} (${it.count})" })
                for (r in personaResults) {
                    appendLine("* ${r.personaId}: Ausstieg \"${r.exitPoint}\" wegen \"${r.exitReason}\". Fehlt: \"${r.missing}\". Verwirrt: \"${r.confusing}\"")
                }
            }
        }
        val result = llm.askJson(system, user, emptyList(), 4000, ModeratorResult.serializer())
        return result.copy(
            actions = result.actions.map {
                it.copy(impact = it.impact.coerceIn(1, 5), effort = it.effort.coerceIn(1, 5))
            }
        )
    }

    suspend fun generatePersonas(count: Int, audience: String, goal: String, pageTitle: String, url: String): List<Persona> {
        val system = assets.render(
            "prompts/personas_generate.txt",
            mapOf("language" to language, "count" to count.toString()),
        )
        val user = buildString {
            appendLine("Website: $url")
            appendLine("Seitentitel: ${pageTitle.ifBlank { "(leer)" }}")
            appendLine("Zielgruppe: ${audience.ifBlank { "(nicht angegeben)" }}")
            appendLine("Seitenziel: ${goal.ifBlank { "(nicht angegeben)" }}")
        }
        val list = llm.askJson(system, user, emptyList(), 300 * count + 500, PersonaList.serializer())
        return list.personas.take(count).mapIndexed { i, p ->
            p.copy(id = "p${i + 1}", name = p.name.ifBlank { "Persona ${i + 1}" })
        }
    }

    suspend fun runPersona(persona: Persona, goal: String, context: String, images: List<String>): PersonaResult {
        val description = buildString {
            appendLine("Name: ${persona.name}, ${persona.age} Jahre")
            appendLine("Digitalkompetenz: ${persona.digitalSkill}")
            appendLine("Sehvermögen: ${persona.vision}")
            appendLine("Gerät: ${persona.device}")
            appendLine("Eile: ${persona.hurry}")
            appendLine("Vorwissen: ${persona.priorKnowledge}")
            appendLine("Motivation: ${persona.motivation}")
            appendLine("Sprachniveau: ${persona.languageLevel}")
            append("Haltung: ${persona.attitude}")
        }
        val system = assets.render(
            "prompts/persona_run.txt",
            mapOf(
                "language" to language,
                "persona" to description,
                "goal" to goal.ifBlank { "(nicht angegeben)" },
            ),
        )
        val result = llm.askJson(system, context, images, 1500, PersonaResult.serializer())
        return result.copy(
            personaId = persona.id,
            clickBox = result.clickBox?.clamped()?.takeIf { it.usable() },
            areas = result.areas.take(4).map { a -> a.copy(box = a.box?.clamped()?.takeIf { it.usable() }) },
        )
    }

    suspend fun heatmap(
        context: String,
        images: List<String>,
        votes: List<ExpertVote>,
        personaResults: List<PersonaResult>,
    ): HeatmapData {
        val system = assets.render("prompts/heatmap.txt", mapOf("language" to language))
        val user = buildString {
            append(context)
            appendLine()
            appendLine("== BEFUNDE DER EXPERTEN (Schwere | Experte | Bereich | x y w h | Problem | Änderung) ==")
            for (vote in votes) {
                for (f in vote.findings) {
                    val box = f.box?.let { ContextBuilder.formatBox(it) } ?: "ohne Position"
                    appendLine("${f.severity} | ${vote.expert} | ${f.area} | $box | ${f.problem} | ${f.fix}")
                }
            }
            appendLine()
            appendLine("== PERSONAS (Klickziel | x y w h | Ausstieg | Grund) ==")
            for (r in personaResults) {
                val box = r.clickBox?.let { ContextBuilder.formatBox(it) } ?: "ohne Position"
                appendLine("${r.clickTarget} | $box | ${r.exitPoint} | ${r.exitReason}")
                for (a in r.areas) {
                    val areaBox = a.box?.let { ContextBuilder.formatBox(it) } ?: "ohne Position"
                    appendLine("  erwähnt: ${a.area} | $areaBox")
                }
            }
        }
        val data = llm.askJson(system, user, images, 3500, HeatmapData.serializer())
        return HeatmapFallback.sanitize(data)
    }
}
