package com.sitecouncil.analysis

import com.sitecouncil.data.AnthropicClient
import com.sitecouncil.data.AssetStore
import com.sitecouncil.data.HistoryRepository
import com.sitecouncil.data.SettingsStore
import com.sitecouncil.domain.AnalysisError
import com.sitecouncil.domain.AnalysisRequest
import com.sitecouncil.domain.AnalysisResult
import com.sitecouncil.domain.ExpertVote
import com.sitecouncil.domain.ModeratorResult
import com.sitecouncil.domain.Persona
import com.sitecouncil.domain.PersonaResult
import com.sitecouncil.domain.ScoreCalculator
import com.sitecouncil.domain.UrlValidator
import com.sitecouncil.domain.UsageTotals
import com.sitecouncil.experts.Council
import com.sitecouncil.experts.ExpertDef
import com.sitecouncil.experts.LlmRunner
import com.sitecouncil.experts.UsageMeter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

data class Step(
    val id: String,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String = "",
)

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val steps: List<Step>) : AnalysisState
    data class Done(val resultId: Long) : AnalysisState
    data class Failed(val error: AnalysisError, val steps: List<Step>) : AnalysisState
}

/**
 * Steuert eine Analyse von Anfang bis Ende und hält ihren Fortschritt.
 * Lebt als Singleton, damit eine laufende Analyse Bildschirmwechsel übersteht.
 */
