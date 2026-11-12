package com.sitecouncil.experts

import com.sitecouncil.data.AnthropicClient
import com.sitecouncil.data.ApiReply
import com.sitecouncil.domain.AnalysisError
import com.sitecouncil.domain.JsonExtractor
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.DeserializationStrategy
import java.util.concurrent.atomic.AtomicInteger

/** Zählt Tokens und Aufrufe einer Analyse. */
class UsageMeter {
    private val input = AtomicInteger(0)
    private val output = AtomicInteger(0)
    private val calls = AtomicInteger(0)

    fun add(reply: ApiReply) {
        input.addAndGet(reply.inputTokens)
        output.addAndGet(reply.outputTokens)
        calls.incrementAndGet()
    }

    val inputTokens: Int get() = input.get()
    val outputTokens: Int get() = output.get()
    val callCount: Int get() = calls.get()
}

/**
 * Ein Modellaufruf, der JSON liefern muss. Lässt sich die Antwort nicht
 * parsen, folgt genau ein Reparaturversuch.
 */
class LlmRunner(
    private val client: AnthropicClient,
    private val meter: UsageMeter,
    private val repairPrompt: String,
    parallelCalls: Int = 6,
) {
    private val gate = Semaphore(parallelCalls)

    suspend fun <T> askJson(
        system: String,
        user: String,
        images: List<String>,
        maxTokens: Int,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val first = gate.withPermit { client.message(system, user, images, maxTokens) }
        meter.add(first)

        val parseError = try {
            return JsonExtractor.parse(first.text, deserializer)
        } catch (e: AnalysisError.InvalidJson) {
            e.detail
        }

        val repairInput = buildString {
            appendLine("FEHLERMELDUNG DES PARSERS")
            appendLine(parseError)
            appendLine()
            appendLine("ANTWORT, DIE REPARIERT WERDEN SOLL")
            append(first.text.take(MAX_REPAIR_CHARS))
        }
        val second = gate.withPermit { client.message(repairPrompt, repairInput, emptyList(), maxTokens) }
        meter.add(second)
        return JsonExtractor.parse(second.text, deserializer)
    }

    companion object {
        private const val MAX_REPAIR_CHARS = 24_000
    }
}
