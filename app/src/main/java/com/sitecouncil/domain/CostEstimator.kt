package com.sitecouncil.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Preis in US-Dollar pro Million Tokens. */
@Serializable
data class Price(val input: Double = 0.0, val output: Double = 0.0) {
    fun cost(inputTokens: Int, outputTokens: Int): Double =
        inputTokens / 1_000_000.0 * input + outputTokens / 1_000_000.0 * output
}

@Serializable
data class PricingConfig(
    @SerialName("default") val fallback: Price = Price(),
    val models: Map<String, Price> = emptyMap(),
) {
    fun priceFor(model: String): Price = models[model] ?: fallback
}

data class CostEstimate(
    val calls: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double,
)

/**
 * Grobe Schätzung vor dem Start. Die Seite ist noch nicht geladen, deshalb
 * rechnet die Schätzung mit der maximalen Zahl an Screenshot-Segmenten.
 */
object CostEstimator {
    const val EXPERTS = 6
    private const val TOKENS_PER_SEGMENT = 1300
    private const val TEXT_CONTEXT_TOKENS = 5000
    private const val EXPERT_OUTPUT = 2200
    private const val PERSONA_OUTPUT = 700
    private const val HEATMAP_OUTPUT = 1800
    private const val MODERATOR_INPUT = 9000
    private const val MODERATOR_OUTPUT = 2500
    private const val PERSONA_GEN_INPUT = 800
    private const val PERSONA_GEN_OUTPUT_EACH = 160

    fun estimate(personaCount: Int, price: Price, segments: Int = 5): CostEstimate {
        val personas = personaCount.coerceIn(3, 12)
        val imageCallInput = segments * TOKENS_PER_SEGMENT + TEXT_CONTEXT_TOKENS
        val imageCalls = EXPERTS + personas + 1
        val input = imageCalls * imageCallInput + MODERATOR_INPUT + PERSONA_GEN_INPUT
        val output = EXPERTS * EXPERT_OUTPUT +
            personas * PERSONA_OUTPUT +
            HEATMAP_OUTPUT +
            MODERATOR_OUTPUT +
            personas * PERSONA_GEN_OUTPUT_EACH
        return CostEstimate(
            calls = imageCalls + 2,
            inputTokens = input,
            outputTokens = output,
            costUsd = price.cost(input, output),
        )
    }
}