@Singleton
class AnalysisController @Inject constructor(
    private val pageLoader: PageLoader,
    private val techChecker: TechChecker,
    private val client: AnthropicClient,
    private val assets: AssetStore,
    private val settings: SettingsStore,
    private val history: HistoryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(request: AnalysisRequest) {
        job?.cancel()
        _state.value = AnalysisState.Running(initialSteps())
        job = scope.launch {
            try {
                val id = withTimeout(TIMEOUT_MS) { execute(request) }
                _state.value = AnalysisState.Done(id)
            } catch (e: TimeoutCancellationException) {
                fail(AnalysisError.Timeout())
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnalysisError) {
                fail(e)
            } catch (e: Exception) {
                fail(AnalysisError.Unknown(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = AnalysisState.Idle
    }

    /** Nach dem Wechsel zum Ergebnis oder nach einer Fehlermeldung aufrufen. */
    fun reset() {
        if (_state.value !is AnalysisState.Running) _state.value = AnalysisState.Idle
    }

    private fun fail(error: AnalysisError) {
        val steps = (_state.value as? AnalysisState.Running)?.steps.orEmpty().map {
            if (it.status == StepStatus.RUNNING) it.copy(status = StepStatus.FAILED) else it
        }
        _state.value = AnalysisState.Failed(error, steps)
    }

    private fun initialSteps(): List<Step> = buildList {
        add(Step(STEP_LOAD))
        add(Step(STEP_TECH))
        ExpertDef.entries.forEach { add(Step(expertStep(it.key))) }
        add(Step(STEP_PERSONAS_GEN))
        add(Step(STEP_PERSONAS_RUN))
        add(Step(STEP_MODERATOR))
        add(Step(STEP_HEATMAP))
        add(Step(STEP_SAVE))
    }

    private fun mark(id: String, status: StepStatus, detail: String = "") {
        _state.update { current ->
            if (current is AnalysisState.Running) {
                current.copy(steps = current.steps.map {
                    if (it.id == id) it.copy(status = status, detail = detail) else it
                })
            } else {
                current
            }
        }
    }

    private suspend fun execute(request: AnalysisRequest): Long {
        if (settings.apiKey.isBlank()) throw AnalysisError.NoApiKey()
        val url = UrlValidator.normalize(request.url) ?: throw AnalysisError.InvalidUrl()
        val language = settings.language
        val model = settings.model
        val personaCount = settings.personaCount

        // Schritt 1: Seite laden
        mark(STEP_LOAD, StepStatus.RUNNING)
        val snapshot = pageLoader.load(url, request.desktop)
        mark(STEP_LOAD, StepStatus.DONE)

        // Schritt 2: Technik-Prüfungen ohne KI
        mark(STEP_TECH, StepStatus.RUNNING)
        val tech = techChecker.run(snapshot.url, snapshot.visibleText.length, snapshot.loadMs)
        mark(STEP_TECH, StepStatus.DONE)

        val meter = UsageMeter()
        val council = Council(
            llm = LlmRunner(client, meter, assets.load("prompts/repair.txt")),
            assets = assets,
            language = language,
        )
        val contextPlain = ContextBuilder.build(request, snapshot, tech, includeTech = false)
        val contextTech = ContextBuilder.build(request, snapshot, tech, includeTech = true)
        val images = snapshot.segments
        val lastError = AtomicReference<AnalysisError?>(null)

        // Schritt 3 und 4 laufen gleichzeitig: Experten und Personas brauchen einander nicht.
        val (votes, personaRun) = coroutineScope {
            val expertJobs = ExpertDef.entries.map { def ->
                async {
                    val stepId = expertStep(def.key)
                    mark(stepId, StepStatus.RUNNING)
                    guarded(stepId, lastError) {
                        council.expert(def, if (def.needsTech) contextTech else contextPlain, images)
                    }.also { if (it != null) mark(stepId, StepStatus.DONE) }
                }
            }
            val personaJob = async {
                runPersonas(council, personaCount, request, snapshot, contextPlain, images, lastError)
            }
            expertJobs.awaitAll().filterNotNull() to personaJob.await()
        }
        if (votes.isEmpty()) {
            throw AnalysisError.AllExpertsFailed(describe(lastError.get()))
        }
        val (personas, personaResults) = personaRun
        val aggregate = PersonaAggregator.aggregate(personaResults)

        // Moderator und Heatmap brauchen beide nur Voten und Persona-Ergebnisse.
        val (moderator, heatmap) = coroutineScope {
            val moderatorJob = async {
                mark(STEP_MODERATOR, StepStatus.RUNNING)
                guarded(STEP_MODERATOR, lastError) {
                    council.moderator(request.audience, request.goal, votes, aggregate, personaResults)
                }.also { if (it != null) mark(STEP_MODERATOR, StepStatus.DONE) }
            }
            val heatmapJob = async {
                mark(STEP_HEATMAP, StepStatus.RUNNING)
                guarded(STEP_HEATMAP, lastError) {
                    council.heatmap(contextPlain, images, votes, personaResults)
                }.also { if (it != null) mark(STEP_HEATMAP, StepStatus.DONE) }
            }
            moderatorJob.await() to heatmapJob.await()
        }

        mark(STEP_SAVE, StepStatus.RUNNING)
        val categoryScores = ExpertDef.entries
            .mapNotNull { def -> votes.firstOrNull { it.key == def.key }?.let { def.key to it.score } }
            .toMap()
        val moderatorResult = moderator ?: ModeratorResult()
        val actions = moderatorResult.actions.ifEmpty { ScoreCalculator.actionsFromFindings(votes) }
        val price = assets.priceFor(model)

        val result = AnalysisResult(
            url = url,
            finalUrl = snapshot.url,
            pageTitle = snapshot.title,
            audience = request.audience,
            goal = request.goal,
            desktop = request.desktop,
            createdAt = System.currentTimeMillis(),
            model = model,
            totalScore = ScoreCalculator.total(categoryScores, assets.weights),
            categoryScores = categoryScores,
            votes = votes,
            failedExperts = ExpertDef.entries.map { it.key }.filter { it !in categoryScores.keys },
            moderator = moderatorResult,
            topActions = ScoreCalculator.prioritize(actions).take(5),
            techChecks = tech,
            personas = personas,
            personaResults = personaResults,
            personaAggregate = aggregate,
            heatmap = heatmap ?: HeatmapFallback.fromResults(votes, personaResults, snapshot.elements),
            screenshotPath = snapshot.screenshotPath,
            screenshotWidth = snapshot.screenshotWidth,
            screenshotHeight = snapshot.screenshotHeight,
            truncated = snapshot.truncated,
            usage = UsageTotals(
                inputTokens = meter.inputTokens,
                outputTokens = meter.outputTokens,
                calls = meter.callCount,
                costUsd = price.cost(meter.inputTokens, meter.outputTokens),
            ),
        )
        val id = history.save(result)
        mark(STEP_SAVE, StepStatus.DONE)
        return id
    }

    private suspend fun runPersonas(
        council: Council,
        count: Int,
        request: AnalysisRequest,
        snapshot: PageSnapshot,
        context: String,
        images: List<String>,
        lastError: AtomicReference<AnalysisError?>,
    ): Pair<List<Persona>, List<PersonaResult>> {
        mark(STEP_PERSONAS_GEN, StepStatus.RUNNING)
        val personas = guarded(STEP_PERSONAS_GEN, lastError) {
            council.generatePersonas(count, request.audience, request.goal, snapshot.title, snapshot.url)
        }
        if (personas.isNullOrEmpty()) {
            mark(STEP_PERSONAS_GEN, StepStatus.FAILED)
            mark(STEP_PERSONAS_RUN, StepStatus.FAILED)
            return Pair(emptyList(), emptyList())
        }
        mark(STEP_PERSONAS_GEN, StepStatus.DONE)

        mark(STEP_PERSONAS_RUN, StepStatus.RUNNING, "0/${personas.size}")
        val finished = AtomicInteger(0)
        val results = coroutineScope {
            personas.map { persona ->
                async {
                    val result = guarded(null, lastError) {
                        council.runPersona(persona, request.goal, context, images)
                    }
                    mark(STEP_PERSONAS_RUN, StepStatus.RUNNING, "${finished.incrementAndGet()}/${personas.size}")
                    result
                }
            }.awaitAll().filterNotNull()
        }
        mark(
            STEP_PERSONAS_RUN,
            if (results.isEmpty()) StepStatus.FAILED else StepStatus.DONE,
            "${results.size}/${personas.size}",
        )
        return personas to results
    }

    /**
     * Fängt Fehler eines einzelnen Aufrufs ab, damit der Rest weiterläuft.
     * Abbruch und fatale Fehler (z. B. abgelehnter Key) gehen durch.
     */
    private suspend fun <T> guarded(
        stepId: String?,
        lastError: AtomicReference<AnalysisError?>,
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AnalysisError) {
        if (e.isFatal()) throw e
        lastError.set(e)
        if (stepId != null) mark(stepId, StepStatus.FAILED, describe(e))
        null
    } catch (e: Exception) {
        lastError.set(AnalysisError.Unknown(e.message ?: e.javaClass.simpleName))
        if (stepId != null) mark(stepId, StepStatus.FAILED)
        null
    }

    private fun describe(error: AnalysisError?): String = when (error) {
        null -> ""
        is AnalysisError.RateLimit -> "Rate Limit"
        is AnalysisError.InvalidJson -> "ungültiges JSON"
        is AnalysisError.Network -> "Netzwerk"
        else -> error.detail.take(120)
    }

    companion object {
        const val STEP_LOAD = "load"
        const val STEP_TECH = "tech"
        const val STEP_PERSONAS_GEN = "personas_gen"
        const val STEP_PERSONAS_RUN = "personas_run"
        const val STEP_MODERATOR = "moderator"
        const val STEP_HEATMAP = "heatmap"
        const val STEP_SAVE = "save"
        private const val EXPERT_PREFIX = "expert:"

        /** Obergrenze für die gesamte Analyse. */
        const val TIMEOUT_MS = 180_000L

        fun expertStep(key: String): String = EXPERT_PREFIX + key
        fun expertKeyOf(stepId: String): String? =
            if (stepId.startsWith(EXPERT_PREFIX)) stepId.removePrefix(EXPERT_PREFIX) else null
    }
}
