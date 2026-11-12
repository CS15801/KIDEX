package com.sitecouncil.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/** Liest ganze Zahlen auch dann, wenn das Modell 85.0 oder "85" liefert. */
object FlexIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0
        return primitive.content.trim().toDoubleOrNull()?.roundToInt() ?: 0
    }
}

/** Normalisierte Bounding Box, alle Werte im Bereich 0 bis 1. */
@Serializable
data class Box(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
) {
    fun clamped(): Box {
        val cx = x.coerceIn(0.0, 1.0)
        val cy = y.coerceIn(0.0, 1.0)
        return Box(cx, cy, w.coerceIn(0.0, 1.0 - cx), h.coerceIn(0.0, 1.0 - cy))
    }

    fun usable(): Boolean = w > 0.0 && h > 0.0

    fun contains(px: Double, py: Double): Boolean =
        px >= x && px <= x + w && py >= y && py <= y + h
}

@Serializable
data class Finding(
    val severity: String = "gering",
    val area: String = "",
    val box: Box? = null,
    val problem: String = "",
    val evidence: String = "",
    val fix: String = "",
    @Serializable(with = FlexIntSerializer::class) val impact: Int = 3,
    @Serializable(with = FlexIntSerializer::class) val effort: Int = 3,
)

@Serializable
data class ExpertVote(
    val key: String = "",
    val expert: String = "",
    @Serializable(with = FlexIntSerializer::class) val score: Int = 0,
    val verdict: String = "",
    val findings: List<Finding> = emptyList(),
)

@Serializable
enum class CheckStatus { OK, WARN, ERROR }

@Serializable
data class TechCheck(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val finding: String,
    val recommendation: String = "",
)

@Serializable
data class Persona(
    val id: String = "",
    val name: String = "",
    @Serializable(with = FlexIntSerializer::class) val age: Int = 0,
    @SerialName("digital_skill") val digitalSkill: String = "",
    val vision: String = "",
    val device: String = "",
    val hurry: String = "",
    @SerialName("prior_knowledge") val priorKnowledge: String = "",
    val motivation: String = "",
    @SerialName("language_level") val languageLevel: String = "",
    val attitude: String = "",
)

@Serializable
data class PersonaList(val personas: List<Persona> = emptyList())

@Serializable
data class AreaMention(val area: String = "", val box: Box? = null)

@Serializable
data class PersonaResult(
    val personaId: String = "",
    @SerialName("first_impression") val firstImpression: String = "",
    val understanding: String = "",
    @SerialName("next_action") val nextAction: String = "",
    @SerialName("click_target") val clickTarget: String = "",
    @SerialName("click_box") val clickBox: Box? = null,
    @SerialName("exit_point") val exitPoint: String = "",
    @SerialName("exit_reason") val exitReason: String = "",
    @SerialName("exit_category") val exitCategory: String = "",
    val missing: String = "",
    val confusing: String = "",
    @SerialName("task_success") val taskSuccess: String = "",
    val areas: List<AreaMention> = emptyList(),
)

@Serializable
data class CountItem(val label: String, val count: Int)

@Serializable
data class PersonaAggregate(
    val total: Int = 0,
    val yes: Int = 0,
    val partial: Int = 0,
    val no: Int = 0,
    val successRate: Double = 0.0,
    val exitReasons: List<CountItem> = emptyList(),
    val areas: List<CountItem> = emptyList(),
)

@Serializable
data class HeatBox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
    val intensity: Double = 0.0,
    val reason: String = "",
) {
    fun box(): Box = Box(x, y, w, h)
}

@Serializable
data class HeatmapData(
    val attention: List<HeatBox> = emptyList(),
    val change: List<HeatBox> = emptyList(),
)

@Serializable
data class Conflict(
    val topic: String = "",
    val experts: List<String> = emptyList(),
    val description: String = "",
    val resolution: String = "",
)

@Serializable
data class ActionItem(
    val title: String = "",
    val detail: String = "",
    val category: String = "",
    @Serializable(with = FlexIntSerializer::class) val impact: Int = 3,
    @Serializable(with = FlexIntSerializer::class) val effort: Int = 3,
    val sources: List<String> = emptyList(),
)

@Serializable
data class ModeratorResult(
    val summary: String = "",
    val conflicts: List<Conflict> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
)

@Serializable
data class UsageTotals(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val calls: Int = 0,
    val costUsd: Double = 0.0,
)

/** Vollständiges Ergebnis einer Analyse. Wird als JSON in Room abgelegt. */
@Serializable
data class AnalysisResult(
    val url: String,
    val finalUrl: String = "",
    val pageTitle: String = "",
    val audience: String = "",
    val goal: String = "",
    val desktop: Boolean = false,
    val createdAt: Long = 0L,
    val model: String = "",
    val totalScore: Int = 0,
    val categoryScores: Map<String, Int> = emptyMap(),
    val votes: List<ExpertVote> = emptyList(),
    val failedExperts: List<String> = emptyList(),
    val moderator: ModeratorResult = ModeratorResult(),
    val topActions: List<ActionItem> = emptyList(),
    val techChecks: List<TechCheck> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val personaResults: List<PersonaResult> = emptyList(),
    val personaAggregate: PersonaAggregate = PersonaAggregate(),
    val heatmap: HeatmapData = HeatmapData(),
    val screenshotPath: String = "",
    val screenshotWidth: Int = 0,
    val screenshotHeight: Int = 0,
    val truncated: Boolean = false,
    val usage: UsageTotals = UsageTotals(),
)

data class AnalysisRequest(
    val url: String,
    val audience: String,
    val goal: String,
    val desktop: Boolean,
)
